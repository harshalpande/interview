package com.altimetrik.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatchFrontendWorkspaceFilesRequest {
    private List<EditableCodeFileDto> files;
}
