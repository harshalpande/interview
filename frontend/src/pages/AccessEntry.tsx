import React from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { Button } from '../components/Button';
import { sessionApi } from '../services/sessionApi';
import { useSessionStore } from '../stores/sessionStore';
import { getDisclaimerContent } from './Disclaimer';
import type { AccessVerificationResponse } from '../types/session';
import './Disclaimer.css';

const AccessEntry: React.FC = () => {
  const OTP_LENGTH = 5;
  const { token } = useParams<{ token: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const setRole = useSessionStore((state) => state.setRole);
  const setSession = useSessionStore((state) => state.setSession);
  const currentSession = useSessionStore((state) => state.currentSession);
  const [accepted, setAccepted] = React.useState(false);
  const [otpChars, setOtpChars] = React.useState<string[]>(() => Array.from({ length: OTP_LENGTH }, () => ''));
  const [error, setError] = React.useState('');
  const [notice, setNotice] = React.useState('');
  const [now, setNow] = React.useState(() => Date.now());
  const otpInputRefs = React.useRef<Array<HTMLInputElement | null>>([]);
  const otp = otpChars.join('');

  const { data: access, isLoading, error: accessError, refetch } = useQuery({
    queryKey: ['access-link', token],
    queryFn: () => sessionApi.getAccessLink(token!),
    enabled: !!token,
    refetchInterval: false,
  });

  React.useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  React.useEffect(() => {
    if (!access) {
      return;
    }
    if (access.role === 'INTERVIEWER') {
      setRole('interviewer');
    } else {
      setRole('interviewee');
    }
    setAccepted(Boolean(access.disclaimerAccepted));
  }, [access, setRole]);

  const handleSessionAdvance = React.useCallback(
    async (response: AccessVerificationResponse) => {
      if (!access) {
        return;
      }

      const resolvedSession = response.session ?? currentSession ?? await sessionApi.getSession(access.sessionId);
      setSession(resolvedSession);
      if (access.role === 'INTERVIEWEE') {
        if (access.identityCaptureRequired && !access.identityCaptureComplete) {
          navigate(`/java/identity-capture/${resolvedSession.id}`);
          return;
        }
        navigate(`/java/session/${resolvedSession.id}?role=interviewee`);
        return;
      }

      navigate(`/java/session/${resolvedSession.id}?role=interviewer`);
    },
    [access, currentSession, navigate, setSession]
  );

  const disclaimerMutation = useMutation({
    mutationFn: () => sessionApi.acceptAccessDisclaimer(token!),
    onSuccess: async (session) => {
      setSession(session);
      setAccepted(true);
      setNotice('Disclaimer accepted. Enter the passcode sent to your email.');
      await refetch();
    },
    onError: (mutationError) => {
      setError(mutationError instanceof Error ? mutationError.message : 'Unable to save disclaimer.');
    },
  });

  const verifyOtpMutation = useMutation({
    mutationFn: () => sessionApi.verifyAccessOtp(token!, { otp: otp.trim().toUpperCase() }),
    onSuccess: async (response) => {
      setError('');
      setNotice(response.message);
      setOtpChars(Array.from({ length: OTP_LENGTH }, () => ''));
      setSession(response.session || null);
      await queryClient.invalidateQueries({ queryKey: ['sessions'] });
      await refetch();
      if (response.success) {
        void handleSessionAdvance(response);
      }
    },
    onError: async (mutationError) => {
      setError(mutationError instanceof Error ? mutationError.message : 'Unable to verify the passcode.');
      await refetch();
    },
  });

  const retryMutation = useMutation({
    mutationFn: () => sessionApi.retryAccessOtp(token!),
    onSuccess: async (response) => {
      setError('');
      setNotice(response.message);
      setOtpChars(Array.from({ length: OTP_LENGTH }, () => ''));
      setSession(response.session || null);
      await queryClient.invalidateQueries({ queryKey: ['sessions'] });
      await refetch();
    },
    onError: async (mutationError) => {
      setError(mutationError instanceof Error ? mutationError.message : 'Unable to issue a new passcode.');
      await refetch();
    },
  });

  const updateOtpChar = React.useCallback((index: number, nextValue: string) => {
    const normalized = nextValue.toUpperCase().replace(/[^A-Z0-9]/g, '');
    if (!normalized) {
      setOtpChars((previous) => {
        const next = [...previous];
        next[index] = '';
        return next;
      });
      return;
    }

    setOtpChars((previous) => {
      const next = [...previous];
      normalized.slice(0, OTP_LENGTH).split('').forEach((character, offset) => {
        const targetIndex = index + offset;
        if (targetIndex < OTP_LENGTH) {
          next[targetIndex] = character;
        }
      });
      return next;
    });

    const nextIndex = Math.min(index + normalized.length, OTP_LENGTH - 1);
    window.setTimeout(() => otpInputRefs.current[nextIndex]?.focus(), 0);
  }, [OTP_LENGTH]);

  const handleOtpKeyDown = React.useCallback((index: number, event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Backspace') {
      event.preventDefault();
      setOtpChars((previous) => {
        const next = [...previous];
        if (next[index]) {
          next[index] = '';
          return next;
        }
        const previousIndex = Math.max(0, index - 1);
        next[previousIndex] = '';
        window.setTimeout(() => otpInputRefs.current[previousIndex]?.focus(), 0);
        return next;
      });
      return;
    }

    if (event.key === 'ArrowLeft') {
      event.preventDefault();
      otpInputRefs.current[Math.max(0, index - 1)]?.focus();
      return;
    }

    if (event.key === 'ArrowRight') {
      event.preventDefault();
      otpInputRefs.current[Math.min(OTP_LENGTH - 1, index + 1)]?.focus();
    }
  }, [OTP_LENGTH]);

  const handleOtpPaste = React.useCallback((event: React.ClipboardEvent<HTMLInputElement>) => {
    event.preventDefault();
    const pasted = event.clipboardData.getData('text').toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, OTP_LENGTH);
    if (!pasted) {
      return;
    }
    const nextChars = Array.from({ length: OTP_LENGTH }, (_, index) => pasted[index] ?? '');
    setOtpChars(nextChars);
    const focusIndex = Math.min(pasted.length, OTP_LENGTH) - 1;
    window.setTimeout(() => otpInputRefs.current[Math.max(0, focusIndex)]?.focus(), 0);
  }, [OTP_LENGTH]);

  if (isLoading) {
    return <div className="page-shell"><div className="page-card">Preparing secure session access...</div></div>;
  }

  if (!token || accessError || !access) {
    const message = accessError instanceof Error ? accessError.message : 'Secure access link is invalid.';
    return (
      <div className="page-shell disclaimer-shell">
        <div className="page-card disclaimer-card">
          <div className="page-kicker">Secure Session Access</div>
          <h2>Access Link Unavailable</h2>
          <div className="error-banner">{message}</div>
          <div className="disclaimer-actions">
            <Button variant="secondary" onClick={() => navigate('/')}>Return to Dashboard</Button>
          </div>
        </div>
      </div>
    );
  }

  const roleLabel = access.role === 'INTERVIEWER' ? 'Interviewer' : 'Interviewee';
  const roleSlug = access.role === 'INTERVIEWER' ? 'interviewer' : 'interviewee';
  const disclaimerContent = getDisclaimerContent(roleSlug, access.avMode);
  const expiryMs = access.otpExpiresAt ? Date.parse(access.otpExpiresAt) - now : 0;
  const secondsLeft = Math.max(0, Math.ceil(expiryMs / 1000));
  const otpExpired = Boolean(access.disclaimerAccepted && access.otpExpiresAt && secondsLeft <= 0 && !access.otpVerified);
  const noWindowsLeft = access.remainingOtpWindows <= 0 && !access.otpVerified;
  const accessBlocked = access.sessionStatus === 'AUTH_FAILED' || access.sessionStatus === 'EXPIRED';

  return (
    <div className="page-shell disclaimer-shell">
      <div className="page-card disclaimer-card">
        <div className="page-kicker">Secure Session Access</div>
        <h2>{roleLabel} Verification</h2>
        <p className="page-subtitle">
          Review the disclaimer, then enter the 5-character passcode sent to your registered email address.
        </p>

        <div className="disclaimer-identity">
          <div className="identity-column">
            <div className="identity-item identity-item-row">
              <strong>Name:</strong>
              <span>{access.participantName}</span>
            </div>
            <div className="identity-item identity-item-row">
              <strong>Email:</strong>
              <span>{access.participantEmail}</span>
            </div>
          </div>
          <div className="identity-column identity-column-compact">
            <div className="identity-item identity-item-row identity-item-timezone">
              <strong>Passcode windows left:</strong>
              <span>{access.remainingOtpWindows}</span>
            </div>
          </div>
        </div>

        {error && <div className="error-banner">{error}</div>}
        {notice && <div className="grid-refreshing">{notice}</div>}
        {accessBlocked && (
          <div className="error-banner">
            {access.sessionStatus === 'AUTH_FAILED'
              ? 'Secure participant authentication is no longer available for this session.'
              : 'This session expired before the interview could be started.'}
          </div>
        )}

        {!access.disclaimerAccepted && !accessBlocked ? (
          <>
            <ol className="disclaimer-list">
              {disclaimerContent.points.map((point) => (
                <li key={point}>{point}</li>
              ))}
            </ol>
            <div className="disclaimer-actions">
              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={accepted}
                  onChange={(event) => setAccepted(event.target.checked)}
                />
                {disclaimerContent.acknowledgement}
              </label>
              <Button onClick={() => disclaimerMutation.mutate()} disabled={!accepted || disclaimerMutation.isPending}>
                {disclaimerMutation.isPending ? 'Saving...' : 'Accept and Continue'}
              </Button>
            </div>
          </>
        ) : !accessBlocked ? (
          <div className="stack-form">
            <div className="form-group">
              <label htmlFor="access-otp-0">One-time passcode</label>
              <div className="otp-entry-group">
                {otpChars.map((character, index) => (
                  <React.Fragment key={`otp-${index}`}>
                    <input
                      id={`access-otp-${index}`}
                      ref={(element) => {
                        otpInputRefs.current[index] = element;
                      }}
                      className="otp-entry-box"
                      type="text"
                      inputMode="text"
                      autoComplete="off"
                      autoCorrect="off"
                      autoCapitalize="characters"
                      spellCheck={false}
                      maxLength={1}
                      value={character}
                      onChange={(event) => updateOtpChar(index, event.target.value)}
                      onKeyDown={(event) => handleOtpKeyDown(index, event)}
                      onPaste={handleOtpPaste}
                      disabled={access.otpVerified || verifyOtpMutation.isPending || retryMutation.isPending}
                      aria-label={`Passcode character ${index + 1}`}
                    />
                    {index < otpChars.length - 1 ? <span className="otp-entry-separator">-</span> : null}
                  </React.Fragment>
                ))}
              </div>
            </div>
            <p className="page-subtitle">
              {access.otpVerified
                ? 'Passcode already verified for this participant.'
                : noWindowsLeft
                  ? 'No passcode windows remain for this participant.'
                  : `Passcode expires in ${secondsLeft} seconds.`}
            </p>
            <div className="disclaimer-actions">
              {!access.otpVerified && (
                <Button
                  onClick={() => verifyOtpMutation.mutate()}
                  disabled={otp.length !== 5 || verifyOtpMutation.isPending || retryMutation.isPending || noWindowsLeft}
                >
                  {verifyOtpMutation.isPending ? 'Verifying...' : 'Verify Passcode'}
                </Button>
              )}
              {(otpExpired || error.toLowerCase().includes('expired')) && access.remainingOtpWindows > 0 && !access.otpVerified && (
                <Button variant="secondary" onClick={() => retryMutation.mutate()} disabled={retryMutation.isPending}>
                  {retryMutation.isPending ? 'Sending...' : 'Retry'}
                </Button>
              )}
              {access.otpVerified && (
                <Button
                  onClick={() =>
                    void handleSessionAdvance({
                      success: true,
                      sessionReadyToStart: access.sessionReadyToStart,
                      retryAvailable: access.remainingOtpWindows > 0,
                      remainingOtpWindows: access.remainingOtpWindows,
                      otpExpiresAt: access.otpExpiresAt,
                      message: access.message,
                      access,
                      session: useSessionStore.getState().currentSession,
                    })
                  }
                >
                  Continue
                </Button>
              )}
            </div>
          </div>
        ) : (
          <div className="stack-form">
            <p className="page-subtitle">
              {access.message || 'Secure access is no longer available for this participant.'}
            </p>
            <div className="disclaimer-actions">
              <Button variant="secondary" onClick={() => navigate('/')}>Return to Dashboard</Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default AccessEntry;
