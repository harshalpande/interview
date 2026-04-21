package com.altimetrik.interview.repository;

import com.altimetrik.interview.entity.FrontendWorkspace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FrontendWorkspaceRepository extends JpaRepository<FrontendWorkspace, String> {
    Optional<FrontendWorkspace> findByWorkspaceId(String workspaceId);
}
