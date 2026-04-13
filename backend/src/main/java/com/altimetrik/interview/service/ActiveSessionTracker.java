package com.altimetrik.interview.service;

import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ActiveSessionTracker {

    private final Map<String, OffsetDateTime> sessionEndTimes = new ConcurrentHashMap<>();

    public void track(String sessionId, OffsetDateTime endTime) {
        sessionEndTimes.put(sessionId, endTime);
    }

    public void clear(String sessionId) {
        sessionEndTimes.remove(sessionId);
    }

    public Map<String, OffsetDateTime> snapshot() {
        return Map.copyOf(sessionEndTimes);
    }
}
