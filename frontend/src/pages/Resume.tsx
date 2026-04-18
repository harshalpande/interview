import React from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { Button } from '../components/Button';
import { sessionApi } from '../services/sessionApi';
import { useSessionStore } from '../stores/sessionStore';
import type { Participant, ParticipantRole, ResumeReason, SessionResponse } from '../types/session';
import { getBrowserTimeZone } from '../utils/dateTime';
import { getOrCreateDeviceId } from '../utils/device';

const Resume: React.FC = () => {
  const { sessionId } = useParams<{ sessionId: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const setRole = useSessionStore((state) => state.setRole);
  const setSession = useSessionStore((state) => state.setSession);
  const roleParam = searchParams.get('role');
  const role = roleParam === 'interviewer' ? 'INTERVIEWER' : 'INTERVIEWEE';
  const appRole = role === 'INTERVIEWER' ? 'interviewer' : 'interviewee';
  const reason = (searchParams.get('reason') as ResumeReason | null) || 'MANUAL_RESUME';
  const [name, setName] = React.useState('');
  const [email, setEmail] = React.useState('');
  const [error, setError] = React.useState('');
  const [info, setInfo] = React.useState('');
  const [loading, setLoading] = React.useState(false);
  const [pendingApproval, setPendingApproval] = React.useState(false);

  React.useEffect(() => {
    if (!sessionId || !roleParam) {
      navigate('/java', { replace: true });
    }
  }, [navigate, roleParam, sessionId]);

  React.useEffect(() => {
    if (!pendingApproval || !sessionId || role !== 'INTERVIEWEE') {
      return undefined;
    }

    const interval = window.setInterval(async () => {
      try {
        const session = await sessionApi.getSession(sessionId);
        const participant = findParticipant(session, role);
        if (!participant) {
          return;
        }

        const requestedAt = participant.resumeRequestedAt ? Date.parse(participant.resumeRequestedAt) : 0;
        const approvedAt = participant.resumeApprovedAt ? Date.parse(participant.resumeApprovedAt) : 0;
        const rejectedAt = participant.resumeRejectedAt ? Date.parse(participant.resumeRejectedAt) : 0;

        if (approvedAt && approvedAt >= requestedAt) {
          setPendingApproval(false);
          setRole(appRole);
          setSession(session);
          navigate(nextPathFor(session, role), { replace: true });
          return;
        }

        if (rejectedAt && rejectedAt >= requestedAt) {
          setPendingApproval(false);
          setInfo('');
          setError('The interviewer rejected this resume request.');
        }
      } catch {
        // best-effort polling only
      }
    }, 2000);

    return () => window.clearInterval(interval);
  }, [appRole, navigate, pendingApproval, role, sessionId, setRole, setSession]);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!sessionId) {
      return;
    }

    try {
      setLoading(true);
      setError('');
      setInfo('');

      const response = await sessionApi.requestResume(sessionId, {
        role,
        name,
        email,
        timeZone: getBrowserTimeZone(),
        deviceId: getOrCreateDeviceId(),
        reason,
      });

      if (response.status === 'PENDING_APPROVAL') {
        setPendingApproval(true);
        setInfo(response.message);
        return;
      }

      if (response.status === 'REJECTED') {
        if (response.session) {
          setRole(appRole);
          setSession(response.session);
          navigate(nextPathFor(response.session, role), { replace: true });
          return;
        }
        setError(response.message);
        return;
      }

      if (!response.session) {
        throw new Error('Resume succeeded but the session details were unavailable.');
      }

      setRole(appRole);
      setSession(response.session);
      navigate(nextPathFor(response.session, role), { replace: true });
    } catch (resumeError) {
      setError(resumeError instanceof Error ? resumeError.message : 'Unable to resume the interview.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page-shell">
      <div className="page-card">
        <div className="page-kicker">Resume Interview</div>
        <h2>Confirm your registered details</h2>
        <p className="page-subtitle">
          {role === 'INTERVIEWER'
            ? 'Verify your registered interviewer details to reopen the active session.'
            : 'Verify your registered interview details to request or complete session resume.'}
        </p>

        {error && <div className="error-banner">{error}</div>}
        {info && <div className="success-banner">{info}</div>}

        <form onSubmit={handleSubmit} className="stack-form" autoComplete="off">
          <div className="form-group">
            <label htmlFor="resume-name">Name</label>
            <input
              id="resume-name"
              name="resumeName"
              autoComplete="off"
              value={name}
              onChange={(event) => setName(event.target.value)}
              required
              disabled={pendingApproval}
            />
          </div>
          <div className="form-group">
            <label htmlFor="resume-email">Email</label>
            <input
              id="resume-email"
              name="resumeEmail"
              type="email"
              autoComplete="off"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
              disabled={pendingApproval}
            />
          </div>
          <Button type="submit" disabled={loading || pendingApproval}>
            {loading ? 'Verifying...' : pendingApproval ? 'Waiting for approval...' : 'Verify and Resume'}
          </Button>
        </form>
      </div>
    </div>
  );
};

export default Resume;

function nextPathFor(session: SessionResponse, role: ParticipantRole) {
  const appRole = role === 'INTERVIEWER' ? 'interviewer' : 'interviewee';
  const participant = findParticipant(session, role);

  if (role === 'INTERVIEWEE') {
    if (participant?.identityCaptureStatus === 'PENDING') {
      return `/java/identity-capture/${session.id}`;
    }
    if (!participant?.disclaimerAcceptedAt) {
      return `/java/disclaimer/interviewee?sessionId=${session.id}`;
    }
  } else if (!participant?.disclaimerAcceptedAt) {
    return `/java/disclaimer/interviewer?sessionId=${session.id}`;
  }

  return `/java/session/${session.id}?role=${appRole}`;
}

function findParticipant(session: SessionResponse, role: ParticipantRole): Participant | undefined {
  return session.participants.find((participant) => participant.role === role);
}
