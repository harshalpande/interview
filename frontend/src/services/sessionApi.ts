import axios, { AxiosInstance } from 'axios';
import type { 
  AcceptDisclaimerRequest,
  AccessLinkResponse,
  AccessVerificationResponse,
  ActivityEvent,
  ActivityEventRequest,
  CreateSessionRequest, 
  DisconnectParticipantRequest,
  EndSessionRequest,
  FeedbackRating,
  HeartbeatRequest,
  IdentityCaptureRequest,
  ResumeApprovalRequest,
  ResumeRequest,
  ResumeResponse,
  SessionResponse, 
  FeedbackRequest,
  TechnologySkill,
  VerifyOtpRequest
} from '../types/session';
import { resolveApiBaseUrl } from '../utils/apiUrls';

const SECURE_ACCESS_EMAIL_TIMEOUT_MS = 60000;

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

  async startSecureSession(id: string): Promise<SessionResponse> {
    try {
      const response = await this.axiosInstance.post<SessionResponse>(`/sessions/${id}/start-session`, undefined, {
        timeout: SECURE_ACCESS_EMAIL_TIMEOUT_MS,
      });
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
    search: string = '',
    filters?: {
      from?: string;
      to?: string;
      technologies?: TechnologySkill[];
      ratings?: FeedbackRating[];
    }
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
      if (filters?.from) {
        params.set('from', filters.from);
      }
      if (filters?.to) {
        params.set('to', filters.to);
      }
      filters?.technologies?.forEach((technology) => params.append('technologies', technology));
      filters?.ratings?.forEach((rating) => params.append('ratings', rating));
      const response = await this.axiosInstance.get<any>(`/sessions?${params.toString()}`);
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  async exportSessionsCsv(
    sortBy: 'createdAt' | 'status' | 'summary' = 'createdAt',
    direction: 'asc' | 'desc' = 'desc',
    search: string = '',
    filters?: {
      from?: string;
      to?: string;
      technologies?: TechnologySkill[];
      ratings?: FeedbackRating[];
    }
  ): Promise<{ blob: Blob; filename: string }> {
    try {
      const params = new URLSearchParams({
        sortBy,
        direction,
      });
      if (search.trim()) {
        params.set('search', search.trim());
      }
      if (filters?.from) {
        params.set('from', filters.from);
      }
      if (filters?.to) {
        params.set('to', filters.to);
      }
      filters?.technologies?.forEach((technology) => params.append('technologies', technology));
      filters?.ratings?.forEach((rating) => params.append('ratings', rating));

      const response = await this.axiosInstance.get(`/sessions/export?${params.toString()}`, {
        responseType: 'blob',
      });

      const disposition = response.headers['content-disposition'] as string | undefined;
      const filenameMatch = disposition?.match(/filename="?([^"]+)"?/i);
      return {
        blob: response.data,
        filename: filenameMatch?.[1] || 'sessions-export.csv',
      };
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

  async getAccessLink(token: string): Promise<AccessLinkResponse> {
    try {
      const response = await this.axiosInstance.get<AccessLinkResponse>(`/sessions/access/${token}`);
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  async verifyAccessOtp(token: string, request: VerifyOtpRequest): Promise<AccessVerificationResponse> {
    try {
      const response = await this.axiosInstance.post<AccessVerificationResponse>(`/sessions/access/${token}/verify-otp`, request);
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  async retryAccessOtp(token: string): Promise<AccessVerificationResponse> {
    try {
      const response = await this.axiosInstance.post<AccessVerificationResponse>(`/sessions/access/${token}/retry`);
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  async acceptAccessDisclaimer(token: string): Promise<SessionResponse> {
    try {
      const response = await this.axiosInstance.post<SessionResponse>(`/sessions/access/${token}/disclaimer`);
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  async requestResume(id: string, request: ResumeRequest): Promise<ResumeResponse> {
    try {
      const response = await this.axiosInstance.post<ResumeResponse>(`/sessions/${id}/resume`, request);
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  async approveResume(id: string, request: ResumeApprovalRequest): Promise<SessionResponse> {
    try {
      const response = await this.axiosInstance.post<SessionResponse>(`/sessions/${id}/resume/approve`, request);
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  async rejectResume(id: string, request: ResumeApprovalRequest): Promise<SessionResponse> {
    try {
      const response = await this.axiosInstance.post<SessionResponse>(`/sessions/${id}/resume/reject`, request);
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  async heartbeat(id: string, request: HeartbeatRequest): Promise<SessionResponse> {
    try {
      const response = await this.axiosInstance.post<SessionResponse>(`/sessions/${id}/presence`, request);
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  async disconnectParticipant(id: string, request: DisconnectParticipantRequest): Promise<SessionResponse> {
    try {
      const response = await this.axiosInstance.post<SessionResponse>(`/sessions/${id}/disconnect`, request);
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

  async submitIdentityCapture(id: string, request: IdentityCaptureRequest): Promise<SessionResponse> {
    try {
      const formData = new FormData();
      formData.append('role', request.role);
      formData.append('status', request.status);
      if (request.failureReason) {
        formData.append('failureReason', request.failureReason);
      }
      if (request.image) {
        formData.append('image', request.image, request.filename || 'identity-snapshot.jpg');
      }

      const response = await this.axiosInstance.post<SessionResponse>(`/sessions/${id}/identity-capture`, formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  getIdentityCaptureImageUrl(id: string, role: 'INTERVIEWER' | 'INTERVIEWEE') {
    return `${resolveApiBaseUrl()}/sessions/${id}/identity-capture/${role}`;
  }

  private handleError(error: any): Error {
    if (axios.isAxiosError(error)) {
      if (error.response) {
        const message = (error.response.data as any)?.message || `Server error: ${error.response.status}`;
        return new Error(message);
      }
      if (error.code === 'ECONNABORTED') {
        return new Error('The request took longer than expected. Please refresh the dashboard; passcodes may still have been delivered.');
      }
      if (error.request) {
        return new Error('No response from server. Is the backend running?');
      }
    }
    return error instanceof Error ? error : new Error('Unknown error occurred');
  }
}

export const sessionApi = new SessionApiClient();

