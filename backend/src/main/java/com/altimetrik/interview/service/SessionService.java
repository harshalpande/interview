package com.altimetrik.interview.service;

import com.altimetrik.interview.dto.AcceptDisclaimerRequest;
import com.altimetrik.interview.dto.ActivityEventDto;
import com.altimetrik.interview.dto.ActivityEventRequest;
import com.altimetrik.interview.dto.CreateSessionRequest;
import com.altimetrik.interview.dto.CodeUpdateRequest;
import com.altimetrik.interview.dto.EndSessionRequest;
import com.altimetrik.interview.dto.FeedbackDto;
import com.altimetrik.interview.dto.FeedbackRequest;
import com.altimetrik.interview.dto.HeartbeatRequest;
import com.altimetrik.interview.dto.JoinSessionRequest;
import com.altimetrik.interview.dto.JoinTokenResponse;
import com.altimetrik.interview.dto.ParticipantDto;
import com.altimetrik.interview.dto.DisconnectParticipantRequest;
import com.altimetrik.interview.dto.ResumeApprovalRequest;
import com.altimetrik.interview.dto.ResumeRequest;
import com.altimetrik.interview.dto.ResumeResponse;
import com.altimetrik.interview.dto.RunResultDto;
import com.altimetrik.interview.dto.SessionResponse;
import com.altimetrik.interview.dto.ValidateTokenResponse;
import com.altimetrik.interview.entity.CodeState;
import com.altimetrik.interview.entity.Feedback;
import com.altimetrik.interview.entity.InterviewSession;
import com.altimetrik.interview.entity.Participant;
import com.altimetrik.interview.entity.RunResult;
import com.altimetrik.interview.entity.SessionActivityEvent;
import com.altimetrik.interview.entity.SessionToken;
import com.altimetrik.interview.enums.ActivityEventType;
import com.altimetrik.interview.enums.FeedbackRating;
import com.altimetrik.interview.enums.IdentityCaptureFailureReason;
import com.altimetrik.interview.enums.IdentityCaptureStatus;
import com.altimetrik.interview.enums.ParticipantConnectionStatus;
import com.altimetrik.interview.enums.ParticipantRole;
import com.altimetrik.interview.enums.RecommendationDecision;
import com.altimetrik.interview.enums.ResumeReason;
import com.altimetrik.interview.enums.SessionStatus;
import com.altimetrik.interview.enums.TechnologySkill;
import com.altimetrik.interview.repository.CodeStateRepository;
import com.altimetrik.interview.repository.FeedbackRepository;
import com.altimetrik.interview.repository.ParticipantRepository;
import com.altimetrik.interview.repository.RunResultRepository;
import com.altimetrik.interview.repository.SessionActivityEventRepository;
import com.altimetrik.interview.repository.SessionRepository;
import com.altimetrik.interview.repository.SessionTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private static final int DEFAULT_DURATION_SEC = 60 * 60;
    private static final int MAX_DURATION_SEC = 75 * 60;
    private static final int EXTENSION_SEC = 15 * 60;
    private static final int EXTENSION_THRESHOLD_SEC = 15 * 60;
    private static final int RECOVERY_WINDOW_SEC = 120;
    private static final int TOKEN_EXPIRY_MINUTES = 5;
    private static final String REPEAT_RESUME_REJECTION_REASON = "Candidate was rejected due to suspicious activity after attempting to resume the interview more than once.";
    private static final String EXPIRED_RESUME_REJECTION_REASON = "Candidate was rejected due to suspicious activity because the resume attempt happened after the 120-second recovery window.";
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
    private static final String DEFAULT_TEMPLATE = """
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

    private final SessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final SessionTokenRepository sessionTokenRepository;
    private final CodeStateRepository codeStateRepository;
    private final RunResultRepository runResultRepository;
    private final FeedbackRepository feedbackRepository;
    private final SessionActivityEventRepository sessionActivityEventRepository;
    private final JavaCompilerService javaCompilerService;
    private final IdentitySnapshotStorageService identitySnapshotStorageService;

    @Transactional
    public SessionResponse createSession(CreateSessionRequest request) {
        InterviewSession session = new InterviewSession();
        session.setStatus(SessionStatus.WAITING_JOIN);
        session.setDurationSec(DEFAULT_DURATION_SEC);
        session.setExtensionUsed(false);
        session.setTechnology(request.getTechnology() == null ? TechnologySkill.JAVA : request.getTechnology());
        session = sessionRepository.save(session);

        participantRepository.save(createParticipant(session.getId(), ParticipantRole.INTERVIEWER,
                request.getInterviewerName(), request.getInterviewerEmail(), request.getInterviewerTimeZone()));
        participantRepository.save(createParticipant(session.getId(), ParticipantRole.INTERVIEWEE,
                request.getIntervieweeName(), request.getIntervieweeEmail(), null));

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
            InterviewSession session = getRequiredSession(sessionToken.getSessionId());
            if (session.getStatus() == SessionStatus.ACTIVE || session.getStatus() == SessionStatus.CREATED) {
                return ValidateTokenResponse.builder()
                        .valid(true)
                        .sessionId(sessionToken.getSessionId())
                        .role(sessionToken.getRole())
                        .expiresAt(sessionToken.getExpiresAt())
                        .message("Interview link already used. Please verify your details to resume.")
                        .resumeRequired(true)
                        .build();
            }
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
                .resumeRequired(false)
                .build();
    }

    @Transactional
    public SessionResponse joinSession(String token, JoinSessionRequest request, String clientIp, String userAgent) {
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
        participant.setTimeZone(normalizeTimeZone(request.getTimeZone()));
        markParticipantConnected(participant, request.getDeviceId(), clientIp, userAgent);
        participantRepository.save(participant);

        sessionToken.setIsUsed(true);
        sessionToken.setUsedAt(nowUtc());
        sessionTokenRepository.save(sessionToken);

        InterviewSession session = getRequiredSession(sessionToken.getSessionId());
        session.setStatus(SessionStatus.CREATED);
        clearRecoveryWindow(session);
        sessionRepository.save(session);
        return toSessionResponse(session, true, null);
    }

    @Transactional
    public ResumeResponse requestResume(String sessionId, ResumeRequest request, String clientIp, String userAgent) {
        InterviewSession session = getRequiredSession(sessionId);
        if (session.getStatus() != SessionStatus.ACTIVE && session.getStatus() != SessionStatus.CREATED) {
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
                    .session(toSessionResponse(session, true, getActiveJoinInfo(sessionId)))
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
                .session(toSessionResponse(session, true, getActiveJoinInfo(sessionId)))
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
        saveSystemActivityEvent(sessionId, ParticipantRole.INTERVIEWER, ActivityEventType.TAB_HIDDEN,
                "Interviewer approved the interviewee resume request.");
        return toSessionResponse(session, true, getActiveJoinInfo(sessionId));
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
        return toSessionResponse(session, true, getActiveJoinInfo(sessionId));
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
        return toSessionResponse(session, true, getActiveJoinInfo(sessionId));
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

        return toSessionResponse(session, true, getActiveJoinInfo(sessionId));
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

        clearRecoveryWindow(session);
        sessionRepository.save(session);
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
        return toSessionResponse(session, true, null);
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

        JavaCompilerService.ExecutionResult executionResult = javaCompilerService.execute(
                finalCode,
                JavaCompilerService.defaultTimeoutMs(),
                JavaCompilerService.defaultMemoryMb()
        );
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
        clearRecoveryWindow(session);
        sessionRepository.save(session);
        return toSessionResponse(session, true, null);
    }

    @Transactional
    public SessionResponse abandonSession(String sessionId, String finalCode) {
        InterviewSession session = getRequiredSession(sessionId);
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new IllegalArgumentException("Only active sessions can be marked incomplete");
        }

        upsertCodeState(sessionId, finalCode == null ? "" : finalCode, ParticipantRole.INTERVIEWER);

        JavaCompilerService.ExecutionResult executionResult = javaCompilerService.execute(
                finalCode == null ? "" : finalCode,
                JavaCompilerService.defaultTimeoutMs(),
                JavaCompilerService.defaultMemoryMb()
        );
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
        clearRecoveryWindow(session);
        sessionRepository.save(session);

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

    @Transactional
    public ActivityEventDto recordActivityEvent(String sessionId, ActivityEventRequest request) {
        InterviewSession session = getRequiredSession(sessionId);
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new IllegalArgumentException("Activity events can only be recorded for active sessions");
        }
        if (request.getParticipantRole() != ParticipantRole.INTERVIEWEE) {
            throw new IllegalArgumentException("Only interviewee activity is tracked");
        }

        SessionActivityEvent event = new SessionActivityEvent();
        event.setSessionId(sessionId);
        event.setParticipantRole(request.getParticipantRole());
        event.setEventType(request.getEventType());
        event.setDetail(buildActivityDetail(request));
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
        return toSessionResponse(session, true, getActiveJoinInfo(session.getId()));
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
        return toSessionResponse(session, true, getActiveJoinInfo(sessionId));
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

    private JoinTokenResponse createJoinToken(String sessionId) {
        SessionToken token = new SessionToken();
        token.setSessionId(sessionId);
        token.setRole(ParticipantRole.INTERVIEWEE);
        token.setToken(UUID.randomUUID().toString());
        participantRepository.findBySessionIdAndRole(sessionId, ParticipantRole.INTERVIEWEE)
                .map(Participant::getEmail)
                .ifPresent(token::setExpectedEmail);
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
                .resumeRequired(false)
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
        List<ActivityEventDto> activityEvents = includeDetails
                ? sessionActivityEventRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                        .map(this::toActivityEventDto)
                        .toList()
                : List.of();

        return SessionResponse.builder()
                .id(session.getId())
                .technology(session.getTechnology())
                .status(session.getStatus())
                .createdAt(session.getCreatedAt())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .interruptedAt(session.getInterruptedAt())
                .recoveryDeadlineAt(session.getRecoveryDeadlineAt())
                .recoveryRequiredRole(session.getRecoveryRequiredRole())
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
                        .recommendationDecision(resolveRecommendationDecision(feedback))
                        .submittedAt(feedback.getSubmittedAt())
                        .build())
                .feedbackDraft(session.getFeedbackDraftRating() == null ? null : FeedbackDto.builder()
                        .rating(session.getFeedbackDraftRating())
                        .comments(session.getFeedbackDraftComments())
                        .recommendationDecision(session.getFeedbackDraftRecommendationDecision())
                        .submittedAt(null)
                        .build())
                .activityEvents(activityEvents)
                .joinInfo(joinInfo)
                .summary(buildSummary(session, feedback))
                .suspiciousRejected(Boolean.TRUE.equals(session.getSuspiciousRejected()))
                .suspiciousScenarioKey(session.getSuspiciousScenarioKey())
                .suspiciousActivityReason(session.getSuspiciousActivityReason())
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

    private String buildSummary(InterviewSession session, Feedback feedback) {
        if (Boolean.TRUE.equals(session.getSuspiciousRejected())) {
            return "Rejected due to suspicious activity";
        }
        if (Boolean.TRUE.equals(session.getIncomplete())) {
            return "INCOMPLETE";
        }
        if (session.getStatus() == SessionStatus.EXPIRED) {
            return "Token Expired";
        }
        if (feedback != null) {
            return feedback.getRating() + " / " + formatRecommendationDecision(resolveRecommendationDecision(feedback));
        }
        return session.getStatus().name();
    }

    private String buildActivityDetail(ActivityEventRequest request) {
        if (request.getDetail() != null && !request.getDetail().isBlank()) {
            return request.getDetail().trim();
        }

        return switch (request.getEventType()) {
            case TAB_HIDDEN -> "Interviewee switched away from the interview tab or window.";
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
                .detail(event.getDetail())
                .createdAt(event.getCreatedAt())
                .build();
    }

    private String normalizeTimeZone(String timeZone) {
        return timeZone == null || timeZone.isBlank() ? null : timeZone.trim();
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
                .map(session -> toSessionResponse(session, includeDetails, null))
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

    public record ResourceWithMetadata(org.springframework.core.io.Resource resource, String contentType) {
    }

    public record CsvExport(String filename, String content) {
    }
}
