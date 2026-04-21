package com.altimetrik.interview.dto;

import lombok.Data;

import java.util.List;

@Data
public class EndSessionRequest {
    private String finalCode;
    private List<EditableCodeFileDto> codeFiles;
}
