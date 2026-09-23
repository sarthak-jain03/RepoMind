package com.repomind.service;

import com.repomind.model.CodeChunk;
import com.repomind.repository.CodeChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);

    private final CodeChunkRepository codeChunkRepository;
    private final EmbeddingService embeddingService;

    public VectorStoreService(CodeChunkRepository codeChunkRepository,
                              EmbeddingService embeddingService) {
        this.codeChunkRepository = codeChunkRepository;
        this.embeddingService = embeddingService;
    }

    
    public List<CodeChunk> similaritySearch(Long repoId, String query, int topK) {
        
        float[] queryEmbedding = embeddingService.generateEmbedding(query);
        if (queryEmbedding == null) {
            log.error("Failed to generate query embedding");
            return List.of();
        }

        
        String vectorString = embeddingService.vectorToString(queryEmbedding);

        
        return codeChunkRepository.findSimilarChunks(repoId, vectorString, topK);
    }

    
    public void deleteChunksForRepo(Long repoId) {
        codeChunkRepository.deleteByRepositoryId(repoId);
        log.info("Deleted all chunks for repo {}", repoId);
    }

    
    public long countChunksForRepo(Long repoId) {
        return codeChunkRepository.countByRepositoryId(repoId);
    }
}
