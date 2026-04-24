package com.altimetrik.interview.service;

import com.altimetrik.interview.dto.AcceptDisclaimerRequest;
import com.altimetrik.interview.dto.ActivityEventDto;
import com.altimetrik.interview.dto.ActivityEventRequest;
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
import com.altimetrik.interview.entity.Participant;
import com.altimetrik.interview.entity.ParticipantAccessChallenge;
import com.altimetrik.interview.entity.RunResult;
import com.altimetrik.interview.entity.SessionActivityEvent;
import com.altimetrik.interview.enums.ActivityEventType;
import com.altimetrik.interview.enums.AvMode;
import com.altimetrik.interview.enums.CodeStorageMode;
import com.altimetrik.interview.enums.ExecutionLanguage;
import com.altimetrik.interview.enums.FeedbackRating;
import com.altimetrik.interview.enums.FrontendWorkspaceStatus;
import com.altimetrik.interview.enums.IdentityCaptureFailureReason;
import com.altimetrik.interview.enums.IdentityCaptureStatus;
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
    private final SessionActivityEventRepository sessionActivityEventRepository;
    private final SandboxClientService sandboxClientService;
    private final FrontendSandboxClientService frontendSandboxClientService;
    private final IdentitySnapshotStorageService identitySnapshotStorageService;
    private final FinalPreviewStorageService finalPreviewStorageService;

    @Transactional
    public SessionResponse createSession(CreateSessionRequest request) {
        InterviewSession session = new InterviewSession();
        session.setStatus(SessionStatus.REGISTERED);
        session.setDurationSec(DEFAULT_DURATION_SEC);
        session.setExtensionUsed(false);
        session.setTechnology(request.getTechnology() == null ? TechnologySkill.JAVA : request.getTechnology());
        session.setAvMode(request.getAvMode() == null ? AvMode.EXTERNAL : request.getAvMode());
        session = sessionRepository.save(session);

        participantRepository.save(createParticipant(session.getId(), ParticipantRole.INTERVIEWER,
                request.getInterviewerName(), request.getInterviewerEmail(), request.getInterviewerTimeZone()));
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

    @Transactional
    public SessionResponse endSession(String sessionId, EndSessionRequest request) {
        return endSession(sessionId, request.getFinalCode(), request.getCodeFiles(), null);
    }

    @Transactional
    public SessionResponse endSession(String sessionId, String finalCode, FeedbackRequest feedbackRequest) {
        return endSession(sessionId, finalCode, null, feedbackRequest);
    }

    @Transactional
    public SessionResponse endSession(String sessionId, String finalCode, List<EditableCodeFileDto> codeFiles, FeedbackRequest feedbackRequest) {
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

        ExecuteResponse executionResult = sandboxClientService.execute(buildExecuteRequest(sessionId, finalCode, codeFiles, session.getTechnology()));
        RunResult runResult = runResultRepository.findTopBySessionIdOrderByCompiledAtDesc(sessionId).orElseGet(RunResult::new);
        runResult.setSessionId(sessionId);
        runResult.setStdout(executionResult.getStdout());
        runResult.setStderr((executionResult.getStderr() == null || executionResult.getStderr().isBlank())
                ? String.join("\n", executionResult.getCompileErrors() == null ? List.of() : executionResult.getCompileErrors())
                : executionResult.getStderr());
        runResult.setExitStatus(executionResult.getExitCode());
        runResultRepository.save(runResult);
        captureFinalPreviewIfAvailable(session, executionResult);

        if (feedbackRequest != null) {
            submitFeedback(sessionId, feedbackRequest);
        }

        session.setEndedAt(nowUtc());
        session.setStatus(SessionStatus.ENDED);
        clearRecoveryWindow(session);
        sessionRepository.save(session);
        cleanupFrontendWorkspaceIfNeeded(session);
        return toSessionResponse(session, true);
    }

    @Transactional
    public SessionResponse abandonSession(String sessionId, String finalCode) {
        InterviewSession session = getRequiredSession(sessionId);
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new IllegalArgumentException("Only active sessions can be marked incomplete");
        }

        upsertCodeState(sessionId, finalCode == null ? "" : finalCode, ParticipantRole.INTERVIEWER);

        ExecuteResponse executionResult = sandboxClientService.execute(buildExecuteRequest(sessionId, finalCode == null ? "" : finalCode, null, session.getTechnology()));
        RunResult runResult = runResultRepository.findTopBySessionIdOrderByCompiledAtDesc(sessionId).orElseGet(RunResult::new);
        runResult.setSessionId(sessionId);
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
        if (status == IdentityCaptureStatus.SUCCESS) {
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

        boolean interviewerReady = interviewer.getDisclaimerAcceptedAt() != null
                && isOtpSatisfied(interviewerChallenge);
        boolean intervieweeReady = interviewee.getDisclaimerAcceptedAt() != null
                && isOtpSatisfied(intervieweeChallenge)
                && interviewee.getIdentityCaptureStatus() == IdentityCaptureStatus.SUCCESS;

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
                    || !Objects.equals(existing.getSortOrder(), next.getSortOrder())) {
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
        RunResult runResult = runResultRepository.findTopBySessionIdOrderByCompiledAtDesc(session.getId()).orElse(null);
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
                                              TechnologySkill technology) {
        ExecuteRequest request = new ExecuteRequest(sourceCode);
        request.setSessionId(sessionId);
        request.setLanguage(toExecutionLanguage(technology));
        if (supportsPersistentFrontendWorkspace(technology)) {
            request.setCodeFiles(codeFiles != null && !codeFiles.isEmpty()
                    ? codeFiles
                    : codeFileRepository.findBySessionIdOrderBySortOrderAscCreatedAtAsc(sessionId).stream()
                            .map(this::toEditableCodeFileDto)
                            .toList());
        }
        return request;
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
            case ANGULAR, REACT -> CodeStorageMode.MULTI_FILE;
            default -> CodeStorageMode.SINGLE_FILE;
        };
    }

    private List<EditableCodeFileDto> buildDefaultEditableFiles(TechnologySkill technology) {
        return switch (technology) {
            case PYTHON -> List.of(buildEditableFile("main.py", "main.py", DEFAULT_PYTHON_TEMPLATE, 0));
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
            default -> List.of(buildEditableFile("Solution.java", "Solution.java", DEFAULT_JAVA_TEMPLATE, 0));
        };
    }

    private EditableCodeFileDto buildEditableFile(String path, String displayName, String content, int sortOrder) {
        return EditableCodeFileDto.builder()
                .path(path)
                .displayName(displayName)
                .content(content)
                .editable(true)
                .sortOrder(sortOrder)
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
            return request.getCodeFiles().stream()
                    .map(this::normalizeEditableFile)
                    .toList();
        }
        if (storageMode == CodeStorageMode.MULTI_FILE) {
            List<EditableCodeFileDto> existingFiles = codeFileRepository.findBySessionIdOrderBySortOrderAscCreatedAtAsc(sessionId).stream()
                    .map(this::toEditableCodeFileDto)
                    .toList();
            if (!existingFiles.isEmpty()) {
                return existingFiles;
            }
        }
        return List.of(buildEditableFile(resolveDefaultFilePath(codeState), resolveDefaultFileName(codeState), request.getCode() == null ? "" : request.getCode(), 0));
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
                .build();
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
            }
        }

        if (totalChars > MAX_WORKSPACE_TOTAL_CHARS) {
            throw new IllegalArgumentException("Workspace content is too large");
        }
    }

    private String resolvePrimaryCode(List<EditableCodeFileDto> editableFiles, String fallbackCode) {
        if (editableFiles != null && !editableFiles.isEmpty()) {
            return editableFiles.get(0).getContent();
        }
        return fallbackCode;
    }

    private boolean supportsPersistentFrontendWorkspace(TechnologySkill technology) {
        return technology == TechnologySkill.ANGULAR || technology == TechnologySkill.REACT;
    }

    private boolean supportsFinalPreview(TechnologySkill technology) {
        return supportsPersistentFrontendWorkspace(technology);
    }

    private void replaceCodeFiles(String sessionId, List<EditableCodeFileDto> editableFiles) {
        codeFileRepository.deleteAllBySessionId(sessionId);
        codeFileRepository.flush();
        List<CodeFile> persistedFiles = editableFiles.stream()
                .map(file -> {
                    CodeFile codeFile = new CodeFile();
                    codeFile.setSessionId(sessionId);
                    codeFile.setFilePath(file.getPath());
                    codeFile.setDisplayName(file.getDisplayName());
                    codeFile.setContent(file.getContent());
                    codeFile.setSortOrder(file.getSortOrder());
                    codeFile.setEditable(Boolean.TRUE.equals(file.getEditable()));
                    return codeFile;
                })
                .toList();
        codeFileRepository.saveAll(persistedFiles);
    }

    private List<EditableCodeFileDto> resolveEditableFiles(InterviewSession session, CodeState codeState) {
        List<EditableCodeFileDto> persistedFiles = codeFileRepository.findBySessionIdOrderBySortOrderAscCreatedAtAsc(session.getId()).stream()
                .map(this::toEditableCodeFileDto)
                .toList();
        if (!persistedFiles.isEmpty()) {
            return persistedFiles;
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
        return legacyFiles;
    }

    private EditableCodeFileDto toEditableCodeFileDto(CodeFile file) {
        return EditableCodeFileDto.builder()
                .path(file.getFilePath())
                .displayName(file.getDisplayName())
                .content(file.getContent())
                .editable(file.getEditable())
                .sortOrder(file.getSortOrder())
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

    public record ResourceWithMetadata(org.springframework.core.io.Resource resource, String contentType) {
    }

    public record CsvExport(String filename, String content) {
    }
}
