package com.repomind.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepoDto {

    private Long id;
    private Long githubRepoId;
    private String name;
    private String owner;
    private String fullName;
    private String description;
    private String language;
    private String defaultBranch;
    private Integer starCount;
    private Integer forkCount;
    private Boolean isPrivate;
    private String indexStatus;
    private Integer totalFiles;
    private Integer indexedFiles;
}
