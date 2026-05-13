import type { ExecutionLanguage, ExecuteResponse } from './api';
import type { TechnologySkill } from './session';

export type PreparationAttemptStatus = 'OTP_PENDING' | 'ACTIVE' | 'COMPLETED' | 'EXPIRED' | 'FAILED';
export type QuestionStarterType = 'BUG_FIX' | 'IMPLEMENTATION' | 'EXTENSION';

export interface PreparationRegistrationRequest {
  candidateName: string;
  email: string;
  technology: TechnologySkill;
  yearsOfExperience: number;
  targetRole: string;
}

export interface PreparationAttempt {
  id: string;
  candidateName: string;
  email: string;
  technology: TechnologySkill;
  yearsOfExperience: number;
  experienceBand: string;
  targetRole: string;
  status: PreparationAttemptStatus;
  linkExpiresAt?: string;
  otpIssuedAt?: string;
  otpExpiresAt?: string;
  otpVerifiedAt?: string;
  disclaimerAcceptedAt?: string;
  attemptExpiresAt?: string;
  remainingAttemptSeconds?: number;
  remainingOtpResends?: number;
  questionStartedAt?: string;
  questionExpiresAt?: string;
  currentQuestionId?: string;
  currentSeriesId?: string;
  currentSequenceNumber?: number;
  completedAt?: string;
  createdAt?: string;
  updatedAt?: string;
  message?: string;
}

export interface PreparationAccess {
  attemptId: string;
  candidateName: string;
  email: string;
  yearsOfExperience?: number;
  experienceBand?: string;
  status: PreparationAttemptStatus;
  otpVerified: boolean;
  disclaimerAccepted: boolean;
  disclaimerAcceptedAt?: string;
  linkExpiresAt?: string;
  otpExpiresAt?: string;
  attemptExpiresAt?: string;
  remainingAttemptSeconds?: number;
  remainingOtpResends?: number;
  message?: string;
}

export interface PreparationQuestion {
  attemptId: string;
  questionId: string;
  seriesId: string;
  technology: TechnologySkill;
  title: string;
  displayName: string;
  filePath: string;
  problemStatement: string;
  starterCode: string;
  sequenceNumber: number;
  banyanLevel: number;
  starterType: QuestionStarterType;
  experienceBand: string;
  questionStartedAt?: string;
  questionExpiresAt?: string;
  remainingSeconds: number;
  attemptExpiresAt?: string;
  remainingAttemptSeconds: number;
  executeAttemptCount?: number;
  attemptEnded: boolean;
  message?: string;
  concepts?: string[];
}

export interface PreparationRunResponse {
  execution?: ExecuteResponse;
  passed: boolean;
  attemptEnded: boolean;
  message: string;
  question?: PreparationQuestion;
}

export interface PreparationSubmitResponse {
  execution?: ExecuteResponse;
  passed: boolean;
  attemptEnded: boolean;
  message: string;
  nextQuestion?: PreparationQuestion;
}

export function preparationLanguage(technology: TechnologySkill): ExecutionLanguage {
  return technology === 'PYTHON' ? 'PYTHON' : 'JAVA';
}
