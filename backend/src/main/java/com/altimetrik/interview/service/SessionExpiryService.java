package com.altimetrik.interview.service;

import com.altimetrik.interview.entity.InterviewSession;
import com.altimetrik.interview.enums.SessionStatus;
import com.altimetrik.interview.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionExpiryService {

    private static final long PRE_SESSION_EXPIRY_HOURS = 72L;
    private static final String EXPIRED_REASON = "Interview session was not started within 72 hours of registration.";

    private final SessionRepository sessionRepository;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void expireStalePreSessionRecords() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        expireSessionsByStatus(now, SessionStatus.REGISTERED);
        expireSessionsByStatus(now, SessionStatus.AUTH_IN_PROGRESS);
    }

    private void expireSessionsByStatus(OffsetDateTime now, SessionStatus status) {
        for (InterviewSession session : sessionRepository.findByStatus(status)) {
            OffsetDateTime createdAt = session.getCreatedAt();
            if (createdAt == null || createdAt.plusHours(PRE_SESSION_EXPIRY_HOURS).isAfter(now)) {
                continue;
            }

            session.setStatus(SessionStatus.EXPIRED);
            session.setEndedAt(now);
            session.setExpiredReason(EXPIRED_REASON);
            sessionRepository.save(session);
            log.info("Expired pre-session record {} after {} hours in status {}", session.getId(), PRE_SESSION_EXPIRY_HOURS, status);
        }
    }
}
