/**
 * API Request and Response types
 */

export interface CompileRequest {
  sourceCode: string;
}

export interface CompileResponse {
  success: boolean;
  compileErrors?: string[];
  compileWarnings?: string[];
  message: string;
}

export interface ExecuteRequest {
  sourceCode: string;
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
