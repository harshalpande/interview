import axios, { AxiosInstance } from 'axios';
import type {
  PreparationAccess,
  PreparationAttempt,
  PreparationQuestion,
  PreparationRegistrationRequest,
  PreparationRunResponse,
  PreparationSubmitResponse,
} from '../types/preparation';
import { resolveApiBaseUrl } from '../utils/apiUrls';

class PreparationApiClient {
  private axiosInstance: AxiosInstance;

  constructor() {
    this.axiosInstance = axios.create({
      baseURL: resolveApiBaseUrl(),
      timeout: 30000,
      headers: {
        'Content-Type': 'application/json',
      },
    });
  }

  async register(request: PreparationRegistrationRequest): Promise<PreparationAttempt> {
    const response = await this.axiosInstance.post<PreparationAttempt>('/preparation/register', request, {
      timeout: 60000,
    });
    return response.data;
  }

  async listAttempts(page = 0, size = 20, search = ''): Promise<{ content: PreparationAttempt[]; totalPages: number; totalElements: number; number: number; size: number }> {
    const params = new URLSearchParams({
      page: String(page),
      size: String(size),
      sort: 'createdAt,desc',
    });
    if (search.trim()) {
      params.set('search', search.trim());
    }
    const response = await this.axiosInstance.get(`/preparation/attempts?${params.toString()}`);
    return response.data;
  }

  async resendOtp(attemptId: string): Promise<PreparationAttempt> {
    const response = await this.axiosInstance.post<PreparationAttempt>(`/preparation/attempts/${attemptId}/resend-otp`);
    return response.data;
  }

  async getAccess(token: string): Promise<PreparationAccess> {
    const response = await this.axiosInstance.get<PreparationAccess>(`/preparation/access/${token}`);
    return response.data;
  }

  async acceptDisclaimer(token: string): Promise<PreparationAccess> {
    const response = await this.axiosInstance.post<PreparationAccess>(`/preparation/access/${token}/disclaimer`);
    return response.data;
  }

  async verifyOtp(token: string, otp: string): Promise<PreparationAccess> {
    const response = await this.axiosInstance.post<PreparationAccess>(`/preparation/access/${token}/verify-otp`, { otp });
    return response.data;
  }

  async expireAttempt(token: string): Promise<PreparationAccess> {
    const response = await this.axiosInstance.post<PreparationAccess>(`/preparation/access/${token}/expire`);
    return response.data;
  }

  async currentQuestion(token: string): Promise<PreparationQuestion> {
    const response = await this.axiosInstance.get<PreparationQuestion>(`/preparation/access/${token}/question`, {
      timeout: 180000,
    });
    return response.data;
  }

  async run(token: string, sourceCode: string): Promise<PreparationRunResponse> {
    const response = await this.axiosInstance.post<PreparationRunResponse>(`/preparation/access/${token}/run`, { sourceCode });
    return response.data;
  }

  async submit(token: string, sourceCode: string): Promise<PreparationSubmitResponse> {
    const response = await this.axiosInstance.post<PreparationSubmitResponse>(`/preparation/access/${token}/submit`, { sourceCode }, {
      timeout: 180000,
    });
    return response.data;
  }
}

export const preparationApi = new PreparationApiClient();
