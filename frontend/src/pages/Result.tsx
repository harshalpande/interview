import React from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { sessionApi } from '../services/sessionApi';
import { Button } from '../components/Button';
import { formatDateTime } from '../utils/dateTime';
import type { EditableCodeFile } from '../types/session';

import './Result.css';

const STATUS_LABELS: Record<string, string> = {
  CREATED: 'Ready to Start',
  WAITING_JOIN: 'Waiting for Join',
  ACTIVE: 'Interview In Progress',
  ENDED: 'Ended',
  EXPIRED: 'Expired',
};

const ANGULAR_PACKAGE_JSON = `{
  "name": "interview-angular-sandbox",
  "version": "0.0.1",
  "private": true,
  "scripts": {
    "build": "ng build"
  },
  "dependencies": {
    "@angular/common": "~21.2.0",
    "@angular/compiler": "~21.2.0",
    "@angular/core": "~21.2.0",
    "@angular/platform-browser": "~21.2.0",
    "rxjs": "^7.8.0",
    "tslib": "^2.8.0",
    "zone.js": "~0.15.0"
  },
  "devDependencies": {
    "@angular/build": "~21.2.0",
    "@angular/cli": "~21.2.0",
    "@angular/compiler-cli": "~21.2.0",
    "typescript": "~5.9.0"
  }
}
`;

const REACT_PACKAGE_JSON = `{
  "name": "interview-react-sandbox",
  "private": true,
  "version": "0.0.1",
  "type": "module",
  "scripts": {
    "build": "vite build"
  },
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1"
  },
  "devDependencies": {
    "@types/react": "^18.3.12",
    "@types/react-dom": "^18.3.1",
    "@vitejs/plugin-react": "^4.3.1",
    "typescript": "^5.6.3",
    "vite": "^5.4.10"
  }
}
`;

const Result: React.FC = () => {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const [activeCodePath, setActiveCodePath] = React.useState<string>('');

  const { data: session, isLoading } = useQuery({
    queryKey: ['session', sessionId],
    queryFn: () => sessionApi.getSession(sessionId!),
    enabled: !!sessionId,
  });
  const isFrontendWorkspaceSession = session?.technology === 'ANGULAR' || session?.technology === 'REACT';
  const hasSuccessfulFrontendPreview = Boolean(isFrontendWorkspaceSession && session?.finalPreviewUrl);
  const resultCodeFiles = React.useMemo(
    () => buildResultCodeFiles(session?.technology || '', session?.codeFiles, session?.latestCode || ''),
    [session?.codeFiles, session?.latestCode, session?.technology]
  );
  const activeCodeFile = React.useMemo(
    () => resultCodeFiles.find((file) => file.path === activeCodePath) ?? resultCodeFiles[0] ?? null,
    [activeCodePath, resultCodeFiles]
  );

  React.useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
          navigate('/');
      }
    };

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [navigate]);

  React.useEffect(() => {
    if (!resultCodeFiles.length) {
      setActiveCodePath('');
      return;
    }

    setActiveCodePath((previous) => (
      resultCodeFiles.some((file) => file.path === previous) ? previous : resultCodeFiles[0].path
    ));
  }, [resultCodeFiles]);

  if (isLoading) return <div className="page-shell"><div className="page-card">Loading result...</div></div>;
  if (!session) return <div className="page-shell"><div className="page-card">Session not found</div></div>;

  const interviewer = session.participants.find((participant) => participant.role === 'INTERVIEWER');
  const interviewee = session.participants.find((participant) => participant.role === 'INTERVIEWEE');
  const isTokenExpired = session.status === 'EXPIRED';
  const activityEvents = session.activityEvents || [];
  const tabSwitchEvents = activityEvents.filter(
    (event) => event.eventType === 'TAB_HIDDEN' && !event.detail.toLowerCase().includes('closed or refreshed the browser/tab')
  );
  const browserCloseRefreshEvents = activityEvents.filter(
    (event) => event.eventType === 'TAB_HIDDEN' && event.detail.toLowerCase().includes('closed or refreshed the browser/tab')
  );
  const pasteEvents = activityEvents.filter((event) => event.eventType === 'PASTE_IN_EDITOR');
  const blockedDropEvents = activityEvents.filter((event) => event.eventType === 'EXTERNAL_DROP_BLOCKED');
  const cameraStreamLostEvents = activityEvents.filter((event) => event.eventType === 'CAMERA_STREAM_LOST');
  const microphoneDisabledEvents = activityEvents.filter((event) => event.eventType === 'MICROPHONE_DISABLED_MANUALLY');
  const cameraDisabledEvents = activityEvents.filter((event) => event.eventType === 'CAMERA_DISABLED_MANUALLY');
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
        <Button onClick={() => navigate('/')}>Close (Esc)</Button>
      </div>

      <div className="result-summary-card result-summary-layout">
        <div className="result-summary-details">
          <p><strong>Created:</strong> {formatDateTime(session.createdAt)}</p>
          {session.startedAt && <p><strong>Started:</strong> {formatDateTime(session.startedAt)}</p>}
          {session.endedAt && <p><strong>Ended:</strong> {formatDateTime(session.endedAt)}</p>}
          <p><strong>Allocated duration:</strong> {Math.round(session.durationSec / 60)} minutes</p>
          {session.feedback && (
            <>
              <p><strong>Rating:</strong> {formatRating(session.feedback.rating)}</p>
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
                    <span className="activity-metric-label">Browser refresh / close</span>
                    <strong>{browserCloseRefreshEvents.length}</strong>
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
                    <span className="activity-metric-label">Mic turned off</span>
                    <strong>{microphoneDisabledEvents.length}</strong>
                  </div>
                  <div className="activity-metric">
                    <span className="activity-metric-label">Camera turned off</span>
                    <strong>{cameraDisabledEvents.length}</strong>
                  </div>
                </div>
                <div className="activity-summary-note">
                  <p>
                    <strong>Summary:</strong> {tabSwitchEvents.length} tab switch event{tabSwitchEvents.length === 1 ? '' : 's'}, {browserCloseRefreshEvents.length} browser refresh/close event{browserCloseRefreshEvents.length === 1 ? '' : 's'}, {pasteEvents.length} paste event{pasteEvents.length === 1 ? '' : 's'}, {blockedDropEvents.length} blocked drag-and-drop attempt{blockedDropEvents.length === 1 ? '' : 's'}, {cameraStreamLostEvents.length} camera interruption{cameraStreamLostEvents.length === 1 ? '' : 's'}, {microphoneDisabledEvents.length} microphone-off event{microphoneDisabledEvents.length === 1 ? '' : 's'}, and {cameraDisabledEvents.length} camera-off event{cameraDisabledEvents.length === 1 ? '' : 's'} were recorded during the session.
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
            {isFrontendWorkspaceSession && resultCodeFiles.length > 0 ? (
              <div className="result-code-workspace">
                <div className="result-code-tabs" role="tablist" aria-label={`Final ${session.technology} workspace files`}>
                  {resultCodeFiles.map((file) => {
                    const isActive = file.path === activeCodePath;
                    return (
                      <button
                        key={file.path}
                        type="button"
                        role="tab"
                        aria-selected={isActive}
                        className={`result-code-tab ${isActive ? 'is-active' : ''} ${file.editable ? '' : 'is-readonly'}`}
                        onClick={() => setActiveCodePath(file.path)}
                      >
                        <span>{file.displayName}</span>
                        {!file.editable ? <span className="result-code-tab-meta">Read only</span> : null}
                      </button>
                    );
                  })}
                </div>
                <pre className="result-pre code-pre workspace-code-pre">{activeCodeFile?.content || '(no code captured)'}</pre>
              </div>
            ) : (
              <pre className="result-pre code-pre">{session.latestCode || '(no code captured)'}</pre>
            )}
          </section>

          <section className="result-panel">
            <h3>Final Output</h3>
            <pre className="result-pre">{session.finalRunResult?.stdout || '(no output)'}</pre>
          </section>

          <section className="result-panel">
            <h3>Final Errors</h3>
            <pre className="result-pre">{session.finalRunResult?.stderr || '(no errors)'}</pre>
          </section>

          {hasSuccessfulFrontendPreview && (
            <section className="result-panel">
              <h3>Final Preview</h3>
              <div className="result-preview">
                <iframe
                  title={`Final ${session.technology} Preview`}
                  src={session.finalPreviewUrl!}
                  className="result-preview-frame"
                />
              </div>
            </section>
          )}
        </div>
      )}
    </div>
  );
};

export default Result;

function buildResultCodeFiles(technology: string, codeFiles: EditableCodeFile[] | undefined, latestCode: string) {
  if (technology !== 'ANGULAR' && technology !== 'REACT') {
    return [];
  }

  const persistedFiles = (codeFiles || []).map((file) => ({ ...file }));
  const files = persistedFiles.length > 0
    ? persistedFiles
    : [{
        path: technology === 'REACT' ? 'src/App.tsx' : 'src/app/app.component.ts',
        displayName: technology === 'REACT' ? 'App.tsx' : 'app.component.ts',
        content: latestCode || '',
        editable: true,
        sortOrder: 0,
      }];

  if (!files.some((file) => file.path === 'package.json')) {
    files.push({
      path: 'package.json',
      displayName: 'package.json',
      content: technology === 'REACT' ? REACT_PACKAGE_JSON : ANGULAR_PACKAGE_JSON,
      editable: false,
      sortOrder: 999,
    });
  }

  return [...files].sort((left, right) => {
    if (left.path === 'package.json') return -1;
    if (right.path === 'package.json') return 1;
    return (left.sortOrder ?? 0) - (right.sortOrder ?? 0);
  });
}

function formatRecommendation(value: string) {
  return value === 'REEVALUATION'
    ? 'Reevaluation'
    : value.charAt(0) + value.slice(1).toLowerCase();
}

function formatRating(value: string) {
  return value.charAt(0) + value.slice(1).toLowerCase();
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
