package com.altimetrik.interview.service;

import com.altimetrik.interview.dto.AcceptDisclaimerRequest;
import com.altimetrik.interview.dto.ActivityEventDto;
import com.altimetrik.interview.dto.ActivityEventRequest;
import com.altimetrik.interview.dto.AiEvaluateQuestionRequest;
import com.altimetrik.interview.dto.AiInterviewRecommendationRequest;
import com.altimetrik.interview.dto.AiInterviewRecommendationResponse;
import com.altimetrik.interview.dto.AiPersistedRecommendationDto;
import com.altimetrik.interview.dto.AiQuestionGenerationRequest;
import com.altimetrik.interview.dto.AiQuestionResponse;
import com.altimetrik.interview.dto.AiQuestionSessionResponse;
import com.altimetrik.interview.dto.AiSolutionEvaluationRequest;
import com.altimetrik.interview.dto.AiSolutionEvaluationResponse;
import com.altimetrik.interview.dto.AuthAuditEventDto;
import com.altimetrik.interview.dto.CreateSessionRequest;
import com.altimetrik.interview.dto.CodeUpdateRequest;
import com.altimetrik.interview.dto.EditableCodeFileDto;
import com.altimetrik.interview.dto.EndSessionRequest;
import com.altimetrik.interview.dto.ExecuteRequest;
import com.altimetrik.interview.dto.ExecuteResponse;
import com.altimetrik.interview.dto.FeedbackDto;
import com.altimetrik.interview.dto.FeedbackRequest;
import com.altimetrik.interview.dto.FrontendWorkspaceDto;
import com.altimetrik.interview.dto.FrontendWorkspaceRequest;
import com.altimetrik.interview.dto.FrontendWorkspaceResponse;
import com.altimetrik.interview.dto.HeartbeatRequest;
import com.altimetrik.interview.dto.ParticipantDto;
import com.altimetrik.interview.dto.DisconnectParticipantRequest;
import com.altimetrik.interview.dto.ResumeApprovalRequest;
import com.altimetrik.interview.dto.ResumeRequest;
import com.altimetrik.interview.dto.ResumeResponse;
import com.altimetrik.interview.dto.RunResultDto;
import com.altimetrik.interview.dto.SessionResponse;
import com.altimetrik.interview.entity.CodeState;
import com.altimetrik.interview.entity.CodeFile;
import com.altimetrik.interview.entity.Feedback;
import com.altimetrik.interview.entity.FrontendWorkspace;
import com.altimetrik.interview.entity.InterviewSession;
import com.altimetrik.interview.entity.InterviewQuestionBank;
import com.altimetrik.interview.entity.Participant;
import com.altimetrik.interview.entity.ParticipantAccessChallenge;
import com.altimetrik.interview.entity.RunResult;
import com.altimetrik.interview.entity.SessionActivityEvent;
import com.altimetrik.interview.enums.ActivityEventSeverity;
import com.altimetrik.interview.enums.ActivityEventType;
import com.altimetrik.interview.enums.AvMode;
import com.altimetrik.interview.enums.CodeStorageMode;
import com.altimetrik.interview.enums.ExecutionLanguage;
import com.altimetrik.interview.enums.FeedbackRating;
import com.altimetrik.interview.enums.FrontendWorkspaceStatus;
import com.altimetrik.interview.enums.IdentityCaptureFailureReason;
import com.altimetrik.interview.enums.IdentityCaptureStatus;
import com.altimetrik.interview.enums.InterviewMode;
import com.altimetrik.interview.enums.ParticipantConnectionStatus;
import com.altimetrik.interview.enums.ParticipantAccessStatus;
import com.altimetrik.interview.enums.ParticipantRole;
import com.altimetrik.interview.enums.RecommendationDecision;
import com.altimetrik.interview.enums.ResumeReason;
import com.altimetrik.interview.enums.SessionStatus;
import com.altimetrik.interview.enums.TechnologySkill;
import com.altimetrik.interview.repository.CodeFileRepository;
import com.altimetrik.interview.repository.CodeStateRepository;
import com.altimetrik.interview.repository.FeedbackRepository;
import com.altimetrik.interview.repository.FrontendWorkspaceRepository;
import com.altimetrik.interview.repository.InterviewQuestionBankRepository;
import com.altimetrik.interview.repository.ParticipantRepository;
import com.altimetrik.interview.repository.ParticipantAccessChallengeRepository;
import com.altimetrik.interview.repository.RunResultRepository;
import com.altimetrik.interview.repository.SessionActivityEventRepository;
import com.altimetrik.interview.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    @PersistenceContext
    private EntityManager entityManager;

    private static final int DEFAULT_DURATION_SEC = 60 * 60;
    private static final int MAX_DURATION_SEC = 75 * 60;
    private static final int EXTENSION_SEC = 15 * 60;
    private static final int EXTENSION_THRESHOLD_SEC = 15 * 60;
    private static final int RECOVERY_WINDOW_SEC = 120;
    private static final int MAX_WORKSPACE_FILE_COUNT = 20;
    private static final int MAX_WORKSPACE_TOTAL_CHARS = 300_000;
    private static final int MAX_WORKSPACE_FILE_CHARS = 100_000;
    private static final long IN_APP_TAB_AWAY_SUSPICIOUS_MS = 10_000L;
    private static final long EXTERNAL_TAB_AWAY_SUSPICIOUS_MS = 30_000L;
    private static final long IN_APP_AV_OFF_SUSPICIOUS_MS = 15_000L;
    private static final String SCENARIO_REFRESH = "REFRESH_OR_REOPEN";
    private static final String SCENARIO_CONNECTION = "CONNECTION_RECOVERY";
    private static final String SCENARIO_NETWORK = "NETWORK_CHANGE";
    private static final String SCENARIO_DEVICE = "DEVICE_CHANGE";
    private static final Map<String, String> SUSPICIOUS_FEEDBACK_LIBRARY = Map.ofEntries(
            Map.entry(SCENARIO_REFRESH + "__" + SCENARIO_REFRESH,
                    "Candidate was disqualified because repeated session refresh or re-entry attempts affected interview integrity and recovery controls."),
            Map.entry(SCENARIO_CONNECTION + "__" + SCENARIO_CONNECTION,
                    "Candidate was disqualified because repeated session recovery attempts after connectivity loss exceeded the permitted continuity controls for this interview."),
            Map.entry(SCENARIO_NETWORK + "__" + SCENARIO_NETWORK,
                    "Candidate was disqualified because repeated network-change recovery attempts created an unacceptable session integrity risk."),
            Map.entry(SCENARIO_DEVICE + "__" + SCENARIO_DEVICE,
                    "Candidate was disqualified because repeated recovery attempts from different devices could not be validated within the interview integrity policy.")
    );
    private static final String DEFAULT_JAVA_TEMPLATE = """
            import org.junit.Assert;

            public class Solution {
                static int add(int a, int b) {
                    return a + b;
                }

                public static void main(String[] args) {
                    Assert.assertEquals(5, add(2, 3));
                    System.out.println("All assertions passed");
                }
            }""";
    private static final String DEFAULT_PYTHON_TEMPLATE = """
            def add(a, b):
                return a + b


            def main():
                assert add(2, 3) == 5
                print("All assertions passed")


            if __name__ == "__main__":
                main()
            """;
    private static final String DEFAULT_ANGULAR_COMPONENT_TS = """
            import { Component } from '@angular/core';
            import { CommonModule } from '@angular/common';

            @Component({
              selector: 'app-root',
              standalone: true,
              imports: [CommonModule],
              templateUrl: './app.component.html',
              styleUrl: './app.component.css'
            })
            export class AppComponent {
              title = 'Angular interview sandbox';
            }
            """;
    private static final String DEFAULT_ANGULAR_COMPONENT_HTML = """
            <main class="app-shell">
              <h1>{{ title }}</h1>
              <p>Start building your Angular solution here.</p>
            </main>
            """;
    private static final String DEFAULT_ANGULAR_COMPONENT_CSS = """
            .app-shell {
              display: grid;
              gap: 12px;
              padding: 24px;
              font-family: Arial, sans-serif;
            }

            h1 {
              margin: 0;
              color: #0f3d59;
            }

            p {
              margin: 0;
              color: #4f6474;
            }
            """;
    private static final String DEFAULT_REACT_APP_TSX = """
            import React from 'react';
            import './App.css';

            export default function App() {
              return (
                <main className="app-shell">
                  <h1>React interview sandbox</h1>
                  <p>Start building your React solution here.</p>
                </main>
              );
            }
            """;
    private static final String DEFAULT_REACT_APP_CSS = """
            .app-shell {
              display: grid;
              gap: 12px;
              padding: 24px;
              font-family: Arial, sans-serif;
            }

            h1 {
              margin: 0;
              color: #0f3d59;
            }

            p {
              margin: 0;
              color: #4f6474;
            }
            """;
    private static final String DEFAULT_REACT_MAIN_TSX = """
            import React from 'react';
            import { createRoot } from 'react-dom/client';
            import App from './App';
            import './index.css';

            const container = document.getElementById('root');

            if (!container) {
              throw new Error('React root container was not found.');
            }

            createRoot(container).render(
              <React.StrictMode>
                <App />
              </React.StrictMode>
            );
            """;

    private final SessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final ParticipantAccessChallengeRepository participantAccessChallengeRepository;
    private final CodeFileRepository codeFileRepository;
    private final CodeStateRepository codeStateRepository;
    private final RunResultRepository runResultRepository;
    private final FeedbackRepository feedbackRepository;
    private final FrontendWorkspaceRepository frontendWorkspaceRepository;
    private final InterviewQuestionBankRepository interviewQuestionBankRepository;
    private final SessionActivityEventRepository sessionActivityEventRepository;
    private final SandboxClientService sandboxClientService;
    private final FrontendSandboxClientService frontendSandboxClientService;
    private final AiInterviewClientService aiInterviewClientService;
    private final AiPolicyEngineService aiPolicyEngineService;
    private final IdentitySnapshotStorageService identitySnapshotStorageService;
    private final FinalPreviewStorageService finalPreviewStorageService;

    @Transactional
    public SessionResponse createSession(CreateSessionRequest request) {
        InterviewSession session = new InterviewSession();
        session.setStatus(SessionStatus.REGISTERED);
        session.setDurationSec(DEFAULT_DURATION_SEC);
        session.setExtensionUsed(false);
        session.setTechnology(request.getTechnology() == null ? TechnologySkill.JAVA : request.getTechnology());
        InterviewMode interviewMode = request.getInterviewMode() == null ? InterviewMode.HUMAN_INTERVIEWER : request.getInterviewMode();
        validateInterviewerDetailsForMode(request, interviewMode);
        session.setInterviewMode(interviewMode);
        session.setYearsOfExperience(normalizeYearsOfExperience(request.getYearsOfExperience()));
        session.setTargetRole(normalizeTargetRole(request.getTargetRole()));
        session.setStartingDifficultyLevel(normalizeDifficultyLevel(request.getStartingDifficultyLevel()));
        session.setMaxQuestions(normalizeMaxQuestions(request.getMaxQuestions()));
        session.setAvMode(interviewMode == InterviewMode.AI_INTERVIEWER
                ? AvMode.EXTERNAL
                : request.getAvMode() == null ? AvMode.EXTERNAL : request.getAvMode());
        session = sessionRepository.save(session);

        String interviewerName = interviewMode == InterviewMode.AI_INTERVIEWER ? "AI Interviewer" : request.getInterviewerName();
        String interviewerEmail = interviewMode == InterviewMode.AI_INTERVIEWER ? "ai-interviewer@interview.local" : request.getInterviewerEmail();
        String interviewerTimeZone = interviewMode == InterviewMode.AI_INTERVIEWER ? null : request.getInterviewerTimeZone();
        participantRepository.save(createParticipant(session.getId(), ParticipantRole.INTERVIEWER,
                interviewerName, interviewerEmail, interviewerTimeZone));
        participantRepository.save(createParticipant(session.getId(), ParticipantRole.INTERVIEWEE,
                request.getIntervieweeName(), request.getIntervieweeEmail(), null));

        CodeState codeState = new CodeState();
        codeState.setSessionId(session.getId());
        codeState.setLatestCode(defaultTemplateFor(session.getTechnology()));
        codeState.setStorageMode(storageModeFor(session.getTechnology()));
        codeState.setUpdatedAt(nowUtc());
        codeState.setUpdatedByRole(ParticipantRole.INTERVIEWER.name());
        codeState.setVersion(0L);
        codeStateRepository.save(codeState);
        replaceCodeFiles(session.getId(), buildDefaultEditableFiles(session.getTechnology()));

        InterviewSession persisted = sessionRepository.findById(session.getId())
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        log.info("Created session {}", session.getId());
        return toSessionResponse(persisted, true);
    }

    @Transactional
    public SessionResponse getSession(String id) {
        InterviewSession session = getRequiredSession(id);
        ensureFrontendWorkspaceIfNeeded(session);
        return toSessionResponse(session, true);
    }

    public AiQuestionSessionResponse generateNextAiQuestion(String sessionId) {
        InterviewSession session = getRequiredSession(sessionId);
        ensureAiInterview(session);
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "AI questions can be generated only while the interview is active.");
        }

        CodeState codeState = codeStateRepository.findBySessionId(sessionId).orElse(null);
        List<EditableCodeFileDto> files = new ArrayList<>(resolveEditableFiles(session, codeState));
        List<EditableCodeFileDto> questionFiles = files.stream()
                .filter(file -> isManagedAiQuestionFile(session.getTechnology(), file))
                .toList();

        long submittedCount = questionFiles.stream().filter(file -> Boolean.TRUE.equals(file.getSubmitted())).count();
        int maxQuestions = session.getMaxQuestions() == null ? 5 : session.getMaxQuestions();
        if (submittedCount >= maxQuestions) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "AI interview has already reached the configured question limit.");
        }

        EditableCodeFileDto activeQuestion = questionFiles.stream()
                .filter(file -> Boolean.TRUE.equals(file.getActiveQuestion()) && !Boolean.TRUE.equals(file.getSubmitted()))
                .findFirst()
                .orElse(null);
        if (activeQuestion != null) {
            return AiQuestionSessionResponse.builder()
                    .question(toAiQuestionResponse(activeQuestion))
                    .session(toSessionResponse(session, true))
                    .build();
        }

        int questionNumber = (int) submittedCount + 1;
        int currentDifficulty = resolveCurrentAiDifficultyLevel(session, submittedCount);
        AiPolicyEngineService.QuestionPolicyPlan policyPlan = aiPolicyEngineService.questionPolicy(
                session,
                currentDifficulty,
                questionNumber,
                questionFiles,
                calculateRemainingSec(session)
        );
        AiQuestionGenerationRequest generationRequest = AiQuestionGenerationRequest.builder()
                .sessionId(sessionId)
                .technology(session.getTechnology().name())
                .yearsOfExperience(session.getYearsOfExperience())
                .targetRole(session.getTargetRole())
                .startingDifficulty(String.valueOf(session.getStartingDifficultyLevel() == null ? 1 : session.getStartingDifficultyLevel()))
                .currentDifficulty(String.valueOf(currentDifficulty))
                .questionNumber(questionNumber)
                .maxQuestions(maxQuestions)
                .timeRemainingSeconds((long) calculateRemainingSec(session))
                .variationSeed(sessionId + "-" + questionNumber + "-" + UUID.randomUUID())
                .idealDurationMinutes(policyPlan.idealDurationMinutes())
                .questionPolicy(policyPlan.questionPolicy())
                .evaluationRubric(policyPlan.evaluationRubric())
                .targetConcepts(policyPlan.targetConcepts())
                .previousQuestionTitles(aiQuestionHistoryForGeneration(questionFiles))
                .previousConcepts(policyPlan.previousConcepts())
                .avoidConcepts(policyPlan.avoidConcepts())
                .forbiddenCapabilities(policyPlan.forbiddenCapabilities())
                .requiredQuestionElements(policyPlan.requiredQuestionElements())
                .sandboxRules(policyPlan.sandboxRules())
                .build();
        AiQuestionResponse generated = generateValidatedAiQuestion(session, generationRequest, questionFiles);

        EditableCodeFileDto generatedFile = buildAiQuestionFile(session.getTechnology(), generated, questionNumber, activeQuestion, files.size());
        List<EditableCodeFileDto> nextFiles = upsertAiQuestionFile(files, generatedFile);
        validateWorkspaceFiles(session.getTechnology(), nextFiles);
        runResultRepository.deleteBySessionIdAndFilePath(sessionId, generatedFile.getPath());
        replaceCodeFiles(sessionId, nextFiles);

        SessionResponse refreshed = getSession(sessionId);
        return AiQuestionSessionResponse.builder()
                .question(toAiQuestionResponse(generatedFile))
                .session(refreshed)
                .build();
    }

    public AiSolutionEvaluationResponse evaluateAiQuestion(String sessionId, AiEvaluateQuestionRequest request) {
        InterviewSession session = getRequiredSession(sessionId);
        ensureAiInterview(session);

        EditableCodeFileDto question = resolveAiEvaluationTarget(session, request == null ? null : request.getFilePath());
        AiSolutionEvaluationResponse response = aiInterviewClientService.evaluateSolution(toAiEvaluationRequest(session, question, questionIndex(session, question)));
        CodeFile codeFile = codeFileRepository.findBySessionIdAndFilePath(sessionId, question.getPath())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI question file was not found."));
        codeFile.setQuestionIntegrityNotes(resolveQuestionIntegrityNotes(question));
        applyAiEvaluation(codeFile, response);
        codeFileRepository.save(codeFile);
        return response;
    }

    public AiInterviewRecommendationResponse recommendAiInterview(String sessionId) {
        InterviewSession session = getRequiredSession(sessionId);
        ensureAiInterview(session);

        List<EditableCodeFileDto> files = resolveEditableFiles(session, codeStateRepository.findBySessionId(sessionId).orElse(null)).stream()
                .filter(file -> isManagedAiQuestionFile(session.getTechnology(), file))
                .filter(file -> Boolean.TRUE.equals(file.getSubmitted()))
                .toList();
        if (files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No submitted AI questions are available for recommendation.");
        }

        List<AiSolutionEvaluationRequest> questionResults = new ArrayList<>();
        for (EditableCodeFileDto file : files) {
            questionResults.add(toAiEvaluationRequest(session, file, questionIndex(session, file)));
        }

        AiPolicyEngineService.RecommendationPolicy recommendationPolicy = aiPolicyEngineService.recommendationPolicy(session);
        AiInterviewRecommendationRequest recommendationRequest = AiInterviewRecommendationRequest.builder()
                .sessionId(sessionId)
                .technology(session.getTechnology().name())
                .targetRole(session.getTargetRole())
                .yearsOfExperience(session.getYearsOfExperience())
                .maxQuestions(session.getMaxQuestions())
                .recommendationPolicy(recommendationPolicy.recommendationPolicy())
                .evaluationRubric(recommendationPolicy.evaluationRubric())
                .questionResults(questionResults)
                .build();
        AiInterviewRecommendationResponse response = recommendAiInterviewOrFallback(session, recommendationRequest, questionResults);
        response = applyRecommendationGuardrails(session, response, questionResults);
        applyAiRecommendation(session, response);
        sessionRepository.save(session);
        return response;
    }

    private AiInterviewRecommendationResponse recommendAiInterviewOrFallback(InterviewSession session,
                                                                             AiInterviewRecommendationRequest request,
                                                                             List<AiSolutionEvaluationRequest> questionResults) {
        try {
            return aiInterviewClientService.recommend(request);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE
                    || exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS
                    || exception.getStatusCode() == HttpStatus.BAD_GATEWAY) {
                log.warn("AI recommendation provider unavailable for session {}. Using metrics-based fallback. Reason: {}",
                        session.getId(), exception.getReason());
                return fallbackAiRecommendation(session, questionResults, exception.getReason());
            }
            throw exception;
        }
    }

    private AiInterviewRecommendationResponse fallbackAiRecommendation(InterviewSession session,
                                                                       List<AiSolutionEvaluationRequest> questionResults,
                                                                       String providerReason) {
        List<AiSolutionEvaluationRequest> results = questionResults == null ? List.of() : questionResults;
        long successfulRuns = results.stream().filter(this::hasSuccessfulQuestionRun).count();
        int attempted = results.size();
        int totalAttempts = results.stream()
                .map(AiSolutionEvaluationRequest::getExecuteAttemptCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        double successRatio = attempted == 0 ? 0.0 : (double) successfulRuns / attempted;
        int attemptPenalty = Math.min(15, Math.max(0, totalAttempts - attempted * 3));
        int score = Math.max(0, Math.min(100, (int) Math.round(successRatio * 90) + completionBonus(results) - attemptPenalty));

        AiInterviewRecommendationResponse response = new AiInterviewRecommendationResponse();
        response.setOverallScore(score);
        response.setRating(score >= 85 ? "EXCELLENT" : score >= 70 ? "GOOD" : score >= 50 ? "FAIR" : score >= 35 ? "BAD" : "DISQUALIFIED");
        response.setRecommendationDecision(score >= 70 ? "YES" : score >= 50 ? "REEVALUATION" : "NO");
        response.setSummary("Generated from captured execution metrics because the AI provider was unavailable. "
                + successfulRuns + " of " + attempted + " submitted question(s) had a successful captured run.");
        response.setStrengths(List.of(
                "Submitted " + attempted + " AI question(s) for review.",
                successfulRuns + " question(s) show successful execution output."
        ));
        response.setRisks(List.of(
                firstNonBlank(providerReason, "AI provider did not return a live recommendation."),
                "Human review is required to validate code quality, edge cases, and communication."
        ));
        response.setSuggestedFollowUps(List.of(
                "Review the submitted code, run output, errors, time taken, and execute-attempt count.",
                "Compare the solution against the hidden reference solution and expected complexity."
        ));
        response.setHumanReviewRequired(true);
        return response;
    }

    private boolean hasSuccessfulQuestionRun(AiSolutionEvaluationRequest result) {
        String stdout = result.getStdout() == null ? "" : result.getStdout().toLowerCase(Locale.ROOT);
        String stderr = result.getStderr() == null ? "" : result.getStderr().trim();
        return Objects.equals(result.getExitStatus(), 0)
                && stderr.isBlank()
                && (stdout.contains("all assertions passed") || !stdout.isBlank());
    }

    private AiInterviewRecommendationResponse applyRecommendationGuardrails(InterviewSession session,
                                                                            AiInterviewRecommendationResponse response,
                                                                            List<AiSolutionEvaluationRequest> questionResults) {
        List<AiSolutionEvaluationRequest> results = questionResults == null ? List.of() : questionResults;
        int expectedClean = Math.min(3, Math.max(1, session.getMaxQuestions() == null ? 5 : session.getMaxQuestions()));
        long cleanSolved = results.stream().filter(this::isCleanSolvedQuestion).count();
        long successfulRuns = results.stream().filter(this::hasSuccessfulQuestionRun).count();
        long integrityIssues = results.stream().filter(this::hasQuestionIntegrityViolation).count();
        long missingTime = results.stream().filter(result -> result.getSolveDurationSeconds() == null || result.getSolveDurationSeconds() <= 0).count();
        long suspiciousCount = sessionActivityEventRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .filter(event -> event.getSeverity() == ActivityEventSeverity.SUSPICIOUS)
                .count();

        int originalScore = response.getOverallScore() == null ? scoreFromRating(response.getRating()) : response.getOverallScore();
        int cleanScore = Math.round((cleanSolved * 100f) / expectedClean);
        int guardedScore = Math.min(originalScore, cleanScore);
        if (cleanSolved < expectedClean) {
            guardedScore = Math.min(guardedScore, cleanSolved == 0 ? 25 : cleanSolved == 1 ? 45 : 65);
        }
        if (integrityIssues > 0) {
            guardedScore = Math.min(guardedScore, 45);
        }
        if (missingTime > 0) {
            guardedScore = Math.min(guardedScore, 70);
        }
        if (suspiciousCount >= 5) {
            guardedScore = Math.min(guardedScore, 50);
        } else if (suspiciousCount >= 3) {
            guardedScore = Math.min(guardedScore, 65);
        }

        response.setOverallScore(Math.max(0, Math.min(100, guardedScore)));
        response.setRating(ratingForScore(response.getOverallScore()));
        response.setRecommendationDecision(decisionForGuardrails(response.getOverallScore(), cleanSolved, expectedClean, integrityIssues, suspiciousCount));
        response.setSummary("%d/%d clean question%s. %d successful run%s. Integrity issues: %d. Suspicious events: %d."
                .formatted(
                        cleanSolved,
                        expectedClean,
                        expectedClean == 1 ? "" : "s expected",
                        successfulRuns,
                        successfulRuns == 1 ? "" : "s",
                        integrityIssues,
                        suspiciousCount
                ));
        response.setStrengths(compactSignals(nullableSignals(
                cleanSolved > 0 ? cleanSolved + " clean solved question" + (cleanSolved == 1 ? "" : "s") : null,
                successfulRuns > 0 ? successfulRuns + " successful captured run" + (successfulRuns == 1 ? "" : "s") : null
        )));
        response.setRisks(compactSignals(nullableSignals(
                cleanSolved < expectedClean ? "Below minimum clean-solve expectation" : null,
                integrityIssues > 0 ? "Question/test integrity concern" : null,
                suspiciousCount >= 3 ? "High suspicious activity" : null,
                missingTime > 0 ? "Unsubmitted or incomplete question evidence" : null
        )));
        response.setSuggestedFollowUps(compactSignals(nullableSignals(
                integrityIssues > 0 ? "Review tampered problem/assertions" : null,
                suspiciousCount >= 3 ? "Review integrity activity" : null,
                cleanSolved < expectedClean ? "Review failed or unattempted questions" : null
        )));
        response.setHumanReviewRequired(true);
        return response;
    }

    private boolean isCleanSolvedQuestion(AiSolutionEvaluationRequest result) {
        return hasSuccessfulQuestionRun(result)
                && !hasQuestionIntegrityViolation(result)
                && result.getSolveDurationSeconds() != null
                && result.getSolveDurationSeconds() > 0;
    }

    private boolean hasQuestionIntegrityViolation(AiSolutionEvaluationRequest result) {
        String notes = (result.getQuestionIntegrityNotes() == null ? "" : result.getQuestionIntegrityNotes()).toLowerCase(Locale.ROOT);
        return notes.contains("healthy: false")
                || notes.contains("changed")
                || notes.contains("removed")
                || notes.contains("tamper")
                || notes.contains("mismatch")
                || notes.contains("integrity concern");
    }

    private int scoreFromRating(String rating) {
        if (rating == null) {
            return 50;
        }
        return switch (rating.toUpperCase(Locale.ROOT)) {
            case "EXCELLENT" -> 90;
            case "GOOD" -> 75;
            case "FAIR" -> 55;
            case "BAD" -> 35;
            case "DISQUALIFIED" -> 10;
            default -> 50;
        };
    }

    private String ratingForScore(Integer score) {
        int safeScore = score == null ? 0 : score;
        if (safeScore >= 85) {
            return "EXCELLENT";
        }
        if (safeScore >= 70) {
            return "GOOD";
        }
        if (safeScore >= 50) {
            return "FAIR";
        }
        if (safeScore >= 35) {
            return "BAD";
        }
        return "DISQUALIFIED";
    }

    private String decisionForGuardrails(Integer score, long cleanSolved, int expectedClean, long integrityIssues, long suspiciousCount) {
        if (integrityIssues > 0 || suspiciousCount >= 5 || cleanSolved == 0) {
            return "NO";
        }
        if (cleanSolved < expectedClean || score == null || score < 70 || suspiciousCount >= 3) {
            return "REEVALUATION";
        }
        return "YES";
    }

    private List<String> compactSignals(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .limit(3)
                .toList();
    }

    private List<String> nullableSignals(String... values) {
        return Arrays.asList(values);
    }

    private int completionBonus(List<AiSolutionEvaluationRequest> results) {
        return results.stream().anyMatch(result -> result.getSolveDurationSeconds() != null && result.getSolveDurationSeconds() > 0)
                ? 10
                : 0;
    }

    @Transactional(readOnly = true)
    public Page<SessionResponse> listSessions(Pageable pageable,
                                              String search,
                                              OffsetDateTime from,
                                              OffsetDateTime to,
                                              List<TechnologySkill> technologies,
                                              List<FeedbackRating> ratings) {
        List<SessionResponse> sessions = filterSessions(search, from, to, technologies, ratings, false)
                .stream()
                .sorted(buildSessionComparator(pageable))
                .toList();

        int pageNumber = pageable == null ? 0 : Math.max(0, pageable.getPageNumber());
        int pageSize = pageable == null || pageable.getPageSize() <= 0 ? 20 : pageable.getPageSize();
        int start = Math.min(pageNumber * pageSize, sessions.size());
        int end = Math.min(start + pageSize, sessions.size());
        return new PageImpl<>(sessions.subList(start, end), pageable, sessions.size());
    }

    @Transactional(readOnly = true)
    public CsvExport exportSessionsCsv(String search,
                                       OffsetDateTime from,
                                       OffsetDateTime to,
                                       List<TechnologySkill> technologies,
                                       List<FeedbackRating> ratings,
                                       String sortBy,
                                       Sort.Direction direction) {
        List<SessionResponse> sessions = filterSessions(search, from, to, technologies, ratings, true)
                .stream()
                .sorted(buildSessionComparator(sortBy, direction))
                .toList();

        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",",
                csvCell("Interview Date"),
                csvCell("Technology"),
                csvCell("Status"),
                csvCell("Summary"),
                csvCell("Interviewer Name"),
                csvCell("Interviewer Email"),
                csvCell("Interviewer Time Zone"),
                csvCell("Interviewee Name"),
                csvCell("Interviewee Email"),
                csvCell("Interviewee Time Zone"),
                csvCell("Started At"),
                csvCell("Ended At"),
                csvCell("Rating"),
                csvCell("Recommendation"),
                csvCell("Comments"),
                csvCell("Identity Snapshot Status"),
                csvCell("Identity Capture Failure Reason"),
                csvCell("Suspicious Event Count"),
                csvCell("Tab Switch Count"),
                csvCell("Paste Event Count")
        )).append('\n');

        for (SessionResponse session : sessions) {
            ParticipantDto interviewer = findParticipant(session, ParticipantRole.INTERVIEWER);
            ParticipantDto interviewee = findParticipant(session, ParticipantRole.INTERVIEWEE);
            List<ActivityEventDto> activityEvents = session.getActivityEvents() == null ? List.of() : session.getActivityEvents();
            long tabSwitchCount = activityEvents.stream().filter(event -> event.getEventType() == ActivityEventType.TAB_HIDDEN).count();
            long pasteCount = activityEvents.stream().filter(event -> event.getEventType() == ActivityEventType.PASTE_IN_EDITOR).count();

            csv.append(String.join(",",
                    csvCell(toCsvTimestamp(session.getCreatedAt())),
                    csvCell(session.getTechnology() == null ? "" : session.getTechnology().name()),
                    csvCell(session.getStatus() == null ? "" : session.getStatus().name()),
                    csvCell(nullSafe(session.getSummary())),
                    csvCell(interviewer == null ? "" : interviewer.getName()),
                    csvCell(interviewer == null ? "" : interviewer.getEmail()),
                    csvCell(interviewer == null ? "" : nullSafe(interviewer.getTimeZone())),
                    csvCell(interviewee == null ? "" : interviewee.getName()),
                    csvCell(interviewee == null ? "" : interviewee.getEmail()),
                    csvCell(interviewee == null ? "" : nullSafe(interviewee.getTimeZone())),
                    csvCell(toCsvTimestamp(session.getStartedAt())),
                    csvCell(toCsvTimestamp(session.getEndedAt())),
                    csvCell(session.getFeedback() == null || session.getFeedback().getRating() == null ? "" : session.getFeedback().getRating().name()),
                    csvCell(session.getFeedback() == null || session.getFeedback().getRecommendationDecision() == null ? "" : session.getFeedback().getRecommendationDecision().name()),
                    csvCell(session.getFeedback() == null ? "" : nullSafe(session.getFeedback().getComments())),
                    csvCell(interviewee == null || interviewee.getIdentityCaptureStatus() == null ? "" : interviewee.getIdentityCaptureStatus().name()),
                    csvCell(interviewee == null || interviewee.getIdentityCaptureFailureReason() == null ? "" : interviewee.getIdentityCaptureFailureReason().name()),
                    csvCell(String.valueOf(activityEvents.size())),
                    csvCell(String.valueOf(tabSwitchCount)),
                    csvCell(String.valueOf(pasteCount))
            )).append('\n');
        }

        String filename = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
                .withZone(ZoneOffset.UTC)
                .format(nowUtc().toInstant()) + ".csv";

        return new CsvExport(filename, csv.toString());
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> listActiveSessions() {
        return sessionRepository.findByStatus(SessionStatus.ACTIVE).stream()
                .map(session -> toSessionResponse(session, true))
                .toList();
    }

    @Transactional
    public SessionResponse acceptDisclaimer(String sessionId, AcceptDisclaimerRequest request) {
        acceptDisclaimerInternal(sessionId, request.getRole());
        return reevaluatePreSessionState(sessionId);
    }

    @Transactional
    public ResumeResponse requestResume(String sessionId, ResumeRequest request, String clientIp, String userAgent) {
        InterviewSession session = getRequiredSession(sessionId);
        if (session.getStatus() != SessionStatus.ACTIVE && session.getStatus() != SessionStatus.READY_TO_START) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This session is not available for resume.");
        }

        Participant participant = validateParticipantIdentity(sessionId, request.getRole(), request.getName(), request.getEmail());
        participant.setTimeZone(normalizeTimeZone(request.getTimeZone()));
        String scenarioTag = determineScenarioTag(participant, request.getReason(), request.getDeviceId(), clientIp);

        if (request.getRole() == ParticipantRole.INTERVIEWEE) {
            String suspiciousRejectionReason = resolveIntervieweeResumeViolation(session, participant, scenarioTag);
            if (suspiciousRejectionReason != null) {
                SessionResponse rejectedSession = rejectIntervieweeForSuspiciousResume(session, participant, scenarioTag, suspiciousRejectionReason);
                return ResumeResponse.builder()
                        .status(ResumeResponse.REJECTED)
                        .approvalRequired(false)
                        .message(suspiciousRejectionReason)
                        .session(rejectedSession)
                        .build();
            }
        } else {
            validateInterviewerResumeWindow(session, participant);
        }

        boolean requiresApproval = request.getRole() == ParticipantRole.INTERVIEWEE
                && requiresInterviewerApproval(participant, request.getReason(), request.getDeviceId(), clientIp);

        participant.setResumeRequestedAt(nowUtc());
        participant.setResumeRejectedAt(null);

        if (requiresApproval) {
            participant.setAwaitingResumeApproval(true);
            participant.setPendingResumeReason(resolvePendingResumeReason(participant, request.getReason(), request.getDeviceId(), clientIp));
            participant.setDeviceId(request.getDeviceId());
            participant.setUserAgent(userAgent);
            participant.setLastKnownIp(clientIp);
            participant.setConnectionStatus(ParticipantConnectionStatus.AWAITING_APPROVAL);
            participantRepository.save(participant);
            appendSuspiciousScenario(session, scenarioTag);
            sessionRepository.save(session);
            saveSystemActivityEvent(sessionId, ParticipantRole.INTERVIEWEE, ActivityEventType.TAB_HIDDEN,
                    buildResumePendingMessage(participant, participant.getPendingResumeReason()));
            return ResumeResponse.builder()
                    .status(ResumeResponse.PENDING_APPROVAL)
                    .approvalRequired(true)
                    .message("Resume request sent to the interviewer for approval.")
                    .session(toSessionResponse(session, true))
                    .build();
        }

        participant.setAwaitingResumeApproval(false);
        participant.setPendingResumeReason(null);
        participant.setResumeApprovedAt(nowUtc());
        if (request.getRole() == ParticipantRole.INTERVIEWEE) {
            participant.setResumeCount((participant.getResumeCount() == null ? 0 : participant.getResumeCount()) + 1);
            appendSuspiciousScenario(session, scenarioTag);
        }
        markParticipantConnected(participant, request.getDeviceId(), clientIp, userAgent);
        participantRepository.save(participant);
        clearRecoveryWindow(session);
        sessionRepository.save(session);

        return ResumeResponse.builder()
                .status(ResumeResponse.APPROVED)
                .approvalRequired(false)
                .message("Resume approved.")
                .session(toSessionResponse(session, true))
                .build();
    }

    @Transactional
    public SessionResponse approveResume(String sessionId, ResumeApprovalRequest request, String clientIp, String userAgent) {
        InterviewSession session = getRequiredSession(sessionId);
        Participant interviewer = validateParticipantIdentity(sessionId, ParticipantRole.INTERVIEWER, request.getInterviewerName(), request.getInterviewerEmail());
        markParticipantConnected(interviewer, interviewer.getDeviceId(), clientIp, userAgent);
        participantRepository.save(interviewer);

        Participant interviewee = participantRepository.findBySessionIdAndRole(sessionId, ParticipantRole.INTERVIEWEE)
                .orElseThrow(() -> new IllegalArgumentException("Interviewee not registered"));
        if (!Boolean.TRUE.equals(interviewee.getAwaitingResumeApproval())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "There is no pending interviewee resume request.");
        }

        interviewee.setAwaitingResumeApproval(false);
        interviewee.setResumeApprovedAt(nowUtc());
        interviewee.setResumeRejectedAt(null);
        interviewee.setResumeCount((interviewee.getResumeCount() == null ? 0 : interviewee.getResumeCount()) + 1);
        interviewee.setConnectionStatus(ParticipantConnectionStatus.CONNECTED);
        interviewee.setLastSeenAt(nowUtc());
        interviewee.setDisconnectedAt(null);
        interviewee.setPendingResumeReason(null);
        participantRepository.save(interviewee);
        clearRecoveryWindow(session);
        sessionRepository.save(session);
        ensureFrontendWorkspaceIfNeeded(session);
        saveSystemActivityEvent(sessionId, ParticipantRole.INTERVIEWER, ActivityEventType.TAB_HIDDEN,
                "Interviewer approved the interviewee resume request.");
        return toSessionResponse(session, true);
    }

    @Transactional
    public SessionResponse rejectResume(String sessionId, ResumeApprovalRequest request, String clientIp, String userAgent) {
        InterviewSession session = getRequiredSession(sessionId);
        Participant interviewer = validateParticipantIdentity(sessionId, ParticipantRole.INTERVIEWER, request.getInterviewerName(), request.getInterviewerEmail());
        markParticipantConnected(interviewer, interviewer.getDeviceId(), clientIp, userAgent);
        participantRepository.save(interviewer);

        Participant interviewee = participantRepository.findBySessionIdAndRole(sessionId, ParticipantRole.INTERVIEWEE)
                .orElseThrow(() -> new IllegalArgumentException("Interviewee not registered"));
        if (!Boolean.TRUE.equals(interviewee.getAwaitingResumeApproval())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "There is no pending interviewee resume request.");
        }

        interviewee.setAwaitingResumeApproval(false);
        interviewee.setResumeRejectedAt(nowUtc());
        interviewee.setConnectionStatus(ParticipantConnectionStatus.DISCONNECTED);
        participantRepository.save(interviewee);

        String interviewerName = interviewer.getName() == null || interviewer.getName().isBlank()
                ? "The interviewer"
                : interviewer.getName().trim();
        String reason = interviewerName + " rejected the interviewee resume request after the session was interrupted. "
                + "The candidate has been disqualified because the required session recovery approval was not granted.";
        session.setSuspiciousRejected(true);
        session.setSuspiciousScenarioKey("RESUME_REJECTED_BY_INTERVIEWER");
        session.setSuspiciousActivityReason(reason);
        session.setFeedbackDraftRating(com.altimetrik.interview.enums.FeedbackRating.DISQUALIFIED);
        session.setFeedbackDraftRecommendationDecision(RecommendationDecision.NO);
        session.setFeedbackDraftComments("");
        session.setEndedAt(nowUtc());
        session.setStatus(SessionStatus.ENDED);
        clearRecoveryWindow(session);
        sessionRepository.save(session);

        saveSystemActivityEvent(sessionId, ParticipantRole.INTERVIEWER, ActivityEventType.TAB_HIDDEN, reason);
        return toSessionResponse(session, true);
    }

    @Transactional
    public SessionResponse registerHeartbeat(String sessionId, HeartbeatRequest request, String clientIp, String userAgent) {
        InterviewSession session = getRequiredSession(sessionId);
        Participant participant = participantRepository.findBySessionIdAndRole(sessionId, request.getRole())
                .orElseThrow(() -> new IllegalArgumentException("Participant not found"));
        markParticipantConnected(participant, request.getDeviceId(), clientIp, userAgent);
        participantRepository.save(participant);
        if (session.getRecoveryDeadlineAt() != null) {
            clearRecoveryWindow(session);
            sessionRepository.save(session);
        }
        ensureFrontendWorkspaceIfNeeded(session);
        return toSessionResponse(session, true);
    }

    @Transactional
    public SessionResponse disconnectParticipant(String sessionId, DisconnectParticipantRequest request) {
        InterviewSession session = getRequiredSession(sessionId);
        Participant participant = participantRepository.findBySessionIdAndRole(sessionId, request.getRole())
                .orElseThrow(() -> new IllegalArgumentException("Participant not found"));

        participant.setConnectionStatus(ParticipantConnectionStatus.DISCONNECTED);
        participant.setDisconnectedAt(nowUtc());
        if (request.getRole() == ParticipantRole.INTERVIEWEE && request.getReason() == ResumeReason.TAB_OR_BROWSER_CLOSED) {
            participant.setPendingResumeReason(ResumeReason.TAB_OR_BROWSER_CLOSED);
        }
        participantRepository.save(participant);

        if (request.getRole() == ParticipantRole.INTERVIEWEE && request.getReason() == ResumeReason.TAB_OR_BROWSER_CLOSED) {
            session.setInterruptedAt(nowUtc());
            session.setRecoveryDeadlineAt(nowUtc().plusSeconds(RECOVERY_WINDOW_SEC));
            session.setRecoveryRequiredRole(null);
            sessionRepository.save(session);
            saveSystemActivityEvent(sessionId, ParticipantRole.INTERVIEWEE, ActivityEventType.TAB_HIDDEN,
                    "Interviewee closed or refreshed the browser/tab during the interview. Resume now requires interviewer approval.");
        } else if (request.getRole() == ParticipantRole.INTERVIEWER) {
            session.setInterruptedAt(nowUtc());
            session.setRecoveryDeadlineAt(nowUtc().plusSeconds(RECOVERY_WINDOW_SEC));
            session.setRecoveryRequiredRole(ParticipantRole.INTERVIEWER);
            sessionRepository.save(session);
            saveSystemActivityEvent(sessionId, ParticipantRole.INTERVIEWER, ActivityEventType.TAB_HIDDEN,
                    "Interviewer disconnected during the interview and must resume within the allowed recovery window.");
        }

        return toSessionResponse(session, true);
    }

    @Transactional
    public List<SessionResponse> closeInterruptedSessionsPastRecoveryWindow() {
        OffsetDateTime now = nowUtc();
        return sessionRepository.findByStatus(SessionStatus.ACTIVE).stream()
                .filter(session -> session.getRecoveryDeadlineAt() != null && !session.getRecoveryDeadlineAt().isAfter(now))
                .map(this::autoCloseInterruptedSessionIfNeeded)
                .filter(response -> response != null)
                .toList();
    }

    @Transactional
    public SessionResponse startSession(String sessionId) {
        InterviewSession session = getRequiredSession(sessionId);
        if (session.getStatus() == SessionStatus.ENDED
                || session.getStatus() == SessionStatus.EXPIRED
                || session.getStatus() == SessionStatus.AUTH_FAILED) {
            throw new IllegalArgumentException("Session can no longer be started");
        }
        if (session.getStatus() != SessionStatus.READY_TO_START && session.getStatus() != SessionStatus.ACTIVE) {
            throw new IllegalArgumentException("Session is not ready to start");
        }

        session.setStatus(SessionStatus.ACTIVE);
        if (session.getStartedAt() == null) {
            session.setStartedAt(nowUtc());
        }
        if (session.getDurationSec() == null || session.getDurationSec() == 0) {
            session.setDurationSec(DEFAULT_DURATION_SEC);
        }

        clearRecoveryWindow(session);
        sessionRepository.save(session);
        ensureFrontendWorkspaceIfNeeded(session);
        return toSessionResponse(session, true);
    }

    @Transactional
    public SessionResponse extendSession(String sessionId) {
        InterviewSession session = getRequiredSession(sessionId);
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new IllegalArgumentException("Only active sessions can be extended");
        }
        if (Boolean.TRUE.equals(session.getExtensionUsed())) {
            throw new IllegalArgumentException("Session has already been extended once");
        }
        int remainingSec = calculateRemainingSec(session);
        if (remainingSec > EXTENSION_THRESHOLD_SEC) {
            throw new IllegalArgumentException("Extension is only allowed in the last 15 minutes");
        }

        int newDuration = Math.min(MAX_DURATION_SEC, (session.getDurationSec() == null ? DEFAULT_DURATION_SEC : session.getDurationSec()) + EXTENSION_SEC);
        session.setDurationSec(newDuration);
        session.setExtensionUsed(true);
        sessionRepository.save(session);
        return toSessionResponse(session, true);
    }

    @Transactional
    public SessionResponse submitFeedback(String sessionId, FeedbackRequest request) {
        InterviewSession session = getRequiredSession(sessionId);
        Feedback feedback = feedbackRepository.findBySessionId(sessionId).orElseGet(Feedback::new);
        feedback.setSessionId(sessionId);
        feedback.setRating(request.getRating());
        feedback.setComments(request.getComments());
        RecommendationDecision recommendationDecision = (request.getRating() == com.altimetrik.interview.enums.FeedbackRating.BAD
                || request.getRating() == com.altimetrik.interview.enums.FeedbackRating.DISQUALIFIED)
                ? RecommendationDecision.NO
                : request.getRecommendationDecision();
        feedback.setRecommendationDecision(recommendationDecision);
        feedback.setRecommendation(recommendationDecision == RecommendationDecision.YES);
        feedbackRepository.save(feedback);
        session.setFeedbackDraftRating(null);
        session.setFeedbackDraftComments(null);
        session.setFeedbackDraftRecommendationDecision(null);
        sessionRepository.save(session);
        return toSessionResponse(session, true);
    }

    public SessionResponse endSession(String sessionId, EndSessionRequest request) {
        return endSession(sessionId, request.getFinalCode(), request.getCodeFiles(), request.getActiveFilePath(), null);
    }

    public SessionResponse endSession(String sessionId, String finalCode, FeedbackRequest feedbackRequest) {
        return endSession(sessionId, finalCode, null, null, feedbackRequest);
    }

    public SessionResponse endSession(String sessionId, String finalCode, List<EditableCodeFileDto> codeFiles, FeedbackRequest feedbackRequest) {
        return endSession(sessionId, finalCode, codeFiles, null, feedbackRequest);
    }

    public SessionResponse endSession(String sessionId,
                                      String finalCode,
                                      List<EditableCodeFileDto> codeFiles,
                                      String activeFilePath,
                                      FeedbackRequest feedbackRequest) {
        InterviewSession session = getRequiredSession(sessionId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new IllegalArgumentException("Session is already ended");
        }
        validateWorkspaceFiles(session.getTechnology(), codeFiles);

        CodeUpdateRequest codeUpdateRequest = new CodeUpdateRequest();
        codeUpdateRequest.setCode(finalCode);
        codeUpdateRequest.setCodeFiles(codeFiles);
        codeUpdateRequest.setUpdatedByRole(ParticipantRole.INTERVIEWER);
        upsertCodeState(sessionId, codeUpdateRequest);

        List<EditableCodeFileDto> effectiveFiles = codeFiles != null && !codeFiles.isEmpty()
                ? codeFiles
                : resolveEditableFiles(session, codeStateRepository.findBySessionId(sessionId).orElse(null));
        String executableCode = resolveExecutableCodeForPath(effectiveFiles, activeFilePath, finalCode == null ? "" : finalCode);
        ExecuteResponse executionResult = sandboxClientService.execute(buildExecuteRequest(sessionId, executableCode, effectiveFiles, activeFilePath, session.getTechnology()));
        RunResult runResult = runResultRepository.findTopBySessionIdAndFilePathIsNullOrderByCompiledAtDesc(sessionId).orElseGet(RunResult::new);
        runResult.setSessionId(sessionId);
        runResult.setFilePath(null);
        runResult.setDisplayName(null);
        runResult.setSourceSnapshot(null);
        runResult.setStdout(executionResult.getStdout());
        runResult.setStderr((executionResult.getStderr() == null || executionResult.getStderr().isBlank())
                ? String.join("\n", executionResult.getCompileErrors() == null ? List.of() : executionResult.getCompileErrors())
                : executionResult.getStderr());
        runResult.setExitStatus(executionResult.getExitCode());
        runResult.setExecutionTimeMs(executionResult.getExecutionTimeMs());
        runResultRepository.save(runResult);
        captureFinalPreviewIfAvailable(session, executionResult);

        if (feedbackRequest != null) {
            submitFeedback(sessionId, feedbackRequest);
        }

        session.setEndedAt(nowUtc());
        session.setStatus(SessionStatus.ENDED);
        clearRecoveryWindow(session);
        sessionRepository.save(session);
        generateAiRecommendationAfterEndIfNeeded(session);
        cleanupFrontendWorkspaceIfNeeded(session);
        return getSession(sessionId);
    }

    @Transactional
    public SessionResponse abandonSession(String sessionId, String finalCode) {
        InterviewSession session = getRequiredSession(sessionId);
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new IllegalArgumentException("Only active sessions can be marked incomplete");
        }

        upsertCodeState(sessionId, finalCode == null ? "" : finalCode, ParticipantRole.INTERVIEWER);

        ExecuteResponse executionResult = sandboxClientService.execute(buildExecuteRequest(sessionId, finalCode == null ? "" : finalCode, null, null, session.getTechnology()));
        RunResult runResult = runResultRepository.findTopBySessionIdAndFilePathIsNullOrderByCompiledAtDesc(sessionId).orElseGet(RunResult::new);
        runResult.setSessionId(sessionId);
        runResult.setFilePath(null);
        runResult.setDisplayName(null);
        runResult.setSourceSnapshot(null);
        runResult.setStdout(executionResult.getStdout());
        runResult.setStderr((executionResult.getStderr() == null || executionResult.getStderr().isBlank())
                ? String.join("\n", executionResult.getCompileErrors() == null ? List.of() : executionResult.getCompileErrors())
                : executionResult.getStderr());
        runResult.setExitStatus(executionResult.getExitCode());
        runResultRepository.save(runResult);
        captureFinalPreviewIfAvailable(session, executionResult);

        session.setIncomplete(true);
        session.setEndedAt(nowUtc());
        session.setStatus(SessionStatus.ENDED);
        clearRecoveryWindow(session);
        sessionRepository.save(session);
        cleanupFrontendWorkspaceIfNeeded(session);

        return toSessionResponse(session, true);
    }

    private void generateAiRecommendationAfterEndIfNeeded(InterviewSession session) {
        if (!isAiInterview(session) || session.getAiRecommendationGeneratedAt() != null) {
            return;
        }
        try {
            recommendAiInterview(session.getId());
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.CONFLICT) {
                log.info("Skipping AI recommendation for session {}: {}", session.getId(), exception.getReason());
            } else {
                log.warn("AI recommendation could not be generated for ended session {}: {}",
                        session.getId(), exception.getReason());
            }
        } catch (RuntimeException exception) {
            log.warn("AI recommendation could not be generated for ended session {}", session.getId(), exception);
        }
    }

    @Transactional
    public SessionResponse updateCodeState(String sessionId, String latestCode, ParticipantRole updatedByRole) {
        CodeUpdateRequest request = new CodeUpdateRequest();
        request.setCode(latestCode);
        request.setUpdatedByRole(updatedByRole);
        return updateCodeState(sessionId, request);
    }

    @Transactional
    public SessionResponse updateCodeState(String sessionId, CodeUpdateRequest request) {
        InterviewSession session = getRequiredSession(sessionId);
        if (session.getStatus() == SessionStatus.ENDED || session.getStatus() == SessionStatus.EXPIRED) {
            throw new IllegalArgumentException("Session is read-only");
        }
        validateWorkspaceFiles(session.getTechnology(), request.getCodeFiles());
        upsertCodeState(sessionId, request);
        return toSessionResponse(session, true);
    }

    @Transactional
    public ActivityEventDto recordActivityEvent(String sessionId, ActivityEventRequest request) {
        InterviewSession session = getRequiredSession(sessionId);
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new IllegalArgumentException("Activity events can only be recorded for active sessions");
        }
        if (request.getParticipantRole() != ParticipantRole.INTERVIEWEE) {
            throw new IllegalArgumentException("Only interviewee activity is tracked");
        }

        int occurrenceCount = Math.toIntExact(sessionActivityEventRepository.countBySessionIdAndEventType(sessionId, request.getEventType()) + 1);
        ActivityEventSeverity severity = resolveActivitySeverity(session, request, occurrenceCount);
        SessionActivityEvent event = new SessionActivityEvent();
        event.setSessionId(sessionId);
        event.setParticipantRole(request.getParticipantRole());
        event.setEventType(request.getEventType());
        event.setSeverity(severity);
        event.setDetail(buildActivityDetail(request));
        event.setCandidateMessage(buildCandidateActivityMessage(request, severity));
        event.setDurationMs(normalizeDurationMs(request.getDurationMs()));
        event.setOccurrenceCount(occurrenceCount);
        event = sessionActivityEventRepository.save(event);
        return toActivityEventDto(event);
    }

    private void acceptDisclaimerInternal(String sessionId, ParticipantRole role) {
        Participant participant = participantRepository.findBySessionIdAndRole(sessionId, role)
                .orElseThrow(() -> new IllegalArgumentException("Participant not found"));
        participant.setDisclaimerAcceptedAt(nowUtc());
        if (role == ParticipantRole.INTERVIEWER && participant.getJoinedAt() == null) {
            participant.setJoinedAt(nowUtc());
        }
        participantRepository.save(participant);
    }

    private Participant createParticipant(String sessionId, ParticipantRole role, String name, String email, String timeZone) {
        Participant participant = new Participant();
        participant.setSessionId(sessionId);
        participant.setRole(role);
        participant.setName(name.trim());
        participant.setEmail(email.trim().toLowerCase(Locale.ROOT));
        participant.setTimeZone(normalizeTimeZone(timeZone));
        if (role == ParticipantRole.INTERVIEWEE) {
            participant.setIdentityCaptureStatus(IdentityCaptureStatus.PENDING);
        }
        return participant;
    }

    private Participant validateParticipantIdentity(String sessionId, ParticipantRole role, String name, String email) {
        Participant participant = participantRepository.findBySessionIdAndRole(sessionId, role)
                .orElseThrow(() -> new IllegalArgumentException("Participant not found"));

        if (!participant.getName().equalsIgnoreCase(name.trim())
                || !participant.getEmail().equalsIgnoreCase(email.trim())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Details do not match our records. Please enter the same name and email that were registered for this interview."
            );
        }

        return participant;
    }

    private boolean requiresInterviewerApproval(Participant participant, ResumeReason requestedReason, String deviceId, String clientIp) {
        if (requestedReason == ResumeReason.TAB_OR_BROWSER_CLOSED) {
            return true;
        }
        if (participant.getPendingResumeReason() == ResumeReason.TAB_OR_BROWSER_CLOSED) {
            return true;
        }
        if (participant.getDeviceId() != null && deviceId != null && !participant.getDeviceId().equals(deviceId)) {
            return true;
        }
        return participant.getLastKnownIp() != null && clientIp != null && !participant.getLastKnownIp().equals(clientIp);
    }

    private ResumeReason resolvePendingResumeReason(Participant participant, ResumeReason requestedReason, String deviceId, String clientIp) {
        if (requestedReason == ResumeReason.TAB_OR_BROWSER_CLOSED) {
            return ResumeReason.TAB_OR_BROWSER_CLOSED;
        }
        if (participant.getDeviceId() != null && deviceId != null && !participant.getDeviceId().equals(deviceId)) {
            return ResumeReason.DEVICE_CHANGE;
        }
        if (participant.getLastKnownIp() != null && clientIp != null && !participant.getLastKnownIp().equals(clientIp)) {
            return ResumeReason.NETWORK_CHANGE;
        }
        return requestedReason;
    }

    private String determineScenarioTag(Participant participant, ResumeReason requestedReason, String deviceId, String clientIp) {
        ResumeReason effectiveReason = requestedReason;
        if (effectiveReason == null || effectiveReason == ResumeReason.MANUAL_RESUME) {
            effectiveReason = participant.getPendingResumeReason();
        }

        if (effectiveReason == ResumeReason.TAB_OR_BROWSER_CLOSED) {
            return SCENARIO_REFRESH;
        }
        if (participant.getDeviceId() != null && deviceId != null && !participant.getDeviceId().equals(deviceId)) {
            return SCENARIO_DEVICE;
        }
        if (participant.getLastKnownIp() != null && clientIp != null && !participant.getLastKnownIp().equals(clientIp)) {
            return SCENARIO_NETWORK;
        }
        if (effectiveReason == ResumeReason.NETWORK_CHANGE) {
            return SCENARIO_NETWORK;
        }
        if (effectiveReason == ResumeReason.DEVICE_CHANGE) {
            return SCENARIO_DEVICE;
        }
        return SCENARIO_CONNECTION;
    }

    private void markParticipantConnected(Participant participant, String deviceId, String clientIp, String userAgent) {
        participant.setConnectionStatus(ParticipantConnectionStatus.CONNECTED);
        if (participant.getJoinedAt() == null) {
            participant.setJoinedAt(nowUtc());
        }
        participant.setDeviceId(deviceId == null || deviceId.isBlank() ? participant.getDeviceId() : deviceId.trim());
        participant.setUserAgent(userAgent);
        participant.setLastKnownIp(clientIp);
        participant.setLastSeenAt(nowUtc());
        participant.setDisconnectedAt(null);
        participant.setAwaitingResumeApproval(false);
        participant.setPendingResumeReason(null);
    }

    private String resolveIntervieweeResumeViolation(InterviewSession session, Participant participant, String scenarioTag) {
        int resumeCount = participant.getResumeCount() == null ? 0 : participant.getResumeCount();
        if (resumeCount >= 1) {
            return buildSuspiciousRejectionMessage(session, scenarioTag, false);
        }

        OffsetDateTime deadline = session.getRecoveryDeadlineAt();
        if (deadline != null && deadline.isBefore(nowUtc())) {
            return buildSuspiciousRejectionMessage(session, scenarioTag, true);
        }

        OffsetDateTime disconnectedAt = participant.getDisconnectedAt();
        if (disconnectedAt != null && disconnectedAt.plusSeconds(RECOVERY_WINDOW_SEC).isBefore(nowUtc())) {
            return buildSuspiciousRejectionMessage(session, scenarioTag, true);
        }

        OffsetDateTime lastSeenAt = participant.getLastSeenAt();
        if (lastSeenAt != null && lastSeenAt.plusSeconds(RECOVERY_WINDOW_SEC).isBefore(nowUtc())) {
            return buildSuspiciousRejectionMessage(session, scenarioTag, true);
        }

        return null;
    }

    private void validateInterviewerResumeWindow(InterviewSession session, Participant participant) {
        OffsetDateTime deadline = session.getRecoveryDeadlineAt();
        if (session.getRecoveryRequiredRole() == ParticipantRole.INTERVIEWER && deadline != null && deadline.isBefore(nowUtc())) {
            throw new ResponseStatusException(HttpStatus.GONE, "The interviewer recovery window has expired and the session can no longer be resumed.");
        }

        OffsetDateTime disconnectedAt = participant.getDisconnectedAt();
        if (disconnectedAt != null && disconnectedAt.plusSeconds(RECOVERY_WINDOW_SEC).isBefore(nowUtc())) {
            throw new ResponseStatusException(HttpStatus.GONE, "The interviewer recovery window has expired and the session can no longer be resumed.");
        }
    }

    private SessionResponse rejectIntervieweeForSuspiciousResume(InterviewSession session, Participant participant, String scenarioTag, String reason) {
        participant.setAwaitingResumeApproval(false);
        participant.setResumeRejectedAt(nowUtc());
        participant.setConnectionStatus(ParticipantConnectionStatus.DISCONNECTED);
        participantRepository.save(participant);

        String scenarioKey = buildSuspiciousScenarioKey(session, scenarioTag);
        session.setSuspiciousRejected(true);
        session.setSuspiciousScenarioKey(scenarioKey);
        session.setSuspiciousActivityReason(reason);
        session.setFeedbackDraftRating(com.altimetrik.interview.enums.FeedbackRating.DISQUALIFIED);
        session.setFeedbackDraftRecommendationDecision(RecommendationDecision.NO);
        session.setFeedbackDraftComments(reason);
        session.setEndedAt(nowUtc());
        session.setStatus(SessionStatus.ENDED);
        clearRecoveryWindow(session);
        sessionRepository.save(session);

        saveSystemActivityEvent(session.getId(), ParticipantRole.INTERVIEWEE, ActivityEventType.TAB_HIDDEN, reason);
        return toSessionResponse(session, true);
    }

    private void appendSuspiciousScenario(InterviewSession session, String scenarioTag) {
        List<String> history = getSuspiciousScenarioHistory(session);
        history.add(scenarioTag);
        session.setSuspiciousActivityHistory(String.join(",", history));
    }

    private String buildSuspiciousScenarioKey(InterviewSession session, String currentScenarioTag) {
        List<String> history = getSuspiciousScenarioHistory(session);
        List<String> effective = new ArrayList<>();
        if (!history.isEmpty()) {
            effective.add(history.get(Math.max(0, history.size() - 1)));
        }
        effective.add(currentScenarioTag);
        if (effective.size() == 1) {
            effective.add(currentScenarioTag);
        }
        return effective.get(0) + "__" + effective.get(1);
    }

    private List<String> getSuspiciousScenarioHistory(InterviewSession session) {
        if (session.getSuspiciousActivityHistory() == null || session.getSuspiciousActivityHistory().isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.stream(session.getSuspiciousActivityHistory().split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList());
    }

    private String buildSuspiciousRejectionMessage(InterviewSession session, String currentScenarioTag, boolean lateResume) {
        String scenarioKey = buildSuspiciousScenarioKey(session, currentScenarioTag);
        String base = SUSPICIOUS_FEEDBACK_LIBRARY.get(scenarioKey);
        if (base != null) {
            return lateResume ? base + " The final resume attempt happened after the allowed recovery window." : base;
        }

        List<String> parts = Arrays.stream(scenarioKey.split("__"))
                .map(this::formatScenarioTag)
                .toList();
        String uniqueSummary = String.join(" and ", new LinkedHashSet<>(parts));
        String generic = "Candidate was disqualified because repeated session interruptions involving " + uniqueSummary + " could not be cleared within the interview integrity policy.";
        return lateResume ? generic + " The final recovery attempt happened after the allowed session recovery window." : generic;
    }

    private String formatScenarioTag(String scenarioTag) {
        return switch (scenarioTag) {
            case SCENARIO_REFRESH -> "session refresh or reopen activity";
            case SCENARIO_NETWORK -> "network change activity";
            case SCENARIO_DEVICE -> "device change activity";
            case SCENARIO_CONNECTION -> "connection recovery activity";
            default -> "session continuity violations";
        };
    }

    private void clearRecoveryWindow(InterviewSession session) {
        session.setInterruptedAt(null);
        session.setRecoveryDeadlineAt(null);
        session.setRecoveryRequiredRole(null);
    }

    private SessionResponse autoCloseInterruptedSessionIfNeeded(InterviewSession session) {
        List<Participant> participants = participantRepository.findBySessionId(session.getId());
        boolean interviewerConnected = participants.stream()
                .anyMatch(participant -> participant.getRole() == ParticipantRole.INTERVIEWER
                        && participant.getConnectionStatus() == ParticipantConnectionStatus.CONNECTED);
        boolean anyoneConnected = participants.stream()
                .anyMatch(participant -> participant.getConnectionStatus() == ParticipantConnectionStatus.CONNECTED);

        boolean recoverySatisfied = session.getRecoveryRequiredRole() == ParticipantRole.INTERVIEWER
                ? interviewerConnected
                : anyoneConnected;

        if (recoverySatisfied) {
            clearRecoveryWindow(session);
            sessionRepository.save(session);
            return null;
        }

        SessionResponse response = abandonSession(session.getId(), codeStateRepository.findBySessionId(session.getId())
                .map(CodeState::getLatestCode)
                .orElse(""));
        String detail = session.getRecoveryRequiredRole() == ParticipantRole.INTERVIEWER
                ? "Session was marked incomplete because the interviewer did not resume within the 120-second recovery window."
                : "Session was marked incomplete because both participants remained disconnected for more than 120 seconds after the browser/tab interruption.";
        saveSystemActivityEvent(session.getId(),
                session.getRecoveryRequiredRole() == ParticipantRole.INTERVIEWER ? ParticipantRole.INTERVIEWER : ParticipantRole.INTERVIEWEE,
                ActivityEventType.TAB_HIDDEN,
                detail);
        return response;
    }

    private void saveSystemActivityEvent(String sessionId, ParticipantRole role, ActivityEventType eventType, String detail) {
        SessionActivityEvent event = new SessionActivityEvent();
        event.setSessionId(sessionId);
        event.setParticipantRole(role);
        event.setEventType(eventType);
        event.setDetail(detail);
        sessionActivityEventRepository.save(event);
    }

    private String buildResumePendingMessage(Participant participant, ResumeReason reason) {
        String name = participant.getName() == null || participant.getName().isBlank() ? "Interviewee" : participant.getName().trim();
        return switch (reason) {
            case DEVICE_CHANGE -> name + " is trying to resume from a different device. Interviewer approval is required.";
            case NETWORK_CHANGE -> name + " is trying to resume from a different network. Interviewer approval is required.";
            case TAB_OR_BROWSER_CLOSED -> name + " closed or refreshed the browser/tab and now requires interviewer approval to resume.";
            default -> name + " has requested interviewer approval to resume the session.";
        };
    }

    private Integer normalizeYearsOfExperience(Integer yearsOfExperience) {
        if (yearsOfExperience == null) {
            return null;
        }
        return Math.max(0, Math.min(50, yearsOfExperience));
    }

    private String normalizeTargetRole(String targetRole) {
        if (targetRole == null || targetRole.isBlank()) {
            return null;
        }
        String trimmed = targetRole.trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
    }

    private void ensureAiInterview(InterviewSession session) {
        if (!isAiInterview(session)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "AI operations are available only for AI interviewer sessions.");
        }
    }

    private AiQuestionResponse generateAiQuestionOrFallback(InterviewSession session,
                                                            AiQuestionGenerationRequest request,
                                                            List<EditableCodeFileDto> previousQuestionFiles) {
        try {
            return aiInterviewClientService.generateQuestion(request);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE
                    || exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS
                    || exception.getStatusCode() == HttpStatus.BAD_GATEWAY) {
                log.warn("AI provider unavailable for session {} question {}. Using fallback question. Reason: {}",
                        session.getId(), request.getQuestionNumber(), exception.getReason());
                return fallbackAiQuestion(session.getTechnology(), request, previousQuestionFiles);
            }
            throw exception;
        }
    }

    private AiQuestionResponse generateValidatedAiQuestion(InterviewSession session,
                                                           AiQuestionGenerationRequest request,
                                                           List<EditableCodeFileDto> previousQuestionFiles) {
        if (!requiresReferenceValidation(session.getTechnology())) {
            return generateAiQuestionOrFallback(session, request, previousQuestionFiles);
        }

        List<String> failures = new ArrayList<>();
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            request.setVariationSeed(request.getSessionId() + "-" + request.getQuestionNumber() + "-validated-" + attempt + "-" + UUID.randomUUID());
            AiQuestionResponse generated = generateAiQuestionOrFallback(session, request, previousQuestionFiles);
            QuestionValidationResult validation = validateGeneratedQuestion(session, generated, request.getQuestionNumber());
            if (validation.valid()) {
                return generated;
            }
            failures.add("Attempt " + attempt + ": " + validation.reason());
            log.warn("AI generated question failed validation for session {} question {} attempt {}: {}",
                    session.getId(), request.getQuestionNumber(), attempt, validation.reason());
        }

        AiQuestionResponse fallback = hardcodedFallbackAiQuestion(session.getTechnology(), request);
        QuestionValidationResult fallbackValidation = validateGeneratedQuestion(session, fallback, request.getQuestionNumber());
        if (fallbackValidation.valid()) {
            log.warn("Using validated fallback question for session {} question {} after AI validation failures: {}",
                    session.getId(), request.getQuestionNumber(), String.join(" | ", failures));
            return fallback;
        }

        failures.add("Fallback: " + fallbackValidation.reason());
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Unable to prepare a validated AI question right now. " + String.join(" | ", failures)
        );
    }

    private boolean requiresReferenceValidation(TechnologySkill technology) {
        return technology == TechnologySkill.JAVA || technology == TechnologySkill.PYTHON;
    }

    private QuestionValidationResult validateGeneratedQuestion(InterviewSession session, AiQuestionResponse question, Integer questionNumber) {
        if (question == null) {
            return QuestionValidationResult.invalid("question response was empty");
        }
        String starterCode = question.getStarterCode() == null ? "" : question.getStarterCode();
        String referenceSolution = question.getReferenceSolution() == null ? "" : question.getReferenceSolution();
        if (referenceSolution.isBlank()) {
            return QuestionValidationResult.invalid("referenceSolution was empty");
        }

        List<String> starterAssertions = extractValidationLines(starterCode);
        if (starterAssertions.isEmpty()) {
            return QuestionValidationResult.invalid("starterCode did not contain runnable validation assertions");
        }
        List<String> referenceAssertions = extractValidationLines(referenceSolution);
        if (referenceAssertions.isEmpty()) {
            return QuestionValidationResult.invalid("referenceSolution did not contain validation assertions");
        }
        List<String> normalizedReferenceAssertions = referenceAssertions.stream()
                .map(this::normalizeForIntegrity)
                .toList();
        List<String> missingAssertions = starterAssertions.stream()
                .filter(assertion -> !normalizedReferenceAssertions.contains(normalizeForIntegrity(assertion)))
                .toList();
        if (!missingAssertions.isEmpty()) {
            return QuestionValidationResult.invalid("referenceSolution did not include starter assertions: " + String.join(" | ", missingAssertions));
        }

        String validationSessionId = session.getId() + "-question-validation";
        String filePath = normalizeAiFilePath(session.getTechnology(), question.getFilePath(), defaultAiQuestionPath(session.getTechnology(), questionNumber == null ? 1 : questionNumber));
        ExecuteResponse response = sandboxClientService.execute(buildExecuteRequest(
                validationSessionId,
                referenceSolution,
                null,
                filePath,
                session.getTechnology()
        ));
        String stderr = firstNonBlank(response.getStderr(), String.join("\n", response.getCompileErrors() == null ? List.of() : response.getCompileErrors()));
        if (response.getExitCode() != 0 || !stderr.isBlank()) {
            return QuestionValidationResult.invalid("reference solution failed sandbox run with exit " + response.getExitCode() + ": " + compactValidationMessage(stderr));
        }
        return QuestionValidationResult.ok();
    }

    private String compactValidationMessage(String value) {
        if (value == null || value.isBlank()) {
            return "no error details";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() > 240 ? compact.substring(0, 240) : compact;
    }

    private record QuestionValidationResult(boolean valid, String reason) {
        static QuestionValidationResult ok() {
            return new QuestionValidationResult(true, "");
        }

        static QuestionValidationResult invalid(String reason) {
            return new QuestionValidationResult(false, reason == null || reason.isBlank() ? "validation failed" : reason);
        }
    }

    private AiQuestionResponse fallbackAiQuestion(TechnologySkill technology,
                                                  AiQuestionGenerationRequest request,
                                                  List<EditableCodeFileDto> previousQuestionFiles) {
        AiQuestionResponse questionBankFallback = questionBankFallback(technology, request, previousQuestionFiles);
        if (questionBankFallback != null) {
            return questionBankFallback;
        }

        return hardcodedFallbackAiQuestion(technology, request);
    }

    private AiQuestionResponse hardcodedFallbackAiQuestion(TechnologySkill technology, AiQuestionGenerationRequest request) {
        int difficultyLevel = normalizeDifficultyLevel(null, request.getCurrentDifficulty());
        return switch (technology) {
            case PYTHON -> fallbackPythonQuestion(request, difficultyLevel);
            case ANGULAR -> fallbackAngularQuestion(request, difficultyLevel);
            case REACT -> fallbackReactQuestion(request, difficultyLevel);
            default -> fallbackJavaQuestion(request, difficultyLevel);
        };
    }

    private AiQuestionResponse questionBankFallback(TechnologySkill technology,
                                                    AiQuestionGenerationRequest request,
                                                    List<EditableCodeFileDto> previousQuestionFiles) {
        if (technology != TechnologySkill.JAVA && technology != TechnologySkill.PYTHON) {
            return null;
        }
        int requestedLevel = normalizeDifficultyLevel(null, request.getCurrentDifficulty());
        List<String> previousTitles = request.getPreviousQuestionTitles() == null ? List.of() : request.getPreviousQuestionTitles();
        List<InterviewQuestionBank> candidates = interviewQuestionBankRepository.findByTechnologyAndActiveTrueOrderByDifficultyLevelAscTitleAsc(technology);
        if (candidates.isEmpty()) {
            return null;
        }

        List<InterviewQuestionBank> exactLevelCandidates = candidates.stream()
                .filter(question -> question.getDifficultyLevel() != null && question.getDifficultyLevel() == requestedLevel)
                .filter(question -> previousTitles.stream().noneMatch(previous -> previous.equalsIgnoreCase(question.getTitle())))
                .filter(question -> !matchesPreviousQuestion(question, previousQuestionFiles))
                .toList();
        List<InterviewQuestionBank> nearLevelCandidates = candidates.stream()
                .filter(question -> Math.abs(normalizeDifficultyLevel(question.getDifficultyLevel()) - requestedLevel) <= 1)
                .filter(question -> previousTitles.stream().noneMatch(previous -> previous.equalsIgnoreCase(question.getTitle())))
                .filter(question -> !matchesPreviousQuestion(question, previousQuestionFiles))
                .toList();
        List<InterviewQuestionBank> selectionPool = !exactLevelCandidates.isEmpty()
                ? exactLevelCandidates
                : !nearLevelCandidates.isEmpty() ? nearLevelCandidates : candidates;
        InterviewQuestionBank selected = selectionPool.get(Math.floorMod(
                Objects.hash(request.getSessionId(), request.getQuestionNumber(), request.getVariationSeed(), requestedLevel),
                selectionPool.size()));

        return AiQuestionResponse.builder()
                .title(selected.getTitle())
                .filePath(selected.getFilePath())
                .displayName("Question " + (request.getQuestionNumber() == null ? 1 : request.getQuestionNumber()))
                .problemStatement(selected.getProblemStatement())
                .starterCode(selected.getStarterCode())
                .difficulty(String.valueOf(normalizeDifficultyLevel(selected.getDifficultyLevel())))
                .difficultyLevel(normalizeDifficultyLevel(selected.getDifficultyLevel()))
                .idealDurationMinutes(normalizeIdealDuration(selected.getIdealDurationMinutes(), normalizeDifficultyLevel(selected.getDifficultyLevel())))
                .referenceSolution(selected.getReferenceSolution())
                .expectedTimeComplexity(selected.getExpectedTimeComplexity())
                .expectedSpaceComplexity(selected.getExpectedSpaceComplexity())
                .concepts(splitList(selected.getConcepts()))
                .evaluationFocus(splitList(selected.getEvaluationFocus()))
                .build();
    }

    private List<String> aiQuestionHistoryForGeneration(List<EditableCodeFileDto> questionFiles) {
        if (questionFiles == null || questionFiles.isEmpty()) {
            return List.of();
        }
        return questionFiles.stream()
                .map(file -> {
                    String source = firstNonBlank(file.getOriginalProblemStatement(), file.getContent());
                    String compactSource = source == null ? "" : source.replaceAll("\\s+", " ").trim();
                    if (compactSource.length() > 320) {
                        compactSource = compactSource.substring(0, 320);
                    }
                    return firstNonBlank(file.getDisplayName(), file.getPath()) + ": " + compactSource;
                })
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private boolean matchesPreviousQuestion(InterviewQuestionBank question, List<EditableCodeFileDto> previousQuestionFiles) {
        if (question == null || previousQuestionFiles == null || previousQuestionFiles.isEmpty()) {
            return false;
        }
        String bankStarter = normalizeQuestionMatchText(question.getStarterCode());
        String bankProblem = normalizeQuestionMatchText(question.getProblemStatement());
        return previousQuestionFiles.stream().anyMatch(file -> {
            String previousStarter = normalizeQuestionMatchText(firstNonBlank(file.getOriginalStarterCode(), file.getContent()));
            String previousProblem = normalizeQuestionMatchText(firstNonBlank(file.getOriginalProblemStatement(), file.getContent()));
            return (!bankStarter.isBlank() && bankStarter.equals(previousStarter))
                    || (!bankProblem.isBlank() && bankProblem.equals(previousProblem));
        });
    }

    private String normalizeQuestionMatchText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private AiQuestionResponse fallbackJavaQuestion(AiQuestionGenerationRequest request, int difficultyLevel) {
        String methodName = difficultyLevel >= 3 ? "hasBalancedBrackets" : "countCharOccurrences";
        String problem = difficultyLevel >= 3
                ? """
                Implement a Java method `hasBalancedBrackets` that receives a non-null string and returns true when all `()`, `{}`, and `[]` brackets are balanced and correctly nested. Ignore all non-bracket characters.

                Examples:
                - "{[()]}" -> true
                - "{[(])}" -> false
                - "abc(def)[x]" -> true
                - "(((" -> false
                """
                : """
                Implement a Java method `countCharOccurrences` that receives a non-null string `text` and a character `targetChar`, then returns how many times the character appears in the text. The comparison is case-sensitive.

                Examples:
                - "hello world", 'o' -> 2
                - "Programming", 'g' -> 2
                - "Test", 't' -> 1
                - "", 'x' -> 0
                """;
        String starter = difficultyLevel >= 3
                ? """
                import org.junit.Assert;
                import java.util.ArrayDeque;
                import java.util.Deque;

                public class Main {
                    public static boolean hasBalancedBrackets(String text) {
                        // TODO: implement
                        return false;
                    }

                    public static void main(String[] args) {
                        Assert.assertTrue(hasBalancedBrackets("{[()]}"));
                        Assert.assertFalse(hasBalancedBrackets("{[(])}"));
                        Assert.assertTrue(hasBalancedBrackets("abc(def)[x]"));
                        Assert.assertFalse(hasBalancedBrackets("((("));
                        System.out.println("All assertions passed");
                    }
                }
                """
                : """
                import org.junit.Assert;

                public class Main {
                    public static int countCharOccurrences(String text, char targetChar) {
                        // TODO: implement
                        return 0;
                    }

                    public static void main(String[] args) {
                        Assert.assertEquals(2, countCharOccurrences("hello world", 'o'));
                        Assert.assertEquals(2, countCharOccurrences("Programming", 'g'));
                        Assert.assertEquals(1, countCharOccurrences("Test", 't'));
                        Assert.assertEquals(0, countCharOccurrences("", 'x'));
                        System.out.println("All assertions passed");
                    }
                }
                """;
        String reference = difficultyLevel >= 3
                ? """
                import org.junit.Assert;
                import java.util.ArrayDeque;
                import java.util.Deque;

                public class Main {
                    public static boolean hasBalancedBrackets(String text) {
                        Deque<Character> stack = new ArrayDeque<>();
                        for (int i = 0; i < text.length(); i++) {
                            char ch = text.charAt(i);
                            if (ch == '(' || ch == '{' || ch == '[') {
                                stack.push(ch);
                            } else if (ch == ')' || ch == '}' || ch == ']') {
                                if (stack.isEmpty()) {
                                    return false;
                                }
                                char open = stack.pop();
                                if ((ch == ')' && open != '(') || (ch == '}' && open != '{') || (ch == ']' && open != '[')) {
                                    return false;
                                }
                            }
                        }
                        return stack.isEmpty();
                    }

                    public static void main(String[] args) {
                        Assert.assertTrue(hasBalancedBrackets("{[()]}"));
                        Assert.assertFalse(hasBalancedBrackets("{[(])}"));
                        Assert.assertTrue(hasBalancedBrackets("abc(def)[x]"));
                        Assert.assertFalse(hasBalancedBrackets("((("));
                        System.out.println("All assertions passed");
                    }
                }
                """
                : """
                import org.junit.Assert;

                public class Main {
                    public static int countCharOccurrences(String text, char targetChar) {
                        int count = 0;
                        for (int i = 0; i < text.length(); i++) {
                            if (text.charAt(i) == targetChar) {
                                count++;
                            }
                        }
                        return count;
                    }

                    public static void main(String[] args) {
                        Assert.assertEquals(2, countCharOccurrences("hello world", 'o'));
                        Assert.assertEquals(2, countCharOccurrences("Programming", 'g'));
                        Assert.assertEquals(1, countCharOccurrences("Test", 't'));
                        Assert.assertEquals(0, countCharOccurrences("", 'x'));
                        System.out.println("All assertions passed");
                    }
                }
                """;
        return fallbackQuestion(request, methodName, "Question" + request.getQuestionNumber() + ".java", problem, starter, reference,
                difficultyLevel >= 3 ? "O(n)" : "O(n)", difficultyLevel >= 3 ? "O(n)" : "O(1)",
                List.of(difficultyLevel >= 3 ? "stack" : "string iteration", "conditionals"),
                List.of("Correctness", "Edge cases", "Readable implementation"));
    }

    private AiQuestionResponse fallbackPythonQuestion(AiQuestionGenerationRequest request, int difficultyLevel) {
        String problem = """
                Implement `count_char_occurrences(text, target_char)` and return how many times `target_char` appears in `text`. The comparison is case-sensitive.
                """;
        String starter = """
                def count_char_occurrences(text, target_char):
                    # TODO: implement
                    return 0


                def main():
                    assert count_char_occurrences("hello world", "o") == 2
                    assert count_char_occurrences("Programming", "g") == 2
                    assert count_char_occurrences("Test", "t") == 1
                    assert count_char_occurrences("", "x") == 0
                    print("All assertions passed")


                if __name__ == "__main__":
                    main()
                """;
        String reference = """
                def count_char_occurrences(text, target_char):
                    return sum(1 for ch in text if ch == target_char)


                def main():
                    assert count_char_occurrences("hello world", "o") == 2
                    assert count_char_occurrences("Programming", "g") == 2
                    assert count_char_occurrences("Test", "t") == 1
                    assert count_char_occurrences("", "x") == 0
                    print("All assertions passed")


                if __name__ == "__main__":
                    main()
                """;
        return fallbackQuestion(request, "Count Character Occurrences", "question-" + request.getQuestionNumber() + ".py",
                problem, starter, reference, "O(n)", "O(1)", List.of("string iteration"), List.of("Correctness", "Edge cases"));
    }

    private AiQuestionResponse fallbackAngularQuestion(AiQuestionGenerationRequest request, int difficultyLevel) {
        String problem = """
                Update the Angular component so it displays a list of tasks and shows a computed count of completed tasks. Keep the implementation inside the existing component files and do not add dependencies.
                """;
        String starter = """
                import { Component } from '@angular/core';
                import { CommonModule } from '@angular/common';

                @Component({
                  selector: 'app-root',
                  standalone: true,
                  imports: [CommonModule],
                  template: `
                    <!-- AI Generated Problem Statement:
                    Render the tasks and show how many are completed.
                    -->
                    <main class="app-shell">
                      <h1>Task Summary</h1>
                      <!-- TODO: implement -->
                    </main>
                  `,
                  styles: [`
                    .app-shell { display: grid; gap: 12px; padding: 24px; font-family: Arial, sans-serif; }
                  `]
                })
                export class AppComponent {
                  tasks = [
                    { title: 'Design API', done: true },
                    { title: 'Write tests', done: false },
                    { title: 'Review code', done: true }
                  ];
                }
                """;
        return fallbackQuestion(request, "Task Summary Component", "src/app/app.component.ts", problem, starter, null,
                "O(n)", "O(1)", List.of("Angular templates", "computed state"), List.of("Template binding", "Simple component logic"));
    }

    private AiQuestionResponse fallbackReactQuestion(AiQuestionGenerationRequest request, int difficultyLevel) {
        String problem = """
                Update the React component so it displays a list of tasks and shows a computed count of completed tasks. Keep the implementation inside the existing React source files and do not add dependencies.
                """;
        String starter = """
                import React from 'react';
                import './App.css';

                const tasks = [
                  { title: 'Design API', done: true },
                  { title: 'Write tests', done: false },
                  { title: 'Review code', done: true },
                ];

                export default function App() {
                  return (
                    <main className="app-shell">
                      {/* AI Generated Problem Statement:
                        Render the tasks and show how many are completed.
                      */}
                      <h1>Task Summary</h1>
                    </main>
                  );
                }
                """;
        return fallbackQuestion(request, "Task Summary Component", "src/App.tsx", problem, starter, null,
                "O(n)", "O(1)", List.of("React rendering", "computed state"), List.of("JSX structure", "Simple component logic"));
    }

    private AiQuestionResponse fallbackQuestion(AiQuestionGenerationRequest request,
                                                String title,
                                                String filePath,
                                                String problem,
                                                String starter,
                                                String referenceSolution,
                                                String expectedTimeComplexity,
                                                String expectedSpaceComplexity,
                                                List<String> concepts,
                                                List<String> evaluationFocus) {
        int difficultyLevel = normalizeDifficultyLevel(null, request.getCurrentDifficulty());
        return AiQuestionResponse.builder()
                .title(title)
                .filePath(filePath)
                .displayName("Question " + request.getQuestionNumber())
                .problemStatement(problem)
                .starterCode(starter)
                .difficulty(String.valueOf(difficultyLevel))
                .difficultyLevel(difficultyLevel)
                .idealDurationMinutes(normalizeIdealDuration(null, difficultyLevel))
                .referenceSolution(referenceSolution)
                .expectedTimeComplexity(expectedTimeComplexity)
                .expectedSpaceComplexity(expectedSpaceComplexity)
                .concepts(concepts)
                .evaluationFocus(evaluationFocus)
                .build();
    }

    private int resolveCurrentAiDifficultyLevel(InterviewSession session, long submittedCount) {
        int startingLevel = normalizeDifficultyLevel(session.getStartingDifficultyLevel());
        return Math.min(5, startingLevel + (int) submittedCount);
    }

    private String sandboxRulesFor(TechnologySkill technology) {
        return switch (technology) {
            case JAVA -> "Java 17 only. Single source execution. Use org.junit.Assert assertions from main for validation. No file IO, network IO, databases, external processes, or external dependencies.";
            case PYTHON -> "Python standard library only. Include runnable assert statements for validation. No file IO, network IO, databases, external processes, or external packages.";
            case ANGULAR -> "Angular source edits only under src/app. No new dependencies, file IO, network IO, or browser APIs that require unavailable services.";
            case REACT -> "React source edits only under src. Only .tsx, .ts, and .css are editable. No new dependencies, file IO, network IO, or unavailable browser services.";
            default -> "Generate only tasks that can execute in the configured sandbox without external systems.";
        };
    }

    private boolean isAiQuestionFile(TechnologySkill technology, String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String normalized = path.replace('\\', '/');
        return switch (technology) {
            case JAVA -> normalized.endsWith(".java");
            case PYTHON -> normalized.endsWith(".py");
            case ANGULAR -> normalized.equals("src/app/app.component.ts");
            case REACT -> normalized.equals("src/App.tsx");
            default -> false;
        };
    }

    private boolean isManagedAiQuestionFile(TechnologySkill technology, EditableCodeFileDto file) {
        if (file == null || !isAiQuestionFile(technology, file.getPath())) {
            return false;
        }
        return file.getAiEvaluation() != null || containsAiProblemComment(file.getContent());
    }

    private boolean containsAiProblemComment(String content) {
        return content != null && content.contains("AI Generated Problem Statement:");
    }

    private AiQuestionResponse toAiQuestionResponse(EditableCodeFileDto file) {
        return AiQuestionResponse.builder()
                .title(file.getDisplayName())
                .filePath(file.getPath())
                .displayName(file.getDisplayName())
                .problemStatement(file.getContent())
                .starterCode(file.getContent())
                .difficulty(String.valueOf(normalizeDifficultyLevel(file.getDifficultyLevel())))
                .difficultyLevel(normalizeDifficultyLevel(file.getDifficultyLevel()))
                .idealDurationMinutes(file.getIdealDurationMinutes())
                .expectedTimeComplexity(file.getExpectedTimeComplexity())
                .expectedSpaceComplexity(file.getExpectedSpaceComplexity())
                .concepts(List.of())
                .evaluationFocus(List.of())
                .build();
    }

    private EditableCodeFileDto buildAiQuestionFile(TechnologySkill technology,
                                                    AiQuestionResponse generated,
                                                    int questionNumber,
                                                    EditableCodeFileDto existingActiveQuestion,
                                                    int existingFileCount) {
        String fallbackPath = defaultAiQuestionPath(technology, questionNumber);
        String path = existingActiveQuestion != null && existingActiveQuestion.getPath() != null
                ? existingActiveQuestion.getPath()
                : normalizeAiFilePath(technology, generated.getFilePath(), fallbackPath);
        String displayName = "Question " + questionNumber;
        String content = starterWithProblemStatement(technology, generated.getProblemStatement(), generated.getStarterCode());
        int sortOrder = existingActiveQuestion != null && existingActiveQuestion.getSortOrder() != null
                ? existingActiveQuestion.getSortOrder()
                : existingFileCount;

        return EditableCodeFileDto.builder()
                .path(path)
                .displayName(displayName)
                .content(content)
                .editable(true)
                .sortOrder(sortOrder)
                .enabledForCandidate(true)
                .activeQuestion(true)
                .submitted(false)
                .difficultyLevel(normalizeDifficultyLevel(generated.getDifficultyLevel(), generated.getDifficulty()))
                .idealDurationMinutes(normalizeIdealDuration(generated.getIdealDurationMinutes(), normalizeDifficultyLevel(generated.getDifficultyLevel(), generated.getDifficulty())))
                .expectedTimeComplexity(normalizeShortText(generated.getExpectedTimeComplexity(), 80))
                .expectedSpaceComplexity(normalizeShortText(generated.getExpectedSpaceComplexity(), 80))
                .originalProblemStatement(generated.getProblemStatement())
                .originalStarterCode(content)
                .referenceSolution(generated.getReferenceSolution())
                .questionConcepts(joinList(generated.getConcepts()))
                .questionEvaluationFocus(joinList(generated.getEvaluationFocus()))
                .candidateStartedAt(null)
                .submittedAt(null)
                .solveDurationSeconds(null)
                .executeAttemptCount(0)
                .questionIntegrityNotes("Original AI problem statement and validation tests captured.")
                .build();
    }

    private List<EditableCodeFileDto> upsertAiQuestionFile(List<EditableCodeFileDto> files, EditableCodeFileDto generatedFile) {
        List<EditableCodeFileDto> nextFiles = new ArrayList<>();
        boolean replaced = false;
        for (EditableCodeFileDto file : files) {
            boolean isTarget = generatedFile.getPath().equals(file.getPath());
            nextFiles.add(EditableCodeFileDto.builder()
                    .path(isTarget ? generatedFile.getPath() : file.getPath())
                    .displayName(isTarget ? generatedFile.getDisplayName() : file.getDisplayName())
                    .content(isTarget ? generatedFile.getContent() : file.getContent())
                    .editable(isTarget ? generatedFile.getEditable() : file.getEditable())
                    .sortOrder(isTarget ? generatedFile.getSortOrder() : file.getSortOrder())
                    .enabledForCandidate(isTarget ? true : file.getEnabledForCandidate())
                    .activeQuestion(isTarget)
                    .submitted(isTarget ? false : file.getSubmitted())
                    .difficultyLevel(isTarget ? generatedFile.getDifficultyLevel() : file.getDifficultyLevel())
                    .idealDurationMinutes(isTarget ? generatedFile.getIdealDurationMinutes() : file.getIdealDurationMinutes())
                    .expectedTimeComplexity(isTarget ? generatedFile.getExpectedTimeComplexity() : file.getExpectedTimeComplexity())
                    .expectedSpaceComplexity(isTarget ? generatedFile.getExpectedSpaceComplexity() : file.getExpectedSpaceComplexity())
                    .questionIntegrityNotes(isTarget ? generatedFile.getQuestionIntegrityNotes() : file.getQuestionIntegrityNotes())
                    .originalProblemStatement(isTarget ? generatedFile.getOriginalProblemStatement() : file.getOriginalProblemStatement())
                    .originalStarterCode(isTarget ? generatedFile.getOriginalStarterCode() : file.getOriginalStarterCode())
                    .referenceSolution(isTarget ? generatedFile.getReferenceSolution() : file.getReferenceSolution())
                    .questionConcepts(isTarget ? generatedFile.getQuestionConcepts() : file.getQuestionConcepts())
                    .questionEvaluationFocus(isTarget ? generatedFile.getQuestionEvaluationFocus() : file.getQuestionEvaluationFocus())
                    .candidateStartedAt(isTarget ? null : file.getCandidateStartedAt())
                    .submittedAt(isTarget ? null : file.getSubmittedAt())
                    .solveDurationSeconds(isTarget ? null : file.getSolveDurationSeconds())
                    .executeAttemptCount(isTarget ? 0 : file.getExecuteAttemptCount())
                    .runResult(isTarget ? null : file.getRunResult())
                    .aiEvaluation(isTarget ? generatedFile.getAiEvaluation() : file.getAiEvaluation())
                    .changedAfterLastRun(isTarget ? false : file.getChangedAfterLastRun())
                    .build());
            replaced = replaced || isTarget;
        }
        if (!replaced) {
            nextFiles.add(generatedFile);
        }
        return normalizeEditableFileList(nextFiles);
    }

    private String normalizeAiFilePath(TechnologySkill technology, String requestedPath, String fallbackPath) {
        String path = requestedPath == null || requestedPath.isBlank()
                ? fallbackPath
                : requestedPath.replace('\\', '/').trim();
        return switch (technology) {
            case JAVA, PYTHON -> fallbackPath;
            case ANGULAR -> path.startsWith("src/app/") && (path.endsWith(".ts") || path.endsWith(".html") || path.endsWith(".css"))
                    ? path
                    : fallbackPath;
            case REACT -> path.startsWith("src/") && (path.endsWith(".tsx") || path.endsWith(".ts") || path.endsWith(".css"))
                    ? path
                    : fallbackPath;
            default -> fallbackPath;
        };
    }

    private String defaultAiQuestionPath(TechnologySkill technology, int questionNumber) {
        return switch (technology) {
            case PYTHON -> "question-" + questionNumber + ".py";
            case ANGULAR -> "src/app/app.component.ts";
            case REACT -> "src/App.tsx";
            default -> "Question" + questionNumber + ".java";
        };
    }

    private int normalizeIdealDuration(Integer idealDurationMinutes, int difficultyLevel) {
        if (idealDurationMinutes != null && idealDurationMinutes > 0) {
            return Math.max(5, Math.min(20, idealDurationMinutes));
        }
        if (difficultyLevel >= 4) {
            return 15;
        }
        if (difficultyLevel >= 3) {
            return 12;
        }
        return 10;
    }

    private int normalizeDifficultyLevel(Integer difficultyLevel) {
        if (difficultyLevel == null) {
            return 1;
        }
        return Math.max(1, Math.min(5, difficultyLevel));
    }

    private int normalizeDifficultyLevel(Integer difficultyLevel, String legacyDifficulty) {
        if (difficultyLevel != null) {
            return normalizeDifficultyLevel(difficultyLevel);
        }
        if (legacyDifficulty == null || legacyDifficulty.isBlank()) {
            return 1;
        }
        String trimmed = legacyDifficulty.trim();
        try {
            return normalizeDifficultyLevel(Integer.parseInt(trimmed));
        } catch (NumberFormatException ignored) {
            // fall through to legacy labels
        }
        if ("HARD".equalsIgnoreCase(trimmed)) {
            return 4;
        }
        if ("MEDIUM".equalsIgnoreCase(trimmed)) {
            return 3;
        }
        return 1;
    }

    private String normalizeShortText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    private String resolveQuestionIntegrityNotes(EditableCodeFileDto question) {
        List<String> notes = new ArrayList<>();
        String content = question.getContent() == null ? "" : question.getContent();
        String originalProblem = question.getOriginalProblemStatement();
        String originalStarter = question.getOriginalStarterCode();

        if (originalProblem != null && !originalProblem.isBlank() && !normalizeForIntegrity(content).contains(normalizeForIntegrity(originalProblem))) {
            notes.add("Problem statement text appears changed or removed.");
        }

        if (originalStarter != null && !originalStarter.isBlank()) {
            List<String> originalAssertions = extractValidationLines(originalStarter);
            List<String> currentAssertions = extractValidationLines(content);
            List<String> normalizedCurrentAssertions = currentAssertions.stream()
                    .map(this::normalizeForIntegrity)
                    .toList();
            List<String> missingAssertions = originalAssertions.stream()
                    .filter(assertion -> !normalizedCurrentAssertions.contains(normalizeForIntegrity(assertion)))
                    .toList();
            if (!missingAssertions.isEmpty()) {
                notes.add("Validation assertions changed or removed.");
                notes.add("Expected active assertions: " + String.join(" | ", missingAssertions));
                notes.add("Current active assertions: " + (currentAssertions.isEmpty() ? "none" : String.join(" | ", currentAssertions)));
            }
        }

        if (notes.isEmpty()) {
            return "Healthy: TRUE";
        }
        return "Healthy: FALSE. " + String.join(" ", notes);
    }

    private String normalizeForIntegrity(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
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

    private String starterWithProblemStatement(TechnologySkill technology, String problemStatement, String starterCode) {
        String problem = problemStatement == null || problemStatement.isBlank()
                ? "Implement the requested solution."
                : problemStatement.trim();
        String starter = starterCode == null ? "" : starterCode.trim();
        String problemComment = switch (technology) {
            case PYTHON -> "\"\"\"\nAI Generated Problem Statement:\n" + problem + "\n\"\"\"\n\n";
            default -> "/*\nAI Generated Problem Statement:\n" + problem + "\n*/\n\n";
        };
        if (!starter.isBlank()) {
            return problemComment + starter;
        }
        return switch (technology) {
            case PYTHON, ANGULAR, REACT -> problemComment;
            default -> problemComment + "public class Main {\n    public static void main(String[] args) {\n        // TODO: implement\n    }\n}\n";
        };
    }

    private EditableCodeFileDto resolveAiEvaluationTarget(InterviewSession session, String requestedFilePath) {
        List<EditableCodeFileDto> files = resolveEditableFiles(session, codeStateRepository.findBySessionId(session.getId()).orElse(null)).stream()
                .filter(file -> isManagedAiQuestionFile(session.getTechnology(), file))
                .toList();
        if (files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No AI question files are available for evaluation.");
        }
        if (requestedFilePath != null && !requestedFilePath.isBlank()) {
            String normalizedPath = requestedFilePath.replace('\\', '/').trim();
            return files.stream()
                    .filter(file -> normalizedPath.equals(file.getPath()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Requested AI question file was not found."));
        }
        return files.stream()
                .filter(file -> Boolean.TRUE.equals(file.getSubmitted()))
                .reduce((first, second) -> second)
                .orElseGet(() -> files.stream()
                        .filter(file -> Boolean.TRUE.equals(file.getActiveQuestion()))
                        .findFirst()
                        .orElse(files.get(0)));
    }

    private AiSolutionEvaluationRequest toAiEvaluationRequest(InterviewSession session, EditableCodeFileDto question, int questionNumber) {
        RunResultDto result = question.getRunResult();
        AiPolicyEngineService.EvaluationPolicy evaluationPolicy = aiPolicyEngineService.evaluationPolicy(session, question);
        return AiSolutionEvaluationRequest.builder()
                .sessionId(session.getId())
                .technology(session.getTechnology().name())
                .targetRole(session.getTargetRole())
                .yearsOfExperience(session.getYearsOfExperience())
                .difficulty(String.valueOf(normalizeDifficultyLevel(question.getDifficultyLevel())))
                .questionNumber(questionNumber)
                .questionTitle(question.getDisplayName())
                .problemStatement(question.getContent())
                .code(question.getContent())
                .stdout(result == null ? null : result.getStdout())
                .stderr(result == null ? null : result.getStderr())
                .exitStatus(result == null ? null : result.getExitStatus())
                .executionTimeMs(result == null ? null : result.getExecutionTimeMs())
                .solveDurationSeconds(question.getSolveDurationSeconds())
                .executeAttemptCount(question.getExecuteAttemptCount())
                .originalProblemStatement(question.getOriginalProblemStatement())
                .originalStarterCode(question.getOriginalStarterCode())
                .referenceSolution(question.getReferenceSolution())
                .expectedTimeComplexity(question.getExpectedTimeComplexity())
                .expectedSpaceComplexity(question.getExpectedSpaceComplexity())
                .questionIntegrityNotes(resolveQuestionIntegrityNotes(question))
                .questionPolicy(evaluationPolicy.questionPolicy())
                .evaluationRubric(evaluationPolicy.evaluationRubric())
                .expectedConcepts(evaluationPolicy.expectedConcepts())
                .nonNegotiableSignals(evaluationPolicy.nonNegotiableSignals())
                .build();
    }

    private void applyAiEvaluation(CodeFile codeFile, AiSolutionEvaluationResponse response) {
        codeFile.setAiCorrectnessScore(response.getCorrectnessScore());
        codeFile.setAiCodeQualityScore(response.getCodeQualityScore());
        codeFile.setAiEdgeCaseScore(response.getEdgeCaseScore());
        codeFile.setAiEfficiencyScore(response.getEfficiencyScore());
        codeFile.setAiOverallScore(response.getOverallScore());
        codeFile.setAiVerdict(response.getVerdict());
        codeFile.setAiNextDifficulty(String.valueOf(normalizeDifficultyLevel(response.getNextDifficultyLevel(), response.getNextDifficulty())));
        codeFile.setAiEvaluationSummary(response.getSummary());
        codeFile.setAiComplexityAssessment(response.getComplexityAssessment());
        codeFile.setAiQuestionIntegrityNotes(firstNonBlank(response.getQuestionIntegrityNotes(), codeFile.getQuestionIntegrityNotes()));
        codeFile.setAiEvaluationStrengths(joinList(response.getStrengths()));
        codeFile.setAiEvaluationConcerns(joinList(response.getConcerns()));
        codeFile.setAiEvaluatedAt(nowUtc());
    }

    private void applyAiEvaluationSnapshot(CodeFile target, AiSolutionEvaluationResponse incoming, CodeFile existing) {
        if (incoming != null) {
            applyAiEvaluation(target, incoming);
            return;
        }
        if (existing == null) {
            return;
        }
        target.setAiCorrectnessScore(existing.getAiCorrectnessScore());
        target.setAiCodeQualityScore(existing.getAiCodeQualityScore());
        target.setAiEdgeCaseScore(existing.getAiEdgeCaseScore());
        target.setAiEfficiencyScore(existing.getAiEfficiencyScore());
        target.setAiOverallScore(existing.getAiOverallScore());
        target.setAiVerdict(existing.getAiVerdict());
        target.setAiNextDifficulty(existing.getAiNextDifficulty());
        target.setAiEvaluationSummary(existing.getAiEvaluationSummary());
        target.setAiComplexityAssessment(existing.getAiComplexityAssessment());
        target.setAiQuestionIntegrityNotes(existing.getAiQuestionIntegrityNotes());
        target.setAiEvaluationStrengths(existing.getAiEvaluationStrengths());
        target.setAiEvaluationConcerns(existing.getAiEvaluationConcerns());
        target.setAiEvaluatedAt(existing.getAiEvaluatedAt());
    }

    private void applyAiRecommendation(InterviewSession session, AiInterviewRecommendationResponse response) {
        session.setAiRecommendationRating(response.getRating());
        session.setAiRecommendationDecision(response.getRecommendationDecision());
        session.setAiRecommendationOverallScore(response.getOverallScore());
        session.setAiRecommendationSummary(response.getSummary());
        session.setAiRecommendationStrengths(joinList(response.getStrengths()));
        session.setAiRecommendationRisks(joinList(response.getRisks()));
        session.setAiRecommendationFollowUps(joinList(response.getSuggestedFollowUps()));
        session.setAiHumanReviewRequired(response.getHumanReviewRequired());
        session.setAiRecommendationGeneratedAt(nowUtc());
    }

    private String joinList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .reduce((left, right) -> left + "\n" + right)
                .orElse(null);
    }

    private List<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\R"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private AiSolutionEvaluationResponse toAiEvaluationDto(CodeFile file) {
        if (file.getAiEvaluatedAt() == null
                && file.getAiOverallScore() == null
                && (file.getAiEvaluationSummary() == null || file.getAiEvaluationSummary().isBlank())) {
            return null;
        }
        return AiSolutionEvaluationResponse.builder()
                .correctnessScore(file.getAiCorrectnessScore())
                .codeQualityScore(file.getAiCodeQualityScore())
                .edgeCaseScore(file.getAiEdgeCaseScore())
                .efficiencyScore(file.getAiEfficiencyScore())
                .overallScore(file.getAiOverallScore())
                .verdict(file.getAiVerdict())
                .nextDifficulty(file.getAiNextDifficulty())
                .summary(file.getAiEvaluationSummary())
                .complexityAssessment(file.getAiComplexityAssessment())
                .questionIntegrityNotes(file.getAiQuestionIntegrityNotes())
                .strengths(splitList(file.getAiEvaluationStrengths()))
                .concerns(splitList(file.getAiEvaluationConcerns()))
                .build();
    }

    private AiPersistedRecommendationDto toAiRecommendationDto(InterviewSession session) {
        if (session.getAiRecommendationGeneratedAt() == null
                && session.getAiRecommendationOverallScore() == null
                && (session.getAiRecommendationSummary() == null || session.getAiRecommendationSummary().isBlank())) {
            return null;
        }
        return AiPersistedRecommendationDto.builder()
                .rating(session.getAiRecommendationRating())
                .recommendationDecision(session.getAiRecommendationDecision())
                .overallScore(session.getAiRecommendationOverallScore())
                .summary(session.getAiRecommendationSummary())
                .strengths(splitList(session.getAiRecommendationStrengths()))
                .risks(splitList(session.getAiRecommendationRisks()))
                .suggestedFollowUps(splitList(session.getAiRecommendationFollowUps()))
                .humanReviewRequired(session.getAiHumanReviewRequired())
                .generatedAt(session.getAiRecommendationGeneratedAt())
                .build();
    }

    private int questionIndex(InterviewSession session, EditableCodeFileDto question) {
        List<EditableCodeFileDto> files = resolveEditableFiles(session, codeStateRepository.findBySessionId(session.getId()).orElse(null)).stream()
                .filter(file -> isManagedAiQuestionFile(session.getTechnology(), file))
                .toList();
        for (int index = 0; index < files.size(); index++) {
            if (files.get(index).getPath().equals(question.getPath())) {
                return index + 1;
            }
        }
        return 1;
    }

    private void validateInterviewerDetailsForMode(CreateSessionRequest request, InterviewMode interviewMode) {
        if (interviewMode == InterviewMode.AI_INTERVIEWER) {
            return;
        }
        if (request.getInterviewerName() == null || request.getInterviewerName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Interviewer name is required for human interviews.");
        }
        if (request.getInterviewerEmail() == null || request.getInterviewerEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Interviewer email is required for human interviews.");
        }
    }

    private Integer normalizeMaxQuestions(Integer maxQuestions) {
        if (maxQuestions == null) {
            return 5;
        }
        return Math.max(1, Math.min(5, maxQuestions));
    }

    @Transactional
    public SessionResponse updateIdentityCapture(String sessionId,
                                                 ParticipantRole role,
                                                 IdentityCaptureStatus status,
                                                 IdentityCaptureFailureReason failureReason,
                                                 MultipartFile image) {
        InterviewSession session = getRequiredSession(sessionId);
        Participant participant = participantRepository.findBySessionIdAndRole(sessionId, role)
                .orElseThrow(() -> new IllegalArgumentException("Participant not found"));

        if (role != ParticipantRole.INTERVIEWEE) {
            throw new IllegalArgumentException("Identity capture is only supported for the interviewee");
        }

        if (status == IdentityCaptureStatus.SUCCESS && (image == null || image.isEmpty())) {
            throw new IllegalArgumentException("Snapshot image is required for a successful capture");
        }

        try {
            if (status == IdentityCaptureStatus.SUCCESS) {
                Path tempFile = Files.createTempFile("identity-capture-", ".upload");
                try {
                    image.transferTo(tempFile);
                    identitySnapshotStorageService.deleteIfExists(participant.getIdentitySnapshotPath());
                    String storedPath = identitySnapshotStorageService.storeSnapshot(sessionId, role, image.getOriginalFilename(), tempFile);

                    participant.setIdentityCaptureStatus(IdentityCaptureStatus.SUCCESS);
                    participant.setIdentityCaptureFailureReason(null);
                    participant.setIdentitySnapshotPath(storedPath);
                    participant.setIdentitySnapshotMimeType(image.getContentType());
                    participant.setIdentitySnapshotCapturedAt(nowUtc());
                } finally {
                    Files.deleteIfExists(tempFile);
                }
            } else {
                identitySnapshotStorageService.deleteIfExists(participant.getIdentitySnapshotPath());
                participant.setIdentityCaptureStatus(status);
                participant.setIdentityCaptureFailureReason(resolveCaptureFailureReason(status, failureReason));
                participant.setIdentitySnapshotPath(null);
                participant.setIdentitySnapshotMimeType(null);
                participant.setIdentitySnapshotCapturedAt(null);
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store identity snapshot", ex);
        }

        participantRepository.save(participant);
        if (isIdentityCaptureComplete(status)) {
            participantAccessChallengeRepository.findBySessionIdAndParticipantRole(sessionId, role)
                    .ifPresent(challenge -> {
                        if (challenge.getStatus() == ParticipantAccessStatus.OTP_VERIFIED) {
                            challenge.setStatus(ParticipantAccessStatus.COMPLETED);
                            participantAccessChallengeRepository.save(challenge);
                        }
                    });
        }
        return reevaluatePreSessionState(sessionId);
    }

    @Transactional
    public SessionResponse reevaluatePreSessionState(String sessionId) {
        InterviewSession session = getRequiredSession(sessionId);
        if (session.getStatus() == SessionStatus.ACTIVE
                || session.getStatus() == SessionStatus.ENDED
                || session.getStatus() == SessionStatus.EXPIRED
                || session.getStatus() == SessionStatus.AUTH_FAILED) {
            return toSessionResponse(session, true);
        }

        Participant interviewer = participantRepository.findBySessionIdAndRole(sessionId, ParticipantRole.INTERVIEWER)
                .orElseThrow(() -> new IllegalArgumentException("Interviewer not found"));
        Participant interviewee = participantRepository.findBySessionIdAndRole(sessionId, ParticipantRole.INTERVIEWEE)
                .orElseThrow(() -> new IllegalArgumentException("Interviewee not found"));
        ParticipantAccessChallenge interviewerChallenge = participantAccessChallengeRepository
                .findBySessionIdAndParticipantRole(sessionId, ParticipantRole.INTERVIEWER)
                .orElse(null);
        ParticipantAccessChallenge intervieweeChallenge = participantAccessChallengeRepository
                .findBySessionIdAndParticipantRole(sessionId, ParticipantRole.INTERVIEWEE)
                .orElse(null);

        boolean interviewerReady = isAiInterview(session)
                || (interviewer.getDisclaimerAcceptedAt() != null && isOtpSatisfied(interviewerChallenge));
        boolean intervieweeReady = interviewee.getDisclaimerAcceptedAt() != null
                && isOtpSatisfied(intervieweeChallenge)
                && isIdentityCaptureComplete(interviewee.getIdentityCaptureStatus());

        if (interviewerReady && intervieweeReady) {
            session.setStatus(SessionStatus.READY_TO_START);
            if (session.getReadyToStartAt() == null) {
                session.setReadyToStartAt(nowUtc());
            }
        } else if (session.getAuthStartedAt() != null) {
            session.setStatus(SessionStatus.AUTH_IN_PROGRESS);
            session.setReadyToStartAt(null);
        } else {
            session.setStatus(SessionStatus.REGISTERED);
            session.setReadyToStartAt(null);
        }

        sessionRepository.save(session);
        return toSessionResponse(session, true);
    }

    @Transactional(readOnly = true)
    public ResourceWithMetadata getIdentityCaptureResource(String sessionId, ParticipantRole role) {
        Participant participant = participantRepository.findBySessionIdAndRole(sessionId, role)
                .orElseThrow(() -> new IllegalArgumentException("Participant not found"));

        var resource = identitySnapshotStorageService.loadAsResource(participant.getIdentitySnapshotPath());
        if (resource == null || !resource.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Identity snapshot not found");
        }

        return new ResourceWithMetadata(resource, participant.getIdentitySnapshotMimeType());
    }

    @Transactional(readOnly = true)
    public ResourceWithMetadata getFinalPreviewResource(String sessionId, String assetPath) {
        InterviewSession session = getRequiredSession(sessionId);
        log.debug("Loading final preview resource for session {} finalPreviewPath={} assetPath={}",
                sessionId,
                session.getFinalPreviewPath(),
                assetPath);
        var resource = finalPreviewStorageService.loadPreviewResource(
                session.getFinalPreviewPath(),
                assetPath == null ? "" : assetPath.replaceFirst("^/", "")
        );
        if (resource == null || !resource.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Final preview not found");
        }

        try {
            return new ResourceWithMetadata(resource, finalPreviewStorageService.detectContentType(resource));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Final preview could not be loaded", exception);
        }
    }

    private void expireSessionIfNeeded(String sessionId) {
        InterviewSession session = getRequiredSession(sessionId);
        if (session.getStatus() == SessionStatus.REGISTERED || session.getStatus() == SessionStatus.AUTH_IN_PROGRESS) {
            session.setStatus(SessionStatus.EXPIRED);
            if (session.getEndedAt() == null) {
                session.setEndedAt(nowUtc());
            }
            if (session.getExpiredReason() == null || session.getExpiredReason().isBlank()) {
                session.setExpiredReason("Interview session was not started within the allowed pre-session window.");
            }
            sessionRepository.save(session);
        }
    }

    private InterviewSession getRequiredSession(String sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
    }

    private void upsertCodeState(String sessionId, String latestCode, ParticipantRole updatedByRole) {
        CodeUpdateRequest request = new CodeUpdateRequest();
        request.setCode(latestCode);
        request.setUpdatedByRole(updatedByRole);
        upsertCodeState(sessionId, request);
    }

    private void upsertCodeState(String sessionId, CodeUpdateRequest request) {
        CodeState codeState = codeStateRepository.findBySessionId(sessionId).orElseGet(CodeState::new);
        CodeStorageMode storageMode = resolveStorageMode(codeState, request);
        List<EditableCodeFileDto> editableFiles = resolveEditableFilesForUpdate(sessionId, request, storageMode, codeState);
        Long storedVersion = codeState.getVersion() == null ? 0L : codeState.getVersion();
        Long requestedVersion = request.getVersion();
        String nextLatestCode = resolvePrimaryCode(editableFiles, request.getCode());

        if (requestedVersion != null && requestedVersion < storedVersion) {
            log.debug("Ignoring stale code update for session {} from {}. requestedVersion={} storedVersion={}",
                    sessionId, request.getUpdatedByRole(), requestedVersion, storedVersion);
            return;
        }
        if (requestedVersion != null && requestedVersion.equals(storedVersion)) {
            boolean sameCodeState = hasSameCodeState(sessionId, codeState, nextLatestCode, editableFiles);
            if (sameCodeState || request.getUpdatedByRole() != ParticipantRole.INTERVIEWER) {
                log.debug("Ignoring same-version code update for session {} from {}. requestedVersion={} storedVersion={} sameCodeState={}",
                        sessionId, request.getUpdatedByRole(), requestedVersion, storedVersion, sameCodeState);
                return;
            }
        }

        codeState.setSessionId(sessionId);
        codeState.setLatestCode(nextLatestCode);
        codeState.setStorageMode(storageMode);
        codeState.setUpdatedAt(nowUtc());
        codeState.setUpdatedByRole(request.getUpdatedByRole().name());
        codeState.setVersion(resolveAcceptedCodeVersion(storedVersion, requestedVersion));
        codeStateRepository.save(codeState);
        replaceCodeFiles(sessionId, editableFiles);
    }

    private Long resolveAcceptedCodeVersion(Long storedVersion, Long requestedVersion) {
        if (requestedVersion == null) {
            return storedVersion + 1;
        }
        return requestedVersion > storedVersion ? requestedVersion : storedVersion + 1;
    }

    private boolean hasSameCodeState(String sessionId,
                                     CodeState codeState,
                                     String nextLatestCode,
                                     List<EditableCodeFileDto> nextFiles) {
        if (!Objects.equals(codeState.getLatestCode(), nextLatestCode)) {
            return false;
        }
        List<EditableCodeFileDto> existingFiles = codeFileRepository.findBySessionIdOrderBySortOrderAscCreatedAtAsc(sessionId).stream()
                .map(this::toEditableCodeFileDto)
                .toList();
        if (existingFiles.size() != nextFiles.size()) {
            return false;
        }
        for (int index = 0; index < existingFiles.size(); index++) {
            EditableCodeFileDto existing = existingFiles.get(index);
            EditableCodeFileDto next = nextFiles.get(index);
            if (!Objects.equals(existing.getPath(), next.getPath())
                    || !Objects.equals(existing.getContent(), next.getContent())
                    || !Objects.equals(existing.getEditable(), next.getEditable())
                    || !Objects.equals(existing.getSortOrder(), next.getSortOrder())
                    || !Objects.equals(existing.getEnabledForCandidate(), next.getEnabledForCandidate())
                    || !Objects.equals(existing.getActiveQuestion(), next.getActiveQuestion())
                    || !Objects.equals(existing.getSubmitted(), next.getSubmitted())
                    || !Objects.equals(existing.getIdealDurationMinutes(), next.getIdealDurationMinutes())
                    || !Objects.equals(existing.getCandidateStartedAt(), next.getCandidateStartedAt())
                    || !Objects.equals(existing.getSubmittedAt(), next.getSubmittedAt())
                    || !Objects.equals(existing.getSolveDurationSeconds(), next.getSolveDurationSeconds())
                    || !Objects.equals(existing.getExecuteAttemptCount(), next.getExecuteAttemptCount())) {
                return false;
            }
        }
        return true;
    }

    private SessionResponse toSessionResponse(InterviewSession session, boolean includeDetails) {
        List<Participant> participantEntities = participantRepository.findBySessionId(session.getId());
        List<ParticipantDto> participants = participantEntities.stream()
                .map(this::toParticipantDto)
                .toList();

        CodeState codeState = codeStateRepository.findBySessionId(session.getId()).orElse(null);
        List<EditableCodeFileDto> editableFiles = includeDetails ? resolveEditableFiles(session, codeState) : List.of();
        RunResult runResult = runResultRepository.findTopBySessionIdAndFilePathIsNullOrderByCompiledAtDesc(session.getId()).orElse(null);
        Feedback feedback = feedbackRepository.findBySessionId(session.getId()).orElse(null);
        FrontendWorkspace frontendWorkspace = frontendWorkspaceRepository.findById(session.getId()).orElse(null);
        List<ActivityEventDto> activityEvents = includeDetails
                ? sessionActivityEventRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                        .map(this::toActivityEventDto)
                        .toList()
                : List.of();
        List<AuthAuditEventDto> authAuditEvents = includeDetails
                ? buildAuthAuditEvents(session, participantEntities)
                : List.of();

        return SessionResponse.builder()
                .id(session.getId())
                .technology(session.getTechnology())
                .interviewMode(session.getInterviewMode() == null ? InterviewMode.HUMAN_INTERVIEWER : session.getInterviewMode())
                .yearsOfExperience(session.getYearsOfExperience())
                .targetRole(session.getTargetRole())
                .startingDifficultyLevel(normalizeDifficultyLevel(session.getStartingDifficultyLevel()))
                .maxQuestions(session.getMaxQuestions() == null ? 5 : session.getMaxQuestions())
                .avMode(session.getAvMode() == null ? AvMode.EXTERNAL : session.getAvMode())
                .status(session.getStatus())
                .createdAt(session.getCreatedAt())
                .authStartedAt(session.getAuthStartedAt())
                .readyToStartAt(session.getReadyToStartAt())
                .authFailedAt(session.getAuthFailedAt())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .interruptedAt(session.getInterruptedAt())
                .recoveryDeadlineAt(session.getRecoveryDeadlineAt())
                .recoveryRequiredRole(session.getRecoveryRequiredRole())
                .durationSec(session.getDurationSec())
                .remainingSec(calculateRemainingSec(session))
                .extensionUsed(Boolean.TRUE.equals(session.getExtensionUsed()))
                .readOnly(session.getStatus() == SessionStatus.ENDED
                        || session.getStatus() == SessionStatus.EXPIRED
                        || session.getStatus() == SessionStatus.AUTH_FAILED)
                .participants(participants)
                .latestCode(includeDetails && codeState != null ? codeState.getLatestCode() : null)
                .codeFiles(editableFiles)
                .codeVersion(codeState != null ? codeState.getVersion() : 0L)
                .finalRunResult(runResult == null ? null : toRunResultDto(runResult))
                .feedback(feedback == null ? null : FeedbackDto.builder()
                        .rating(feedback.getRating())
                        .comments(feedback.getComments())
                        .recommendationDecision(resolveRecommendationDecision(feedback))
                        .submittedAt(feedback.getSubmittedAt())
                        .build())
                .feedbackDraft(session.getFeedbackDraftRating() == null ? null : FeedbackDto.builder()
                        .rating(session.getFeedbackDraftRating())
                        .comments(session.getFeedbackDraftComments())
                        .recommendationDecision(session.getFeedbackDraftRecommendationDecision())
                        .submittedAt(null)
                        .build())
                .aiRecommendation(toAiRecommendationDto(session))
                .activityEvents(activityEvents)
                .authAuditEvents(authAuditEvents)
                .summary(buildSummary(session, feedback))
                .suspiciousRejected(Boolean.TRUE.equals(session.getSuspiciousRejected()))
                .suspiciousScenarioKey(session.getSuspiciousScenarioKey())
                .suspiciousActivityReason(session.getSuspiciousActivityReason())
                .authFailureReason(session.getAuthFailureReason())
                .expiredReason(session.getExpiredReason())
                .frontendWorkspace(frontendWorkspace == null ? null : toFrontendWorkspaceDto(frontendWorkspace))
                .finalPreviewUrl(resolveFinalPreviewUrl(session))
                .build();
    }

    private List<AuthAuditEventDto> buildAuthAuditEvents(InterviewSession session, List<Participant> participants) {
        Map<ParticipantRole, Participant> participantByRole = new HashMap<>();
        for (Participant participant : participants) {
            participantByRole.put(participant.getRole(), participant);
        }

        List<AuthAuditEventDto> events = new ArrayList<>();
        if (session.getCreatedAt() != null) {
            events.add(AuthAuditEventDto.builder()
                    .createdAt(session.getCreatedAt())
                    .participantRole(null)
                    .title("Registration Created")
                    .detail("Interview registration record was created.")
                    .build());
        }
        if (session.getAuthStartedAt() != null) {
            events.add(AuthAuditEventDto.builder()
                    .createdAt(session.getAuthStartedAt())
                    .participantRole(ParticipantRole.INTERVIEWER)
                    .title("Secure Session Started")
                    .detail("Interviewer initiated secure participant verification and passcode delivery.")
                    .build());
        }

        for (Participant participant : participants) {
            if (participant.getDisclaimerAcceptedAt() != null) {
                events.add(AuthAuditEventDto.builder()
                        .createdAt(participant.getDisclaimerAcceptedAt())
                        .participantRole(participant.getRole())
                        .title("Disclaimer Accepted")
                        .detail(participant.getName() + " accepted the pre-session disclaimer.")
                        .build());
            }
            if (participant.getRole() == ParticipantRole.INTERVIEWEE && participant.getIdentitySnapshotCapturedAt() != null) {
                events.add(AuthAuditEventDto.builder()
                        .createdAt(participant.getIdentitySnapshotCapturedAt())
                        .participantRole(participant.getRole())
                        .title("Identity Capture Completed")
                        .detail("Interviewee identity capture completed successfully.")
                        .build());
            }
        }

        for (ParticipantAccessChallenge challenge : participantAccessChallengeRepository.findBySessionId(session.getId())) {
            Participant participant = participantByRole.get(challenge.getParticipantRole());
            String participantName = participant == null ? challenge.getParticipantRole().name() : participant.getName();
            if (challenge.getCreatedAt() != null) {
                events.add(AuthAuditEventDto.builder()
                        .createdAt(challenge.getCreatedAt())
                        .participantRole(challenge.getParticipantRole())
                        .title("Secure Link Prepared")
                        .detail("Secure access link was prepared for " + participantName + ".")
                        .build());
            }
            if (challenge.getLastEmailSentAt() != null) {
                events.add(AuthAuditEventDto.builder()
                        .createdAt(challenge.getLastEmailSentAt())
                        .participantRole(challenge.getParticipantRole())
                        .title("Passcode Sent")
                        .detail("A one-time passcode email was issued to " + participantName + ". Window count: " + (challenge.getOtpWindowCount() == null ? 0 : challenge.getOtpWindowCount()) + ".")
                        .build());
            }
            if (challenge.getOtpVerifiedAt() != null) {
                events.add(AuthAuditEventDto.builder()
                        .createdAt(challenge.getOtpVerifiedAt())
                        .participantRole(challenge.getParticipantRole())
                        .title("Passcode Verified")
                        .detail(participantName + " verified the one-time passcode successfully.")
                        .build());
            }
            if (challenge.getStatus() == ParticipantAccessStatus.FAILED && challenge.getUpdatedAt() != null) {
                events.add(AuthAuditEventDto.builder()
                        .createdAt(challenge.getUpdatedAt())
                        .participantRole(challenge.getParticipantRole())
                        .title("Participant Authentication Failed")
                        .detail(challenge.getFailureReason() == null || challenge.getFailureReason().isBlank()
                                ? participantName + " could not complete secure authentication."
                                : challenge.getFailureReason())
                        .build());
            }
        }

        if (session.getReadyToStartAt() != null) {
            events.add(AuthAuditEventDto.builder()
                    .createdAt(session.getReadyToStartAt())
                    .participantRole(null)
                    .title("Session Ready to Start")
                    .detail("All required pre-session checks were completed.")
                    .build());
        }
        if (session.getStartedAt() != null) {
            events.add(AuthAuditEventDto.builder()
                    .createdAt(session.getStartedAt())
                    .participantRole(ParticipantRole.INTERVIEWER)
                    .title("Interview Started")
                    .detail("Interviewer started the live interview session.")
                    .build());
        }
        if (session.getAuthFailedAt() != null) {
            events.add(AuthAuditEventDto.builder()
                    .createdAt(session.getAuthFailedAt())
                    .participantRole(null)
                    .title("Authentication Failed")
                    .detail(session.getAuthFailureReason() == null || session.getAuthFailureReason().isBlank()
                            ? "Secure participant authentication failed."
                            : session.getAuthFailureReason())
                    .build());
        }
        if (session.getStatus() == SessionStatus.EXPIRED && session.getEndedAt() != null) {
            events.add(AuthAuditEventDto.builder()
                    .createdAt(session.getEndedAt())
                    .participantRole(null)
                    .title("Session Expired")
                    .detail(session.getExpiredReason() == null || session.getExpiredReason().isBlank()
                            ? "Session expired before the interview started."
                            : session.getExpiredReason())
                    .build());
        }
        if (session.getEndedAt() != null && session.getStatus() == SessionStatus.ENDED) {
            events.add(AuthAuditEventDto.builder()
                    .createdAt(session.getEndedAt())
                    .participantRole(null)
                    .title("Interview Ended")
                    .detail("Interview session ended.")
                    .build());
        }

        return events.stream()
                .sorted(Comparator.comparing(AuthAuditEventDto::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private int calculateRemainingSec(InterviewSession session) {
        if (session.getStatus() != SessionStatus.ACTIVE || session.getStartedAt() == null) {
            return session.getDurationSec() == null ? DEFAULT_DURATION_SEC : session.getDurationSec();
        }
        long elapsed = Duration.between(session.getStartedAt(), nowUtc()).getSeconds();
        int duration = session.getDurationSec() == null ? DEFAULT_DURATION_SEC : session.getDurationSec();
        return Math.max(0, duration - (int) elapsed);
    }

    private ParticipantDto toParticipantDto(Participant participant) {
        if (participant == null) {
            return null;
        }
        return ParticipantDto.builder()
                .role(participant.getRole())
                .name(participant.getName())
                .email(participant.getEmail())
                .timeZone(participant.getTimeZone())
                .identityCaptureStatus(participant.getIdentityCaptureStatus())
                .identityCaptureFailureReason(participant.getIdentityCaptureFailureReason())
                .identitySnapshotPath(participant.getIdentitySnapshotPath())
                .identitySnapshotCapturedAt(participant.getIdentitySnapshotCapturedAt())
                .disclaimerAcceptedAt(participant.getDisclaimerAcceptedAt())
                .joinedAt(participant.getJoinedAt())
                .connectionStatus(participant.getConnectionStatus())
                .deviceId(participant.getDeviceId())
                .lastKnownIp(participant.getLastKnownIp())
                .lastSeenAt(participant.getLastSeenAt())
                .disconnectedAt(participant.getDisconnectedAt())
                .resumeRequestedAt(participant.getResumeRequestedAt())
                .resumeApprovedAt(participant.getResumeApprovedAt())
                .resumeRejectedAt(participant.getResumeRejectedAt())
                .resumeCount(participant.getResumeCount())
                .pendingResumeReason(participant.getPendingResumeReason())
                .awaitingResumeApproval(Boolean.TRUE.equals(participant.getAwaitingResumeApproval()))
                .build();
    }

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private boolean isOtpSatisfied(ParticipantAccessChallenge challenge) {
        if (challenge == null) {
            return false;
        }
        return challenge.getStatus() == ParticipantAccessStatus.OTP_VERIFIED
                || challenge.getStatus() == ParticipantAccessStatus.COMPLETED;
    }

    private String buildSummary(InterviewSession session, Feedback feedback) {
        if (Boolean.TRUE.equals(session.getSuspiciousRejected())) {
            return "Rejected due to suspicious activity";
        }
        if (Boolean.TRUE.equals(session.getIncomplete())) {
            return "INCOMPLETE";
        }
        if (session.getStatus() == SessionStatus.EXPIRED) {
            return session.getStartedAt() == null ? "Expired before interview start" : "Token expired";
        }
        if (feedback != null) {
            return feedback.getRating() + " / " + formatRecommendationDecision(resolveRecommendationDecision(feedback));
        }
        return switch (session.getStatus()) {
            case REGISTERED -> "Pending secure session start";
            case AUTH_IN_PROGRESS -> "Participant verification in progress";
            case READY_TO_START -> "Ready for interview start";
            case ACTIVE -> "Interview in progress";
            case ENDED -> "Completed";
            case AUTH_FAILED -> "Authentication failed";
            case EXPIRED -> "Expired before interview start";
        };
    }

    private ActivityEventSeverity resolveActivitySeverity(InterviewSession session, ActivityEventRequest request, int occurrenceCount) {
        long durationMs = normalizeDurationMs(request.getDurationMs());
        boolean inAppAv = session.getAvMode() == AvMode.IN_APP;

        return switch (request.getEventType()) {
            case TAB_HIDDEN -> resolveFocusAwaySeverity(inAppAv, durationMs, occurrenceCount);
            case COPY_FROM_EDITOR, PASTE_IN_EDITOR, EXTERNAL_DROP_BLOCKED -> occurrenceCount >= 2
                    ? ActivityEventSeverity.SUSPICIOUS
                    : ActivityEventSeverity.WARNING;
            case MICROPHONE_DISABLED_MANUALLY, CAMERA_DISABLED_MANUALLY -> {
                if (!inAppAv) {
                    yield ActivityEventSeverity.INFO;
                }
                yield durationMs >= IN_APP_AV_OFF_SUSPICIOUS_MS || occurrenceCount >= 2
                        ? ActivityEventSeverity.SUSPICIOUS
                        : ActivityEventSeverity.WARNING;
            }
            case CAMERA_STREAM_LOST, NO_FACE_DETECTED, MULTIPLE_FACES_DETECTED -> inAppAv
                    ? ActivityEventSeverity.SUSPICIOUS
                    : ActivityEventSeverity.INFO;
        };
    }

    private ActivityEventSeverity resolveFocusAwaySeverity(boolean inAppAv, long durationMs, int occurrenceCount) {
        if (inAppAv) {
            return durationMs >= IN_APP_TAB_AWAY_SUSPICIOUS_MS || occurrenceCount >= 2
                    ? ActivityEventSeverity.SUSPICIOUS
                    : ActivityEventSeverity.WARNING;
        }
        return durationMs >= EXTERNAL_TAB_AWAY_SUSPICIOUS_MS || occurrenceCount >= 3
                ? ActivityEventSeverity.SUSPICIOUS
                : ActivityEventSeverity.INFO;
    }

    private String buildCandidateActivityMessage(ActivityEventRequest request, ActivityEventSeverity severity) {
        boolean suspicious = severity == ActivityEventSeverity.SUSPICIOUS;
        return switch (request.getEventType()) {
            case TAB_HIDDEN -> suspicious
                    ? "Changing tabs during the live interview is marked as suspicious."
                    : "Please stay on the interview tab. Repeated or long focus changes may be marked as suspicious.";
            case COPY_FROM_EDITOR -> suspicious
                    ? "Copying content from the editor is not allowed in AI interview mode and has been recorded."
                    : "Copying content from the editor is not allowed in AI interview mode. Repeating this action will be marked as suspicious.";
            case MICROPHONE_DISABLED_MANUALLY, CAMERA_DISABLED_MANUALLY -> suspicious
                    ? "Switching off audio or video during the live interview is marked as suspicious."
                    : "Please keep your interview audio and video enabled. Repeated or extended interruptions may be marked as suspicious.";
            case PASTE_IN_EDITOR -> suspicious
                    ? "Pasting external content is not allowed and has been recorded."
                    : "Pasting external content is not allowed. Repeating this action will be marked as suspicious.";
            case EXTERNAL_DROP_BLOCKED -> suspicious
                    ? "Dragging external content into the editor is not allowed and has been recorded."
                    : "Dragging external content into the editor is not allowed. Repeating this action will be marked as suspicious.";
            case CAMERA_STREAM_LOST -> suspicious
                    ? "Camera interruption during the live interview is marked as suspicious."
                    : "Please keep your camera active throughout the interview.";
            case NO_FACE_DETECTED -> suspicious
                    ? "Face not visible during the live interview is marked as suspicious."
                    : "Please keep your face visible in the camera frame.";
            case MULTIPLE_FACES_DETECTED -> suspicious
                    ? "Multiple faces detected during the live interview is marked as suspicious."
                    : "Only the candidate should be visible in the camera frame.";
        };
    }

    private long normalizeDurationMs(Long durationMs) {
        return durationMs == null || durationMs < 0 ? 0L : durationMs;
    }

    private String buildActivityDetail(ActivityEventRequest request) {
        if (request.getDetail() != null && !request.getDetail().isBlank()) {
            return request.getDetail().trim();
        }

        return switch (request.getEventType()) {
            case TAB_HIDDEN -> "Interviewee switched away from the interview tab or window.";
            case COPY_FROM_EDITOR -> "Interviewee tried to copy content from the editor.";
            case EXTERNAL_DROP_BLOCKED -> "Interviewee tried to drag text into the editor.";
            case CAMERA_STREAM_LOST -> "Interviewee's camera stream was interrupted.";
            case MICROPHONE_DISABLED_MANUALLY -> "Interviewee manually turned off the microphone during the interview.";
            case CAMERA_DISABLED_MANUALLY -> "Interviewee manually turned off the camera during the interview.";
            case NO_FACE_DETECTED -> "Interviewee's face was not visible in the camera frame.";
            case MULTIPLE_FACES_DETECTED -> "Multiple faces were detected in the interviewee's camera frame.";
            case PASTE_IN_EDITOR -> "Interviewee pasted content into the editor.";
        };
    }

    private ActivityEventDto toActivityEventDto(SessionActivityEvent event) {
        return ActivityEventDto.builder()
                .id(event.getId())
                .participantRole(event.getParticipantRole())
                .eventType(event.getEventType())
                .severity(event.getSeverity() == null ? ActivityEventSeverity.WARNING : event.getSeverity())
                .detail(event.getDetail())
                .candidateMessage(event.getCandidateMessage())
                .durationMs(event.getDurationMs())
                .occurrenceCount(event.getOccurrenceCount())
                .createdAt(event.getCreatedAt())
                .build();
    }

    private String normalizeTimeZone(String timeZone) {
        return timeZone == null || timeZone.isBlank() ? null : timeZone.trim();
    }

    private void ensureFrontendWorkspaceIfNeeded(InterviewSession session) {
        if (!supportsPersistentFrontendWorkspace(session.getTechnology())) {
            return;
        }
        if (session.getStatus() != SessionStatus.READY_TO_START && session.getStatus() != SessionStatus.ACTIVE) {
            return;
        }

        List<EditableCodeFileDto> editableFiles = resolveEditableFiles(session, codeStateRepository.findBySessionId(session.getId()).orElse(null));
        try {
            FrontendWorkspaceResponse response = frontendSandboxClientService.getWorkspaceBySessionId(session.getId());
            if (response == null) {
                response = frontendSandboxClientService.createWorkspace(FrontendWorkspaceRequest.builder()
                        .sessionId(session.getId())
                        .language(toExecutionLanguage(session.getTechnology()))
                        .files(editableFiles)
                        .build());
            }
            upsertFrontendWorkspace(session, response);
        } catch (ResponseStatusException exception) {
            log.warn("Frontend workspace could not be created for session {} technology={}. Falling back to cold-build flow for now.",
                    session.getId(), session.getTechnology(), exception);
        }
    }

    private void cleanupFrontendWorkspaceIfNeeded(InterviewSession session) {
        if (!supportsPersistentFrontendWorkspace(session.getTechnology())) {
            return;
        }

        frontendWorkspaceRepository.findById(session.getId()).ifPresent(workspace -> {
            try {
                frontendSandboxClientService.deleteWorkspace(workspace.getWorkspaceId());
            } catch (ResponseStatusException exception) {
                log.warn("Frontend workspace {} could not be deleted cleanly for session {}", workspace.getWorkspaceId(), session.getId(), exception);
            }

            workspace.setStatus(FrontendWorkspaceStatus.STOPPED);
            workspace.setUpdatedAt(nowUtc());
            frontendWorkspaceRepository.save(workspace);
        });
    }

    private void captureFinalPreviewIfAvailable(InterviewSession session, ExecuteResponse executionResult) {
        if (!supportsFinalPreview(session.getTechnology())) {
            log.info("Skipping final preview capture for session {} because technology {} does not support it",
                    session.getId(), session.getTechnology());
            session.setFinalPreviewPath(null);
            return;
        }
        if (!hasDurableFrontendPreview(executionResult)) {
            log.info("Skipping final preview capture for session {} because the final build result is not durable: success={} exitCode={} previewUrl={} stderrLength={} compileErrorCount={}",
                    session.getId(),
                    executionResult != null && executionResult.isSuccess(),
                    executionResult == null ? null : executionResult.getExitCode(),
                    executionResult == null ? null : executionResult.getPreviewUrl(),
                    executionResult == null || executionResult.getStderr() == null ? 0 : executionResult.getStderr().length(),
                    executionResult == null || executionResult.getCompileErrors() == null ? 0 : executionResult.getCompileErrors().size());
            session.setFinalPreviewPath(null);
            return;
        }

        try {
            log.info("Capturing final {} preview for session {} from previewUrl={}",
                    session.getTechnology(),
                    session.getId(),
                    executionResult.getPreviewUrl());
            byte[] archive = frontendSandboxClientService.downloadPreviewArchive(executionResult.getPreviewUrl());
            if (archive == null || archive.length == 0) {
                log.warn("Final frontend preview archive was unavailable for session {}", session.getId());
                session.setFinalPreviewPath(null);
                return;
            }

            String storedPath = finalPreviewStorageService.storePreviewArchive(session.getId(), archive);
            session.setFinalPreviewPath(storedPath);
            log.info("Stored final {} preview snapshot for session {} at {}", session.getTechnology(), session.getId(), storedPath);
        } catch (IOException | ResponseStatusException exception) {
            log.warn("Final frontend preview could not be stored for session {}", session.getId(), exception);
            session.setFinalPreviewPath(null);
        }
    }

    private boolean hasDurableFrontendPreview(ExecuteResponse executionResult) {
        if (executionResult == null || !executionResult.isSuccess() || executionResult.getExitCode() != 0) {
            return false;
        }
        if (executionResult.getPreviewUrl() == null || executionResult.getPreviewUrl().isBlank()) {
            return false;
        }
        if (executionResult.getStderr() != null && !executionResult.getStderr().isBlank()) {
            return false;
        }
        return executionResult.getCompileErrors() == null || executionResult.getCompileErrors().isEmpty();
    }

    private String resolveFinalPreviewUrl(InterviewSession session) {
        if (session.getFinalPreviewPath() == null || session.getFinalPreviewPath().isBlank()) {
            return null;
        }
        if (!finalPreviewStorageService.hasPreview(session.getFinalPreviewPath())) {
            log.debug("Final preview URL unavailable for session {} because stored preview is missing at {}",
                    session.getId(),
                    session.getFinalPreviewPath());
            return null;
        }
        String finalPreviewUrl = "/api/sessions/" + session.getId() + "/final-preview/";
        log.debug("Resolved final preview URL for session {} finalPreviewPath={} finalPreviewUrl={}",
                session.getId(),
                session.getFinalPreviewPath(),
                finalPreviewUrl);
        return finalPreviewUrl;
    }

    private void upsertFrontendWorkspace(InterviewSession session, FrontendWorkspaceResponse response) {
        OffsetDateTime now = nowUtc();
        FrontendWorkspace workspace = frontendWorkspaceRepository.findById(session.getId()).orElseGet(FrontendWorkspace::new);
        applyFrontendWorkspaceState(workspace, session, response, now);
        try {
            frontendWorkspaceRepository.saveAndFlush(workspace);
        } catch (DataIntegrityViolationException exception) {
            // Another concurrent request created the row first; reload and converge on that row.
            log.debug("Retrying frontend workspace upsert after duplicate insert for session {}", session.getId(), exception);
            entityManager.clear();
            FrontendWorkspace existing = frontendWorkspaceRepository.findById(session.getId()).orElseGet(FrontendWorkspace::new);
            applyFrontendWorkspaceState(existing, session, response, now);
            frontendWorkspaceRepository.saveAndFlush(existing);
        }
    }

    private void applyFrontendWorkspaceState(FrontendWorkspace workspace,
                                             InterviewSession session,
                                             FrontendWorkspaceResponse response,
                                             OffsetDateTime now) {
        workspace.setSessionId(session.getId());
        workspace.setWorkspaceId(response.getWorkspaceId());
        workspace.setTechnology(session.getTechnology());
        workspace.setStatus(resolveFrontendWorkspaceStatus(response.getStatus()));
        workspace.setPreviewUrl(response.getPreviewPath());
        workspace.setSandboxInstance("sandbox-frontend");
        workspace.setCreatedAt(response.getCreatedAt() == null ? (workspace.getCreatedAt() == null ? now : workspace.getCreatedAt()) : response.getCreatedAt());
        workspace.setUpdatedAt(response.getUpdatedAt() == null ? now : response.getUpdatedAt());
        workspace.setLastHeartbeatAt(response.getLastHeartbeatAt());
    }

    private FrontendWorkspaceDto toFrontendWorkspaceDto(FrontendWorkspace workspace) {
        return FrontendWorkspaceDto.builder()
                .sessionId(workspace.getSessionId())
                .workspaceId(workspace.getWorkspaceId())
                .technology(workspace.getTechnology())
                .status(workspace.getStatus())
                .previewUrl(workspace.getPreviewUrl())
                .createdAt(workspace.getCreatedAt())
                .updatedAt(workspace.getUpdatedAt())
                .lastHeartbeatAt(workspace.getLastHeartbeatAt())
                .build();
    }

    private FrontendWorkspaceStatus resolveFrontendWorkspaceStatus(String status) {
        if (status == null || status.isBlank()) {
            return FrontendWorkspaceStatus.READY;
        }
        try {
            return FrontendWorkspaceStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return FrontendWorkspaceStatus.READY;
        }
    }

    private ExecuteRequest buildExecuteRequest(String sessionId,
                                              String sourceCode,
                                              List<EditableCodeFileDto> codeFiles,
                                              String activeFilePath,
                                              TechnologySkill technology) {
        ExecuteRequest request = new ExecuteRequest(sourceCode);
        request.setSessionId(sessionId);
        request.setLanguage(toExecutionLanguage(technology));
        request.setActiveFilePath(activeFilePath);
        if (supportsPersistentFrontendWorkspace(technology)) {
            request.setCodeFiles(codeFiles != null && !codeFiles.isEmpty()
                    ? codeFiles
                    : codeFileRepository.findBySessionIdOrderBySortOrderAscCreatedAtAsc(sessionId).stream()
                            .map(this::toEditableCodeFileDto)
                            .toList());
        }
        return request;
    }

    @Transactional
    public void recordQuestionRunResult(ExecuteRequest request, ExecuteResponse response) {
        if (request == null || response == null || request.getSessionId() == null || request.getSessionId().isBlank()) {
            return;
        }
        if (request.getLanguage() != ExecutionLanguage.JAVA && request.getLanguage() != ExecutionLanguage.PYTHON) {
            return;
        }

        String filePath = request.getActiveFilePath();
        if (filePath == null || filePath.isBlank()) {
            filePath = request.getLanguage() == ExecutionLanguage.PYTHON ? "question-1.py" : "Question1.java";
        }
        String normalizedPath = filePath.replace('\\', '/').trim();
        String displayName = codeFileRepository.findBySessionIdAndFilePath(request.getSessionId(), normalizedPath)
                .map(CodeFile::getDisplayName)
                .orElse(normalizedPath);

        RunResult runResult = new RunResult();
        runResult.setSessionId(request.getSessionId());
        runResult.setFilePath(normalizedPath);
        runResult.setDisplayName(displayName);
        runResult.setSourceSnapshot(request.getSourceCode() == null ? "" : request.getSourceCode());
        runResult.setStdout(response.getStdout());
        runResult.setStderr((response.getStderr() == null || response.getStderr().isBlank())
                ? String.join("\n", response.getCompileErrors() == null ? List.of() : response.getCompileErrors())
                : response.getStderr());
        runResult.setExitStatus(response.getExitCode());
        runResult.setExecutionTimeMs(response.getExecutionTimeMs());
        runResultRepository.save(runResult);
    }

    private ExecutionLanguage toExecutionLanguage(TechnologySkill technology) {
        return switch (technology) {
            case PYTHON -> ExecutionLanguage.PYTHON;
            case ANGULAR -> ExecutionLanguage.ANGULAR;
            case REACT -> ExecutionLanguage.REACT;
            default -> ExecutionLanguage.JAVA;
        };
    }

    private String defaultTemplateFor(TechnologySkill technology) {
        return switch (technology) {
            case PYTHON -> DEFAULT_PYTHON_TEMPLATE;
            case ANGULAR -> DEFAULT_ANGULAR_COMPONENT_TS;
            case REACT -> DEFAULT_REACT_APP_TSX;
            default -> DEFAULT_JAVA_TEMPLATE;
        };
    }

    private CodeStorageMode storageModeFor(TechnologySkill technology) {
        return switch (technology) {
            case JAVA, PYTHON, ANGULAR, REACT -> CodeStorageMode.MULTI_FILE;
            default -> CodeStorageMode.SINGLE_FILE;
        };
    }

    private List<EditableCodeFileDto> buildDefaultEditableFiles(TechnologySkill technology) {
        return switch (technology) {
            case PYTHON -> List.of(buildEditableFile("question-1.py", "Question 1", DEFAULT_PYTHON_TEMPLATE, 0));
            case ANGULAR -> List.of(
                    buildEditableFile("src/app/app.component.ts", "app.component.ts", DEFAULT_ANGULAR_COMPONENT_TS, 0),
                    buildEditableFile("src/app/app.component.html", "app.component.html", DEFAULT_ANGULAR_COMPONENT_HTML, 1),
                    buildEditableFile("src/app/app.component.css", "app.component.css", DEFAULT_ANGULAR_COMPONENT_CSS, 2)
            );
            case REACT -> List.of(
                    buildEditableFile("src/App.tsx", "App.tsx", DEFAULT_REACT_APP_TSX, 0),
                    buildEditableFile("src/App.css", "App.css", DEFAULT_REACT_APP_CSS, 1),
                    buildEditableFile("src/main.tsx", "main.tsx", DEFAULT_REACT_MAIN_TSX, 2)
            );
            default -> List.of(buildEditableFile("Question1.java", "Question 1", DEFAULT_JAVA_TEMPLATE, 0));
        };
    }

    private EditableCodeFileDto buildEditableFile(String path, String displayName, String content, int sortOrder) {
        return EditableCodeFileDto.builder()
                .path(path)
                .displayName(displayName)
                .content(content)
                .editable(true)
                .sortOrder(sortOrder)
                .enabledForCandidate(sortOrder == 0)
                .activeQuestion(sortOrder == 0)
                .submitted(false)
                .build();
    }

    private CodeStorageMode resolveStorageMode(CodeState codeState, CodeUpdateRequest request) {
        if (request.getCodeFiles() != null && !request.getCodeFiles().isEmpty()) {
            return CodeStorageMode.MULTI_FILE;
        }
        if (codeState.getStorageMode() != null) {
            return codeState.getStorageMode();
        }
        return CodeStorageMode.SINGLE_FILE;
    }

    private List<EditableCodeFileDto> resolveEditableFilesForUpdate(String sessionId,
                                                                    CodeUpdateRequest request,
                                                                    CodeStorageMode storageMode,
                                                                    CodeState codeState) {
        if (request.getCodeFiles() != null && !request.getCodeFiles().isEmpty()) {
            return normalizeEditableFileList(request.getCodeFiles().stream()
                    .map(this::normalizeEditableFile)
                    .toList());
        }
        if (storageMode == CodeStorageMode.MULTI_FILE) {
            List<EditableCodeFileDto> existingFiles = codeFileRepository.findBySessionIdOrderBySortOrderAscCreatedAtAsc(sessionId).stream()
                    .map(this::toEditableCodeFileDto)
                    .toList();
            if (!existingFiles.isEmpty()) {
                return normalizeEditableFileList(existingFiles);
            }
        }
        return normalizeEditableFileList(List.of(buildEditableFile(resolveDefaultFilePath(codeState), resolveDefaultFileName(codeState), request.getCode() == null ? "" : request.getCode(), 0)));
    }

    private String resolveDefaultFilePath(CodeState codeState) {
        return codeState.getStorageMode() == CodeStorageMode.MULTI_FILE ? "src/app/app.component.ts" : "main.txt";
    }

    private String resolveDefaultFileName(CodeState codeState) {
        return codeState.getStorageMode() == CodeStorageMode.MULTI_FILE ? "app.component.ts" : "main.txt";
    }

    private EditableCodeFileDto normalizeEditableFile(EditableCodeFileDto file) {
        return EditableCodeFileDto.builder()
                .path(file.getPath())
                .displayName(file.getDisplayName() == null || file.getDisplayName().isBlank() ? file.getPath() : file.getDisplayName())
                .content(file.getContent() == null ? "" : file.getContent())
                .editable(file.getEditable() == null ? true : file.getEditable())
                .sortOrder(file.getSortOrder() == null ? 0 : file.getSortOrder())
                .enabledForCandidate(file.getEnabledForCandidate() == null ? true : file.getEnabledForCandidate())
                .activeQuestion(file.getActiveQuestion() == null ? false : file.getActiveQuestion())
                .submitted(file.getSubmitted() == null ? false : file.getSubmitted())
                .difficultyLevel(file.getDifficultyLevel())
                .idealDurationMinutes(file.getIdealDurationMinutes())
                .expectedTimeComplexity(file.getExpectedTimeComplexity())
                .expectedSpaceComplexity(file.getExpectedSpaceComplexity())
                .questionIntegrityNotes(file.getQuestionIntegrityNotes())
                .originalProblemStatement(file.getOriginalProblemStatement())
                .originalStarterCode(file.getOriginalStarterCode())
                .referenceSolution(file.getReferenceSolution())
                .questionConcepts(file.getQuestionConcepts())
                .questionEvaluationFocus(file.getQuestionEvaluationFocus())
                .candidateStartedAt(file.getCandidateStartedAt())
                .submittedAt(file.getSubmittedAt())
                .solveDurationSeconds(file.getSolveDurationSeconds())
                .executeAttemptCount(file.getExecuteAttemptCount() == null ? 0 : file.getExecuteAttemptCount())
                .aiEvaluation(file.getAiEvaluation())
                .build();
    }

    private List<EditableCodeFileDto> normalizeEditableFileList(List<EditableCodeFileDto> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        boolean hasActive = files.stream().anyMatch(file -> Boolean.TRUE.equals(file.getActiveQuestion()));
        List<EditableCodeFileDto> normalized = new ArrayList<>();
        for (int index = 0; index < files.size(); index++) {
            EditableCodeFileDto file = files.get(index);
            if (hasActive || index > 0) {
                normalized.add(file);
                continue;
            }
            normalized.add(EditableCodeFileDto.builder()
                    .path(file.getPath())
                    .displayName(file.getDisplayName())
                    .content(file.getContent())
                    .editable(file.getEditable())
                    .sortOrder(file.getSortOrder())
                    .enabledForCandidate(file.getEnabledForCandidate())
                    .activeQuestion(true)
                    .submitted(file.getSubmitted())
                    .idealDurationMinutes(file.getIdealDurationMinutes())
                    .difficultyLevel(file.getDifficultyLevel())
                    .expectedTimeComplexity(file.getExpectedTimeComplexity())
                    .expectedSpaceComplexity(file.getExpectedSpaceComplexity())
                    .questionIntegrityNotes(file.getQuestionIntegrityNotes())
                    .originalProblemStatement(file.getOriginalProblemStatement())
                    .originalStarterCode(file.getOriginalStarterCode())
                    .referenceSolution(file.getReferenceSolution())
                    .questionConcepts(file.getQuestionConcepts())
                    .questionEvaluationFocus(file.getQuestionEvaluationFocus())
                    .candidateStartedAt(file.getCandidateStartedAt())
                    .submittedAt(file.getSubmittedAt())
                    .solveDurationSeconds(file.getSolveDurationSeconds())
                    .executeAttemptCount(file.getExecuteAttemptCount())
                    .runResult(file.getRunResult())
                    .aiEvaluation(file.getAiEvaluation())
                    .changedAfterLastRun(file.getChangedAfterLastRun())
                    .build());
        }
        return normalized;
    }

    private void validateWorkspaceFiles(TechnologySkill technology, List<EditableCodeFileDto> files) {
        if (files == null || files.isEmpty()) {
            return;
        }

        if (files.size() > MAX_WORKSPACE_FILE_COUNT) {
            throw new IllegalArgumentException("Too many editable files in the workspace");
        }

        LinkedHashSet<String> seenPaths = new LinkedHashSet<>();
        int totalChars = 0;
        for (EditableCodeFileDto file : files) {
            String path = file.getPath() == null ? "" : file.getPath().replace('\\', '/').trim();
            if (path.isBlank()) {
                throw new IllegalArgumentException("Each editable file must include a valid path");
            }
            if (!seenPaths.add(path)) {
                throw new IllegalArgumentException("Duplicate file path detected: " + path);
            }

            String content = file.getContent() == null ? "" : file.getContent();
            if (content.length() > MAX_WORKSPACE_FILE_CHARS) {
                throw new IllegalArgumentException("File is too large: " + path);
            }
            totalChars += content.length();

            if (technology == TechnologySkill.ANGULAR) {
                if (!path.startsWith("src/app/")) {
                    throw new IllegalArgumentException("Only src/app files are editable for Angular interviews");
                }
                if (!(path.endsWith(".ts") || path.endsWith(".html") || path.endsWith(".css"))) {
                    throw new IllegalArgumentException("Only .ts, .html, and .css files are supported for Angular interviews");
                }
            } else if (technology == TechnologySkill.REACT) {
                if (!path.startsWith("src/")) {
                    throw new IllegalArgumentException("Only src files are editable for React interviews");
                }
                if (!(path.endsWith(".tsx") || path.endsWith(".ts") || path.endsWith(".css"))) {
                    throw new IllegalArgumentException("Only .tsx, .ts, and .css files are supported for React interviews");
                }
            } else if (technology == TechnologySkill.JAVA) {
                if (!path.endsWith(".java")) {
                    throw new IllegalArgumentException("Only .java question tabs are supported for Java interviews");
                }
            } else if (technology == TechnologySkill.PYTHON) {
                if (!path.endsWith(".py")) {
                    throw new IllegalArgumentException("Only .py question tabs are supported for Python interviews");
                }
            }
        }

        if (totalChars > MAX_WORKSPACE_TOTAL_CHARS) {
            throw new IllegalArgumentException("Workspace content is too large");
        }
    }

    private String resolvePrimaryCode(List<EditableCodeFileDto> editableFiles, String fallbackCode) {
        if (editableFiles != null && !editableFiles.isEmpty()) {
            return editableFiles.stream()
                    .filter(file -> Boolean.TRUE.equals(file.getActiveQuestion()))
                    .findFirst()
                    .orElse(editableFiles.get(0))
                    .getContent();
        }
        return fallbackCode;
    }

    private String resolveExecutableCodeForPath(List<EditableCodeFileDto> editableFiles, String activeFilePath, String fallbackCode) {
        if (editableFiles == null || editableFiles.isEmpty()) {
            return fallbackCode;
        }
        if (activeFilePath != null && !activeFilePath.isBlank()) {
            String normalizedPath = activeFilePath.replace('\\', '/').trim();
            return editableFiles.stream()
                    .filter(file -> normalizedPath.equals(file.getPath()))
                    .map(EditableCodeFileDto::getContent)
                    .findFirst()
                    .orElse(fallbackCode);
        }
        return resolvePrimaryCode(editableFiles, fallbackCode);
    }

    private boolean supportsPersistentFrontendWorkspace(TechnologySkill technology) {
        return technology == TechnologySkill.ANGULAR || technology == TechnologySkill.REACT;
    }

    private boolean supportsFinalPreview(TechnologySkill technology) {
        return supportsPersistentFrontendWorkspace(technology);
    }

    private void replaceCodeFiles(String sessionId, List<EditableCodeFileDto> editableFiles) {
        Map<String, CodeFile> existingByPath = codeFileRepository.findBySessionIdOrderBySortOrderAscCreatedAtAsc(sessionId).stream()
                .collect(java.util.stream.Collectors.toMap(CodeFile::getFilePath, file -> file, (left, right) -> left));
        codeFileRepository.deleteAllBySessionId(sessionId);
        codeFileRepository.flush();
        List<CodeFile> persistedFiles = editableFiles.stream()
                .map(file -> {
                    CodeFile codeFile = new CodeFile();
                    CodeFile existing = existingByPath.get(file.getPath());
                    codeFile.setSessionId(sessionId);
                    codeFile.setFilePath(file.getPath());
                    codeFile.setDisplayName(file.getDisplayName());
                    codeFile.setContent(file.getContent());
                    codeFile.setSortOrder(file.getSortOrder());
                    codeFile.setEditable(Boolean.TRUE.equals(file.getEditable()));
                    codeFile.setEnabledForCandidate(file.getEnabledForCandidate() == null || Boolean.TRUE.equals(file.getEnabledForCandidate()));
                    codeFile.setActiveQuestion(Boolean.TRUE.equals(file.getActiveQuestion()));
                    codeFile.setSubmitted(Boolean.TRUE.equals(file.getSubmitted()));
                    codeFile.setDifficultyLevel(resolvePersistedDifficultyLevel(file, existing));
                    codeFile.setIdealDurationMinutes(file.getIdealDurationMinutes() == null && existing != null ? existing.getIdealDurationMinutes() : file.getIdealDurationMinutes());
                    codeFile.setOriginalProblemStatement(firstNonBlank(file.getOriginalProblemStatement(), existing == null ? null : existing.getOriginalProblemStatement()));
                    codeFile.setOriginalStarterCode(firstNonBlank(file.getOriginalStarterCode(), existing == null ? null : existing.getOriginalStarterCode()));
                    codeFile.setReferenceSolution(firstNonBlank(file.getReferenceSolution(), existing == null ? null : existing.getReferenceSolution()));
                    codeFile.setExpectedTimeComplexity(firstNonBlank(file.getExpectedTimeComplexity(), existing == null ? null : existing.getExpectedTimeComplexity()));
                    codeFile.setExpectedSpaceComplexity(firstNonBlank(file.getExpectedSpaceComplexity(), existing == null ? null : existing.getExpectedSpaceComplexity()));
                    codeFile.setQuestionConcepts(firstNonBlank(file.getQuestionConcepts(), existing == null ? null : existing.getQuestionConcepts()));
                    codeFile.setQuestionEvaluationFocus(firstNonBlank(file.getQuestionEvaluationFocus(), existing == null ? null : existing.getQuestionEvaluationFocus()));
                    codeFile.setQuestionIntegrityNotes(firstNonBlank(file.getQuestionIntegrityNotes(), existing == null ? null : existing.getQuestionIntegrityNotes()));
                    codeFile.setCandidateStartedAt(file.getCandidateStartedAt());
                    codeFile.setSubmittedAt(file.getSubmittedAt());
                    codeFile.setSolveDurationSeconds(file.getSolveDurationSeconds());
                    codeFile.setExecuteAttemptCount(file.getExecuteAttemptCount() == null ? 0 : file.getExecuteAttemptCount());
                    applyAiEvaluationSnapshot(codeFile, file.getAiEvaluation(), existing);
                    return codeFile;
                })
                .toList();
        codeFileRepository.saveAll(persistedFiles);
    }

    private Integer resolvePersistedDifficultyLevel(EditableCodeFileDto file, CodeFile existing) {
        if (file.getDifficultyLevel() != null) {
            return file.getDifficultyLevel();
        }
        if (existing != null && existing.getDifficultyLevel() != null) {
            return existing.getDifficultyLevel();
        }
        boolean hasAiQuestionMetadata = file.getExpectedTimeComplexity() != null
                || file.getExpectedSpaceComplexity() != null
                || file.getQuestionIntegrityNotes() != null
                || containsAiProblemComment(file.getContent());
        return hasAiQuestionMetadata ? 1 : null;
    }

    private List<EditableCodeFileDto> resolveEditableFiles(InterviewSession session, CodeState codeState) {
        List<EditableCodeFileDto> persistedFiles = codeFileRepository.findBySessionIdOrderBySortOrderAscCreatedAtAsc(session.getId()).stream()
                .map(this::toEditableCodeFileDto)
                .toList();
        if (!persistedFiles.isEmpty()) {
            return attachRunResults(session.getId(), normalizeEditableFileList(persistedFiles));
        }
        if (codeState == null || codeState.getLatestCode() == null) {
            return List.of();
        }
        List<EditableCodeFileDto> defaults = buildDefaultEditableFiles(session.getTechnology());
        if (defaults.isEmpty()) {
            return List.of();
        }
        List<EditableCodeFileDto> legacyFiles = new ArrayList<>();
        for (EditableCodeFileDto file : defaults) {
            if (file.getSortOrder() != null && file.getSortOrder() == 0) {
                legacyFiles.add(buildEditableFile(file.getPath(), file.getDisplayName(), codeState.getLatestCode(), 0));
            } else {
                legacyFiles.add(file);
            }
        }
        return attachRunResults(session.getId(), normalizeEditableFileList(legacyFiles));
    }

    private EditableCodeFileDto toEditableCodeFileDto(CodeFile file) {
        return EditableCodeFileDto.builder()
                .path(file.getFilePath())
                .displayName(file.getDisplayName())
                .content(file.getContent())
                .editable(file.getEditable())
                .sortOrder(file.getSortOrder())
                .enabledForCandidate(file.getEnabledForCandidate() == null || Boolean.TRUE.equals(file.getEnabledForCandidate()))
                .activeQuestion(Boolean.TRUE.equals(file.getActiveQuestion()))
                .submitted(Boolean.TRUE.equals(file.getSubmitted()))
                .difficultyLevel(file.getDifficultyLevel())
                .idealDurationMinutes(file.getIdealDurationMinutes())
                .expectedTimeComplexity(file.getExpectedTimeComplexity())
                .expectedSpaceComplexity(file.getExpectedSpaceComplexity())
                .questionIntegrityNotes(file.getQuestionIntegrityNotes())
                .originalProblemStatement(file.getOriginalProblemStatement())
                .originalStarterCode(file.getOriginalStarterCode())
                .referenceSolution(file.getReferenceSolution())
                .questionConcepts(file.getQuestionConcepts())
                .questionEvaluationFocus(file.getQuestionEvaluationFocus())
                .candidateStartedAt(file.getCandidateStartedAt())
                .submittedAt(file.getSubmittedAt())
                .solveDurationSeconds(file.getSolveDurationSeconds())
                .executeAttemptCount(file.getExecuteAttemptCount() == null ? 0 : file.getExecuteAttemptCount())
                .aiEvaluation(toAiEvaluationDto(file))
                .build();
    }

    private List<EditableCodeFileDto> attachRunResults(String sessionId, List<EditableCodeFileDto> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        Map<String, RunResult> latestByPath = new HashMap<>();
        runResultRepository.findBySessionIdAndFilePathIsNotNullOrderByCompiledAtDesc(sessionId)
                .forEach(result -> latestByPath.putIfAbsent(result.getFilePath(), result));

        return files.stream()
                .map(file -> {
                    RunResult result = latestByPath.get(file.getPath());
                    return EditableCodeFileDto.builder()
                            .path(file.getPath())
                            .displayName(file.getDisplayName())
                            .content(file.getContent())
                            .editable(file.getEditable())
                            .sortOrder(file.getSortOrder())
                            .enabledForCandidate(file.getEnabledForCandidate())
                            .activeQuestion(file.getActiveQuestion())
                            .submitted(file.getSubmitted())
                            .difficultyLevel(file.getDifficultyLevel())
                            .idealDurationMinutes(file.getIdealDurationMinutes())
                            .expectedTimeComplexity(file.getExpectedTimeComplexity())
                            .expectedSpaceComplexity(file.getExpectedSpaceComplexity())
                            .questionIntegrityNotes(file.getQuestionIntegrityNotes())
                            .originalProblemStatement(file.getOriginalProblemStatement())
                            .originalStarterCode(file.getOriginalStarterCode())
                            .referenceSolution(file.getReferenceSolution())
                            .questionConcepts(file.getQuestionConcepts())
                            .questionEvaluationFocus(file.getQuestionEvaluationFocus())
                            .candidateStartedAt(file.getCandidateStartedAt())
                            .submittedAt(file.getSubmittedAt())
                            .solveDurationSeconds(file.getSolveDurationSeconds())
                            .executeAttemptCount(file.getExecuteAttemptCount())
                            .runResult(result == null ? null : toRunResultDto(result))
                            .aiEvaluation(file.getAiEvaluation())
                            .changedAfterLastRun(result != null && !Objects.equals(file.getContent(), result.getSourceSnapshot()))
                            .build();
                })
                .toList();
    }

    private RunResultDto toRunResultDto(RunResult result) {
        return RunResultDto.builder()
                .compiledAt(result.getCompiledAt())
                .filePath(result.getFilePath())
                .displayName(result.getDisplayName())
                .sourceSnapshot(result.getSourceSnapshot())
                .stdout(result.getStdout())
                .stderr(result.getStderr())
                .exitStatus(result.getExitStatus())
                .executionTimeMs(result.getExecutionTimeMs())
                .build();
    }

    private RecommendationDecision resolveRecommendationDecision(Feedback feedback) {
        if (feedback.getRecommendationDecision() != null) {
            return feedback.getRecommendationDecision();
        }
        return Boolean.TRUE.equals(feedback.getRecommendation()) ? RecommendationDecision.YES : RecommendationDecision.NO;
    }

    private String formatRecommendationDecision(RecommendationDecision decision) {
        if (decision == null) {
            return "No";
        }
        return switch (decision) {
            case YES -> "Yes";
            case NO -> "No";
            case REEVALUATION -> "Reevaluation";
        };
    }

    private boolean matchesSearch(SessionResponse session, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }

        String normalizedSearch = search.trim().toLowerCase(Locale.ROOT);
        if (normalizedSearch.length() < 3) {
            return true;
        }

        return session.getParticipants().stream()
                .map(participant -> (participant.getName() + " " + participant.getEmail()).toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(normalizedSearch));
    }

    private boolean matchesFilters(SessionResponse session,
                                   OffsetDateTime from,
                                   OffsetDateTime to,
                                   List<TechnologySkill> technologies,
                                   List<FeedbackRating> ratings) {
        if (from != null && (session.getCreatedAt() == null || session.getCreatedAt().isBefore(from))) {
            return false;
        }
        if (to != null && (session.getCreatedAt() == null || session.getCreatedAt().isAfter(to))) {
            return false;
        }
        if (technologies != null && !technologies.isEmpty() && (session.getTechnology() == null || !technologies.contains(session.getTechnology()))) {
            return false;
        }
        if (ratings != null && !ratings.isEmpty()) {
            if (session.getFeedback() == null || session.getFeedback().getRating() == null || !ratings.contains(session.getFeedback().getRating())) {
                return false;
            }
        }
        return true;
    }

    private List<SessionResponse> filterSessions(String search,
                                                 OffsetDateTime from,
                                                 OffsetDateTime to,
                                                 List<TechnologySkill> technologies,
                                                 List<FeedbackRating> ratings,
                                                 boolean includeDetails) {
        return sessionRepository.findAll().stream()
                .map(session -> toSessionResponse(session, includeDetails))
                .filter(session -> matchesSearch(session, search))
                .filter(session -> matchesFilters(session, from, to, technologies, ratings))
                .toList();
    }

    private Comparator<SessionResponse> buildSessionComparator(Pageable pageable) {
        Sort.Order order = pageable != null && pageable.getSort().isSorted()
                ? pageable.getSort().iterator().next()
                : Sort.Order.desc("createdAt");

        return buildSessionComparator(order.getProperty(), order.getDirection());
    }

    private Comparator<SessionResponse> buildSessionComparator(String property, Sort.Direction direction) {
        Comparator<SessionResponse> comparator = switch (property) {
            case "status" -> Comparator.comparing(session -> session.getStatus().name(), String.CASE_INSENSITIVE_ORDER);
            case "summary" -> Comparator.comparing(session -> session.getSummary() == null ? "" : session.getSummary(), String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparing(SessionResponse::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };

        if (direction == Sort.Direction.DESC) {
            comparator = comparator.reversed();
        }

        return comparator;
    }

    private ParticipantDto findParticipant(SessionResponse session, ParticipantRole role) {
        if (session.getParticipants() == null) {
            return null;
        }
        return session.getParticipants().stream()
                .filter(participant -> participant.getRole() == role)
                .findFirst()
                .orElse(null);
    }

    private String toCsvTimestamp(OffsetDateTime value) {
        return value == null ? "" : value.toString();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String csvCell(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private IdentityCaptureFailureReason resolveCaptureFailureReason(IdentityCaptureStatus status,
                                                                    IdentityCaptureFailureReason failureReason) {
        if (status == IdentityCaptureStatus.SKIPPED) {
            return failureReason == null ? IdentityCaptureFailureReason.USER_SKIPPED : failureReason;
        }
        if (status == IdentityCaptureStatus.FAILED) {
            return failureReason == null ? IdentityCaptureFailureReason.UNKNOWN : failureReason;
        }
        return null;
    }

    private boolean isIdentityCaptureComplete(IdentityCaptureStatus status) {
        return status == IdentityCaptureStatus.SUCCESS
                || status == IdentityCaptureStatus.SKIPPED
                || status == IdentityCaptureStatus.FAILED;
    }

    private boolean isAiInterview(InterviewSession session) {
        return session.getInterviewMode() == InterviewMode.AI_INTERVIEWER;
    }

    public record ResourceWithMetadata(org.springframework.core.io.Resource resource, String contentType) {
    }

    public record CsvExport(String filename, String content) {
    }
}
