/**
 * API Request and Response types
 */

export type ExecutionLanguage = 'JAVA' | 'PYTHON';

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
  language?: ExecutionLanguage;
  timeoutMs?: number;
  memoryLimitMb?: number;
}

export interface ExecuteResponse {
  success: boolean;
  stdout?: string;
  stderr?: string;
  compileErrors?: string[];
  exitCode?: number;
  executionTimeMs?: number;
  message: string;
}
