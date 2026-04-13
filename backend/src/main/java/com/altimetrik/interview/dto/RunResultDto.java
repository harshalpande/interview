package com.altimetrik.interview.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class RunResultDto {
    private OffsetDateTime compiledAt;
    private String stdout;
    private String stderr;
    private Integer exitStatus;
}
