package com.repomind.repository;

import com.repomind.model.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@org.springframework.stereotype.Repository
public interface RepoRepository extends JpaRepository<Repository, Long> {

    List<Repository> findByUserId(Long userId);

    List<Repository> findByUserIdAndGithubRepoId(Long userId, Long githubRepoId);

    Optional<Repository> findByUserIdAndFullName(Long userId, String fullName);
}
