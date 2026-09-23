package com.repomind.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "repositories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Repository {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Long githubRepoId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false)
    private String fullName;

    private String description;

    private String language;

    private String defaultBranch;

    private Integer starCount;

    private Integer forkCount;

    private Boolean isPrivate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private IndexStatus indexStatus = IndexStatus.NOT_INDEXED;

    private Integer totalFiles;

    private Integer indexedFiles;

    private LocalDateTime lastIndexedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (indexStatus == null) {
            indexStatus = IndexStatus.NOT_INDEXED;
        }
    }

    public enum IndexStatus {
        NOT_INDEXED,
        INDEXING,
        INDEXED,
        FAILED
    }
}
