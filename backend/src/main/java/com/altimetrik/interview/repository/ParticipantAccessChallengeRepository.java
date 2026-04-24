package com.altimetrik.interview.repository;

import com.altimetrik.interview.entity.ParticipantAccessChallenge;
import com.altimetrik.interview.enums.ParticipantRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParticipantAccessChallengeRepository extends JpaRepository<ParticipantAccessChallenge, String> {
    Optional<ParticipantAccessChallenge> findBySecureToken(String secureToken);
    Optional<ParticipantAccessChallenge> findBySessionIdAndParticipantRole(String sessionId, ParticipantRole participantRole);
    List<ParticipantAccessChallenge> findBySessionId(String sessionId);
}
