import React from 'react';
import { useNavigate, useParams } from 'react-router-dom';
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
  const navigate = useNavigate();

  const { data: session, isLoading } = useQuery({
    queryKey: ['session', sessionId],
    queryFn: () => sessionApi.getSession(sessionId!),
    enabled: !!sessionId,
  });

  React.useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        navigate('/java');
      }
    };

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [navigate]);

  if (isLoading) return <div className="page-shell"><div className="page-card">Loading result...</div></div>;
  if (!session) return <div className="page-shell"><div className="page-card">Session not found</div></div>;

  const interviewer = session.participants.find((participant) => participant.role === 'INTERVIEWER');
  const interviewee = session.participants.find((participant) => participant.role === 'INTERVIEWEE');
  const isTokenExpired = session.status === 'EXPIRED';
  const activityEvents = session.activityEvents || [];
  const tabSwitchEvents = activityEvents.filter((event) => event.eventType === 'TAB_HIDDEN');
  const pasteEvents = activityEvents.filter((event) => event.eventType === 'PASTE_IN_EDITOR');
  const blockedDropEvents = activityEvents.filter((event) => event.eventType === 'EXTERNAL_DROP_BLOCKED');
  const cameraStreamLostEvents = activityEvents.filter((event) => event.eventType === 'CAMERA_STREAM_LOST');
  const noFaceEvents = activityEvents.filter((event) => event.eventType === 'NO_FACE_DETECTED');
  const multipleFaceEvents = activityEvents.filter((event) => event.eventType === 'MULTIPLE_FACES_DETECTED');
  const latestActivity = activityEvents.length ? activityEvents[activityEvents.length - 1] : null;
  const snapshotUrl = interviewee?.identityCaptureStatus === 'SUCCESS'
    ? sessionApi.getIdentityCaptureImageUrl(session.id, 'INTERVIEWEE')
    : null;

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
        <Button onClick={() => navigate('/java')}>Close (Esc)</Button>
      </div>

      <div className="result-summary-card result-summary-layout">
        <div className="result-summary-details">
          <p><strong>Created:</strong> {formatDateTime(session.createdAt)}</p>
          {session.startedAt && <p><strong>Started:</strong> {formatDateTime(session.startedAt)}</p>}
          {session.endedAt && <p><strong>Ended:</strong> {formatDateTime(session.endedAt)}</p>}
          <p><strong>Allocated duration:</strong> {Math.round(session.durationSec / 60)} minutes</p>
          {session.feedback && (
            <>
              <p><strong>Rating:</strong> {session.feedback.rating}</p>
              <p><strong>Recommendation:</strong> {formatRecommendation(session.feedback.recommendationDecision)}</p>
              <p><strong>Comments:</strong> {session.feedback.comments}</p>
            </>
          )}
          <p><strong>Identity snapshot:</strong> {formatIdentityCaptureStatus(interviewee?.identityCaptureStatus, interviewee?.identityCaptureFailureReason)}</p>
          {isTokenExpired && <p><strong>Reason:</strong> Token Expired</p>}
        </div>

        <div className="result-summary-identity">
          <h3>Identity Verification</h3>
          {snapshotUrl ? (
            <div className="identity-result-card">
              <img src={snapshotUrl} alt="Interviewee identity snapshot" className="identity-result-image" />
            </div>
          ) : (
            <p className="activity-empty">{formatIdentityCaptureStatus(interviewee?.identityCaptureStatus, interviewee?.identityCaptureFailureReason)}</p>
          )}
        </div>
      </div>

      {!isTokenExpired && (
        <div className="result-panels">
          <section className="result-panel">
            <h3 className="result-section-title">
              <span>Suspicious Activity</span>
              {activityEvents.length ? <span className="result-section-total">Total {activityEvents.length}</span> : null}
            </h3>
            {activityEvents.length ? (
              <div className="activity-summary">
                <div className="activity-summary-grid">
                  <div className="activity-metric">
                    <span className="activity-metric-label">Tab switches</span>
                    <strong>{tabSwitchEvents.length}</strong>
                  </div>
                  <div className="activity-metric">
                    <span className="activity-metric-label">Paste events</span>
                    <strong>{pasteEvents.length}</strong>
                  </div>
                  <div className="activity-metric">
                    <span className="activity-metric-label">Blocked drops</span>
                    <strong>{blockedDropEvents.length}</strong>
                  </div>
                  <div className="activity-metric">
                    <span className="activity-metric-label">Camera interruptions</span>
                    <strong>{cameraStreamLostEvents.length}</strong>
                  </div>
                  <div className="activity-metric">
                    <span className="activity-metric-label">Face not visible</span>
                    <strong>{noFaceEvents.length}</strong>
                  </div>
                  <div className="activity-metric">
                    <span className="activity-metric-label">Multiple faces</span>
                    <strong>{multipleFaceEvents.length}</strong>
                  </div>
                </div>
                <div className="activity-summary-note">
                  <p>
                    <strong>Summary:</strong> {tabSwitchEvents.length} tab switch event{tabSwitchEvents.length === 1 ? '' : 's'}, {pasteEvents.length} paste event{pasteEvents.length === 1 ? '' : 's'}, {blockedDropEvents.length} blocked drag-and-drop attempt{blockedDropEvents.length === 1 ? '' : 's'}, {cameraStreamLostEvents.length} camera interruption{cameraStreamLostEvents.length === 1 ? '' : 's'}, {noFaceEvents.length} no-face alert{noFaceEvents.length === 1 ? '' : 's'}, and {multipleFaceEvents.length} multiple-face alert{multipleFaceEvents.length === 1 ? '' : 's'} were recorded during the session.
                  </p>
                  {latestActivity ? (
                    <p>
                      <strong>Most recent:</strong> {latestActivity.detail} at {formatDateTime(latestActivity.createdAt)}.
                    </p>
                  ) : null}
                </div>
              </div>
            ) : (
              <p className="activity-empty">No suspicious activities were observed.</p>
            )}
          </section>

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

function formatRecommendation(value: string) {
  return value === 'REEVALUATION'
    ? 'Reevaluation'
    : value.charAt(0) + value.slice(1).toLowerCase();
}

function formatIdentityCaptureStatus(status?: string | null, reason?: string | null) {
  if (status === 'SUCCESS') {
    return 'Captured successfully';
  }
  if (status === 'FAILED') {
    return `Camera capture could not be completed${reason ? ` (${formatFailureReason(reason)})` : ''}.`;
  }
  if (status === 'SKIPPED') {
    return 'Candidate continued without a photo capture.';
  }
  return 'Identity capture is still pending.';
}

function formatFailureReason(reason: string) {
  return reason
    .toLowerCase()
    .split('_')
    .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
    .join(' ');
}
