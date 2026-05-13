package com.altimetrik.interview.service;

import com.altimetrik.interview.dto.AiQuestionGenerationRequest;
import com.altimetrik.interview.dto.AiQuestionResponse;
import com.altimetrik.interview.dto.ExecuteRequest;
import com.altimetrik.interview.dto.ExecuteResponse;
import com.altimetrik.interview.dto.PreparationAccessResponse;
import com.altimetrik.interview.dto.PreparationAttemptResponse;
import com.altimetrik.interview.dto.PreparationQuestionResponse;
import com.altimetrik.interview.dto.PreparationRegistrationRequest;
import com.altimetrik.interview.dto.PreparationRunRequest;
import com.altimetrik.interview.dto.PreparationRunResponse;
import com.altimetrik.interview.dto.PreparationSubmitResponse;
import com.altimetrik.interview.dto.VerifyOtpRequest;
import com.altimetrik.interview.entity.InterviewQuestionBank;
import com.altimetrik.interview.entity.PreparationAttempt;
import com.altimetrik.interview.entity.PreparationQuestionAssignment;
import com.altimetrik.interview.entity.QuestionSeries;
import com.altimetrik.interview.enums.EvaluationStyle;
import com.altimetrik.interview.enums.ExecutionLanguage;
import com.altimetrik.interview.enums.ExecutionPriority;
import com.altimetrik.interview.enums.PreparationAttemptStatus;
import com.altimetrik.interview.enums.PreparationQuestionStatus;
import com.altimetrik.interview.enums.QuestionSource;
import com.altimetrik.interview.enums.QuestionStarterType;
import com.altimetrik.interview.enums.QuestionSourceFlow;
import com.altimetrik.interview.enums.TechnologySkill;
import com.altimetrik.interview.repository.InterviewQuestionBankRepository;
import com.altimetrik.interview.repository.PreparationAttemptRepository;
import com.altimetrik.interview.repository.PreparationQuestionAssignmentRepository;
import com.altimetrik.interview.repository.QuestionSeriesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class PreparationService {

    private static final int ACCESS_VALID_HOURS = 72;
    private static final int MAX_OTP_RESENDS = 2;
    private static final int ATTEMPT_DURATION_MINUTES = 60;
    private static final int QUESTION_DURATION_MINUTES = 20;
    private static final int QUESTION_GENERATION_ATTEMPTS = 3;
    private static final int OTP_RESEND_COOLDOWN_SECONDS = 120;
    private static final String OTP_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String INTERNAL_DOMAIN = "altimetrik.com";

    private final PreparationAttemptRepository preparationAttemptRepository;
    private final PreparationQuestionAssignmentRepository assignmentRepository;
    private final QuestionSeriesRepository questionSeriesRepository;
    private final InterviewQuestionBankRepository questionBankRepository;
    private final AiInterviewClientService aiInterviewClientService;
    private final SandboxClientService sandboxClientService;
    private final EmailService emailService;
    private final CandidateQuestionHistoryService candidateQuestionHistoryService;
    private final BanyanCandidateContentService banyanCandidateContentService;

    @Value("${app.public-origin:http://localhost:3000}")
    private String publicOrigin;

    @Transactional
    public PreparationAttemptResponse register(PreparationRegistrationRequest request) {
        TechnologySkill technology = requirePreparationTechnology(request.getTechnology());
        OffsetDateTime now = nowUtc();
        String emailNormalized = normalizeEmail(request.getEmail());
        ensureRegistrationAllowed(emailNormalized, now);

        PreparationAttempt attempt = new PreparationAttempt();
        attempt.setId(UUID.randomUUID().toString());
        attempt.setCandidateName(request.getCandidateName().trim());
        attempt.setEmail(request.getEmail().trim());
        attempt.setEmailNormalized(emailNormalized);
        attempt.setTechnology(technology);
        attempt.setYearsOfExperience(request.getYearsOfExperience());
        attempt.setExperienceBand(experienceBand(request.getYearsOfExperience()));
        attempt.setTargetRole(request.getTargetRole().trim());
        attempt.setSecureToken(UUID.randomUUID().toString());
        attempt.setOtpResendCount(0);
        String otp = issueOtp(attempt, now, false);
        attempt.setStatus(PreparationAttemptStatus.OTP_PENDING);
        PreparationAttempt saved = preparationAttemptRepository.save(attempt);
        sendPreparationAccessEmail(saved, otp);

        return toAttemptResponse(saved, sentMessage("Access details and one-time passcode have been sent to", saved));
    }

    @Transactional(readOnly = true)
    public Page<PreparationAttemptResponse> listAttempts(String search, Pageable pageable) {
        Page<PreparationAttempt> page = search == null || search.isBlank()
                ? preparationAttemptRepository.findAll(pageable)
                : preparationAttemptRepository.findByEmailNormalizedContainingIgnoreCaseOrCandidateNameContainingIgnoreCase(
                search.trim(), search.trim(), pageable);
        return page.map(attempt -> toAttemptResponse(attempt, null));
    }

    @Transactional
    public PreparationAttemptResponse resendOtp(String attemptId) {
        PreparationAttempt attempt = preparationAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate preparation record was not found."));
        expireAccessIfNeeded(attempt);

        if (attempt.getOtpVerifiedAt() != null
                || attempt.getStatus() == PreparationAttemptStatus.ACTIVE
                || attempt.getStatus() == PreparationAttemptStatus.COMPLETED
                || attempt.getStatus() == PreparationAttemptStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Candidate access has already been used. Create a new registration if another attempt is required.");
        }
        if (remainingOtpResends(attempt) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The passcode has already been resent twice for this candidate.");
        }

        OffsetDateTime now = nowUtc();
        if (attempt.getOtpIssuedAt() != null && attempt.getOtpIssuedAt().plusSeconds(OTP_RESEND_COOLDOWN_SECONDS).isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Please wait a short while before requesting another passcode.");
        }

        String otp = issueOtp(attempt, now, true);
        attempt.setStatus(PreparationAttemptStatus.OTP_PENDING);
        attempt.setCompletedAt(null);
        attempt.setCompletionReason(null);
        PreparationAttempt saved = preparationAttemptRepository.save(attempt);
        sendPreparationAccessEmail(saved, otp);
        return toAttemptResponse(saved, sentMessage("A new one-time passcode has been sent to", saved));
    }

    @Transactional
    public PreparationAccessResponse getAccess(String token) {
        PreparationAttempt attempt = getRequiredAttempt(token);
        expireAccessIfNeeded(attempt);
        return toAccessResponse(attempt, "Preparation access is available.");
    }

    @Transactional
    public PreparationAccessResponse acceptDisclaimer(String token) {
        PreparationAttempt attempt = getRequiredAttempt(token);
        expireAccessIfNeeded(attempt);
        if (attempt.getStatus() == PreparationAttemptStatus.EXPIRED) {
            throw new ResponseStatusException(HttpStatus.GONE, "This preparation access link has expired.");
        }
        if (attempt.getDisclaimerAcceptedAt() == null) {
            attempt.setDisclaimerAcceptedAt(nowUtc());
            preparationAttemptRepository.save(attempt);
        }
        return toAccessResponse(attempt, "Preparation guidelines accepted. Please enter the passcode sent to your registered email address.");
    }

    @Transactional
    public PreparationAccessResponse verifyOtp(String token, VerifyOtpRequest request) {
        PreparationAttempt attempt = getRequiredAttempt(token);
        expireAccessIfNeeded(attempt);
        if (attempt.getStatus() == PreparationAttemptStatus.EXPIRED) {
            throw new ResponseStatusException(HttpStatus.GONE, "This preparation access link has expired.");
        }
        if (attempt.getDisclaimerAcceptedAt() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Please review and accept the preparation guidelines before entering the passcode.");
        }
        String otp = request == null || request.getOtp() == null
                ? ""
                : request.getOtp().trim().toUpperCase(Locale.ROOT);
        if (!hash(otp).equals(attempt.getOtpHash())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The passcode is incorrect. Please enter the latest passcode sent to your registered email address.");
        }
        if (attempt.getOtpVerifiedAt() == null) {
            attempt.setOtpVerifiedAt(nowUtc());
        }
        attempt.setStatus(PreparationAttemptStatus.ACTIVE);
        preparationAttemptRepository.save(attempt);
        return toAccessResponse(attempt, "Passcode verified successfully.");
    }

    @Transactional
    public PreparationAccessResponse expireAttempt(String token) {
        PreparationAttempt attempt = getRequiredAttempt(token);
        expireAccessIfNeeded(attempt);
        if (attempt.getStatus() == PreparationAttemptStatus.ACTIVE) {
            attempt.setStatus(PreparationAttemptStatus.EXPIRED);
            attempt.setCompletedAt(nowUtc());
            attempt.setCompletionReason("Preparation Mode ended before the current level was completed.");
            expireCurrentAssignment(attempt);
            preparationAttemptRepository.save(attempt);
        }
        return toAccessResponse(attempt, attempt.getCompletionReason() == null ? "Preparation Mode has ended." : attempt.getCompletionReason());
    }

    @Transactional
    public PreparationQuestionResponse currentQuestion(String token) {
        PreparationAttempt attempt = requireActiveAttempt(token);
        expireQuestionIfNeeded(attempt);
        if (attempt.getStatus() != PreparationAttemptStatus.ACTIVE) {
            OffsetDateTime attemptExpiresAt = attemptExpiresAt(attempt);
            return PreparationQuestionResponse.builder()
                    .attemptId(attempt.getId())
                    .attemptExpiresAt(attemptExpiresAt)
                    .remainingAttemptSeconds(remainingSeconds(attemptExpiresAt))
                    .attemptEnded(true)
                    .message(attempt.getCompletionReason())
                    .build();
        }
        InterviewQuestionBank question = attempt.getCurrentQuestionId() == null
                ? assignQuestion(attempt, 1)
                : questionBankRepository.findById(attempt.getCurrentQuestionId())
                .orElseGet(() -> assignQuestion(attempt, attempt.getCurrentSequenceNumber() == null ? 1 : attempt.getCurrentSequenceNumber()));
        return toQuestionResponse(attempt, question, "Question is ready.");
    }

    @Transactional
    public PreparationRunResponse run(String token, PreparationRunRequest request) {
        PreparationAttempt attempt = requireActiveAttempt(token);
        expireQuestionIfNeeded(attempt);
        if (attempt.getStatus() != PreparationAttemptStatus.ACTIVE) {
            return PreparationRunResponse.builder()
                    .attemptEnded(true)
                    .passed(false)
                    .message(attempt.getCompletionReason())
                    .build();
        }
        InterviewQuestionBank question = currentQuestionEntity(attempt);
        if (!validationAssertionsIntact(question.getStarterCode(), request.getSourceCode())) {
            return PreparationRunResponse.builder()
                    .passed(false)
                    .attemptEnded(false)
                    .message("The validation checks must remain unchanged. Restore the original checks before running again.")
                    .question(toQuestionResponse(attempt, question, null))
                    .build();
        }
        incrementExecuteAttemptCount(attempt, question);
        ExecuteResponse execution = executeLowPriority(attempt, request.getSourceCode());
        boolean passed = isAssertionPass(execution);
        return PreparationRunResponse.builder()
                .execution(execution)
                .passed(passed)
                .attemptEnded(false)
                .message(passed ? "Validation passed. Submit to continue." : "Validation failed. Review the output and try again.")
                .question(toQuestionResponse(attempt, question, null))
                .build();
    }

    @Transactional
    public PreparationSubmitResponse submit(String token, PreparationRunRequest request) {
        PreparationAttempt attempt = requireActiveAttempt(token);
        expireQuestionIfNeeded(attempt);
        if (attempt.getStatus() != PreparationAttemptStatus.ACTIVE) {
            return PreparationSubmitResponse.builder()
                    .attemptEnded(true)
                    .passed(false)
                    .message(attempt.getCompletionReason())
                    .build();
        }

        InterviewQuestionBank currentQuestion = currentQuestionEntity(attempt);
        if (!validationAssertionsIntact(currentQuestion.getStarterCode(), request.getSourceCode())) {
            return PreparationSubmitResponse.builder()
                    .passed(false)
                    .attemptEnded(false)
                    .message("The validation checks must remain unchanged. Restore the original checks before submitting.")
                    .nextQuestion(toQuestionResponse(attempt, currentQuestion, null))
                    .build();
        }
        ExecuteResponse execution = executeLowPriority(attempt, request.getSourceCode());
        boolean passed = isAssertionPass(execution);
        if (!passed) {
            return PreparationSubmitResponse.builder()
                    .execution(execution)
                    .passed(false)
                    .attemptEnded(false)
                    .message("Validation failed. The solution cannot be submitted yet.")
                    .nextQuestion(toQuestionResponse(attempt, currentQuestion, null))
                    .build();
        }

        markQuestionPassed(attempt, currentQuestion);
        InterviewQuestionBank nextQuestion = assignQuestion(attempt, safeSequence(currentQuestion.getSequenceNumber()) + 1, request.getSourceCode());
        return PreparationSubmitResponse.builder()
                .execution(execution)
                .passed(true)
                .attemptEnded(false)
                .message("Level submitted. The next Banyan level is ready.")
                .nextQuestion(toQuestionResponse(attempt, nextQuestion, "Next question is ready."))
                .build();
    }

    private void ensureRegistrationAllowed(String emailNormalized, OffsetDateTime now) {
        if (isInternalEmail(emailNormalized)) {
            return;
        }
        List<PreparationAttempt> attempts = preparationAttemptRepository.findByEmailNormalizedOrderByCreatedAtDesc(emailNormalized);
        if (attempts.size() < 2) {
            return;
        }
        OffsetDateTime anchor = attempts.get(0).getCreatedAt();
        if (anchor == null) {
            anchor = now;
        }
        OffsetDateTime nextAllowed = anchor.plusMonths(6);
        if (now.isBefore(nextAllowed)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "External candidates can register for Preparation Mode only two times in six months. The next attempt is available after " + nextAllowed.toLocalDate() + ".");
        }
    }

    private InterviewQuestionBank assignQuestion(PreparationAttempt attempt, int sequenceNumber) {
        return assignQuestion(attempt, sequenceNumber, null);
    }

    private InterviewQuestionBank assignQuestion(PreparationAttempt attempt, int sequenceNumber, String previousSubmittedSource) {
        InterviewQuestionBank question = previousSubmittedSource == null || previousSubmittedSource.isBlank()
                ? findReusableQuestion(attempt, sequenceNumber)
                : null;
        if (question == null) {
            question = generateAndSaveQuestion(attempt, sequenceNumber, previousSubmittedSource);
        }
        OffsetDateTime now = nowUtc();
        attempt.setCurrentQuestionId(question.getId());
        attempt.setCurrentSeriesId(question.getSeriesId());
        attempt.setCurrentSequenceNumber(question.getSequenceNumber());
        attempt.setQuestionStartedAt(now);
        attempt.setQuestionExpiresAt(now.plusMinutes(QUESTION_DURATION_MINUTES));
        preparationAttemptRepository.save(attempt);

        PreparationQuestionAssignment assignment = new PreparationQuestionAssignment();
        assignment.setId(UUID.randomUUID().toString());
        assignment.setAttemptId(attempt.getId());
        assignment.setEmailNormalized(attempt.getEmailNormalized());
        assignment.setQuestionId(question.getId());
        assignment.setSeriesId(question.getSeriesId());
        assignment.setSequenceNumber(question.getSequenceNumber());
        assignment.setStatus(PreparationQuestionStatus.ASSIGNED);
        assignment.setAssignedAt(now);
        assignment.setExecuteAttemptCount(0);
        assignmentRepository.save(assignment);
        candidateQuestionHistoryService.recordAssignment(
                attempt.getEmailNormalized(),
                attempt.getTechnology(),
                attempt.getYearsOfExperience(),
                attempt.getTargetRole(),
                EvaluationStyle.BANYAN,
                question,
                QuestionSourceFlow.PREPARATION,
                attempt.getId()
        );
        return question;
    }

    private InterviewQuestionBank findReusableQuestion(PreparationAttempt attempt, int sequenceNumber) {
        if (sequenceNumber > 1 && attempt.getCurrentSeriesId() != null) {
            Set<String> seenQuestions = candidateQuestionHistoryService.seenQuestionIds(
                    attempt.getEmailNormalized(),
                    attempt.getTechnology(),
                    attempt.getYearsOfExperience(),
                    attempt.getTargetRole(),
                    EvaluationStyle.BANYAN
            );
            return questionBankRepository.findFirstBySeriesIdAndSequenceNumberAndActiveTrue(attempt.getCurrentSeriesId(), sequenceNumber)
                    .filter(question -> !seenQuestions.contains(question.getId()))
                    .filter(question -> validateReusableQuestion(attempt, question))
                    .orElse(null);
        }

        Set<String> seenQuestions = candidateQuestionHistoryService.seenQuestionIds(
                attempt.getEmailNormalized(),
                attempt.getTechnology(),
                attempt.getYearsOfExperience(),
                attempt.getTargetRole(),
                EvaluationStyle.BANYAN
        );
        Set<String> seenSeries = candidateQuestionHistoryService.seenSeriesIds(
                attempt.getEmailNormalized(),
                attempt.getTechnology(),
                attempt.getYearsOfExperience(),
                attempt.getTargetRole(),
                EvaluationStyle.BANYAN
        );
        Map<String, Long> seriesUsage = candidateQuestionHistoryService.seriesUsageCounts(
                attempt.getTechnology(),
                attempt.getYearsOfExperience(),
                attempt.getTargetRole(),
                EvaluationStyle.BANYAN
        );
        List<InterviewQuestionBank> pool = questionSeriesRepository.findByTechnologyAndEvaluationStyleAndExperienceBandAndActiveTrueOrderByCreatedAtAsc(
                        attempt.getTechnology(), EvaluationStyle.BANYAN, attempt.getExperienceBand())
                .stream()
                .filter(series -> series.getId() == null || !seenSeries.contains(series.getId()))
                .map(series -> questionBankRepository.findFirstBySeriesIdAndSequenceNumberAndStarterTypeAndActiveTrue(
                        series.getId(), 1, QuestionStarterType.BUG_FIX).orElse(null))
                .filter(Objects::nonNull)
                .filter(question -> !seenQuestions.contains(question.getId()))
                .filter(question -> validateReusableQuestion(attempt, question))
                .toList();
        return selectLeastUsedQuestion(pool, seriesUsage, true);
    }

    private InterviewQuestionBank selectLeastUsedQuestion(List<InterviewQuestionBank> pool,
                                                          Map<String, Long> usageCounts,
                                                          boolean bySeries) {
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        long lowestUsage = pool.stream()
                .mapToLong(question -> usageCounts.getOrDefault(usageKey(question, bySeries), 0L))
                .min()
                .orElse(0L);
        List<InterviewQuestionBank> leastUsed = pool.stream()
                .filter(question -> usageCounts.getOrDefault(usageKey(question, bySeries), 0L) == lowestUsage)
                .toList();
        return leastUsed.get(ThreadLocalRandom.current().nextInt(leastUsed.size()));
    }

    private String usageKey(InterviewQuestionBank question, boolean bySeries) {
        String key = bySeries ? question.getSeriesId() : question.getId();
        return key == null ? "" : key;
    }

    private boolean validateReusableQuestion(PreparationAttempt attempt, InterviewQuestionBank question) {
        PreparationQuestionValidationResult validation = validateQuestion(attempt, question);
        if (validation.valid()) {
            return true;
        }
        question.setActive(false);
        questionBankRepository.save(question);
        log.warn("Preparation question skipped because validation failed attemptId={} questionId={} reason={}",
                attempt.getId(), question.getId(), validation.reason());
        return false;
    }

    private InterviewQuestionBank generateAndSaveQuestion(PreparationAttempt attempt, int sequenceNumber, String previousSubmittedSource) {
        QuestionSeries series = sequenceNumber > 1 && attempt.getCurrentSeriesId() != null
                ? questionSeriesRepository.findById(attempt.getCurrentSeriesId()).orElseGet(() -> createGeneratedSeries(attempt))
                : createGeneratedSeries(attempt);
        InterviewQuestionBank previousQuestion = sequenceNumber > 1
                ? questionBankRepository.findFirstBySeriesIdAndSequenceNumberAndActiveTrue(series.getId(), sequenceNumber - 1).orElse(null)
                : null;
        List<String> failures = new ArrayList<>();
        for (int generationAttempt = 1; generationAttempt <= QUESTION_GENERATION_ATTEMPTS; generationAttempt++) {
            AiQuestionResponse aiQuestion = aiInterviewClientService.generateQuestion(buildAiRequest(attempt, series, previousQuestion, previousSubmittedSource, sequenceNumber, generationAttempt, failures));
            InterviewQuestionBank question = toQuestionBank(attempt, series, sequenceNumber, aiQuestion);
            PreparationQuestionValidationResult validation = validateQuestion(attempt, question);
            if (validation.valid()) {
                validation = validateBanyanExtension(previousQuestion, previousSubmittedSource, question);
            }
            if (validation.valid()) {
                return questionBankRepository.save(question);
            }
            failures.add("Attempt " + generationAttempt + ": " + validation.reason());
            log.warn("Generated Preparation question failed validation attemptId={} sequenceNumber={} generationAttempt={} reason={}",
                    attempt.getId(), sequenceNumber, generationAttempt, validation.reason());
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Unable to prepare a validated question right now. " + String.join(" | ", failures));
    }

    private InterviewQuestionBank toQuestionBank(PreparationAttempt attempt,
                                                 QuestionSeries series,
                                                 int sequenceNumber,
                                                 AiQuestionResponse aiQuestion) {
        InterviewQuestionBank question = new InterviewQuestionBank();
        question.setId("prep-ai-" + UUID.randomUUID());
        question.setTechnology(attempt.getTechnology());
        question.setDifficultyLevel(normalizeDifficulty(sequenceNumber));
        question.setSeriesId(series.getId());
        question.setSequenceNumber(sequenceNumber);
        question.setBanyanLevel(sequenceNumber);
        question.setEvaluationStyle(EvaluationStyle.BANYAN);
        question.setExperienceBand(attempt.getExperienceBand());
        question.setTargetRole(attempt.getTargetRole());
        question.setProblemFamilyKey(series.getProblemFamilyKey());
        question.setStarterType(sequenceNumber == 1 ? QuestionStarterType.BUG_FIX : QuestionStarterType.EXTENSION);
        question.setSource(QuestionSource.AI_GENERATED);
        question.setTitle(valueOrDefault(aiQuestion == null ? null : aiQuestion.getTitle(), "Banyan Level " + sequenceNumber));
        question.setFilePath(valueOrDefault(aiQuestion == null ? null : aiQuestion.getFilePath(), attempt.getTechnology() == TechnologySkill.PYTHON ? "banyan.py" : "Banyan.java"));
        question.setDisplayName(valueOrDefault(aiQuestion == null ? null : aiQuestion.getDisplayName(), "Banyan Level " + sequenceNumber));
        question.setProblemStatement(valueOrDefault(aiQuestion == null ? null : aiQuestion.getProblemStatement(), "Solve the Banyan level requirements in the editor."));
        question.setStarterCode(banyanCandidateContentService.cleanseCandidateStarterCode(valueOrDefault(aiQuestion == null ? null : aiQuestion.getStarterCode(), "")).sourceCode());
        question.setReferenceSolution(valueOrDefault(aiQuestion == null ? null : aiQuestion.getReferenceSolution(), ""));
        question.setIdealDurationMinutes(QUESTION_DURATION_MINUTES);
        question.setExpectedTimeComplexity(valueOrDefault(aiQuestion == null ? null : aiQuestion.getExpectedTimeComplexity(), "O(n)"));
        question.setExpectedSpaceComplexity(valueOrDefault(aiQuestion == null ? null : aiQuestion.getExpectedSpaceComplexity(), "O(1)"));
        question.setConcepts(aiQuestion == null || aiQuestion.getConcepts() == null ? "" : String.join("\n", aiQuestion.getConcepts()));
        question.setEvaluationFocus(aiQuestion == null || aiQuestion.getEvaluationFocus() == null
                ? "Assertions pass\nBanyan requirements preserved"
                : String.join("\n", aiQuestion.getEvaluationFocus()));
        question.setActive(true);
        return question;
    }

    private QuestionSeries createGeneratedSeries(PreparationAttempt attempt) {
        QuestionSeries series = new QuestionSeries();
        series.setId("prep-series-" + UUID.randomUUID());
        series.setTitle("Generated Banyan " + attempt.getTechnology() + " Preparation");
        series.setTechnology(attempt.getTechnology());
        series.setEvaluationStyle(EvaluationStyle.BANYAN);
        series.setExperienceBand(attempt.getExperienceBand());
        series.setTargetRole(attempt.getTargetRole());
        series.setProblemFamilyKey("generated-" + UUID.randomUUID());
        series.setProblemFamilyDescription("AI-generated Banyan preparation series for " + attempt.getTargetRole());
        series.setSource(QuestionSource.AI_GENERATED);
        series.setActive(true);
        return questionSeriesRepository.save(series);
    }

    private AiQuestionGenerationRequest buildAiRequest(PreparationAttempt attempt,
                                                       QuestionSeries series,
                                                       InterviewQuestionBank previousQuestion,
                                                       String previousSubmittedSource,
                                                       int sequenceNumber,
                                                       int generationAttempt,
                                                       List<String> previousValidationFailures) {
        String policy = """
                Preparation Mode question-generation policy.
                Generate BANYAN style only: one evolving challenge, one file, no multi-question challenge.
                Experience band: %s. Target positioning: %s.
                Level 1 must contain an existing method with a hidden defect, but candidate-facing title/problem text must describe it as completing business logic, not fixing a bug.
                Level 2 and later must extend the same problem family, same business domain, same model/method shape, and add exactly one new requirement with assertions.
                Do not include hints, line-level repair guidance, comments naming the bug, comments naming the fix, or solution direction in the starter code or problem statement.
                Candidate-facing title/problem text must not use words such as hint, bug, defect, incorrect, wrong, failing assertion, line number, fix the failing, or repair.
                Prefer neutral business wording such as "Complete the scheduling rule" or "Implement the eligibility check".
                The starter code must begin with one leading scenario comment header before imports/classes/functions.
                For Banyan level N, that leading header must list "Level 1", "Level 2", ... through "Level N" requirements in order, with the newest requirement below the previous ones.
                After the first executable code/import/class/function line, do not include comments anywhere in the starter code.
                The starter code may contain neutral TODO markers only where implementation is expected; it must not identify the faulty line or algorithmic correction.
                Difficulty and business-rule complexity must match the candidate's experience band and target role.
                Candidate attempts, solutions, outputs, and alerts are not stored. The generated reusable question may be stored in the question bank.
                Previous rejected generation reasons: %s
                """.formatted(
                attempt.getExperienceBand(),
                attempt.getTargetRole(),
                previousValidationFailures == null || previousValidationFailures.isEmpty() ? "none" : String.join(" | ", previousValidationFailures)
        );
        return AiQuestionGenerationRequest.builder()
                .sessionId(attempt.getId())
                .technology(attempt.getTechnology().name())
                .evaluationStyle(EvaluationStyle.BANYAN.name())
                .yearsOfExperience(attempt.getYearsOfExperience())
                .targetRole(attempt.getTargetRole())
                .startingDifficulty(String.valueOf(normalizeDifficulty(sequenceNumber)))
                .currentDifficulty(String.valueOf(normalizeDifficulty(sequenceNumber)))
                .questionNumber(sequenceNumber)
                .maxQuestions(10)
                .timeRemainingSeconds((long) QUESTION_DURATION_MINUTES * 60)
                .variationSeed(attempt.getId() + "|" + series.getId() + "|" + sequenceNumber + "|validated-" + generationAttempt + "|" + UUID.randomUUID())
                .idealDurationMinutes(QUESTION_DURATION_MINUTES)
                .banyanLevel(sequenceNumber)
                .previousBanyanChallenge(previousBanyanChallenge(previousQuestion, previousSubmittedSource))
                .questionPolicy(policy)
                .evaluationRubric("Validation checks must pass. Do not provide hints or line-level repair guidance to the candidate.")
                .targetConcepts(List.of("banyan-family:" + series.getProblemFamilyKey(), series.getProblemFamilyDescription(), "single evolving challenge"))
                .previousQuestionTitles(previousQuestion == null ? List.of() : List.of(previousQuestion.getTitle()))
                .previousConcepts(previousQuestion == null ? List.of() : splitLines(previousQuestion.getConcepts()))
                .avoidConcepts(List.of())
                .forbiddenCapabilities(List.of("file IO", "network IO", "databases", "external services", "external processes", "unsupported dependencies"))
                .requiredQuestionElements(requiredQuestionElements(attempt.getTechnology(), sequenceNumber))
                .sandboxRules(sandboxRules(attempt.getTechnology()))
                .build();
    }

    private String previousBanyanChallenge(InterviewQuestionBank previousQuestion, String previousSubmittedSource) {
        if (previousQuestion == null) {
            return null;
        }
        String acceptedSource = valueOrDefault(previousSubmittedSource, previousQuestion.getStarterCode());
        return """
                Previous title: %s
                Previous problem:
                %s
                Previous accepted source:
                %s
                Previous reference:
                %s
                """.formatted(
                previousQuestion.getTitle(),
                previousQuestion.getProblemStatement(),
                acceptedSource,
                previousQuestion.getReferenceSolution()
        );
    }

    private ExecuteResponse executeLowPriority(PreparationAttempt attempt, String sourceCode) {
        ExecuteRequest executeRequest = ExecuteRequest.builder()
                .sourceCode(sourceCode)
                .sessionId("preparation-" + attempt.getId())
                .language(ExecutionLanguage.valueOf(attempt.getTechnology().name()))
                .timeoutMs(5000)
                .memoryLimitMb(512)
                .executionPriority(ExecutionPriority.PREPARATION)
                .build();
        return sandboxClientService.execute(executeRequest);
    }

    private void markQuestionPassed(PreparationAttempt attempt, InterviewQuestionBank question) {
        PreparationQuestionAssignment assignment = assignmentRepository.findByAttemptIdAndQuestionId(attempt.getId(), question.getId())
                .orElseThrow(() -> new IllegalStateException("Preparation question assignment was not found"));
        OffsetDateTime now = nowUtc();
        assignment.setStatus(PreparationQuestionStatus.PASSED);
        assignment.setSubmittedAt(now);
        assignment.setPassedAt(now);
        assignmentRepository.save(assignment);
    }

    private void incrementExecuteAttemptCount(PreparationAttempt attempt, InterviewQuestionBank question) {
        PreparationQuestionAssignment assignment = assignmentRepository.findByAttemptIdAndQuestionId(attempt.getId(), question.getId())
                .orElseThrow(() -> new IllegalStateException("Preparation question assignment was not found"));
        int nextCount = (assignment.getExecuteAttemptCount() == null ? 0 : assignment.getExecuteAttemptCount()) + 1;
        assignment.setExecuteAttemptCount(nextCount);
        assignmentRepository.save(assignment);
    }

    private int executeAttemptCount(PreparationAttempt attempt, InterviewQuestionBank question) {
        if (attempt == null || question == null) {
            return 0;
        }
        return assignmentRepository.findByAttemptIdAndQuestionId(attempt.getId(), question.getId())
                .map(PreparationQuestionAssignment::getExecuteAttemptCount)
                .filter(Objects::nonNull)
                .orElse(0);
    }

    private PreparationAttempt requireActiveAttempt(String token) {
        PreparationAttempt attempt = getRequiredAttempt(token);
        expireAccessIfNeeded(attempt);
        if (attempt.getStatus() == PreparationAttemptStatus.OTP_PENDING) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Please verify the passcode before starting Preparation Mode.");
        }
        return attempt;
    }

    private InterviewQuestionBank currentQuestionEntity(PreparationAttempt attempt) {
        if (attempt.getCurrentQuestionId() == null) {
            return assignQuestion(attempt, 1);
        }
        return questionBankRepository.findById(attempt.getCurrentQuestionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "The current preparation question was not found."));
    }

    private void expireAccessIfNeeded(PreparationAttempt attempt) {
        if (attempt.getStatus() == PreparationAttemptStatus.EXPIRED) {
            return;
        }
        OffsetDateTime now = nowUtc();
        if (attempt.getLinkExpiresAt() != null && attempt.getLinkExpiresAt().isBefore(now)) {
            attempt.setStatus(PreparationAttemptStatus.EXPIRED);
            attempt.setCompletedAt(now);
            attempt.setCompletionReason("The 72-hour preparation access window has expired.");
            preparationAttemptRepository.save(attempt);
            return;
        }
        OffsetDateTime attemptExpiresAt = attemptExpiresAt(attempt);
        if (attempt.getStatus() == PreparationAttemptStatus.ACTIVE
                && attemptExpiresAt != null
                && !attemptExpiresAt.isAfter(now)) {
            attempt.setStatus(PreparationAttemptStatus.EXPIRED);
            attempt.setCompletedAt(now);
            attempt.setCompletionReason("The 60-minute preparation session has ended.");
            expireCurrentAssignment(attempt);
            preparationAttemptRepository.save(attempt);
        }
    }

    private void expireQuestionIfNeeded(PreparationAttempt attempt) {
        if (attempt.getQuestionExpiresAt() == null || attempt.getStatus() != PreparationAttemptStatus.ACTIVE) {
            return;
        }
        if (attempt.getQuestionExpiresAt().isBefore(nowUtc())) {
            attempt.setStatus(PreparationAttemptStatus.EXPIRED);
            attempt.setCompletedAt(nowUtc());
            attempt.setCompletionReason("The 20-minute countdown elapsed before the current question was solved.");
            expireCurrentAssignment(attempt);
            preparationAttemptRepository.save(attempt);
        }
    }

    private void expireCurrentAssignment(PreparationAttempt attempt) {
        if (attempt.getCurrentQuestionId() == null) {
            return;
        }
        assignmentRepository.findByAttemptIdAndQuestionId(attempt.getId(), attempt.getCurrentQuestionId())
                .ifPresent(assignment -> {
                    assignment.setStatus(PreparationQuestionStatus.EXPIRED);
                    assignmentRepository.save(assignment);
                });
    }

    private PreparationAttempt getRequiredAttempt(String token) {
        return preparationAttemptRepository.findBySecureToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Preparation access link is invalid."));
    }

    private boolean isAssertionPass(ExecuteResponse execution) {
        return execution != null
                && execution.isSuccess()
                && execution.getExitCode() == 0
                && (execution.getCompileErrors() == null || execution.getCompileErrors().isEmpty());
    }

    private boolean validationAssertionsIntact(String originalStarter, String currentSource) {
        List<String> originalAssertions = extractValidationLines(originalStarter);
        if (originalAssertions.isEmpty()) {
            return true;
        }
        List<String> currentAssertions = extractValidationLines(currentSource);
        List<String> normalizedCurrentAssertions = currentAssertions.stream()
                .map(this::normalizeForIntegrity)
                .toList();
        return originalAssertions.stream()
                .map(this::normalizeForIntegrity)
                .allMatch(normalizedCurrentAssertions::contains);
    }

    private PreparationQuestionValidationResult validateQuestion(PreparationAttempt attempt, InterviewQuestionBank question) {
        if (question == null) {
            return PreparationQuestionValidationResult.invalid("question was not found");
        }
        String starterCode = question.getStarterCode() == null ? "" : question.getStarterCode();
        String referenceSolution = question.getReferenceSolution() == null ? "" : question.getReferenceSolution();
        if (referenceSolution.isBlank()) {
            return PreparationQuestionValidationResult.invalid("reference solution is missing");
        }
        Optional<String> hintViolation = candidateFacingHintViolation(question);
        if (hintViolation.isPresent()) {
            return PreparationQuestionValidationResult.invalid("candidate-facing question contains hint language: " + hintViolation.get());
        }
        List<String> starterAssertions = extractValidationLines(starterCode);
        if (starterAssertions.isEmpty()) {
            return PreparationQuestionValidationResult.invalid("starter validation checks are missing");
        }
        List<String> referenceAssertions = extractValidationLines(referenceSolution);
        if (referenceAssertions.isEmpty()) {
            return PreparationQuestionValidationResult.invalid("reference validation checks are missing");
        }
        List<String> normalizedReferenceAssertions = referenceAssertions.stream()
                .map(this::normalizeForIntegrity)
                .toList();
        List<String> missingAssertions = starterAssertions.stream()
                .filter(assertion -> !normalizedReferenceAssertions.contains(normalizeForIntegrity(assertion)))
                .toList();
        if (!missingAssertions.isEmpty()) {
            return PreparationQuestionValidationResult.invalid("reference solution does not include all starter validation checks");
        }

        ExecuteResponse execution = executeValidation(attempt, question, referenceSolution);
        String failureText = firstNonBlank(
                execution == null ? null : execution.getStderr(),
                execution == null || execution.getCompileErrors() == null ? "" : String.join("\n", execution.getCompileErrors())
        );
        if (execution == null || !execution.isSuccess() || execution.getExitCode() != 0 || !failureText.isBlank()) {
            failureText = firstNonBlank(
                    failureText,
                    execution == null ? null : execution.getMessage(),
                    execution == null ? "validation did not return a sandbox response" : "sandbox exited with code " + execution.getExitCode()
            );
            return PreparationQuestionValidationResult.invalid("reference solution failed validation run: " + compactValidationMessage(failureText));
        }
        return PreparationQuestionValidationResult.ok();
    }

    private Optional<String> candidateFacingHintViolation(InterviewQuestionBank question) {
        return banyanCandidateContentService.candidateFacingHintViolation(question.getProblemStatement(), question.getStarterCode());
    }

    private PreparationQuestionValidationResult validateBanyanExtension(InterviewQuestionBank previousQuestion,
                                                                        String previousSubmittedSource,
                                                                        InterviewQuestionBank nextQuestion) {
        if (previousQuestion == null || nextQuestion == null || safeSequence(nextQuestion.getSequenceNumber()) <= 1) {
            return PreparationQuestionValidationResult.ok();
        }
        String previousSource = valueOrDefault(previousSubmittedSource, previousQuestion.getStarterCode());
        List<String> previousAssertions = extractValidationLines(previousSource);
        if (previousAssertions.isEmpty()) {
            previousAssertions = extractValidationLines(previousQuestion.getStarterCode());
        }
        if (!previousAssertions.isEmpty()) {
            List<String> nextStarterAssertions = extractValidationLines(nextQuestion.getStarterCode()).stream()
                    .map(this::normalizeForIntegrity)
                    .toList();
            List<String> nextReferenceAssertions = extractValidationLines(nextQuestion.getReferenceSolution()).stream()
                    .map(this::normalizeForIntegrity)
                    .toList();
            List<String> missingStarterAssertions = previousAssertions.stream()
                    .filter(assertion -> !nextStarterAssertions.contains(normalizeForIntegrity(assertion)))
                    .toList();
            if (!missingStarterAssertions.isEmpty()) {
                return PreparationQuestionValidationResult.invalid("next level starter does not preserve previous validation checks");
            }
            List<String> missingReferenceAssertions = previousAssertions.stream()
                    .filter(assertion -> !nextReferenceAssertions.contains(normalizeForIntegrity(assertion)))
                    .toList();
            if (!missingReferenceAssertions.isEmpty()) {
                return PreparationQuestionValidationResult.invalid("next level reference does not preserve previous validation checks");
            }
        }

        String starterLower = nextQuestion.getStarterCode() == null ? "" : nextQuestion.getStarterCode().toLowerCase(Locale.ROOT);
        for (int level = 1; level <= safeSequence(nextQuestion.getSequenceNumber()); level++) {
            if (!starterLower.contains("level " + level)) {
                return PreparationQuestionValidationResult.invalid("next level starter header is missing Level " + level + " requirement");
            }
        }
        return PreparationQuestionValidationResult.ok();
    }

    private Optional<String> firstProblemStatementHintViolation(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalizeHintText(content);
        List<String> blockedPhrases = List.of(
                "hint",
                "bug",
                "defect",
                "wrong",
                "incorrect",
                "line ",
                "line-level",
                "bug is",
                "defect is",
                "fix the",
                "fix the failing",
                "failing assertion",
                "provided assertion",
                "provided assertions",
                "repair",
                "wrong update",
                "incorrect update",
                "solution direction"
        );
        return blockedPhrases.stream()
                .filter(normalized::contains)
                .findFirst()
                .map(phrase -> "problem statement contains '" + phrase + "'");
    }

    private Optional<String> firstStarterCommentHintViolation(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalizeHintText(content);
        List<String> blockedPhrases = List.of(
                "hint",
                "bug",
                "defect",
                "wrong",
                "incorrect",
                "repair",
                "failing",
                "fix the",
                "fix this",
                "line ",
                "strict bound",
                "strict bounds",
                "should ensure",
                "should be"
        );
        return blockedPhrases.stream()
                .filter(normalized::contains)
                .findFirst()
                .map(phrase -> "starter code comment contains '" + phrase + "'");
    }

    private String normalizeHintText(String content) {
        return content.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private StarterCodeCleanseResult cleanseCandidateStarterCode(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return new StarterCodeCleanseResult("", "");
        }
        List<String> cleanedLines = new ArrayList<>();
        List<String> removedComments = new ArrayList<>();
        boolean inBlockComment = false;
        boolean leadingCommentSection = true;
        for (String rawLine : sourceCode.split("\\R", -1)) {
            String line = rawLine == null ? "" : rawLine;
            String trimmed = line.trim();

            if (leadingCommentSection && !inBlockComment && trimmed.isBlank()) {
                cleanedLines.add(rawLine);
                continue;
            }

            if (leadingCommentSection) {
                LeadingCommentLine leadingLine = preserveLeadingCommentLine(rawLine, inBlockComment);
                if (leadingLine.preserved()) {
                    cleanedLines.add(rawLine);
                    inBlockComment = leadingLine.inBlockComment();
                    continue;
                }
            }

            leadingCommentSection = false;
            CodeLineCleanseResult cleaned = stripCodeLineComments(rawLine, inBlockComment);
            inBlockComment = cleaned.inBlockComment();
            if (!cleaned.removedComment().isBlank()) {
                removedComments.add(cleaned.removedComment());
            }
            if (cleaned.content().isBlank() && !rawLine.isBlank()) {
                continue;
            }
            cleanedLines.add(cleaned.content());
        }
        return new StarterCodeCleanseResult(String.join("\n", cleanedLines).strip(), String.join("\n", removedComments));
    }

    private LeadingCommentLine preserveLeadingCommentLine(String rawLine, boolean startsInBlockComment) {
        String trimmed = rawLine == null ? "" : rawLine.trim();
        if (startsInBlockComment) {
            return new LeadingCommentLine(true, !trimmed.contains("*/"));
        }
        if (trimmed.startsWith("//") || trimmed.startsWith("#")) {
            return new LeadingCommentLine(true, false);
        }
        if (trimmed.startsWith("/*")) {
            return new LeadingCommentLine(true, !trimmed.contains("*/"));
        }
        if (trimmed.startsWith("*")) {
            return new LeadingCommentLine(true, false);
        }
        return new LeadingCommentLine(false, false);
    }

    private CodeLineCleanseResult stripCodeLineComments(String rawLine, boolean startsInBlockComment) {
        if (rawLine == null) {
            return new CodeLineCleanseResult("", "", startsInBlockComment);
        }
        String line = rawLine;
        StringBuilder removed = new StringBuilder();
        boolean inBlock = startsInBlockComment;
        if (inBlock) {
            int blockEnd = line.indexOf("*/");
            if (blockEnd < 0) {
                return new CodeLineCleanseResult("", line, true);
            }
            removed.append(line, 0, blockEnd);
            line = line.substring(blockEnd + 2);
            inBlock = false;
        }
        while (true) {
            int blockStart = line.indexOf("/*");
            if (blockStart < 0) {
                break;
            }
            int blockEnd = line.indexOf("*/", blockStart + 2);
            if (blockEnd < 0) {
                appendRemovedComment(removed, line.substring(blockStart + 2));
                return new CodeLineCleanseResult(line.substring(0, blockStart).stripTrailing(), removed.toString(), true);
            }
            appendRemovedComment(removed, line.substring(blockStart + 2, blockEnd));
            line = line.substring(0, blockStart) + line.substring(blockEnd + 2);
        }
        LineCommentMatch lineComment = firstLineComment(line);
        if (lineComment.index() >= 0) {
            appendRemovedComment(removed, line.substring(lineComment.index() + lineComment.markerLength()));
            line = line.substring(0, lineComment.index());
        }
        return new CodeLineCleanseResult(line.stripTrailing(), removed.toString(), inBlock);
    }

    private LineCommentMatch firstLineComment(String line) {
        int slashComment = line.indexOf("//");
        int hashComment = line.indexOf("#");
        if (slashComment >= 0 && hashComment >= 0) {
            return slashComment < hashComment
                    ? new LineCommentMatch(slashComment, 2)
                    : new LineCommentMatch(hashComment, 1);
        }
        if (slashComment >= 0) {
            return new LineCommentMatch(slashComment, 2);
        }
        if (hashComment >= 0) {
            return new LineCommentMatch(hashComment, 1);
        }
        return new LineCommentMatch(-1, 0);
    }

    private void appendRemovedComment(StringBuilder removed, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!removed.isEmpty()) {
            removed.append('\n');
        }
        removed.append(value);
    }

    private ExecuteResponse executeValidation(PreparationAttempt attempt, InterviewQuestionBank question, String referenceSolution) {
        ExecuteRequest executeRequest = ExecuteRequest.builder()
                .sourceCode(referenceSolution)
                .sessionId("preparation-validation-" + attempt.getId() + "-" + question.getId())
                .language(ExecutionLanguage.valueOf(question.getTechnology().name()))
                .timeoutMs(5000)
                .memoryLimitMb(512)
                .executionPriority(ExecutionPriority.PREPARATION)
                .build();
        return sandboxClientService.execute(executeRequest);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String compactValidationMessage(String value) {
        if (value == null || value.isBlank()) {
            return "no error details";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() > 220 ? compact.substring(0, 220) : compact;
    }

    private record PreparationQuestionValidationResult(boolean valid, String reason) {
        static PreparationQuestionValidationResult ok() {
            return new PreparationQuestionValidationResult(true, "");
        }

        static PreparationQuestionValidationResult invalid(String reason) {
            return new PreparationQuestionValidationResult(false, reason == null || reason.isBlank() ? "validation failed" : reason);
        }
    }

    private List<String> extractValidationLines(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        boolean inBlockComment = false;
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            if (inBlockComment) {
                if (line.contains("*/")) {
                    inBlockComment = false;
                }
                continue;
            }
            if (line.startsWith("/*")) {
                if (!line.contains("*/")) {
                    inBlockComment = true;
                }
                continue;
            }
            if (line.startsWith("*") || line.startsWith("//") || line.startsWith("#")) {
                continue;
            }
            if (line.contains("Assert.") || line.startsWith("assert ") || line.contains(" assert ")) {
                lines.add(line);
            }
        }
        return lines;
    }

    private String normalizeForIntegrity(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private PreparationQuestionResponse toQuestionResponse(PreparationAttempt attempt, InterviewQuestionBank question, String message) {
        OffsetDateTime attemptExpiresAt = attemptExpiresAt(attempt);
        return PreparationQuestionResponse.builder()
                .attemptId(attempt.getId())
                .questionId(question.getId())
                .seriesId(question.getSeriesId())
                .technology(question.getTechnology())
                .title(question.getTitle())
                .displayName(question.getDisplayName())
                .filePath(question.getFilePath())
                .problemStatement(candidateProblemStatement(question.getProblemStatement()))
                .starterCode(candidateStarterCode(question.getStarterCode()))
                .sequenceNumber(question.getSequenceNumber())
                .banyanLevel(question.getBanyanLevel())
                .starterType(question.getStarterType())
                .experienceBand(question.getExperienceBand())
                .questionStartedAt(attempt.getQuestionStartedAt())
                .questionExpiresAt(attempt.getQuestionExpiresAt())
                .remainingSeconds(remainingSeconds(attempt.getQuestionExpiresAt()))
                .attemptExpiresAt(attemptExpiresAt)
                .remainingAttemptSeconds(remainingSeconds(attemptExpiresAt))
                .executeAttemptCount(executeAttemptCount(attempt, question))
                .attemptEnded(attempt.getStatus() != PreparationAttemptStatus.ACTIVE)
                .message(message)
                .concepts(splitLines(question.getConcepts()))
                .build();
    }

    private String candidateProblemStatement(String problemStatement) {
        if (problemStatement == null || problemStatement.isBlank()) {
            return "Complete the requested business logic.";
        }
        String normalized = problemStatement.replaceAll("\\s+", " ").trim();
        String[] parts = normalized.split("(?<=[.!?])\\s+");
        List<String> kept = new ArrayList<>();
        for (String part : parts) {
            String lower = part.toLowerCase(Locale.ROOT);
            if (lower.contains("hint")
                    || lower.contains("example")
                    || lower.contains("approach")
                    || lower.contains("bug")
                    || lower.contains("defect")
                    || lower.contains("wrong")
                    || lower.contains("incorrect")
                    || lower.contains("repair")
                    || lower.contains("failing")
                    || lower.contains("failing assertion")
                    || lower.contains("provided assertions")
                    || lower.contains("strict bound")
                    || lower.contains("line ")) {
                continue;
            }
            kept.add(part.trim());
        }
        return kept.isEmpty() ? "Complete the requested business logic." : String.join(" ", kept);
    }

    private String candidateStarterCode(String starterCode) {
        if (starterCode == null || starterCode.isBlank()) {
            return "";
        }
        return banyanCandidateContentService.cleanseCandidateStarterCode(starterCode).sourceCode();
    }

    private record StarterCodeCleanseResult(String sourceCode, String removedComments) {
    }

    private record LeadingCommentLine(boolean preserved, boolean inBlockComment) {
    }

    private record CodeLineCleanseResult(String content, String removedComment, boolean inBlockComment) {
    }

    private record LineCommentMatch(int index, int markerLength) {
    }

    private PreparationAttemptResponse toAttemptResponse(PreparationAttempt attempt, String message) {
        OffsetDateTime attemptExpiresAt = attemptExpiresAt(attempt);
        return PreparationAttemptResponse.builder()
                .id(attempt.getId())
                .candidateName(attempt.getCandidateName())
                .email(attempt.getEmail())
                .technology(attempt.getTechnology())
                .yearsOfExperience(attempt.getYearsOfExperience())
                .experienceBand(attempt.getExperienceBand())
                .targetRole(attempt.getTargetRole())
                .status(attempt.getStatus())
                .linkExpiresAt(attempt.getLinkExpiresAt())
                .otpIssuedAt(attempt.getOtpIssuedAt())
                .otpExpiresAt(attempt.getOtpExpiresAt())
                .otpVerifiedAt(attempt.getOtpVerifiedAt())
                .disclaimerAcceptedAt(attempt.getDisclaimerAcceptedAt())
                .remainingOtpResends(remainingOtpResends(attempt))
                .questionStartedAt(attempt.getQuestionStartedAt())
                .questionExpiresAt(attempt.getQuestionExpiresAt())
                .attemptExpiresAt(attemptExpiresAt)
                .remainingAttemptSeconds(remainingSeconds(attemptExpiresAt))
                .currentQuestionId(attempt.getCurrentQuestionId())
                .currentSeriesId(attempt.getCurrentSeriesId())
                .currentSequenceNumber(attempt.getCurrentSequenceNumber())
                .completedAt(attempt.getCompletedAt())
                .createdAt(attempt.getCreatedAt())
                .updatedAt(attempt.getUpdatedAt())
                .message(message)
                .build();
    }

    private PreparationAccessResponse toAccessResponse(PreparationAttempt attempt, String message) {
        OffsetDateTime attemptExpiresAt = attemptExpiresAt(attempt);
        return PreparationAccessResponse.builder()
                .attemptId(attempt.getId())
                .candidateName(attempt.getCandidateName())
                .email(attempt.getEmail())
                .yearsOfExperience(attempt.getYearsOfExperience())
                .experienceBand(attempt.getExperienceBand())
                .status(attempt.getStatus())
                .otpVerified(attempt.getOtpVerifiedAt() != null)
                .disclaimerAccepted(attempt.getDisclaimerAcceptedAt() != null)
                .disclaimerAcceptedAt(attempt.getDisclaimerAcceptedAt())
                .linkExpiresAt(attempt.getLinkExpiresAt())
                .otpExpiresAt(attempt.getOtpExpiresAt())
                .attemptExpiresAt(attemptExpiresAt)
                .remainingAttemptSeconds(remainingSeconds(attemptExpiresAt))
                .remainingOtpResends(remainingOtpResends(attempt))
                .message(message)
                .build();
    }

    private String issueOtp(PreparationAttempt attempt, OffsetDateTime now, boolean resend) {
        String otp = generateOtp();
        attempt.setOtpHash(hash(otp));
        attempt.setOtpIssuedAt(now);
        attempt.setOtpExpiresAt(now.plusHours(ACCESS_VALID_HOURS));
        attempt.setLinkExpiresAt(now.plusHours(ACCESS_VALID_HOURS));
        if (resend) {
            attempt.setOtpResendCount((attempt.getOtpResendCount() == null ? 0 : attempt.getOtpResendCount()) + 1);
        }
        return otp;
    }

    private int remainingOtpResends(PreparationAttempt attempt) {
        int used = attempt.getOtpResendCount() == null ? 0 : attempt.getOtpResendCount();
        return Math.max(0, MAX_OTP_RESENDS - used);
    }

    private String sentMessage(String prefix, PreparationAttempt attempt) {
        return "%s %s (%s).".formatted(prefix, attempt.getCandidateName(), attempt.getEmail());
    }

    private void sendPreparationAccessEmail(PreparationAttempt attempt, String otp) {
        String accessUrl = publicOrigin.replaceAll("/$", "") + "/preparation/access/" + attempt.getSecureToken();
        String subject = "Preparation Mode Access Details";
        String textBody = """
                Dear %s,

                Your Preparation Mode access is ready for %s.

                Open the link:
                <%s>

                One-time passcode: %s
                Link and passcode expiry: 72 hours

                Please use this access within 72 hours.

                Regards,
                Interview Platform
                """.formatted(attempt.getCandidateName(), attempt.getTechnology(), accessUrl, otp);
        String htmlBody = """
                <html>
                  <body style="font-family:Segoe UI, Arial, sans-serif; color:#16324f; line-height:1.6;">
                    <p>Dear %s,</p>
                    <p>Your Preparation Mode access is ready for <strong>%s</strong>.</p>
                    <p><a href="%s" style="display:inline-block; padding:10px 18px; border-radius:999px; background:#0b7285; color:#ffffff; text-decoration:none; font-weight:600;">Open Preparation Mode</a></p>
                    <p>If the button does not open, use this link:<br><a href="%s">%s</a></p>
                    <p><strong>One-time passcode:</strong> %s<br>
                    <strong>Link and passcode expiry:</strong> 72 hours</p>
                    <p>Please use this access within 72 hours.</p>
                    <p>Regards,<br>Interview Platform</p>
                  </body>
                </html>
                """.formatted(attempt.getCandidateName(), attempt.getTechnology(), accessUrl, accessUrl, accessUrl, otp);
        emailService.sendEmail(attempt.getEmail(), subject, textBody, htmlBody);
    }

    private TechnologySkill requirePreparationTechnology(TechnologySkill technology) {
        if (technology == TechnologySkill.JAVA || technology == TechnologySkill.PYTHON) {
            return technology;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preparation Mode currently supports Java and Python only.");
    }

    private boolean isInternalEmail(String emailNormalized) {
        return emailNormalized.endsWith("@" + INTERNAL_DOMAIN);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String experienceBand(Integer years) {
        int value = years == null ? 0 : years;
        if (value <= 3) {
            return "1-3";
        }
        if (value <= 6) {
            return "4-6";
        }
        if (value <= 9) {
            return "7-9";
        }
        return "10+";
    }

    private int normalizeDifficulty(int sequenceNumber) {
        return Math.max(1, Math.min(5, sequenceNumber));
    }

    private int safeSequence(Integer sequenceNumber) {
        return sequenceNumber == null ? 1 : sequenceNumber;
    }

    private long remainingSeconds(OffsetDateTime expiresAt) {
        if (expiresAt == null) {
            return 0;
        }
        return Math.max(0, expiresAt.toEpochSecond() - nowUtc().toEpochSecond());
    }

    private OffsetDateTime attemptExpiresAt(PreparationAttempt attempt) {
        if (attempt == null || attempt.getOtpVerifiedAt() == null) {
            return null;
        }
        return attempt.getOtpVerifiedAt().plusMinutes(ATTEMPT_DURATION_MINUTES);
    }

    private List<String> requiredQuestionElements(TechnologySkill technology, int sequenceNumber) {
        String firstLevel = sequenceNumber == 1
                ? "Level 1 must be a bug fix in an existing method with validation checks"
                : "Extend the exact same Banyan challenge with one new requirement";
        if (technology == TechnologySkill.PYTHON) {
            return List.of(firstLevel, "runnable assert checks", "standard library only", "filePath must be banyan.py", "no hints or bug-location comments");
        }
        return List.of(firstLevel, "single runnable Main class", "org.junit.Assert checks from main", "filePath must be Banyan.java", "no hints or bug-location comments");
    }

    private String sandboxRules(TechnologySkill technology) {
        return technology == TechnologySkill.PYTHON
                ? "Python standard library only. Include runnable assert statements. No file IO, network IO, databases, external processes, or external packages."
                : "Java 17 only. Single source execution. Use org.junit.Assert assertions from main. No file IO, network IO, databases, external processes, or external dependencies.";
    }

    private List<String> splitLines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return value.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String generateOtp() {
        StringBuilder builder = new StringBuilder(5);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int index = 0; index < 5; index++) {
            builder.append(OTP_CHARS.charAt(random.nextInt(OTP_CHARS.length())));
        }
        return builder.toString();
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
