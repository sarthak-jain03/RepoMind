package com.repomind.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Service
public class GitHubService {

    private static final Logger log = LoggerFactory.getLogger(GitHubService.class);
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String githubApiUrl;

    public GitHubService(WebClient webClient,
                         ObjectMapper objectMapper,
                         @Value("${app.github.api-url}") String githubApiUrl) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.githubApiUrl = githubApiUrl;
    }

    
    public List<Map<String, Object>> getUserRepositories(String accessToken) {
        List<Map<String, Object>> allRepos = new ArrayList<>();
        int page = 1;
        int perPage = 100;

        while (true) {
            String url = githubApiUrl + "/user/repos?per_page=" + perPage
                    + "&page=" + page + "&sort=updated&direction=desc";

            List<?> repos = webClient.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .bodyToMono(List.class)
                    .block();

            if (repos == null || repos.isEmpty()) {
                break;
            }

            for (Object repo : repos) {
                @SuppressWarnings("unchecked")
                Map<String, Object> repoMap = (Map<String, Object>) repo;
                allRepos.add(repoMap);
            }

            if (repos.size() < perPage) {
                break;
            }
            page++;
        }

        log.info("Fetched {} repositories for user", allRepos.size());
        return allRepos;
    }

    
    public List<Map<String, String>> getRepositoryTree(String accessToken, String owner, String repo, String branch) {
        String url = githubApiUrl + "/repos/" + owner + "/" + repo + "/git/trees/" + branch + "?recursive=1";

        Map<?, ?> response = webClient.get()
                .uri(url)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        List<Map<String, String>> files = new ArrayList<>();
        if (response != null && response.containsKey("tree")) {
            List<?> tree = (List<?>) response.get("tree");
            for (Object item : tree) {
                @SuppressWarnings("unchecked")
                Map<String, Object> node = (Map<String, Object>) item;
                if ("blob".equals(node.get("type"))) {
                    Map<String, String> file = new HashMap<>();
                    file.put("path", (String) node.get("path"));
                    file.put("sha", (String) node.get("sha"));
                    file.put("size", String.valueOf(node.get("size")));
                    files.add(file);
                }
            }
        }

        log.info("Found {} files in {}/{}", files.size(), owner, repo);
        return files;
    }

    
    public String getFileContent(String accessToken, String owner, String repo, String path) {
        try {
            String url = githubApiUrl + "/repos/" + owner + "/" + repo + "/contents/" + path;

            Map<?, ?> response = webClient.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("content")) {
                String encodedContent = (String) response.get("content");
                
                String cleaned = encodedContent.replaceAll("\\s", "");
                return new String(Base64.getDecoder().decode(cleaned));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch file content for {}: {}", path, e.getMessage());
        }
        return null;
    }

    
    public Map<String, Object> getRepositoryInfo(String accessToken, String owner, String repo) {
        String url = githubApiUrl + "/repos/" + owner + "/" + repo;

        @SuppressWarnings("unchecked")
        Map<String, Object> response = webClient.get()
                .uri(url)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        return response;
    }
}
