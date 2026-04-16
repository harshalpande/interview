import React from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Button } from '../components/Button';
import { useSessionStore } from '../stores/sessionStore';
import { sessionApi } from '../services/sessionApi';
import { formatDateTime, getLocalTimeZoneLabel } from '../utils/dateTime';
import { useBackGuard } from '../hooks/useBackGuard';
import './Disclaimer.css';

const interviewerPoints = [
  'Wrap up the interview within 60 minutes. Only one 15 minute extension is allowed and it should be used sparingly.',
  'Conduct the session fairly and evaluate problem solving, communication, and code quality without discrimination, bias, or partiality.',
  'Be polite, professional, and respectful at all times. Avoid hostile, intimidating, sarcastic, or dismissive behavior.',
  'Ask relevant technical questions, provide clear instructions, and give the candidate a reasonable chance to think aloud.',
  'Do not ask for personal, protected, or irrelevant information unrelated to the role.',
  'Use the collaboration tools responsibly and do not share the interview link with anyone other than the intended interviewee.',
  "Provide honest and constructive feedback based only on the candidate's interview performance.",
];

const intervieweePoints = [
  'Participate professionally, communicate clearly, and treat the interviewer with respect.',
  'You may ask relevant clarifying questions about the problem, constraints, or expectations.',
  'Do not use unfair assistance such as hidden notes, copied solutions, AI-generated answers, or help from another person unless explicitly allowed.',
  'Do not misrepresent your identity, prior work, or coding process during the interview.',
  'Stay focused on the interview and avoid disruptive, abusive, or inappropriate behavior.',
  'Use the shared editor responsibly and keep your work relevant to the interview problem.',
  'The interview is expected to conclude within 60 minutes unless the interviewer grants the one permitted 15 minute extension.',
];

const Disclaimer: React.FC = () => {
  const { role } = useParams<{ role: 'interviewer' | 'interviewee' }>();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const currentSession = useSessionStore((state) => state.currentSession);
  const storedSessionId = useSessionStore((state) => state.sessionId);
  const setSession = useSessionStore((state) => state.setSession);

  const [accepted, setAccepted] = React.useState(false);
  const [submitting, setSubmitting] = React.useState(false);

  const activeSessionId = storedSessionId || searchParams.get('sessionId');
  const needsFetch = !!activeSessionId && (!currentSession || currentSession.id !== activeSessionId);

  useBackGuard({
    enabled: true,
    message: 'Leave this step? You will be returned to the dashboard.',
    redirectTo: '/java',
  });

  const { data: fetchedSession, isLoading, error } = useQuery({
    queryKey: ['disclaimer-session', activeSessionId],
    queryFn: () => sessionApi.getSession(activeSessionId!),
    enabled: needsFetch,
  });

  const session = currentSession?.id === activeSessionId ? currentSession : fetchedSession;

  if (!role || !activeSessionId) {
    return <div className="page-shell"><div className="page-card">Invalid disclaimer context</div></div>;
  }

  if (isLoading && !session) {
    return <div className="page-shell"><div className="page-card">Loading disclaimer...</div></div>;
  }

  if (error && !session) {
    return (
      <div className="page-shell">
        <div className="page-card">
          Unable to load the interview session for the disclaimer.
        </div>
      </div>
    );
  }

  const participantRole = role === 'interviewer' ? 'INTERVIEWER' : 'INTERVIEWEE';
  const participant = session?.participants.find((entry) => entry.role === participantRole);
  const points = role === 'interviewer' ? interviewerPoints : intervieweePoints;

  const handleAccept = async () => {
    try {
      setSubmitting(true);
      const nextSession = await sessionApi.acceptDisclaimer(activeSessionId, {
        role: participantRole,
      });
      setSession(nextSession);
      navigate(`/java/session/${activeSessionId}?role=${role}`);
    } catch (acceptError) {
      const message = acceptError instanceof Error ? acceptError.message : 'Failed to save disclaimer';
      alert(message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="page-shell">
      <div className="page-card disclaimer-card">
        <div className="page-kicker">Interview Agreement</div>
        <h2>Disclaimer for {role === 'interviewer' ? 'Interviewer' : 'Interviewee'}</h2>
        <p className="page-subtitle">
          Please review the interview expectations carefully before continuing.
        </p>

        <div className="disclaimer-identity">
          <div>
            <strong>Name:</strong> {participant?.name || 'Not available'}
          </div>
          <div>
            <strong>Email:</strong> {participant?.email || 'Not available'}
          </div>
          <div>
            <strong>Current timezone:</strong> {getLocalTimeZoneLabel()}
          </div>
          {session?.createdAt && (
            <div>
              <strong>Created:</strong> {formatDateTime(session.createdAt)}
            </div>
          )}
        </div>

        <ol className="disclaimer-list">
          {points.map((point) => (
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
            I have read, understood, and agree to follow the interview guidelines above.
          </label>
          <Button onClick={handleAccept} disabled={!accepted || submitting} className="accept-btn">
            {submitting ? 'Saving...' : 'Accept and Continue'}
          </Button>
        </div>
      </div>
    </div>
  );
};

export default Disclaimer;
