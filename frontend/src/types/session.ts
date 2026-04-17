export type ParticipantRole = 'INTERVIEWER' | 'INTERVIEWEE';
export type SessionStatus = 'CREATED' | 'WAITING_JOIN' | 'ACTIVE' | 'ENDED' | 'EXPIRED';
export type FeedbackRating = 'EXCELLENT' | 'GOOD' | 'FAIR' | 'BAD';
export type ActivityEventType =
  | 'TAB_HIDDEN'
  | 'PASTE_IN_EDITOR'
  | 'EXTERNAL_DROP_BLOCKED'
  | 'CAMERA_STREAM_LOST'
  | 'NO_FACE_DETECTED'
  | 'MULTIPLE_FACES_DETECTED';
export type TechnologySkill = 'JAVA' | 'PYTHON' | 'ANGULAR' | 'REACT' | 'SQL';
export type RecommendationDecision = 'YES' | 'NO' | 'REEVALUATION';
export type IdentityCaptureStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'SKIPPED';
export type IdentityCaptureFailureReason = 'NO_CAMERA' | 'PERMISSION_DENIED' | 'CAMERA_IN_USE' | 'UNSUPPORTED' | 'DEVICE_ERROR' | 'USER_SKIPPED' | 'UNKNOWN';
export type WebRtcSignalType = 'READY' | 'OFFER' | 'ANSWER' | 'ICE_CANDIDATE';

export interface Participant {
  role: ParticipantRole;
  name: string;
  email: string;
  timeZone?: string | null;
  identityCaptureStatus?: IdentityCaptureStatus | null;
  identityCaptureFailureReason?: IdentityCaptureFailureReason | null;
  identitySnapshotPath?: string | null;
  identitySnapshotCapturedAt?: string | null;
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
  recommendationDecision: RecommendationDecision;
  submittedAt?: string | null;
}

export interface ActivityEvent {
  id: string;
  participantRole: ParticipantRole;
  eventType: ActivityEventType;
  detail: string;
  createdAt: string;
}

export interface SessionResponse {
  id: string;
  technology: TechnologySkill;
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
  activityEvents?: ActivityEvent[];
  joinInfo?: JoinInfo | null;
  summary?: string | null;
}

export interface SessionSocketMessage {
  type: 'CODE_UPDATE' | 'SESSION_STATE' | 'SESSION_START' | 'SESSION_END' | 'SESSION_EXTEND' | 'USER_JOINED' | 'TIMER_TICK' | 'ACTIVITY_EVENT' | 'WEBRTC_SIGNAL';
  sessionId: string;
  version?: number;
  code?: string;
  timeLeft?: number;
  session?: SessionResponse;
  activityEvent?: ActivityEvent;
  message?: string;
  signalType?: WebRtcSignalType;
  senderRole?: ParticipantRole;
  targetRole?: ParticipantRole;
  sdp?: string;
  candidate?: string;
  sdpMid?: string | null;
  sdpMLineIndex?: number | null;
}

export interface CreateSessionRequest {
  interviewerName: string;
  interviewerEmail: string;
  intervieweeName: string;
  intervieweeEmail: string;
  interviewerTimeZone?: string;
  technology: TechnologySkill;
}

export interface AcceptDisclaimerRequest {
  role: ParticipantRole;
}

export interface JoinSessionRequest {
  name: string;
  email: string;
  timeZone?: string;
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
  recommendationDecision: RecommendationDecision;
}

export interface EndSessionRequest {
  finalCode: string;
}

export interface ActivityEventRequest {
  participantRole: ParticipantRole;
  eventType: ActivityEventType;
  detail: string;
}

export interface IdentityCaptureRequest {
  role: ParticipantRole;
  status: IdentityCaptureStatus;
  failureReason?: IdentityCaptureFailureReason;
  image?: Blob;
  filename?: string;
}
