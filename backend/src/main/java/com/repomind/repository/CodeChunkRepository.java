package com.repomind.repository;

import com.repomind.model.CodeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CodeChunkRepository extends JpaRepository<CodeChunk, Long> {

    List<CodeChunk> findByRepositoryId(Long repositoryId);

    @Modifying
    @Query("DELETE FROM CodeChunk c WHERE c.repository.id = :repoId")
    void deleteByRepositoryId(@Param("repoId") Long repoId);

    long countByRepositoryId(Long repositoryId);

    
    @Query(value = """
        SELECT c.* FROM code_chunks c
        WHERE c.repo_id = :repoId
        AND c.embedding IS NOT NULL
        ORDER BY c.embedding <=> cast(:queryVector as vector)
        LIMIT :topK
        """, nativeQuery = true)
    List<CodeChunk> findSimilarChunks(
            @Param("repoId") Long repoId,
            @Param("queryVector") String queryVector,
            @Param("topK") int topK
    );
}
