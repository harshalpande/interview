import axios, { AxiosInstance } from 'axios';
import { CompileRequest, CompileResponse, ExecuteRequest, ExecuteResponse } from '../types/api';

const API_BASE_URL = process.env.REACT_APP_API_URL || '/api';

/**
 * API client for communicating with the Java compiler backend.
 * Handles all HTTP requests to the backend REST API.
 */
class CompilerApiClient {
  private axiosInstance: AxiosInstance;

  constructor() {
    this.axiosInstance = axios.create({
      baseURL: API_BASE_URL,
      timeout: 30000, // 30 second timeout
      headers: {
        'Content-Type': 'application/json',
      },
    });
  }

  /**
   * Compile Java source code without execution.
   */
  async compile(request: CompileRequest): Promise<CompileResponse> {
    try {
      const response = await this.axiosInstance.post<CompileResponse>(
        '/compile',
        request
      );
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * Compile and execute Java source code.
   */
  async execute(request: ExecuteRequest): Promise<ExecuteResponse> {
    try {
      // Set default values
      const executeRequest = {
        sourceCode: request.sourceCode,
        timeoutMs: request.timeoutMs || 5000,
        memoryLimitMb: request.memoryLimitMb || 512,
      };

      const response = await this.axiosInstance.post<ExecuteResponse>(
        '/compile/run',
        executeRequest
      );
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * Health check endpoint to verify backend is running.
   */
  async health(): Promise<string> {
    try {
      const response = await this.axiosInstance.get<string>('/compile/health');
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * Handle axios errors and convert to user-friendly messages.
   */
  private handleError(error: any): Error {
    if (axios.isAxiosError(error)) {
      if (error.response) {
        // Server responded with error status
        const message = error.response.data?.message || 
                       `Server error: ${error.response.status}`;
        return new Error(message);
      } else if (error.request) {
        // Request made but no response received
        return new Error('No response from server. Is the backend running?');
      }
    }
    // Network error or other issue
    return error instanceof Error ? error : new Error('Unknown error occurred');
  }
}

// Export singleton instance
export const compilerApi = new CompilerApiClient();

// Re-export sessionApi for convenience
export * from './sessionApi';
