import React from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { preparationApi } from '../services/preparationApi';
import type { PreparationAttempt, PreparationRegistrationRequest } from '../types/preparation';
import { TARGET_ROLES_BY_TECHNOLOGY } from '../constants/targetRoles';
import './Preparation.css';

const DEFAULT_TECHNOLOGY: PreparationRegistrationRequest['technology'] = 'JAVA';
const EXPERIENCE_OPTIONS = [
  { label: '1-3 years', value: 1 },
  { label: '4-6 years', value: 4 },
  { label: '7-9 years', value: 7 },
  { label: '10+ years', value: 10 },
];
const OTP_RESEND_COOLDOWN_SECONDS = 120;

function createInitialForm(): PreparationRegistrationRequest {
  return {
    candidateName: '',
    email: '',
    technology: DEFAULT_TECHNOLOGY,
    yearsOfExperience: EXPERIENCE_OPTIONS[0].value,
    targetRole: TARGET_ROLES_BY_TECHNOLOGY[DEFAULT_TECHNOLOGY]?.[0] || '',
  };
}

export default function PreparationDashboard() {
  const queryClient = useQueryClient();
  const fieldIdPrefix = React.useId().replace(/:/g, '');
  const [search, setSearch] = React.useState('');
  const [form, setForm] = React.useState<PreparationRegistrationRequest>(() => createInitialForm());
  const [message, setMessage] = React.useState('');
  const [resendingAttemptId, setResendingAttemptId] = React.useState<string | null>(null);
  const targetRoleOptions = React.useMemo(
    () => TARGET_ROLES_BY_TECHNOLOGY[form.technology] || TARGET_ROLES_BY_TECHNOLOGY.JAVA,
    [form.technology]
  );

  React.useEffect(() => {
    if (!form.targetRole || !targetRoleOptions.includes(form.targetRole)) {
      setForm((previous) => ({
        ...previous,
        targetRole: targetRoleOptions[0] || '',
      }));
    }
  }, [form.targetRole, targetRoleOptions]);

  React.useEffect(() => {
    if (!message) {
      return;
    }
    const timeoutId = window.setTimeout(() => {
      setMessage('');
    }, 8000);
    return () => window.clearTimeout(timeoutId);
  }, [message]);

  const attemptsQuery = useQuery({
    queryKey: ['preparation-attempts', search],
    queryFn: () => preparationApi.listAttempts(0, 20, search),
  });

  const registerMutation = useMutation({
    mutationFn: (request: PreparationRegistrationRequest) => preparationApi.register(request),
    onSuccess: (attempt) => {
      setMessage(attempt.message || `Access details and one-time passcode have been sent to ${attempt.candidateName} (${attempt.email}).`);
      setForm(createInitialForm());
      queryClient.invalidateQueries({ queryKey: ['preparation-attempts'] });
    },
    onError: (error) => {
      setMessage(error instanceof Error ? error.message : 'Unable to send candidate access.');
    },
  });

  const resendMutation = useMutation({
    mutationFn: (attemptId: string) => preparationApi.resendOtp(attemptId),
    onSuccess: (attempt) => {
      setMessage(attempt.message || `A new one-time passcode has been sent to ${attempt.candidateName} (${attempt.email}).`);
      queryClient.invalidateQueries({ queryKey: ['preparation-attempts'] });
    },
    onError: (error) => {
      setMessage(error instanceof Error ? error.message : 'Unable to resend the one-time passcode.');
    },
    onSettled: () => {
      setResendingAttemptId(null);
    },
  });

  const attempts = attemptsQuery.data?.content || [];

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    setMessage('');
    registerMutation.mutate(form);
  };

  return (
    <div className="preparation-page">
      <section className="preparation-header">
        <div>
          <h2>Preparation Dashboard</h2>
          <p className="page-subtitle">Create candidate practice access and track readiness from one place.</p>
        </div>
      </section>

      <section className="preparation-layout">
        <form className="preparation-panel preparation-form" onSubmit={handleSubmit} autoComplete="new-password">
          <h3>Register Candidate</h3>
          <label>
            Name
            <input
              type="search"
              name={`${fieldIdPrefix}-candidate-display`}
              autoComplete="off"
              autoCorrect="off"
              spellCheck={false}
              data-form-type="other"
              data-lpignore="true"
              value={form.candidateName}
              onChange={(event) => setForm({ ...form, candidateName: event.target.value })}
              required
            />
          </label>
          <label>
            Email
            <input
              type="search"
              inputMode="email"
              name={`${fieldIdPrefix}-candidate-contact`}
              autoComplete="off"
              autoCorrect="off"
              spellCheck={false}
              data-form-type="other"
              data-lpignore="true"
              value={form.email}
              onChange={(event) => setForm({ ...form, email: event.target.value })}
              pattern="^[^\s@]+@[^\s@]+\.[^\s@]+$"
              required
            />
          </label>
          <div className="preparation-form-grid">
            <label>
              Skill
              <select
                value={form.technology}
                onChange={(event) => setForm({
                  ...form,
                  technology: event.target.value as 'JAVA' | 'PYTHON',
                  targetRole: '',
                })}
              >
                <option value="JAVA">Java</option>
                <option value="PYTHON">Python</option>
              </select>
            </label>
            <label>
              Experience
              <select
                name="preparation-years-experience"
                value={form.yearsOfExperience}
                onChange={(event) => setForm({ ...form, yearsOfExperience: Number(event.target.value) })}
                required
              >
                {EXPERIENCE_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>{option.label}</option>
                ))}
              </select>
            </label>
          </div>
          <label>
            Position
            <select
              name="preparation-target-role"
              autoComplete="off"
              value={form.targetRole}
              onChange={(event) => setForm({ ...form, targetRole: event.target.value })}
              required
            >
              {targetRoleOptions.map((roleOption) => (
                <option key={roleOption} value={roleOption}>{roleOption}</option>
              ))}
            </select>
          </label>
          <button className="btn btn-primary" type="submit" disabled={registerMutation.isPending}>
            {registerMutation.isPending ? 'Sending...' : 'Send Candidate Access'}
          </button>
          {message && <div className="preparation-message" role="status">{message}</div>}
        </form>

        <section className="preparation-panel preparation-list">
          <div className="preparation-list-header">
            <h3>Candidates</h3>
            <input
              type="search"
              name={`${fieldIdPrefix}-candidate-lookup`}
              autoComplete="off"
              autoCorrect="off"
              spellCheck={false}
              data-form-type="other"
              data-lpignore="true"
              placeholder="Search name or email"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </div>
          <div className="preparation-table-wrap">
            <table className="preparation-table">
              <thead>
                <tr>
                  <th>Candidate</th>
                  <th>Skill</th>
                  <th>Experience</th>
                  <th>Status</th>
                  <th>Date/Time</th>
                  <th>Expires</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {attempts.map((attempt) => {
                  const remainingResends = attempt.remainingOtpResends ?? 0;
                  const resendWaitSeconds = otpResendWaitSeconds(attempt);
                  const canResend = remainingResends > 0
                    && resendWaitSeconds <= 0
                    && (attempt.status === 'OTP_PENDING' || (attempt.status === 'EXPIRED' && !attempt.currentQuestionId));
                  return (
                    <tr key={attempt.id}>
                      <td>
                        <strong>{attempt.candidateName}</strong>
                        <span>{attempt.email}</span>
                      </td>
                      <td><span className="prep-skill">{formatTechnology(attempt.technology)}</span></td>
                      <td>{formatExperience(attempt.experienceBand)}</td>
                      <td><span className={`prep-status prep-status-${statusClass(attempt)}`}>{formatStatus(attempt)}</span></td>
                      <td>{formatDateTimeForStatus(attempt)}</td>
                      <td>{formatExpiry(attempt)}</td>
                      <td>
                        <button
                          type="button"
                          className="preparation-link-button"
                          onClick={() => {
                            setMessage('');
                            setResendingAttemptId(attempt.id);
                            resendMutation.mutate(attempt.id);
                          }}
                          disabled={!canResend || resendingAttemptId === attempt.id}
                          title={resendWaitSeconds > 0 ? `Resend available in ${resendWaitSeconds} seconds` : canResend ? `${remainingResends} resend${remainingResends === 1 ? '' : 's'} available` : 'No resend available'}
                        >
                          {resendingAttemptId === attempt.id ? 'Sending...' : resendWaitSeconds > 0 ? 'Resend Soon' : 'Resend OTP'}
                        </button>
                      </td>
                    </tr>
                  );
                })}
                {!attempts.length && (
                  <tr>
                    <td colSpan={7} className="preparation-empty">{attemptsQuery.isLoading ? 'Loading candidates...' : 'No candidates found.'}</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>
      </section>
    </div>
  );
}

function formatStatus(attempt: PreparationAttempt) {
  if (attempt.status === 'OTP_PENDING') {
    return 'Passcode Sent';
  }
  if (attempt.status === 'ACTIVE') {
    return 'In Progress';
  }
  if (attempt.status === 'EXPIRED') {
    return attempt.currentQuestionId ? 'Ended' : 'Access Expired';
  }
  if (attempt.status === 'COMPLETED') {
    return 'Completed';
  }
  if (attempt.status === 'FAILED') {
    return 'Closed';
  }
  return 'Unavailable';
}

function statusClass(attempt: PreparationAttempt) {
  if (attempt.status === 'EXPIRED' && attempt.currentQuestionId) {
    return 'ended';
  }
  return attempt.status.toLowerCase();
}

function formatDateTimeForStatus(attempt: PreparationAttempt) {
  if (attempt.status === 'ACTIVE') {
    return formatDate(attempt.questionStartedAt || attempt.otpVerifiedAt || attempt.updatedAt || attempt.createdAt);
  }
  if (attempt.status === 'EXPIRED' && attempt.currentQuestionId) {
    return formatDate(attempt.completedAt || attempt.updatedAt || attempt.questionStartedAt || attempt.createdAt);
  }
  if (attempt.status === 'COMPLETED' || attempt.status === 'FAILED') {
    return formatDate(attempt.completedAt || attempt.updatedAt || attempt.createdAt);
  }
  if (attempt.status === 'OTP_PENDING') {
    return formatDate(attempt.otpIssuedAt || attempt.createdAt || attempt.updatedAt);
  }
  return formatDate(attempt.updatedAt || attempt.createdAt);
}

function formatExpiry(attempt: PreparationAttempt) {
  if (attempt.otpVerifiedAt || attempt.currentQuestionId) {
    return '';
  }
  return formatDate(attempt.linkExpiresAt);
}

function formatDate(value?: string) {
  if (!value) {
    return '-';
  }
  return new Date(value).toLocaleString(undefined, {
    month: 'short',
    day: '2-digit',
    hour: 'numeric',
    minute: '2-digit',
  });
}

function formatExperience(experienceBand: string) {
  return experienceBand ? `${experienceBand} yrs` : '-';
}

function formatTechnology(technology: string) {
  return technology.charAt(0) + technology.slice(1).toLowerCase();
}

function otpResendWaitSeconds(attempt: PreparationAttempt) {
  if (!attempt.otpIssuedAt) {
    return 0;
  }
  const issuedAt = new Date(attempt.otpIssuedAt).getTime();
  if (Number.isNaN(issuedAt)) {
    return 0;
  }
  const availableAt = issuedAt + OTP_RESEND_COOLDOWN_SECONDS * 1000;
  return Math.max(0, Math.ceil((availableAt - Date.now()) / 1000));
}
