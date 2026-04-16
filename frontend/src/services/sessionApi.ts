import axios, { AxiosInstance } from 'axios';
import type { 
  AcceptDisclaimerRequest,
  ActivityEvent,
  ActivityEventRequest,
  CreateSessionRequest, 
  EndSessionRequest,
  JoinSessionRequest,
  SessionResponse, 
  FeedbackRequest,
  ValidateTokenResponse 
} from '../types/session';
import { resolveApiBaseUrl } from '../utils/apiUrls';

class SessionApiClient {
  private axiosInstance: AxiosInstance;

  constructor() {
    this.axiosInstance = axios.create({
      baseURL: resolveApiBaseUrl(),
      timeout: 10000,
      headers: {
        'Content-Type': 'application/json',
      },
    });
  }

  async createSession(request: CreateSessionRequest): Promise<SessionResponse> {
    try {
      const response = await this.axiosInstance.post<SessionResponse>('/sessions', request);
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  async getSession(id: string): Promise<SessionResponse> {
    try {
      const response = await this.axiosInstance.get<SessionResponse>(`/sessions/${id}`);
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  async listSessions(
    page: number = 0,
    size: number = 20,
    sortBy: 'createdAt' | 'status' | 'summary' = 'createdAt',
    direction: 'asc' | 'desc' = 'desc',
    search: string = ''
  ): Promise<{ content: SessionResponse[]; totalPages: number; totalElements: number; number: number; size: number; }> {
    try {
      const params = new URLSearchParams({
        page: page.toString(),
        size: size.toString(),
        sort: `${sortBy},${direction}`,
      });
      if (search.trim()) {
        params.set('search', search.trim());
      }
      const response = await this.axiosInstance.get<any>(`/sessions?${params.toString()}`);
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  async acceptDisclaimer(id: string, request: AcceptDisclaimerRequest): Promise<SessionResponse> {
    try {
      const response = await this.axiosInstance.post<SessionResponse>(`/sessions/${id}/disclaimer`, request);
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  async validateToken(token: string): Promise<ValidateTokenResponse> {
    try {
      const response = await this.axiosInstance.get<ValidateTokenResponse>(`/sessions/join/${token}`);
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  async joinSession(token: string, request: JoinSessionRequest): Promise<SessionResponse> {
    try {
      const response = await this.axiosInstance.post<SessionResponse>(`/sessions/join/${token}`, request);
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  async startSession(id: string): Promise<SessionResponse> {
    try {
      const response = await this.axiosInstance.post<SessionResponse>(`/sessions/${id}/start`);
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  async extendSession(id: string): Promise<SessionResponse> {
    try {
      const response = await this.axiosInstance.post<SessionResponse>(`/sessions/${id}/extend`);
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  async submitFeedback(id: string, feedback: FeedbackRequest): Promise<SessionResponse> {
    try {
      const response = await this.axiosInstance.post<SessionResponse>(`/sessions/${id}/feedback`, feedback);
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  async endSession(id: string, request: EndSessionRequest): Promise<SessionResponse> {
    try {
      const response = await this.axiosInstance.post<SessionResponse>(`/sessions/${id}/end`, request);
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  async recordActivityEvent(id: string, request: ActivityEventRequest): Promise<ActivityEvent> {
    try {
      const response = await this.axiosInstance.post<ActivityEvent>(`/sessions/${id}/activity-events`, request);
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  private handleError(error: any): Error {
    if (axios.isAxiosError(error)) {
      if (error.response) {
        const message = (error.response.data as any)?.message || `Server error: ${error.response.status}`;
        return new Error(message);
      }
      if (error.request) {
        return new Error('No response from server. Is the backend running?');
      }
    }
    return error instanceof Error ? error : new Error('Unknown error occurred');
  }
}

export const sessionApi = new SessionApiClient();

