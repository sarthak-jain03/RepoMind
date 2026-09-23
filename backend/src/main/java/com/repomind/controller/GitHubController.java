package com.repomind.controller;

import com.repomind.dto.IndexingStatusDto;
import com.repomind.dto.RepoDto;
import com.repomind.model.Repository;
import com.repomind.model.User;
import com.repomind.repository.RepoRepository;
import com.repomind.service.GitHubService;
import com.repomind.service.IndexingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/github")
public class GitHubController {

    private static final Logger log = LoggerFactory.getLogger(GitHubController.class);

    private final GitHubService gitHubService;
    private final RepoRepository repoRepository;
    private final IndexingService indexingService;

    public GitHubController(GitHubService gitHubService,
                            RepoRepository repoRepository,
                            IndexingService indexingService) {
        this.gitHubService = gitHubService;
        this.repoRepository = repoRepository;
        this.indexingService = indexingService;
    }

    
    @GetMapping("/repos")
    public ResponseEntity<List<RepoDto>> getUserRepositories(Authentication auth,
                                                             @RequestParam(defaultValue = "false") boolean sync) {
        User user = (User) auth.getPrincipal();

        
        List<Repository> allLocalRepos = repoRepository.findByUserId(user.getId());

        if (!sync && !allLocalRepos.isEmpty()) {
            List<RepoDto> repoDtos = new ArrayList<>();
            for (Repository repo : allLocalRepos) {
                repoDtos.add(mapToDto(repo));
            }
            return ResponseEntity.ok(repoDtos);
        }

        String accessToken = user.getAccessToken();
        
        List<Map<String, Object>> githubRepos = gitHubService.getUserRepositories(accessToken);

        Map<Long, Repository> localRepoMap = new java.util.HashMap<>();
        List<Repository> reposToDelete = new ArrayList<>();

        for (Repository r : allLocalRepos) {
            if (localRepoMap.containsKey(r.getGithubRepoId())) {
                reposToDelete.add(r);
            } else {
                localRepoMap.put(r.getGithubRepoId(), r);
            }
        }

        if (!reposToDelete.isEmpty()) {
            repoRepository.deleteAll(reposToDelete);
        }

        List<Repository> reposToSave = new ArrayList<>();

        for (Map<String, Object> ghRepo : githubRepos) {
            Long githubRepoId = Long.valueOf(ghRepo.get("id").toString());

            Repository repo = localRepoMap.get(githubRepoId);
            if (repo == null) {
                repo = Repository.builder()
                        .user(user)
                        .githubRepoId(githubRepoId)
                        .build();
            }

            
            repo.setName((String) ghRepo.get("name"));
            Map<String, Object> ownerMap = (Map<String, Object>) ghRepo.get("owner");
            repo.setOwner((String) ownerMap.get("login"));
            repo.setFullName((String) ghRepo.get("full_name"));
            repo.setDescription((String) ghRepo.get("description"));
            repo.setLanguage((String) ghRepo.get("language"));
            repo.setDefaultBranch((String) ghRepo.get("default_branch"));
            repo.setStarCount(ghRepo.get("stargazers_count") != null
                    ? ((Number) ghRepo.get("stargazers_count")).intValue() : 0);
            repo.setForkCount(ghRepo.get("forks_count") != null
                    ? ((Number) ghRepo.get("forks_count")).intValue() : 0);
            repo.setIsPrivate((Boolean) ghRepo.get("private"));

            reposToSave.add(repo);
        }

        repoRepository.saveAll(reposToSave);

        List<RepoDto> repoDtos = new ArrayList<>();
        for (Repository repo : reposToSave) {
            repoDtos.add(mapToDto(repo));
        }

        return ResponseEntity.ok(repoDtos);
    }

    
    @PostMapping("/repos/{repoId}/index")
    public ResponseEntity<?> indexRepository(@PathVariable Long repoId, Authentication auth) {
        User user = (User) auth.getPrincipal();

        Repository repo = repoRepository.findById(repoId)
                .orElseThrow(() -> new RuntimeException("Repository not found"));

        if (!repo.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
        }

        if (repo.getIndexStatus() == Repository.IndexStatus.INDEXING) {
            return ResponseEntity.badRequest().body(Map.of("error", "Repository is already being indexed"));
        }

        
        indexingService.indexRepository(repo, user);

        return ResponseEntity.ok(Map.of(
                "message", "Indexing started for " + repo.getFullName(),
                "repoId", repoId
        ));
    }

    
    @GetMapping("/repos/{repoId}/index/status")
    public ResponseEntity<IndexingStatusDto> getIndexingStatus(@PathVariable Long repoId, Authentication auth) {
        User user = (User) auth.getPrincipal();

        Repository repo = repoRepository.findById(repoId)
                .orElseThrow(() -> new RuntimeException("Repository not found"));

        if (!repo.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        IndexingService.IndexingProgress progress = indexingService.getProgress(repoId);

        IndexingStatusDto status = IndexingStatusDto.builder()
                .repoId(repoId)
                .status(repo.getIndexStatus())
                .totalFiles(repo.getTotalFiles())
                .indexedFiles(repo.getIndexedFiles())
                .progressPercent(progress.getProgressPercent())
                .message(progress.message())
                .build();

        return ResponseEntity.ok(status);
    }

    
    @GetMapping("/repos/{repoId}/tree")
    public ResponseEntity<?> getRepositoryTree(@PathVariable Long repoId, Authentication auth) {
        User user = (User) auth.getPrincipal();

        Repository repo = repoRepository.findById(repoId)
                .orElseThrow(() -> new RuntimeException("Repository not found"));

        if (!repo.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        String branch = repo.getDefaultBranch() != null ? repo.getDefaultBranch() : "main";
        List<Map<String, String>> tree = gitHubService.getRepositoryTree(
                user.getAccessToken(), repo.getOwner(), repo.getName(), branch);

        return ResponseEntity.ok(tree);
    }

    private RepoDto mapToDto(Repository repo) {
        return RepoDto.builder()
                .id(repo.getId())
                .githubRepoId(repo.getGithubRepoId())
                .name(repo.getName())
                .owner(repo.getOwner())
                .fullName(repo.getFullName())
                .description(repo.getDescription())
                .language(repo.getLanguage())
                .defaultBranch(repo.getDefaultBranch())
                .starCount(repo.getStarCount())
                .forkCount(repo.getForkCount())
                .isPrivate(repo.getIsPrivate())
                .indexStatus(repo.getIndexStatus().name())
                .totalFiles(repo.getTotalFiles())
                .indexedFiles(repo.getIndexedFiles())
                .build();
    }
}
