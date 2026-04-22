import React from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Button } from '../components/Button';
import { useSessionStore } from '../stores/sessionStore';
import { sessionApi } from '../services/sessionApi';
import { formatDateTime, getLocalTimeZoneLabel } from '../utils/dateTime';
import { useBackGuard } from '../hooks/useBackGuard';
import './Disclaimer.css';

const interviewerPointsForInAppAv = [
  'Use this platform only for the scheduled interview and protect the interview link, candidate details, captured identity snapshot, and session outputs from unauthorized sharing.',
  'Conduct the interview professionally, evaluate only job-relevant skills, and avoid discriminatory, intimidating, abusive, or irrelevant questioning.',
  'Clearly explain the problem statement, constraints, and expectations, and give the candidate a fair opportunity to think aloud, ask clarifying questions, and demonstrate problem solving.',
  'Use monitoring alerts responsibly. Camera, tab-switch, paste, and drag-drop alerts are intended to support human judgment and should not be treated as automatic proof of misconduct without context.',
  'Session continuity and access controls may require additional verification, review, or platform-enforced restrictions when interview integrity or identity confidence is impacted.',
  'Do not request confidential personal information, credentials, unrelated sensitive data, or any action that falls outside a legitimate interview process.',
  'Record feedback honestly and objectively based on code quality, problem solving approach, communication, correctness, and overall interview conduct.',
  'The interview is designed for 60 minutes. Only one 15-minute extension is allowed, and it should be used only when genuinely necessary.',
];

const interviewerPointsForExternalAv = [
  'Use this platform only for the scheduled interview and protect the interview link, candidate details, captured identity snapshot, and session outputs from unauthorized sharing.',
  'Conduct the interview professionally, evaluate only job-relevant skills, and avoid discriminatory, intimidating, abusive, or irrelevant questioning.',
  'Clearly explain the problem statement, constraints, and expectations, and give the candidate a fair opportunity to think aloud, ask clarifying questions, and demonstrate problem solving.',
  'Use monitoring alerts responsibly. Tab-switch, paste, and drag-drop alerts are intended to support human judgment and should not be treated as automatic proof of misconduct without context.',
  'Live audio and video for this session will be handled outside the platform through the interviewer-selected channel, such as Microsoft Teams or Zoom.',
  'Session continuity and access controls may require additional verification, review, or platform-enforced restrictions when interview integrity or identity confidence is impacted.',
  'Do not request confidential personal information, credentials, unrelated sensitive data, or any action that falls outside a legitimate interview process.',
  'Record feedback honestly and objectively based on code quality, problem solving approach, communication, correctness, and overall interview conduct.',
  'The interview is designed for 60 minutes. Only one 15-minute extension is allowed, and it should be used only when genuinely necessary.',
];

const intervieweePointsForInAppAv = [
  'By continuing, you confirm that you are the registered interviewee and agree to the identity, editor, and activity monitoring controls enabled for this interview session.',
  'A pre-interview identity snapshot may be captured and the interviewer may view your live camera stream during the active interview for security, verification, and interview integrity purposes.',
  'Remain clearly visible in the camera frame, keep your camera available during the session, and do not permit another person to appear on screen or assist you unless explicitly allowed by the interviewer.',
  'Do not use copied solutions, external notes, unauthorized websites, AI-generated answers, hidden communication tools, or help from another person unless the interviewer has expressly permitted it.',
  'Use the shared editor only for interview-related work. Paste, drag-drop, tab-switch, and similar monitored actions may be recorded and surfaced to the interviewer as suspicious activity alerts.',
  'If session continuity, identity verification, or monitored interview controls are disrupted, the platform may require additional checks, limit re-entry, or end the session to protect interview integrity.',
  'Communicate professionally, ask clarifying questions when needed, and focus on demonstrating your own reasoning, coding approach, and problem-solving ability.',
  'The interview is designed for 60 minutes unless the interviewer grants the one permitted 15-minute extension.',
];

const intervieweePointsForExternalAv = [
  'By continuing, you confirm that you are the registered interviewee and agree to the identity, editor, and activity monitoring controls enabled for this interview session.',
  'A pre-interview identity snapshot remains mandatory for verification and interview integrity before you enter the session.',
  'Live audio and video for this interview will be handled outside this platform through the interviewer-selected channel, such as Microsoft Teams or Zoom.',
  'Do not use copied solutions, external notes, unauthorized websites, AI-generated answers, hidden communication tools, or help from another person unless the interviewer has expressly permitted it.',
  'Use the shared editor only for interview-related work. Paste, drag-drop, tab-switch, and similar monitored actions may be recorded and surfaced to the interviewer as suspicious activity alerts.',
  'If session continuity, identity verification, or monitored interview controls are disrupted, the platform may require additional checks, limit re-entry, or end the session to protect interview integrity.',
  'Communicate professionally, ask clarifying questions when needed, and focus on demonstrating your own reasoning, coding approach, and problem-solving ability.',
  'The interview is designed for 60 minutes unless the interviewer grants the one permitted 15-minute extension.',
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
    redirectTo: '/',
  });

  const { data: fetchedSession, isLoading, error } = useQuery({
    queryKey: ['disclaimer-session', activeSessionId],
    queryFn: () => sessionApi.getSession(activeSessionId!),
    enabled: needsFetch,
  });

  const session = currentSession?.id === activeSessionId ? currentSession : fetchedSession;

  if (!role || !activeSessionId) {
    return <div className="page-shell disclaimer-shell"><div className="page-card">Invalid disclaimer context</div></div>;
  }

  if (isLoading && !session) {
    return <div className="page-shell disclaimer-shell"><div className="page-card">Loading disclaimer...</div></div>;
  }

  if (error && !session) {
    return (
      <div className="page-shell disclaimer-shell">
        <div className="page-card">
          Unable to load the interview session for the disclaimer.
        </div>
      </div>
    );
  }

  const participantRole = role === 'interviewer' ? 'INTERVIEWER' : 'INTERVIEWEE';
  const participant = session?.participants.find((entry) => entry.role === participantRole);
  const points = role === 'interviewer'
    ? session?.avMode === 'IN_APP'
      ? interviewerPointsForInAppAv
      : interviewerPointsForExternalAv
    : session?.avMode === 'IN_APP'
      ? intervieweePointsForInAppAv
      : intervieweePointsForExternalAv;
  const subtitle = role === 'interviewer'
    ? 'Please review the interviewer responsibilities, privacy obligations, and fair-use expectations before continuing.'
    : 'Please review the interview monitoring, privacy, and conduct expectations carefully before continuing.';
  const acknowledgement = role === 'interviewer'
    ? 'I understand my responsibilities as the interviewer and will use this platform fairly, professionally, and in accordance with the interview process.'
    : session?.avMode === 'IN_APP'
      ? 'I understand that this interview session may use identity capture, live camera streaming, activity monitoring, and session integrity controls, and I agree to participate under these guidelines.'
      : 'I understand that this interview session requires identity capture and editor activity monitoring, while live audio and video will be handled outside this platform, and I agree to participate under these guidelines.';

  const handleAccept = async () => {
    try {
      setSubmitting(true);
      const nextSession = await sessionApi.acceptDisclaimer(activeSessionId, {
        role: participantRole,
      });
      setSession(nextSession);
      window.scrollTo({ top: 0, behavior: 'auto' });
      navigate(`/java/session/${activeSessionId}?role=${role}`);
    } catch (acceptError) {
      const message = acceptError instanceof Error ? acceptError.message : 'Failed to save disclaimer';
      alert(message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="page-shell disclaimer-shell">
      <div className="page-card disclaimer-card">
        <div className="page-kicker">Interview Agreement</div>
        <h2>Disclaimer for {role === 'interviewer' ? 'Interviewer' : 'Interviewee'}</h2>
        <p className="page-subtitle">{subtitle}</p>

        <div className="disclaimer-identity">
          <div className="identity-column">
            <div className="identity-item identity-item-row">
              <strong>Name:</strong>
              <span>{participant?.name || 'Not available'}</span>
            </div>
            <div className="identity-item identity-item-row">
              <strong>Email:</strong>
              <span>{participant?.email || 'Not available'}</span>
            </div>
            {session?.createdAt && (
              <div className="identity-item identity-item-row">
                <strong>Created:</strong>
                <span>{formatDateTime(session.createdAt)}</span>
              </div>
            )}
          </div>
          <div className="identity-column identity-column-compact">
            <div className="identity-item identity-item-row identity-item-timezone">
              <strong>Current timezone:</strong>
              <span>{getLocalTimeZoneLabel()}</span>
            </div>
          </div>
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
            {acknowledgement}
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
