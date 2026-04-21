package com.altimetrik.interview.runner;

import com.altimetrik.interview.dto.EditableCodeFileDto;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface PersistentFrontendRunner extends FrontendRunner {

    Path createWorkspace(List<EditableCodeFileDto> files) throws IOException;

    void patchWorkspaceFiles(Path workspaceDir, List<EditableCodeFileDto> files) throws IOException;

    com.altimetrik.interview.runner.model.FrontendBuildResult buildInWorkspace(Path workspaceDir, long timeoutMs);

    void destroyWorkspace(Path workspaceDir);

    Process startWatchProcess(Path workspaceDir) throws IOException;

    Path resolveWorkspacePreviewRoot(Path workspaceDir) throws IOException;
}
