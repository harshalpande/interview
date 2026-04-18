import React, { useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Button } from '../components/Button';
import { sessionApi } from '../services/sessionApi';
import type { ValidateTokenResponse } from '../types/session';
import { useSessionStore } from '../stores/sessionStore';
import { useBackGuard } from '../hooks/useBackGuard';
import { getBrowserTimeZone } from '../utils/dateTime';
import { getOrCreateDeviceId } from '../utils/device';

const Join: React.FC = () => {
  const { token } = useParams<{ token: string }>();
  const navigate = useNavigate();
  const setRole = useSessionStore((state) => state.setRole);
  const setSession = useSessionStore((state) => state.setSession);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [validation, setValidation] = useState<ValidateTokenResponse | null>(null);

  useBackGuard({
    enabled: true,
    message: 'Leave this step? You will be returned to the dashboard.',
    redirectTo: '/java',
  });

  const validateToken = useCallback(async () => {
    if (!token) return;
    try {
      setLoading(true);
      const result = await sessionApi.validateToken(token);
      if (!result.valid) {
        setError(result.message);
      } else if (result.resumeRequired) {
        navigate(`/java/resume/${result.sessionId}?role=interviewee&reason=MANUAL_RESUME`, { replace: true });
        return;
      } else {
        // Do not pre-fill. Interviewee must type to confirm identity.
        setName('');
        setEmail('');
      }
      setValidation(result);
    } catch (err) {
      setError('Invalid or expired token');
    } finally {
      setLoading(false);
    }
  }, [navigate, token]);

  React.useEffect(() => {
    validateToken();
  }, [validateToken]);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!validation?.valid || !token) {
      setError('Invalid or expired token');
      return;
    }
    try {
      setRole('interviewee');
      const session = await sessionApi.joinSession(token, {
        name,
        email,
        timeZone: getBrowserTimeZone(),
        deviceId: getOrCreateDeviceId(),
      });
      setSession(session);
      navigate(`/java/identity-capture/${session.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Validation failed');
    }
  };

  if (loading) {
    return <div className="page-shell"><div className="page-card">Validating interview link...</div></div>;
  }

  return (
    <div className="page-shell">
      <div className="page-card">
        <div className="page-kicker">Join Interview</div>
        <h2>Enter your registered details</h2>
        <p className="page-subtitle">
          Enter the same name and email that the interviewer used while creating the interview session.
        </p>

        {error && <div className="error-banner">{error}</div>}

        <form onSubmit={handleSubmit} className="stack-form" autoComplete="off">
          <div className="form-group">
            <label htmlFor="join-name">Name</label>
            <input
              id="join-name"
              name="joinName"
              autoComplete="off"
              value={name}
              onChange={(event) => setName(event.target.value)}
              required
            />
          </div>
          <div className="form-group">
            <label htmlFor="join-email">Email</label>
            <input
              id="join-email"
              name="joinEmail"
              type="email"
              autoComplete="off"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />
          </div>
          <Button type="submit" disabled={!validation?.valid}>Confirm and Continue</Button>
        </form>
      </div>
    </div>
  );
};

export default Join;
