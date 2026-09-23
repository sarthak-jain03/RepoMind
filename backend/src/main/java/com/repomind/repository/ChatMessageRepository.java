package com.repomind.repository;

import com.repomind.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByRepositoryIdAndUserIdOrderByCreatedAtAsc(Long repositoryId, Long userId);

    List<ChatMessage> findTop20ByRepositoryIdAndUserIdOrderByCreatedAtDesc(Long repositoryId, Long userId);

    void deleteByRepositoryId(Long repositoryId);
}
