package com.altimetrik.interview.repository;

import com.altimetrik.interview.entity.Participant;
import com.altimetrik.interview.enums.ParticipantRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, String> {
    List<Participant> findBySessionId(String sessionId);
    Optional<Participant> findBySessionIdAndRole(String sessionId, ParticipantRole role);
}
