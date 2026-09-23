package com.repomind.dto;

import com.repomind.model.Repository.IndexStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexingStatusDto {

    private Long repoId;
    private IndexStatus status;
    private Integer totalFiles;
    private Integer indexedFiles;
    private Integer progressPercent;
    private String message;
}
