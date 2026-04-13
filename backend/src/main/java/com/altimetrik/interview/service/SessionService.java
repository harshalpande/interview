package com.altimetrik.interview.service;

import com.altimetrik.interview.dto.AcceptDisclaimerRequest;
import com.altimetrik.interview.dto.CreateSessionRequest;
import com.altimetrik.interview.dto.CodeUpdateRequest;
import com.altimetrik.interview.dto.EndSessionRequest;
import com.altimetrik.interview.dto.FeedbackDto;
import com.altimetrik.interview.dto.FeedbackRequest;
import com.altimetrik.interview.dto.JoinSessionRequest;
import com.altimetrik.interview.dto.JoinTokenResponse;
import com.altimetrik.interview.dto.ParticipantDto;
import com.altimetrik.interview.dto.RunResultDto;
import com.altimetrik.interview.dto.SessionResponse;
import com.altimetrik.interview.dto.ValidateTokenResponse;
import com.altimetrik.interview.entity.CodeState;
import com.altimetrik.interview.entity.Feedback;
import com.altimetrik.interview.entity.InterviewSession;
import com.altimetrik.interview.entity.Participant;
import com.altimetrik.interview.entity.RunResult;
import com.altimetrik.interview.entity.SessionToken;
import com.altimetrik.interview.enums.ParticipantRole;
import com.altimetrik.interview.enums.SessionStatus;
import com.altimetrik.interview.repository.CodeStateRepository;
import com.altimetrik.interview.repository.FeedbackRepository;
import com.altimetrik.interview.repository.ParticipantRepository;
import com.altimetrik.interview.repository.RunResultRepository;
import com.altimetrik.interview.repository.SessionRepository;
import com.altimetrik.interview.repository.SessionTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private static final int DEFAULT_DURATION_SEC = 60 * 60;
    private static final int MAX_DURATION_SEC = 75 * 60;
    private static final int EXTENSION_SEC = 15 * 60;
    private static final int EXTENSION_THRESHOLD_SEC = 15 * 60;
    private static final int TOKEN_EXPIRY_MINUTES = 5;
    private static final String DEFAULT_TEMPLATE = """
            public class Solution {
                public static void main(String[] args) {
                    System.out.println("Hello, World!");
                }
            }""";

    private final SessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final SessionTokenRepository sessionTokenRepository;
    private final CodeStateRepository codeStateRepository;
    private final RunResultRepository runResultRepository;
    private final FeedbackRepository feedbackRepository;
    private final JavaCompilerService javaCompilerService;
    private final ActiveSessionTracker activeSessionTracker;

    @Transactional
    public SessionResponse createSession(CreateSessionRequest request) {
        InterviewSession session = new InterviewSession();
        session.setStatus(SessionStatus.WAITING_JOIN);
        session.setDurationSec(DEFAULT_DURATION_SEC);
        session.setExtensionUsed(false);
        session = sessionRepository.save(session);

        participantRepository.save(createParticipant(session.getId(), ParticipantRole.INTERVIEWER,
                request.getInterviewerName(), request.getInterviewerEmail()));
        participantRepository.save(createParticipant(session.getId(), ParticipantRole.INTERVIEWEE,
                request.getIntervieweeName(), request.getIntervieweeEmail()));

        CodeState codeState = new CodeState();
        codeState.setSessionId(session.getId());
        codeState.setLatestCode(DEFAULT_TEMPLATE);
        codeState.setUpdatedAt(nowUtc());
        codeState.setUpdatedByRole(ParticipantRole.INTERVIEWER.name());
        codeState.setVersion(0L);
        codeStateRepository.save(codeState);

        JoinTokenResponse joinInfo = createJoinToken(session.getId());

        InterviewSession persisted = sessionRepository.findById(session.getId())
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        log.info("Created session {}", session.getId());
        return toSessionResponse(persisted, true, joinInfo);
    }

    @Transactional(readOnly = true)
    public SessionResponse getSession(String id) {
        InterviewSession session = getRequiredSession(id);
        return toSessionResponse(session, true, getActiveJoinInfo(id));
    }

    @Transactional(readOnly = true)
    public Page<SessionResponse> listSessions(Pageable pageable) {
        return sessionRepository.findAll(pageable).map(session -> toSessionResponse(session, false, null));
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> listActiveSessions() {
        return sessionRepository.findByStatus(SessionStatus.ACTIVE).stream()
                .map(session -> toSessionResponse(session, true, null))
                .toList();
    }

    @Transactional
    public SessionResponse acceptDisclaimer(String sessionId, AcceptDisclaimerRequest request) {
        acceptDisclaimerInternal(sessionId, request.getRole());
        InterviewSession session = getRequiredSession(sessionId);
        return toSessionResponse(session, true, getActiveJoinInfo(sessionId));
    }

    @Transactional(readOnly = true)
    public ValidateTokenResponse validateToken(String token) {
        SessionToken sessionToken = getRequiredToken(token);
        if (sessionToken.getIsUsed()) {
            return invalidToken(sessionToken, "Token has already been used");
        }
        if (sessionToken.getExpiresAt().isBefore(nowUtc())) {
            expireSessionIfNeeded(sessionToken.getSessionId());
            return invalidToken(sessionToken, "Token has expired");
        }

        return ValidateTokenResponse.builder()
                .valid(true)
                .sessionId(sessionToken.getSessionId())
                .role(sessionToken.getRole())
                .expiresAt(sessionToken.getExpiresAt())
                .message("Token is valid")
                .build();
    }

    @Transactional
    public SessionResponse joinSession(String token, JoinSessionRequest request) {
        SessionToken sessionToken = getRequiredToken(token);
        if (sessionToken.getIsUsed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This interview link has already been used.");
        }
        if (sessionToken.getExpiresAt().isBefore(nowUtc())) {
            expireSessionIfNeeded(sessionToken.getSessionId());
            throw new ResponseStatusException(HttpStatus.GONE, "This interview link has expired. Please ask the interviewer to share a new link.");
        }

        Participant participant = participantRepository.findBySessionIdAndRole(sessionToken.getSessionId(), ParticipantRole.INTERVIEWEE)
                .orElseThrow(() -> new IllegalArgumentException("Interviewee not registered"));

        if (!participant.getName().equalsIgnoreCase(request.getName().trim())
                || !participant.getEmail().equalsIgnoreCase(request.getEmail().trim())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Details do not match our records. Please enter the same name and email that the interviewer registered for this interview."
            );
        }

        participant.setJoinedAt(nowUtc());
        participantRepository.save(participant);

        sessionToken.setIsUsed(true);
        sessionToken.setUsedAt(nowUtc());
        sessionTokenRepository.save(sessionToken);

        InterviewSession session = getRequiredSession(sessionToken.getSessionId());
        session.setStatus(SessionStatus.CREATED);
        sessionRepository.save(session);
        return toSessionResponse(session, true, null);
    }

    @Transactional
    public SessionResponse startSession(String sessionId) {
        InterviewSession session = getRequiredSession(sessionId);
        if (session.getStatus() == SessionStatus.ENDED || session.getStatus() == SessionStatus.EXPIRED) {
            throw new IllegalArgumentException("Session can no longer be started");
        }

        session.setStatus(SessionStatus.ACTIVE);
        if (session.getStartedAt() == null) {
            session.setStartedAt(nowUtc());
        }
        if (session.getDurationSec() == null || session.getDurationSec() == 0) {
            session.setDurationSec(DEFAULT_DURATION_SEC);
        }

        sessionRepository.save(session);
        activeSessionTracker.track(sessionId, session.getStartedAt().plusSeconds(session.getDurationSec()));
        return toSessionResponse(session, true, null);
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
        if (session.getStartedAt() != null) {
            activeSessionTracker.track(sessionId, session.getStartedAt().plusSeconds(session.getDurationSec()));
        }
        return toSessionResponse(session, true, null);
    }

    @Transactional
    public SessionResponse submitFeedback(String sessionId, FeedbackRequest request) {
        InterviewSession session = getRequiredSession(sessionId);
        Feedback feedback = feedbackRepository.findBySessionId(sessionId).orElseGet(Feedback::new);
        feedback.setSessionId(sessionId);
        feedback.setRating(request.getRating());
        feedback.setComments(request.getComments());
        feedback.setRecommendation(request.getRecommendation());
        feedbackRepository.save(feedback);
        return toSessionResponse(session, true, null);
    }

    @Transactional
    public SessionResponse endSession(String sessionId, EndSessionRequest request) {
        return endSession(sessionId, request.getFinalCode(), null);
    }

    @Transactional
    public SessionResponse endSession(String sessionId, String finalCode, FeedbackRequest feedbackRequest) {
        InterviewSession session = getRequiredSession(sessionId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new IllegalArgumentException("Session is already ended");
        }

        upsertCodeState(sessionId, finalCode, ParticipantRole.INTERVIEWER);

        JavaCompilerService.ExecutionResult executionResult = javaCompilerService.execute(finalCode, 5000, 512);
        RunResult runResult = runResultRepository.findTopBySessionIdOrderByCompiledAtDesc(sessionId).orElseGet(RunResult::new);
        runResult.setSessionId(sessionId);
        runResult.setStdout(executionResult.getStdout());
        runResult.setStderr(executionResult.getStderr().isBlank()
                ? String.join("\n", executionResult.getCompileErrors())
                : executionResult.getStderr());
        runResult.setExitStatus(executionResult.getExitCode());
        runResultRepository.save(runResult);

        if (feedbackRequest != null) {
            submitFeedback(sessionId, feedbackRequest);
        }

        session.setEndedAt(nowUtc());
        session.setStatus(SessionStatus.ENDED);
        sessionRepository.save(session);
        activeSessionTracker.clear(sessionId);
        return toSessionResponse(session, true, null);
    }

    @Transactional
    public SessionResponse abandonSession(String sessionId, String finalCode) {
        InterviewSession session = getRequiredSession(sessionId);
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new IllegalArgumentException("Only active sessions can be marked incomplete");
        }

        upsertCodeState(sessionId, finalCode == null ? "" : finalCode, ParticipantRole.INTERVIEWER);

        JavaCompilerService.ExecutionResult executionResult = javaCompilerService.execute(finalCode == null ? "" : finalCode, 5000, 512);
        RunResult runResult = runResultRepository.findTopBySessionIdOrderByCompiledAtDesc(sessionId).orElseGet(RunResult::new);
        runResult.setSessionId(sessionId);
        runResult.setStdout(executionResult.getStdout());
        runResult.setStderr(executionResult.getStderr().isBlank()
                ? String.join("\n", executionResult.getCompileErrors())
                : executionResult.getStderr());
        runResult.setExitStatus(executionResult.getExitCode());
        runResultRepository.save(runResult);

        session.setIncomplete(true);
        session.setEndedAt(nowUtc());
        session.setStatus(SessionStatus.ENDED);
        sessionRepository.save(session);
        activeSessionTracker.clear(sessionId);

        return toSessionResponse(session, true, null);
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
        upsertCodeState(sessionId, request);
        return toSessionResponse(session, true, getActiveJoinInfo(sessionId));
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

    private Participant createParticipant(String sessionId, ParticipantRole role, String name, String email) {
        Participant participant = new Participant();
        participant.setSessionId(sessionId);
        participant.setRole(role);
        participant.setName(name.trim());
        participant.setEmail(email.trim().toLowerCase(Locale.ROOT));
        return participant;
    }

    private JoinTokenResponse createJoinToken(String sessionId) {
        SessionToken token = new SessionToken();
        token.setSessionId(sessionId);
        token.setRole(ParticipantRole.INTERVIEWEE);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(nowUtc().plusMinutes(TOKEN_EXPIRY_MINUTES));
        token.setIsUsed(false);
        sessionTokenRepository.save(token);
        return toJoinTokenResponse(token);
    }

    private JoinTokenResponse getActiveJoinInfo(String sessionId) {
        return sessionTokenRepository.findAll().stream()
                .filter(token -> sessionId.equals(token.getSessionId()))
                .filter(token -> token.getRole() == ParticipantRole.INTERVIEWEE)
                .filter(token -> !Boolean.TRUE.equals(token.getIsUsed()))
                .filter(token -> token.getExpiresAt() != null && token.getExpiresAt().isAfter(nowUtc()))
                .max(Comparator.comparing(SessionToken::getCreatedAt))
                .map(this::toJoinTokenResponse)
                .orElse(null);
    }

    private JoinTokenResponse toJoinTokenResponse(SessionToken token) {
        return JoinTokenResponse.builder()
                .token(token.getToken())
                .joinUrl("/join/" + token.getToken())
                .expiresAt(token.getExpiresAt())
                .build();
    }

    private ValidateTokenResponse invalidToken(SessionToken token, String message) {
        return ValidateTokenResponse.builder()
                .valid(false)
                .sessionId(token.getSessionId())
                .role(token.getRole())
                .expiresAt(token.getExpiresAt())
                .message(message)
                .build();
    }

    private void expireSessionIfNeeded(String sessionId) {
        InterviewSession session = getRequiredSession(sessionId);
        if (session.getStatus() == SessionStatus.WAITING_JOIN) {
            session.setStatus(SessionStatus.EXPIRED);
            if (session.getEndedAt() == null) {
                session.setEndedAt(nowUtc());
            }
            sessionRepository.save(session);
        }
    }

    private InterviewSession getRequiredSession(String sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
    }

    private SessionToken getRequiredToken(String token) {
        return sessionTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token not found"));
    }

    private void upsertCodeState(String sessionId, String latestCode, ParticipantRole updatedByRole) {
        CodeUpdateRequest request = new CodeUpdateRequest();
        request.setCode(latestCode);
        request.setUpdatedByRole(updatedByRole);
        upsertCodeState(sessionId, request);
    }

    private void upsertCodeState(String sessionId, CodeUpdateRequest request) {
        CodeState codeState = codeStateRepository.findBySessionId(sessionId).orElseGet(CodeState::new);
        if (request.getVersion() != null && codeState.getVersion() != null && request.getVersion() < codeState.getVersion()) {
            throw new IllegalArgumentException("Code version is stale");
        }
        codeState.setSessionId(sessionId);
        codeState.setLatestCode(request.getCode());
        codeState.setUpdatedAt(nowUtc());
        codeState.setUpdatedByRole(request.getUpdatedByRole().name());
        codeState.setVersion(codeState.getVersion() == null ? 1L : codeState.getVersion() + 1);
        codeStateRepository.save(codeState);
    }

    private SessionResponse toSessionResponse(InterviewSession session, boolean includeDetails, JoinTokenResponse joinInfo) {
        List<ParticipantDto> participants = participantRepository.findBySessionId(session.getId()).stream()
                .map(this::toParticipantDto)
                .toList();

        CodeState codeState = codeStateRepository.findBySessionId(session.getId()).orElse(null);
        RunResult runResult = runResultRepository.findTopBySessionIdOrderByCompiledAtDesc(session.getId()).orElse(null);
        Feedback feedback = feedbackRepository.findBySessionId(session.getId()).orElse(null);

        return SessionResponse.builder()
                .id(session.getId())
                .status(session.getStatus())
                .createdAt(session.getCreatedAt())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .durationSec(session.getDurationSec())
                .remainingSec(calculateRemainingSec(session))
                .extensionUsed(Boolean.TRUE.equals(session.getExtensionUsed()))
                .readOnly(session.getStatus() == SessionStatus.ENDED || session.getStatus() == SessionStatus.EXPIRED)
                .participants(participants)
                .latestCode(includeDetails && codeState != null ? codeState.getLatestCode() : null)
                .codeVersion(codeState != null ? codeState.getVersion() : 0L)
                .finalRunResult(runResult == null ? null : RunResultDto.builder()
                        .compiledAt(runResult.getCompiledAt())
                        .stdout(runResult.getStdout())
                        .stderr(runResult.getStderr())
                        .exitStatus(runResult.getExitStatus())
                        .build())
                .feedback(feedback == null ? null : FeedbackDto.builder()
                        .rating(feedback.getRating())
                        .comments(feedback.getComments())
                        .recommendation(feedback.getRecommendation())
                        .submittedAt(feedback.getSubmittedAt())
                        .build())
                .joinInfo(joinInfo)
                .summary(buildSummary(session, feedback))
                .build();
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
                .disclaimerAcceptedAt(participant.getDisclaimerAcceptedAt())
                .joinedAt(participant.getJoinedAt())
                .build();
    }

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String buildSummary(InterviewSession session, Feedback feedback) {
        if (Boolean.TRUE.equals(session.getIncomplete())) {
            return "INCOMPLETE";
        }
        if (session.getStatus() == SessionStatus.EXPIRED) {
            return "Token Expired";
        }
        if (feedback != null) {
            return feedback.getRating() + " / " + (Boolean.TRUE.equals(feedback.getRecommendation()) ? "Recommended" : "Not recommended");
        }
        return session.getStatus().name();
    }
}
