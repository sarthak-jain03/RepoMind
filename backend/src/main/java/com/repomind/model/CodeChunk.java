package com.repomind.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalDateTime;

@Entity
@Table(name = "code_chunks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private Repository repository;

    @Column(nullable = false)
    private String filePath;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private String language;

    private Integer startLine;

    private Integer endLine;

    private Integer chunkIndex;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    
    @Column(columnDefinition = "vector(768)")
    @ColumnTransformer(write = "?::vector")
    private String embedding;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
