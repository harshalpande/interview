package com.altimetrik.interview.service;

import com.altimetrik.interview.dto.SessionResponse;
import com.altimetrik.interview.dto.SessionSocketMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SessionBroadcastService {

    private final SessionService sessionService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ActiveSessionTracker activeSessionTracker;

    @Scheduled(fixedRate = 1000)
    public void broadcastTimerTicks() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (Map.Entry<String, OffsetDateTime> entry : activeSessionTracker.snapshot().entrySet()) {
            String sessionId = entry.getKey();
            int timeLeft = Math.max(0, (int) Duration.between(now, entry.getValue()).getSeconds());

            if (timeLeft == 0) {
                SessionResponse endedSession = sessionService.getSession(sessionId);
                if (endedSession.getStatus() != com.altimetrik.interview.enums.SessionStatus.ENDED) {
                    String finalCode = endedSession.getLatestCode() == null ? "" : endedSession.getLatestCode();
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
    }
}
