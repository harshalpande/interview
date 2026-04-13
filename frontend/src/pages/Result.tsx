import React from 'react';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { sessionApi } from '../services/sessionApi';
import { Button } from '../components/Button';
import { formatDateTime } from '../utils/dateTime';

import './Result.css';

const STATUS_LABELS: Record<string, string> = {
  CREATED: 'Ready to Start',
  WAITING_JOIN: 'Waiting for Join',
  ACTIVE: 'Interview In Progress',
  ENDED: 'Ended',
  EXPIRED: 'Expired',
};

const Result: React.FC = () => {
  const { sessionId } = useParams<{ sessionId: string }>();

  const { data: session, isLoading } = useQuery({
    queryKey: ['session', sessionId],
    queryFn: () => sessionApi.getSession(sessionId!),
    enabled: !!sessionId,
  });

  if (isLoading) return <div className="page-shell"><div className="page-card">Loading result...</div></div>;
  if (!session) return <div className="page-shell"><div className="page-card">Session not found</div></div>;

  const interviewer = session.participants.find((participant) => participant.role === 'INTERVIEWER');
  const interviewee = session.participants.find((participant) => participant.role === 'INTERVIEWEE');
  const isTokenExpired = session.status === 'EXPIRED';

  return (
    <div className="result-page polished-page">
      <div className="result-header">
        <div>
          <div className="page-kicker">Interview Result</div>
          <h2>{session.summary || 'Interview Summary'}</h2>
          <div className="result-banner">
            <div className="participant-info">
              <span className="participant-label">Interviewer</span>
              <span className="participant-name">{interviewer?.name}</span>
              <span>{interviewer?.email}</span>
            </div>
            <div className="participant-info">
              <span className="participant-label">Interviewee</span>
              <span className="participant-name">{interviewee?.name}</span>
              <span>{interviewee?.email}</span>
            </div>
            <div className="participant-info">
              <span className="participant-label">Status</span>
              <span className="participant-name">{STATUS_LABELS[session.status] || session.status}</span>
              <span>{formatDateTime(session.endedAt || session.createdAt)}</span>
            </div>
          </div>
        </div>
        <Button onClick={() => window.history.back()}>Close</Button>
      </div>

      <div className="result-summary-card">
        <p><strong>Created:</strong> {formatDateTime(session.createdAt)}</p>
        {session.startedAt && <p><strong>Started:</strong> {formatDateTime(session.startedAt)}</p>}
        {session.endedAt && <p><strong>Ended:</strong> {formatDateTime(session.endedAt)}</p>}
        <p><strong>Allocated duration:</strong> {Math.round(session.durationSec / 60)} minutes</p>
        {session.feedback && (
          <>
            <p><strong>Rating:</strong> {session.feedback.rating}</p>
            <p><strong>Recommendation:</strong> {session.feedback.recommendation ? 'Yes' : 'No'}</p>
            <p><strong>Comments:</strong> {session.feedback.comments}</p>
          </>
        )}
        {isTokenExpired && <p><strong>Reason:</strong> Token Expired</p>}
      </div>

      {!isTokenExpired && (
        <div className="result-panels">
          <section className="result-panel">
            <h3>Final Code</h3>
            <pre className="result-pre code-pre">{session.latestCode || '(no code captured)'}</pre>
          </section>

          <section className="result-panel">
            <h3>Final Output</h3>
            <pre className="result-pre">{session.finalRunResult?.stdout || '(no output)'}</pre>
          </section>

          <section className="result-panel">
            <h3>Final Errors</h3>
            <pre className="result-pre">{session.finalRunResult?.stderr || '(no errors)'}</pre>
          </section>
        </div>
      )}
    </div>
  );
};

export default Result;
