export type ParticipantRole = 'INTERVIEWER' | 'INTERVIEWEE';
export type SessionStatus = 'CREATED' | 'WAITING_JOIN' | 'ACTIVE' | 'ENDED' | 'EXPIRED';
export type FeedbackRating = 'EXCELLENT' | 'GOOD' | 'FAIR' | 'BAD';

export interface Participant {
  role: ParticipantRole;
  name: string;
  email: string;
  disclaimerAcceptedAt?: string | null;
  joinedAt?: string | null;
}

export interface JoinInfo {
  token: string;
  joinUrl: string;
  expiresAt: string;
}

export interface RunResult {
  compiledAt: string;
  stdout: string;
  stderr: string;
  exitStatus: number;
}

export interface Feedback {
  rating: FeedbackRating;
  comments: string;
  recommendation: boolean;
  submittedAt?: string | null;
}

export interface SessionResponse {
  id: string;
  status: SessionStatus;
  createdAt: string;
  startedAt?: string | null;
  endedAt?: string | null;
  durationSec: number;
  remainingSec: number;
  extensionUsed: boolean;
  readOnly: boolean;
  participants: Participant[];
  latestCode?: string | null;
  codeVersion: number;
  finalRunResult?: RunResult | null;
  feedback?: Feedback | null;
  joinInfo?: JoinInfo | null;
  summary?: string | null;
}

export interface SessionSocketMessage {
  type: 'CODE_UPDATE' | 'SESSION_STATE' | 'SESSION_START' | 'SESSION_END' | 'SESSION_EXTEND' | 'USER_JOINED' | 'TIMER_TICK';
  sessionId: string;
  version?: number;
  code?: string;
  timeLeft?: number;
  session?: SessionResponse;
  message?: string;
}

export interface CreateSessionRequest {
  interviewerName: string;
  interviewerEmail: string;
  intervieweeName: string;
  intervieweeEmail: string;
}

export interface AcceptDisclaimerRequest {
  role: ParticipantRole;
}

export interface JoinSessionRequest {
  name: string;
  email: string;
}

export interface ValidateTokenResponse {
  valid: boolean;
  sessionId: string;
  role: ParticipantRole;
  expiresAt: string;
  message: string;
}

export interface FeedbackRequest {
  rating: FeedbackRating;
  comments: string;
  recommendation: boolean;
}

export interface EndSessionRequest {
  finalCode: string;
}
