package com.altimetrik.interview.service;

import com.altimetrik.interview.dto.SessionResponse;
import com.altimetrik.interview.dto.SessionSocketMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SessionBroadcastService {

    private final SessionService sessionService;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedRate = 1000)
    public void broadcastTimerTicks() {
        for (SessionResponse activeSession : sessionService.listActiveSessions()) {
            String sessionId = activeSession.getId();
            int timeLeft = activeSession.getRemainingSec() == null ? 0 : activeSession.getRemainingSec();

            if (timeLeft == 0) {
                SessionResponse endedSession = activeSession;
                if (endedSession.getStatus() != com.altimetrik.interview.enums.SessionStatus.ENDED
                        && endedSession.getStatus() != com.altimetrik.interview.enums.SessionStatus.EXPIRED) {
                    String finalCode = activeSession.getLatestCode() == null ? "" : activeSession.getLatestCode();
                    endedSession = sessionService.endSession(sessionId, finalCode, null);
                }
                messagingTemplate.convertAndSend("/topic/session/" + sessionId, SessionSocketMessage.builder()
                        .type("SESSION_END")
                        .sessionId(sessionId)
                        .timeLeft(0)
                        .version(endedSession.getCodeVersion())
                        .session(endedSession)
                        .message("Interview ended")
                        .build());
                continue;
            }

            messagingTemplate.convertAndSend("/topic/session/" + sessionId, SessionSocketMessage.builder()
                    .type("TIMER_TICK")
                    .sessionId(sessionId)
                    .timeLeft(timeLeft)
                    .message("Timer update")
                    .build());
        }

        for (SessionResponse interruptedSession : sessionService.closeInterruptedSessionsPastRecoveryWindow()) {
            messagingTemplate.convertAndSend("/topic/session/" + interruptedSession.getId(), SessionSocketMessage.builder()
                    .type("SESSION_END")
                    .sessionId(interruptedSession.getId())
                    .timeLeft(interruptedSession.getRemainingSec())
                    .version(interruptedSession.getCodeVersion())
                    .session(interruptedSession)
                    .message("Session marked incomplete after recovery timeout")
                    .build());
        }
    }
}
