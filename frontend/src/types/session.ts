export type ParticipantRole = 'INTERVIEWER' | 'INTERVIEWEE';
export type SessionStatus =
  | 'REGISTERED'
  | 'AUTH_IN_PROGRESS'
  | 'READY_TO_START'
  | 'ACTIVE'
  | 'ENDED'
  | 'EXPIRED'
  | 'AUTH_FAILED';
export type ParticipantConnectionStatus = 'DISCONNECTED' | 'CONNECTED' | 'AWAITING_APPROVAL';
export type FeedbackRating = 'EXCELLENT' | 'GOOD' | 'FAIR' | 'BAD' | 'DISQUALIFIED';
export type AvMode = 'IN_APP' | 'EXTERNAL';
export type ActivityEventSeverity = 'INFO' | 'WARNING' | 'SUSPICIOUS';
export type ActivityEventType =
  | 'TAB_HIDDEN'
  | 'PASTE_IN_EDITOR'
  | 'EXTERNAL_DROP_BLOCKED'
  | 'CAMERA_STREAM_LOST'
  | 'MICROPHONE_DISABLED_MANUALLY'
  | 'CAMERA_DISABLED_MANUALLY'
  | 'NO_FACE_DETECTED'
  | 'MULTIPLE_FACES_DETECTED';
export type TechnologySkill = 'JAVA' | 'PYTHON' | 'ANGULAR' | 'REACT' | 'SQL';
export type RecommendationDecision = 'YES' | 'NO' | 'REEVALUATION';
export type IdentityCaptureStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'SKIPPED';
export type IdentityCaptureFailureReason = 'NO_CAMERA' | 'PERMISSION_DENIED' | 'CAMERA_IN_USE' | 'UNSUPPORTED' | 'DEVICE_ERROR' | 'USER_SKIPPED' | 'UNKNOWN';
export type WebRtcSignalType = 'READY' | 'OFFER' | 'ANSWER' | 'ICE_CANDIDATE' | 'MEDIA_STATE';
export type ResumeReason =
  | 'CONNECTION_RECOVERY'
  | 'BACKEND_REDEPLOY'
  | 'FRONTEND_REDEPLOY'
  | 'NETWORK_CHANGE'
  | 'DEVICE_CHANGE'
  | 'TAB_OR_BROWSER_CLOSED'
  | 'MANUAL_RESUME';
export type FrontendWorkspaceStatus = 'READY' | 'FAILED' | 'STOPPED';
export type ParticipantAccessStatus = 'NOT_STARTED' | 'OTP_PENDING' | 'OTP_VERIFIED' | 'COMPLETED' | 'FAILED';

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
  connectionStatus?: ParticipantConnectionStatus | null;
  deviceId?: string | null;
  lastKnownIp?: string | null;
  lastSeenAt?: string | null;
  disconnectedAt?: string | null;
  resumeRequestedAt?: string | null;
  resumeApprovedAt?: string | null;
  resumeRejectedAt?: string | null;
  resumeCount?: number | null;
  pendingResumeReason?: ResumeReason | null;
  awaitingResumeApproval?: boolean | null;
}

export interface RunResult {
  compiledAt: string;
  filePath?: string | null;
  displayName?: string | null;
  sourceSnapshot?: string | null;
  stdout: string;
  stderr: string;
  exitStatus: number;
  executionTimeMs?: number | null;
}

export interface FrontendWorkspace {
  sessionId: string;
  workspaceId: string;
  technology: TechnologySkill;
  status: FrontendWorkspaceStatus;
  previewUrl?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  lastHeartbeatAt?: string | null;
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
  severity?: ActivityEventSeverity;
  detail: string;
  candidateMessage?: string | null;
  durationMs?: number | null;
  occurrenceCount?: number | null;
  createdAt: string;
}

export interface AuthAuditEvent {
  createdAt: string;
  participantRole?: ParticipantRole | null;
  title: string;
  detail: string;
}

export interface EditableCodeFile {
  path: string;
  displayName: string;
  content: string;
  editable: boolean;
  sortOrder: number;
  enabledForCandidate?: boolean;
  activeQuestion?: boolean;
  submitted?: boolean;
  idealDurationMinutes?: number | null;
  runResult?: RunResult | null;
  changedAfterLastRun?: boolean | null;
}

export interface SessionResponse {
  id: string;
  technology: TechnologySkill;
  avMode: AvMode;
  status: SessionStatus;
  createdAt: string;
  authStartedAt?: string | null;
  readyToStartAt?: string | null;
  authFailedAt?: string | null;
  startedAt?: string | null;
  endedAt?: string | null;
  interruptedAt?: string | null;
  recoveryDeadlineAt?: string | null;
  recoveryRequiredRole?: ParticipantRole | null;
  durationSec: number;
  remainingSec: number;
  extensionUsed: boolean;
  readOnly: boolean;
  participants: Participant[];
  latestCode?: string | null;
  codeFiles?: EditableCodeFile[];
  codeVersion: number;
  finalRunResult?: RunResult | null;
  feedback?: Feedback | null;
  feedbackDraft?: Feedback | null;
  activityEvents?: ActivityEvent[];
  authAuditEvents?: AuthAuditEvent[];
  summary?: string | null;
  frontendWorkspace?: FrontendWorkspace | null;
  finalPreviewUrl?: string | null;
  suspiciousRejected?: boolean;
  suspiciousScenarioKey?: string | null;
  suspiciousActivityReason?: string | null;
  authFailureReason?: string | null;
  expiredReason?: string | null;
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
  cameraEnabled?: boolean | null;
  microphoneEnabled?: boolean | null;
}

export interface CreateSessionRequest {
  interviewerName: string;
  interviewerEmail: string;
  intervieweeName: string;
  intervieweeEmail: string;
  interviewerTimeZone?: string;
  technology: TechnologySkill;
  avMode: AvMode;
}

export interface AcceptDisclaimerRequest {
  role: ParticipantRole;
}

export interface AccessLinkResponse {
  sessionId: string;
  role: ParticipantRole;
  participantName: string;
  participantEmail: string;
  avMode: AvMode;
  sessionStatus: SessionStatus;
  accessStatus: ParticipantAccessStatus;
  otpExpiresAt?: string | null;
  remainingOtpWindows: number;
  disclaimerAccepted: boolean;
  otpVerified: boolean;
  identityCaptureRequired: boolean;
  identityCaptureComplete: boolean;
  sessionReadyToStart: boolean;
  message: string;
}

export interface VerifyOtpRequest {
  otp: string;
}

export interface AccessVerificationResponse {
  success: boolean;
  sessionReadyToStart: boolean;
  retryAvailable: boolean;
  remainingOtpWindows: number;
  otpExpiresAt?: string | null;
  message: string;
  access: AccessLinkResponse;
  session?: SessionResponse | null;
}

export interface ResumeRequest {
  role: ParticipantRole;
  name: string;
  email: string;
  timeZone?: string;
  deviceId: string;
  reason: ResumeReason;
}

export interface ResumeResponse {
  status: 'APPROVED' | 'PENDING_APPROVAL' | 'REJECTED';
  message: string;
  approvalRequired: boolean;
  session?: SessionResponse | null;
}

export interface ResumeApprovalRequest {
  interviewerName: string;
  interviewerEmail: string;
}

export interface HeartbeatRequest {
  role: ParticipantRole;
  deviceId: string;
}

export interface DisconnectParticipantRequest {
  role: ParticipantRole;
  deviceId: string;
  reason: ResumeReason;
  finalCode?: string;
  codeFiles?: EditableCodeFile[];
}

export interface FeedbackRequest {
  rating: FeedbackRating;
  comments: string;
  recommendationDecision: RecommendationDecision;
}

export interface EndSessionRequest {
  finalCode: string;
  codeFiles?: EditableCodeFile[];
  activeFilePath?: string;
}

export interface ActivityEventRequest {
  participantRole: ParticipantRole;
  eventType: ActivityEventType;
  detail: string;
  durationMs?: number;
}

export interface IdentityCaptureRequest {
  role: ParticipantRole;
  status: IdentityCaptureStatus;
  failureReason?: IdentityCaptureFailureReason;
  image?: Blob;
  filename?: string;
}
