/**
 * API Request and Response types
 */

import type { EditableCodeFile } from './session';

export type ExecutionLanguage = 'JAVA' | 'PYTHON' | 'ANGULAR' | 'REACT';
export type ExecutionPriority = 'REGISTERED_INTERVIEW' | 'PREPARATION';

export interface CompileRequest {
  sourceCode: string;
  language?: ExecutionLanguage;
}

export interface CompileResponse {
  success: boolean;
  compileErrors?: string[];
  compileWarnings?: string[];
  message: string;
}

export interface ExecuteRequest {
  sourceCode: string;
  sessionId?: string;
  language?: ExecutionLanguage;
  codeFiles?: EditableCodeFile[];
  activeFilePath?: string;
  timeoutMs?: number;
  memoryLimitMb?: number;
  livePreviewMode?: boolean;
  executionPriority?: ExecutionPriority;
}

export interface ExecuteResponse {
  success: boolean;
  stdout?: string;
  stderr?: string;
  compileErrors?: string[];
  exitCode?: number;
  executionTimeMs?: number;
  message: string;
  previewUrl?: string | null;
}
