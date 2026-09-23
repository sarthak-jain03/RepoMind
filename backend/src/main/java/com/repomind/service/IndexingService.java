package com.repomind.service;

import com.repomind.model.CodeChunk;
import com.repomind.model.Repository;
import com.repomind.model.User;
import com.repomind.repository.CodeChunkRepository;
import com.repomind.repository.RepoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Service
public class IndexingService {

    private static final Logger log = LoggerFactory.getLogger(IndexingService.class);

    private final GitHubService gitHubService;
    private final CodeChunker codeChunker;
    private final EmbeddingService embeddingService;
    private final CodeChunkRepository codeChunkRepository;
    private final RepoRepository repoRepository;

    
    private final ConcurrentHashMap<Long, IndexingProgress> progressMap = new ConcurrentHashMap<>();

    public IndexingService(GitHubService gitHubService,
                           CodeChunker codeChunker,
                           EmbeddingService embeddingService,
                           CodeChunkRepository codeChunkRepository,
                           RepoRepository repoRepository) {
        this.gitHubService = gitHubService;
        this.codeChunker = codeChunker;
        this.embeddingService = embeddingService;
        this.codeChunkRepository = codeChunkRepository;
        this.repoRepository = repoRepository;
    }

    
    @Async
    @Transactional
    public void indexRepository(Repository repo, User user) {
        Long repoId = repo.getId();
        String accessToken = user.getAccessToken();

        try {
            
            repo.setIndexStatus(Repository.IndexStatus.INDEXING);
            repoRepository.save(repo);

            
            codeChunkRepository.deleteByRepositoryId(repoId);

            
            log.info("Fetching file tree for {}", repo.getFullName());
            String branch = repo.getDefaultBranch() != null ? repo.getDefaultBranch() : "main";
            List<Map<String, String>> files = gitHubService.getRepositoryTree(
                    accessToken, repo.getOwner(), repo.getName(), branch);

            
            List<Map<String, String>> indexableFiles = files.stream()
                    .filter(f -> codeChunker.shouldIndex(f.get("path")))
                    .filter(f -> {
                        long size = Long.parseLong(f.getOrDefault("size", "0"));
                        return size > 0 && size < 500_000; 
                    })
                    .toList();

            int totalFiles = indexableFiles.size();
            repo.setTotalFiles(totalFiles);
            repoRepository.save(repo);

            progressMap.put(repoId, new IndexingProgress(totalFiles, 0, "Starting indexing..."));
            log.info("Indexing {} files for {}", totalFiles, repo.getFullName());

            int indexedCount = 0;

            
            for (Map<String, String> file : indexableFiles) {
                String filePath = file.get("path");

                try {
                    
                    String content = gitHubService.getFileContent(
                            accessToken, repo.getOwner(), repo.getName(), filePath);

                    if (content == null || content.isBlank()) {
                        indexedCount++;
                        continue;
                    }

                    
                    List<CodeChunker.ChunkResult> chunks = codeChunker.chunkFile(filePath, content);

                    
                    for (CodeChunker.ChunkResult chunk : chunks) {
                        
                        String embeddingText = "File: " + chunk.filePath()
                                + " | Language: " + chunk.language()
                                + " | Lines " + chunk.startLine() + "-" + chunk.endLine()
                                + "\n\n" + chunk.content();

                        float[] embedding = embeddingService.generateEmbedding(embeddingText);

                        CodeChunk codeChunk = CodeChunk.builder()
                                .repository(repo)
                                .filePath(chunk.filePath())
                                .content(chunk.content())
                                .language(chunk.language())
                                .startLine(chunk.startLine())
                                .endLine(chunk.endLine())
                                .chunkIndex(chunk.chunkIndex())
                                .embedding(embeddingService.vectorToString(embedding))
                                .build();

                        codeChunkRepository.save(codeChunk);
                    }

                    indexedCount++;
                    repo.setIndexedFiles(indexedCount);
                    progressMap.put(repoId, new IndexingProgress(
                            totalFiles, indexedCount, "Indexing: " + filePath));

                    
                    if (indexedCount % 10 == 0) {
                        Thread.sleep(500);
                        repoRepository.save(repo);
                    }

                } catch (Exception e) {
                    log.warn("Failed to index file {}: {}", filePath, e.getMessage());
                    indexedCount++;
                }
            }

            
            repo.setIndexStatus(Repository.IndexStatus.INDEXED);
            repo.setIndexedFiles(indexedCount);
            repo.setLastIndexedAt(LocalDateTime.now());
            repoRepository.save(repo);

            progressMap.put(repoId, new IndexingProgress(totalFiles, indexedCount, "Indexing complete!"));
            log.info("Successfully indexed {} for {} ({}  files, {} chunks)",
                    repo.getFullName(), indexedCount, codeChunkRepository.countByRepositoryId(repoId));

        } catch (Exception e) {
            log.error("Failed to index repository {}: {}", repo.getFullName(), e.getMessage(), e);
            repo.setIndexStatus(Repository.IndexStatus.FAILED);
            repoRepository.save(repo);
            progressMap.put(repoId, new IndexingProgress(0, 0, "Indexing failed: " + e.getMessage()));
        }
    }

    
    public IndexingProgress getProgress(Long repoId) {
        return progressMap.getOrDefault(repoId, new IndexingProgress(0, 0, "Not started"));
    }

    public record IndexingProgress(int totalFiles, int indexedFiles, String message) {
        public int getProgressPercent() {
            if (totalFiles == 0) return 0;
            return (int) ((indexedFiles * 100.0) / totalFiles);
        }
    }
}
