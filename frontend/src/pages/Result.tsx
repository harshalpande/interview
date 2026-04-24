import React from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { sessionApi } from '../services/sessionApi';
import { Button } from '../components/Button';
import { formatDateTime } from '../utils/dateTime';
import type { AuthAuditEvent, EditableCodeFile } from '../types/session';

import './Result.css';

type ResultTabKey = 'overview' | 'code' | 'preview' | 'output' | 'errors' | 'audit' | 'suspicious';
type AuditStageKey = 'registration' | 'delivery' | 'verification' | 'readiness';

const STATUS_LABELS: Record<string, string> = {
  REGISTERED: 'Registered',
  AUTH_IN_PROGRESS: 'Authentication In Progress',
  READY_TO_START: 'Ready to Start',
  ACTIVE: 'Interview In Progress',
  ENDED: 'Ended',
  AUTH_FAILED: 'Authentication Failed',
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
  const [searchParams, setSearchParams] = useSearchParams();
  const [activeCodePath, setActiveCodePath] = React.useState<string>('');
  const [activeAuditStage, setActiveAuditStage] = React.useState<AuditStageKey>('registration');

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
  const isInAppAvSession = session.avMode === 'IN_APP';
  const isPreSessionExpired = session.status === 'EXPIRED';
  const activityEvents = session.activityEvents || [];
  const authAuditEvents = session.authAuditEvents || [];
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
  const hasFinalErrors = Boolean(session.finalRunResult?.stderr?.trim());
  const showExecutionTabs = !isPreSessionExpired;
  const resultTabs = [
    { key: 'overview' as const, label: 'Overview' },
    ...(showExecutionTabs ? [{ key: 'code' as const, label: 'Code' }] : []),
    ...(hasSuccessfulFrontendPreview ? [{ key: 'preview' as const, label: 'Preview' }] : []),
    ...(showExecutionTabs ? [{ key: 'output' as const, label: 'Output' }] : []),
    ...(showExecutionTabs && hasFinalErrors ? [{ key: 'errors' as const, label: 'Errors' }] : []),
    { key: 'audit' as const, label: 'Audit' },
    ...(!isPreSessionExpired ? [{ key: 'suspicious' as const, label: 'Suspicious Activity' }] : []),
  ];
  const requestedTab = searchParams.get('tab') as ResultTabKey | null;
  const activeTab = resultTabs.some((tab) => tab.key === requestedTab) ? requestedTab! : 'overview';
  const auditJourneyStages = buildAuditJourneyStages(authAuditEvents);
  const selectedAuditStage = auditJourneyStages.find((stage) => stage.key === activeAuditStage) ?? auditJourneyStages[0];
  const selectTab = (tab: ResultTabKey) => {
    setSearchParams((previous) => {
      const next = new URLSearchParams(previous);
      if (tab === 'overview') {
        next.delete('tab');
      } else {
        next.set('tab', tab);
      }
      return next;
    }, { replace: true });
  };

  return (
    <div className="result-page polished-page">
      <div className="result-header">
        <div>
          <div className="page-kicker">{session.status === 'ENDED' ? 'Interview Result' : 'Session Details'}</div>
          <h2>{session.summary || (session.status === 'ENDED' ? 'Interview Summary' : 'Pre-Session Access Summary')}</h2>
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
            <div className="participant-info">
              <span className="participant-label">Suspicious Activity</span>
              <span className="participant-name">{activityEvents.length}</span>
              <span>{activityEvents.length === 1 ? 'event observed' : 'events observed'}</span>
            </div>
          </div>
        </div>
        <Button onClick={() => navigate('/')}>Close (Esc)</Button>
      </div>

      <div className="result-workspace">
        <div className="result-workspace-tabs" role="tablist" aria-label="Result workspace sections">
          {resultTabs.map((tab) => (
            <button
              key={tab.key}
              type="button"
              role="tab"
              aria-selected={activeTab === tab.key}
              className={`result-workspace-tab ${activeTab === tab.key ? 'is-active' : ''}`}
              onClick={() => selectTab(tab.key)}
            >
              {tab.label}
            </button>
          ))}
        </div>

        <div className="result-workspace-panel">
          {activeTab === 'overview' && (
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
                {session.authFailureReason && <p><strong>Authentication failure:</strong> {session.authFailureReason}</p>}
                {session.expiredReason && <p><strong>Expiry reason:</strong> {session.expiredReason}</p>}
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
          )}

          {activeTab === 'code' && (
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
          )}

          {activeTab === 'preview' && hasSuccessfulFrontendPreview && (
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

          {activeTab === 'output' && (
            <section className="result-panel">
              <h3>Final Output</h3>
              <pre className="result-pre">{session.finalRunResult?.stdout || '(no output)'}</pre>
            </section>
          )}

          {activeTab === 'errors' && (
            <section className="result-panel">
              <h3>Final Errors</h3>
              <pre className="result-pre error-pre">{session.finalRunResult?.stderr || '(no errors)'}</pre>
            </section>
          )}

          {activeTab === 'audit' && (
            <section className="result-panel">
              <h3 className="result-section-title">
                <span>Session Access Journey</span>
                {authAuditEvents.length ? <span className="result-section-total">Total {authAuditEvents.length}</span> : null}
              </h3>
              {authAuditEvents.length ? (
                <div className="audit-journey">
                  <div className="audit-stage-rail" aria-label="Session access journey stages">
                    {auditJourneyStages.map((stage) => (
                      <button
                        key={stage.key}
                        type="button"
                        className={`audit-stage-card audit-stage-${stage.key} ${selectedAuditStage.key === stage.key ? 'is-active' : ''}`}
                        onClick={() => setActiveAuditStage(stage.key)}
                        aria-pressed={selectedAuditStage.key === stage.key}
                      >
                        <span className="audit-stage-count">{stage.events.length}</span>
                        <strong>{stage.label}</strong>
                        <span>{stage.description}</span>
                      </button>
                    ))}
                  </div>
                  <div className="audit-stage-details">
                    <div className="audit-stage-section">
                      <div className="audit-stage-heading">
                        <strong>{selectedAuditStage.label}</strong>
                        <span>{selectedAuditStage.events.length} touchpoint{selectedAuditStage.events.length === 1 ? '' : 's'}</span>
                      </div>
                      {selectedAuditStage.events.length ? (
                        <div className="audit-touchpoint-list">
                          {selectedAuditStage.events.map((event, index) => (
                            <div key={`${event.createdAt}-${event.title}-${index}`} className="audit-touchpoint">
                              <div className="audit-touchpoint-dot" aria-hidden="true" />
                              <div className="audit-touchpoint-body">
                                <div className="audit-touchpoint-meta">
                                  <strong>{event.title}</strong>
                                  <span>{formatDateTime(event.createdAt)}</span>
                                </div>
                                <p>
                                  {event.participantRole ? <span className="audit-role-badge">{formatParticipantRole(event.participantRole)}</span> : null}
                                  {event.detail}
                                </p>
                              </div>
                            </div>
                          ))}
                        </div>
                      ) : (
                        <p className="activity-empty">No touchpoints were recorded for this stage.</p>
                      )}
                    </div>
                  </div>
                </div>
              ) : (
                <p className="activity-empty">No pre-session access audit events are available for this record.</p>
              )}
            </section>
          )}

          {activeTab === 'suspicious' && !isPreSessionExpired && (
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
                    {isInAppAvSession ? (
                      <>
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
                      </>
                    ) : null}
                  </div>
                  <div className="activity-summary-note">
                    <p>
                      <strong>Summary:</strong> {tabSwitchEvents.length} tab switch event{tabSwitchEvents.length === 1 ? '' : 's'}, {browserCloseRefreshEvents.length} browser refresh/close event{browserCloseRefreshEvents.length === 1 ? '' : 's'}, {pasteEvents.length} paste event{pasteEvents.length === 1 ? '' : 's'}, and {blockedDropEvents.length} blocked drag-and-drop attempt{blockedDropEvents.length === 1 ? '' : 's'} were recorded during the session.{isInAppAvSession ? ` ${cameraStreamLostEvents.length} camera interruption${cameraStreamLostEvents.length === 1 ? '' : 's'}, ${microphoneDisabledEvents.length} microphone-off event${microphoneDisabledEvents.length === 1 ? '' : 's'}, and ${cameraDisabledEvents.length} camera-off event${cameraDisabledEvents.length === 1 ? '' : 's'} were also recorded through the in-app AV workflow.` : ' Live AV was handled outside the platform for this session, so in-app AV monitoring events were not collected.'}
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
          )}
        </div>
      </div>
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

function formatParticipantRole(role: string) {
  return role === 'INTERVIEWER' ? 'Interviewer' : 'Interviewee';
}

function buildAuditJourneyStages(events: AuthAuditEvent[]) {
  const stages: Array<{
    key: AuditStageKey;
    label: string;
    description: string;
    events: AuthAuditEvent[];
  }> = [
    {
      key: 'registration',
      label: 'Registration',
      description: 'Session record and participants created',
      events: [] as AuthAuditEvent[],
    },
    {
      key: 'delivery',
      label: 'Access Delivery',
      description: 'Secure link and passcode sent',
      events: [] as AuthAuditEvent[],
    },
    {
      key: 'verification',
      label: 'Participant Verification',
      description: 'Disclaimer and passcode completed',
      events: [] as AuthAuditEvent[],
    },
    {
      key: 'readiness',
      label: 'Readiness',
      description: 'Identity, readiness, and session state',
      events: [] as AuthAuditEvent[],
    },
  ];

  events.forEach((event) => {
    const normalized = `${event.title} ${event.detail}`.toLowerCase();
    if (normalized.includes('registration') || normalized.includes('created')) {
      stages[0].events.push(event);
      return;
    }
    if (normalized.includes('sent') || normalized.includes('link') || normalized.includes('secure session started') || normalized.includes('prepared')) {
      stages[1].events.push(event);
      return;
    }
    if (normalized.includes('disclaimer') || normalized.includes('passcode verified') || normalized.includes('otp verified')) {
      stages[2].events.push(event);
      return;
    }
    stages[3].events.push(event);
  });

  return stages;
}
