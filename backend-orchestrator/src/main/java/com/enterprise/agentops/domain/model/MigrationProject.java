package com.enterprise.agentops.domain.model;

import com.enterprise.agentops.domain.model.enums.ProjectStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entidad principal que encapsula el ciclo de vida de un proyecto de modernización legacy.
 * Diseñada para estándares de auditoría financiera (Tier-1 Enterprise / Banking).
 */
@Entity
@Table(name = "migration_projects", indexes = {
        @Index(name = "idx_project_status", columnList = "status"),
        @Index(name = "idx_project_created_at", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MigrationProject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "legacy_source_type", nullable = false, length = 50)
    private String legacySourceType; // ej: "ORACLE_PLSQL_19C", "DB2_SQL_PROCEDURES"

    @Column(name = "target_architecture", nullable = false, length = 50)
    private String targetArchitecture; // ej: "SPRING_BOOT_3_HEXAGONAL", "QUARKUS_REACTIVE"

    @Column(name = "source_repository_url", length = 512)
    private String sourceRepositoryUrl;

    @Column(name = "target_repository_url", length = 512)
    private String targetRepositoryUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.INITIALIZING;

    @Column(name = "total_units_count")
    @Builder.Default
    private Integer totalUnitsCount = 0;

    @Column(name = "migrated_units_count")
    @Builder.Default
    private Integer migratedUnitsCount = 0;

    @Column(name = "active_langgraph_thread_id", length = 128)
    private String activeLangGraphThreadId;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AgentTask> tasks = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", length = 100, updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "last_modified_by", length = 100)
    private String lastModifiedBy;

    @Version
    @Column(name = "version")
    private Long version;

    public void addTask(AgentTask task) {
        tasks.add(task);
        task.setProject(this);
    }

    public void removeTask(AgentTask task) {
        tasks.remove(task);
        task.setProject(null);
    }
}
