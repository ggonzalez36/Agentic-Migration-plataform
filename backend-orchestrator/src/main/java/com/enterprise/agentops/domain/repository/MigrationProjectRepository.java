package com.enterprise.agentops.domain.repository;

import com.enterprise.agentops.domain.model.MigrationProject;
import com.enterprise.agentops.domain.model.enums.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MigrationProjectRepository extends JpaRepository<MigrationProject, UUID> {
    List<MigrationProject> findByStatus(ProjectStatus status);
    Optional<MigrationProject> findByActiveLangGraphThreadId(String activeLangGraphThreadId);
}
