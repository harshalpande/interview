import React from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { sessionApi } from '../services/sessionApi';
import { Button } from '../components/Button';
import { formatDateTime } from '../utils/dateTime';
import type { AuthAuditEvent, EditableCodeFile } from '../types/session';

import './Result.css';

type ResultTabKey = 'overview' | 'code' | 'preview' | 'audit' | 'suspicious' | 'ai' | 'human';
type MetricTone = 'best' | 'good' | 'average' | 'worse' | 'worst' | 'neutral';
type AuditStageKey = 'registration' | 'delivery' | 'verification' | 'readiness';
type FinalPreviewStatus = 'unknown' | 'available' | 'missing';

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
  const [finalPreviewStatus, setFinalPreviewStatus] = React.useState<FinalPreviewStatus>('unknown');

  const { data: session, isLoading } = useQuery({
    queryKey: ['session', sessionId],
    queryFn: () => sessionApi.getSession(sessionId!),
    enabled: !!sessionId,
  });
  const isFrontendWorkspaceSession = session?.technology === 'ANGULAR' || session?.technology === 'REACT';
  const isCodeWorkspaceSession = isFrontendWorkspaceSession || session?.technology === 'JAVA' || session?.technology === 'PYTHON';
  const finalPreviewUrl = session?.finalPreviewUrl || '';
  const hasSuccessfulFrontendPreview = Boolean(
    isFrontendWorkspaceSession && finalPreviewUrl && finalPreviewStatus === 'available'
  );
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
        if (document.body.dataset.resultMetricExpanded) {
          return;
        }
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

  React.useEffect(() => {
    if (!finalPreviewUrl) {
      setFinalPreviewStatus('missing');
      return;
    }

    let cancelled = false;
    setFinalPreviewStatus('unknown');

    fetch(finalPreviewUrl, { headers: { Accept: 'text/html' } })
      .then((response) => {
        if (cancelled) {
          return;
        }
        const contentType = response.headers.get('content-type') || '';
        setFinalPreviewStatus(response.ok && contentType.includes('text/html') ? 'available' : 'missing');
      })
      .catch(() => {
        if (!cancelled) {
          setFinalPreviewStatus('missing');
        }
      });

    return () => {
      cancelled = true;
    };
  }, [finalPreviewUrl]);

  if (isLoading) return <div className="page-shell"><div className="page-card">Loading result...</div></div>;
  if (!session) return <div className="page-shell"><div className="page-card">Session not found</div></div>;

  const interviewer = session.participants.find((participant) => participant.role === 'INTERVIEWER');
  const interviewee = session.participants.find((participant) => participant.role === 'INTERVIEWEE');
  const isInAppAvSession = session.avMode === 'IN_APP';
  const isPreSessionExpired = session.status === 'EXPIRED';
  const isAiInterviewSession = session.interviewMode === 'AI_INTERVIEWER';
  const activityEvents = session.activityEvents || [];
  const authAuditEvents = session.authAuditEvents || [];
  const suspiciousEvents = activityEvents.filter((event) => event.severity === 'SUSPICIOUS' || !event.severity);
  const warningEvents = activityEvents.filter((event) => event.severity === 'WARNING');
  const infoEvents = activityEvents.filter((event) => event.severity === 'INFO');
  const tabSwitchEvents = activityEvents.filter(
    (event) => event.eventType === 'TAB_HIDDEN' && !event.detail.toLowerCase().includes('closed or refreshed the browser/tab')
  );
  const browserCloseRefreshEvents = activityEvents.filter(
    (event) => event.eventType === 'TAB_HIDDEN' && event.detail.toLowerCase().includes('closed or refreshed the browser/tab')
  );
  const pasteEvents = activityEvents.filter((event) => event.eventType === 'PASTE_IN_EDITOR');
  const copyEvents = activityEvents.filter((event) => event.eventType === 'COPY_FROM_EDITOR');
  const blockedDropEvents = activityEvents.filter((event) => event.eventType === 'EXTERNAL_DROP_BLOCKED');
  const cameraStreamLostEvents = activityEvents.filter((event) => event.eventType === 'CAMERA_STREAM_LOST');
  const microphoneDisabledEvents = activityEvents.filter((event) => event.eventType === 'MICROPHONE_DISABLED_MANUALLY');
  const cameraDisabledEvents = activityEvents.filter((event) => event.eventType === 'CAMERA_DISABLED_MANUALLY');
  const latestActivity = activityEvents.length ? activityEvents[activityEvents.length - 1] : null;
  const snapshotUrl = interviewee?.identityCaptureStatus === 'SUCCESS'
    ? sessionApi.getIdentityCaptureImageUrl(session.id, 'INTERVIEWEE')
    : null;
  const showExecutionTabs = !isPreSessionExpired;
  const hasAiQuestionEvidence = resultCodeFiles.some((file) => file.content?.includes('AI Generated Problem Statement:') || file.aiEvaluation);
  const showAiTab = !isPreSessionExpired && (session.interviewMode === 'AI_INTERVIEWER' || Boolean(session.aiRecommendation) || hasAiQuestionEvidence);
  const showHumanRecommendationTab = !isAiInterviewSession && Boolean(session.feedback || session.feedbackDraft);
  const resultTabs = [
    { key: 'overview' as const, label: 'Overview' },
    ...(showExecutionTabs ? [{ key: 'code' as const, label: 'Code' }] : []),
    ...(hasSuccessfulFrontendPreview ? [{ key: 'preview' as const, label: 'Preview' }] : []),
    { key: 'audit' as const, label: 'Audit' },
    ...(!isPreSessionExpired ? [{ key: 'suspicious' as const, label: 'Integrity Activity' }] : []),
    ...(showAiTab ? [{ key: 'ai' as const, label: 'AI Recommendation' }] : []),
    ...(showHumanRecommendationTab ? [{ key: 'human' as const, label: `${firstName(interviewer?.name, 'Interviewer')}'s Recommendation` }] : []),
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
            <div className="participant-info">
              <span className="participant-label">AI Recommendation</span>
              <span className="participant-name">{aiRecommendationHeaderValue(session)}</span>
              <span>{aiRecommendationHeaderDetail(session)}</span>
            </div>
            {showHumanRecommendationTab ? (
              <div className="participant-info">
                <span className="participant-label">{firstName(interviewer?.name, 'Interviewer')}'s Recommendation</span>
                <span className="participant-name">{humanRecommendationHeaderValue(session.feedback || session.feedbackDraft)}</span>
                <span>{humanRecommendationHeaderDetail(session.feedback || session.feedbackDraft)}</span>
              </div>
            ) : null}
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
              {isCodeWorkspaceSession && resultCodeFiles.length > 0 ? (
                <div className="result-code-workspace">
                  <div className="result-code-tabs" role="tablist" aria-label={`Final ${session.technology} workspace files`}>
                    {resultCodeFiles.map((file, index) => {
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
                          <span>{resultCodeTabLabel(session.technology, file, index)}</span>
                        </button>
                      );
                    })}
                  </div>
                  <pre className="result-pre code-pre workspace-code-pre">{activeCodeFile?.content || '(no code captured)'}</pre>
                  {(session.technology === 'JAVA' || session.technology === 'PYTHON') && activeCodeFile ? (
                    <div className="question-result-grid">
                      <MetricCard title="Question run status" tone={runStatusTone(activeCodeFile)}>
                        <span>{activeCodeFile.runResult ? resultStatusLabel(activeCodeFile.runResult.exitStatus) : 'Not run'}</span>
                      </MetricCard>
                      <MetricCard title="Time taken" tone={timeTakenTone(activeCodeFile)}>
                        <span>{formatSolveDuration(activeCodeFile.solveDurationSeconds)}</span>
                      </MetricCard>
                      <MetricCard title="Expected solve time" tone="neutral">
                        <span>{formatExpectedSolveTime(activeCodeFile.idealDurationMinutes)}</span>
                      </MetricCard>
                      <MetricCard title="Execute attempts" tone={attemptTone(activeCodeFile.executeAttemptCount)}>
                        <span>{formatExecuteAttemptCount(activeCodeFile.executeAttemptCount)}</span>
                      </MetricCard>
                      <MetricCard title="Difficulty level" tone="neutral">
                        <span>{activeCodeFile.difficultyLevel ? `Level ${activeCodeFile.difficultyLevel}` : 'Not captured'}</span>
                      </MetricCard>
                      <MetricCard title="Expected complexity" tone="neutral">
                        <ComplexityLines time={activeCodeFile.expectedTimeComplexity} space={activeCodeFile.expectedSpaceComplexity} />
                      </MetricCard>
                      <MetricCard title="Actual complexity" tone={actualComplexityTone(activeCodeFile)}>
                        <ComplexityLines
                          time={actualComplexityLabel(activeCodeFile, 'time')}
                          space={actualComplexityLabel(activeCodeFile, 'space')}
                        />
                      </MetricCard>
                      <MetricCard title="Question integrity" tone={integrityTone(activeCodeFile)} expandable>
                        <pre>{activeCodeFile.aiEvaluation?.questionIntegrityNotes || activeCodeFile.questionIntegrityNotes || 'Not captured'}</pre>
                      </MetricCard>
                      {activeCodeFile.aiEvaluation ? (
                        <MetricCard title="AI evaluation" tone={aiEvaluationTone(activeCodeFile)} expandable>
                          <span>{formatAiScore(activeCodeFile.aiEvaluation.overallScore)} {activeCodeFile.aiEvaluation.verdict ? `- ${activeCodeFile.aiEvaluation.verdict}` : ''}</span>
                          <pre>{activeCodeFile.aiEvaluation.summary || '(no AI summary captured)'}</pre>
                        </MetricCard>
                      ) : null}
                      <MetricCard title="Output" tone={outputTone(activeCodeFile, session.technology)} expandable>
                        <pre>{activeCodeFile.runResult?.stdout || '(no output captured)'}</pre>
                      </MetricCard>
                      <MetricCard title="Errors" tone={errorTone(activeCodeFile)} expandable>
                        <pre>{activeCodeFile.runResult?.stderr || '(no errors captured)'}</pre>
                      </MetricCard>
                    </div>
                  ) : null}
                </div>
              ) : (session.technology === 'JAVA' || session.technology === 'PYTHON') && (session.codeFiles || []).length > 0 ? (
                <p className="activity-empty">No attempted question code was captured for this interview.</p>
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
                  src={finalPreviewUrl}
                  className="result-preview-frame"
                />
              </div>
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
                <span>Integrity Activity</span>
                {activityEvents.length ? <span className="result-section-total">Total {activityEvents.length}</span> : null}
              </h3>
              {activityEvents.length ? (
                <div className="activity-summary">
                  <div className="activity-summary-grid">
                    <div className="activity-metric">
                      <span className="activity-metric-label">Suspicious</span>
                      <strong>{suspiciousEvents.length}</strong>
                    </div>
                    <div className="activity-metric">
                      <span className="activity-metric-label">Warnings</span>
                      <strong>{warningEvents.length}</strong>
                    </div>
                    <div className="activity-metric">
                      <span className="activity-metric-label">Info</span>
                      <strong>{infoEvents.length}</strong>
                    </div>
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
                      <span className="activity-metric-label">Copy attempts</span>
                      <strong>{copyEvents.length}</strong>
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
                      <strong>Summary:</strong> {suspiciousEvents.length} suspicious event{suspiciousEvents.length === 1 ? '' : 's'}, {warningEvents.length} warning{warningEvents.length === 1 ? '' : 's'}, and {infoEvents.length} informational signal{infoEvents.length === 1 ? '' : 's'} were recorded. {tabSwitchEvents.length} tab switch event{tabSwitchEvents.length === 1 ? '' : 's'}, {browserCloseRefreshEvents.length} browser refresh/close event{browserCloseRefreshEvents.length === 1 ? '' : 's'}, {copyEvents.length} copy attempt{copyEvents.length === 1 ? '' : 's'}, {pasteEvents.length} paste event{pasteEvents.length === 1 ? '' : 's'}, and {blockedDropEvents.length} blocked drag-and-drop attempt{blockedDropEvents.length === 1 ? '' : 's'} were observed during the session.{isInAppAvSession ? ` ${cameraStreamLostEvents.length} camera interruption${cameraStreamLostEvents.length === 1 ? '' : 's'}, ${microphoneDisabledEvents.length} microphone-off event${microphoneDisabledEvents.length === 1 ? '' : 's'}, and ${cameraDisabledEvents.length} camera-off event${cameraDisabledEvents.length === 1 ? '' : 's'} were also recorded through the in-app AV workflow.` : ' Live AV was handled outside the platform for this session, so focus-away events are reviewed with more context.'}
                    </p>
                    {latestActivity ? (
                      <p>
                        <strong>Most recent:</strong> {latestActivity.detail} at {formatDateTime(latestActivity.createdAt)}.
                      </p>
                    ) : null}
                  </div>
                </div>
              ) : (
                <p className="activity-empty">No integrity activity was observed.</p>
              )}
            </section>
          )}

          {activeTab === 'ai' && (
            <section className="result-panel ai-result-panel">
              <h3>AI Recommendation</h3>
              {session.aiRecommendation ? (
                <div className="ai-result-layout">
                  <div className="question-result-grid ai-score-grid">
                    <MetricCard title="Rating" tone={recommendationRatingTone(session.aiRecommendation.rating)}>
                      <span>{formatOptionalLabel(session.aiRecommendation.rating)}</span>
                    </MetricCard>
                    <MetricCard title="Recommendation" tone={recommendationDecisionTone(session.aiRecommendation.recommendationDecision)}>
                      <span>{formatOptionalLabel(session.aiRecommendation.recommendationDecision)}</span>
                    </MetricCard>
                    <MetricCard title="Score" tone={scoreTone(session.aiRecommendation.overallScore)}>
                      <span>{formatAiScore(session.aiRecommendation.overallScore)}</span>
                    </MetricCard>
                    <MetricCard title="Human review" tone="neutral">
                      <span>{session.aiRecommendation.humanReviewRequired === false ? 'Optional' : 'Required'}</span>
                    </MetricCard>
                  </div>
                  <div className="ai-result-summary">
                    <h4>Summary</h4>
                    <p>{session.aiRecommendation.summary || 'No AI recommendation summary was captured.'}</p>
                    <AiList title="Strengths" items={session.aiRecommendation.strengths} />
                    <AiList title="Risks" items={session.aiRecommendation.risks} />
                    <AiList title="Review focus" items={session.aiRecommendation.suggestedFollowUps} />
                    {session.aiRecommendation.generatedAt ? <p className="activity-empty">Generated {formatDateTime(session.aiRecommendation.generatedAt)}</p> : null}
                  </div>
                </div>
              ) : (
                <p className="activity-empty">No AI recommendation has been generated for this interview yet.</p>
              )}
            </section>
          )}

          {activeTab === 'human' && (
            <section className="result-panel ai-result-panel">
              <h3>{firstName(interviewer?.name, 'Interviewer')}'s Recommendation</h3>
              {session.feedback || session.feedbackDraft ? (
                <div className="ai-result-layout">
                  <div className="question-result-grid ai-score-grid">
                    <MetricCard title="Rating" tone={recommendationRatingTone((session.feedback || session.feedbackDraft)?.rating)}>
                      <span>{formatOptionalLabel((session.feedback || session.feedbackDraft)?.rating)}</span>
                    </MetricCard>
                    <MetricCard title="Recommendation" tone={recommendationDecisionTone((session.feedback || session.feedbackDraft)?.recommendationDecision)}>
                      <span>{formatOptionalLabel((session.feedback || session.feedbackDraft)?.recommendationDecision)}</span>
                    </MetricCard>
                    <MetricCard title="Review status" tone={session.feedback ? 'best' : 'average'}>
                      <span>{session.feedback ? 'Submitted' : 'Draft'}</span>
                    </MetricCard>
                  </div>
                  <div className="ai-result-summary">
                    <h4>Feedback</h4>
                    <p>{(session.feedback || session.feedbackDraft)?.comments || 'No interviewer comments captured.'}</p>
                    {session.feedback?.submittedAt ? <p className="activity-empty">Submitted {formatDateTime(session.feedback.submittedAt)}</p> : null}
                  </div>
                </div>
              ) : (
                <p className="activity-empty">No interviewer recommendation has been captured yet.</p>
              )}
            </section>
          )}
        </div>
      </div>
    </div>
  );
};

export default Result;

function AiList({ title, items }: { title: string; items?: string[] | null }) {
  const normalizedItems = (items || []).filter((item) => item.trim()).slice(0, 3);
  if (!normalizedItems.length) {
    return null;
  }
  return (
    <div className="ai-result-list">
      <h4>{title}</h4>
      <ul>
        {normalizedItems.map((item) => <li key={item}>{item}</li>)}
      </ul>
    </div>
  );
}

function MetricCard({ title, tone, children, expandable = false }: { title: string; tone?: MetricTone; children: React.ReactNode; expandable?: boolean }) {
  const [expanded, setExpanded] = React.useState(false);

  React.useEffect(() => {
    if (!expanded) {
      if (document.body.dataset.resultMetricExpanded === title) {
        delete document.body.dataset.resultMetricExpanded;
      }
      return;
    }
    document.body.dataset.resultMetricExpanded = title;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        event.stopPropagation();
        setExpanded(false);
      }
    };
    window.addEventListener('keydown', onKeyDown, true);
    return () => {
      window.removeEventListener('keydown', onKeyDown, true);
      if (document.body.dataset.resultMetricExpanded === title) {
        delete document.body.dataset.resultMetricExpanded;
      }
    };
  }, [expanded, title]);

  return (
    <div className={`question-result-card metric-${tone || 'neutral'} ${expandable ? 'is-expandable' : 'is-compact'}`}>
      <div className="metric-card-heading">
        <strong>{title}</strong>
        {expandable ? (
          <button type="button" className="metric-expand-button" onClick={() => setExpanded(true)} aria-label={`Expand ${title}`}>
            +
          </button>
        ) : null}
      </div>
      {children}
      {expanded ? (
        <div className="metric-expanded-backdrop" role="presentation" onClick={() => setExpanded(false)}>
          <div className={`metric-expanded-card metric-${tone || 'neutral'}`} role="dialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>
            <div className="metric-expanded-heading">
              <strong>{title}</strong>
              <button type="button" className="metric-collapse-button" onClick={() => setExpanded(false)} aria-label={`Collapse ${title}`}>(Esc) X</button>
            </div>
            <div className="metric-expanded-content">{children}</div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function ComplexityLines({ time, space }: { time?: string | null; space?: string | null }) {
  if (!time && !space) {
    return <span>Not captured</span>;
  }
  return (
    <span className="complexity-lines">
      <span>Time: {time || 'Not captured'}</span>
      <span>Space: {space || 'Not captured'}</span>
    </span>
  );
}

function buildResultCodeFiles(technology: string, codeFiles: EditableCodeFile[] | undefined, latestCode: string) {
  if (technology !== 'ANGULAR' && technology !== 'REACT' && technology !== 'JAVA' && technology !== 'PYTHON') {
    return [];
  }

  const incomingCodeFiles = codeFiles || [];
  const persistedFiles = incomingCodeFiles
    .filter((file) => shouldShowResultCodeFile(technology, file))
    .map((file) => ({ ...file }));
  if ((technology === 'JAVA' || technology === 'PYTHON') && incomingCodeFiles.length > 0 && persistedFiles.length === 0) {
    return [];
  }
  const files = persistedFiles.length > 0
    ? persistedFiles
    : [{
        path: defaultResultFilePath(technology),
        displayName: technology === 'JAVA' || technology === 'PYTHON' ? 'Question 1' : basename(defaultResultFilePath(technology)),
        content: latestCode || '',
        editable: true,
        sortOrder: 0,
        enabledForCandidate: true,
        activeQuestion: true,
      }];

  if ((technology === 'ANGULAR' || technology === 'REACT') && !files.some((file) => file.path === 'package.json')) {
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

function shouldShowResultCodeFile(technology: string, file: EditableCodeFile) {
  if (technology !== 'JAVA' && technology !== 'PYTHON') {
    return true;
  }
  if (file.submitted === true || file.runResult || file.aiEvaluation) {
    return true;
  }
  if ((file.executeAttemptCount ?? 0) > 0) {
    return true;
  }
  return typeof file.solveDurationSeconds === 'number' && file.solveDurationSeconds > 0;
}

function defaultResultFilePath(technology: string) {
  if (technology === 'REACT') return 'src/App.tsx';
  if (technology === 'ANGULAR') return 'src/app/app.component.ts';
  if (technology === 'PYTHON') return 'question-1.py';
  return 'Question1.java';
}

function basename(path: string) {
  const normalized = path.replace(/\\/g, '/');
  const segments = normalized.split('/');
  return segments[segments.length - 1] || normalized;
}

function resultStatusLabel(exitStatus?: number | null) {
  if (exitStatus === 0) return 'Successful run';
  if (typeof exitStatus === 'number') return `Failed with exit code ${exitStatus}`;
  return 'Run captured';
}

function formatSolveDuration(seconds?: number | null) {
  if (typeof seconds !== 'number' || !Number.isFinite(seconds)) {
    return 'Not captured';
  }

  const safeSeconds = Math.max(0, Math.round(seconds));
  const minutes = Math.floor(safeSeconds / 60);
  const remainingSeconds = safeSeconds % 60;
  if (minutes <= 0) {
    return `${remainingSeconds}s`;
  }
  if (remainingSeconds === 0) {
    return `${minutes}m`;
  }
  return `${minutes}m ${remainingSeconds}s`;
}

function formatExpectedSolveTime(minutes?: number | null) {
  if (typeof minutes !== 'number' || !Number.isFinite(minutes) || minutes <= 0) {
    return 'Not captured';
  }

  const roundedMinutes = Math.round(minutes);
  return `${roundedMinutes} min${roundedMinutes === 1 ? '' : 's'}`;
}

function formatExecuteAttemptCount(count?: number | null) {
  if (typeof count !== 'number' || !Number.isFinite(count)) {
    return 'Not captured';
  }

  const safeCount = Math.max(0, Math.round(count));
  return `${safeCount}`;
}

function formatAiScore(score?: number | null) {
  return typeof score === 'number' && Number.isFinite(score) ? `${score}/100` : 'Not captured';
}

function aiRecommendationHeaderValue(session: { interviewMode?: string | null; codeFiles?: EditableCodeFile[]; aiRecommendation?: { recommendationDecision?: string | null; rating?: string | null } | null }) {
  if (!session.aiRecommendation) {
    const hasEvidence = (session.codeFiles || []).some((file) => file.content?.includes('AI Generated Problem Statement:') || file.aiEvaluation);
    return session.interviewMode === 'AI_INTERVIEWER' || hasEvidence ? 'Not generated' : 'Not applicable';
  }
  return formatOptionalLabel(session.aiRecommendation.recommendationDecision || session.aiRecommendation.rating);
}

function aiRecommendationHeaderDetail(session: { aiRecommendation?: { overallScore?: number | null; rating?: string | null; humanReviewRequired?: boolean | null } | null }) {
  const recommendation = session.aiRecommendation;
  if (!recommendation) {
    return 'no AI result';
  }
  const score = formatAiScore(recommendation.overallScore);
  const rating = formatOptionalLabel(recommendation.rating);
  const review = recommendation.humanReviewRequired === false ? 'review optional' : 'human review required';
  return `${rating}; ${score}; ${review}`;
}

function humanRecommendationHeaderValue(feedback?: { recommendationDecision?: string | null; rating?: string | null } | null) {
  if (!feedback) {
    return 'Not submitted';
  }
  return formatOptionalLabel(feedback.recommendationDecision || feedback.rating);
}

function humanRecommendationHeaderDetail(feedback?: { rating?: string | null; submittedAt?: string | null } | null) {
  if (!feedback) {
    return 'awaiting feedback';
  }
  return feedback.submittedAt
    ? `${formatOptionalLabel(feedback.rating)}; submitted`
    : `${formatOptionalLabel(feedback.rating)}; draft`;
}

function firstName(value: string | undefined, fallback: string) {
  const normalized = value?.trim();
  if (!normalized) {
    return fallback;
  }
  return normalized.split(/\s+/)[0] || fallback;
}

function runStatusTone(file: EditableCodeFile): MetricTone {
  if (!file.runResult) return 'worst';
  return file.runResult.exitStatus === 0 ? 'best' : 'worst';
}

function timeTakenTone(file: EditableCodeFile): MetricTone {
  if (typeof file.solveDurationSeconds !== 'number' || file.solveDurationSeconds <= 0) return 'worst';
  const idealSeconds = (file.idealDurationMinutes || 12) * 60;
  if (file.solveDurationSeconds <= idealSeconds) return 'best';
  if (file.solveDurationSeconds <= idealSeconds * 1.25) return 'good';
  if (file.solveDurationSeconds <= idealSeconds * 1.5) return 'average';
  return 'worse';
}

function attemptTone(count?: number | null): MetricTone {
  if (typeof count !== 'number' || !Number.isFinite(count)) return 'worst';
  if (count <= 0) return 'worst';
  if (count <= 2) return 'best';
  if (count <= 4) return 'good';
  if (count <= 6) return 'average';
  if (count <= 9) return 'worse';
  return 'worst';
}

function integrityTone(file: EditableCodeFile): MetricTone {
  const text = `${file.aiEvaluation?.questionIntegrityNotes || ''} ${file.questionIntegrityNotes || ''}`.toLowerCase();
  if (!text.trim()) return 'average';
  if (text.includes('healthy: true')) return 'best';
  if (text.includes('healthy: false')) return 'worst';
  if (hasIntegrityConcern(text)) return 'worst';
  return 'best';
}

function outputTone(file: EditableCodeFile, technology: string): MetricTone {
  const output = file.runResult?.stdout?.trim() || '';
  if (!output) return technology === 'JAVA' || technology === 'PYTHON' ? 'worst' : 'average';
  if (output.toLowerCase().includes('all assertions passed')) return 'best';
  return file.runResult?.exitStatus === 0 ? 'good' : 'average';
}

function errorTone(file: EditableCodeFile): MetricTone {
  return file.runResult?.stderr?.trim() ? 'worst' : 'best';
}

function aiEvaluationTone(file: EditableCodeFile): MetricTone {
  const score = file.aiEvaluation?.overallScore;
  if (typeof score !== 'number') return 'average';
  return scoreTone(score);
}

function actualComplexityTone(file: EditableCodeFile): MetricTone {
  return complexityWordTone(actualComplexityLabel(file, 'time'));
}

function actualComplexityLabel(file: EditableCodeFile, dimension: 'time' | 'space') {
  const text = file.aiEvaluation?.complexityAssessment?.toLowerCase() || '';
  const dimensionText = dimension === 'time' ? text.split(/space[:\s]/i)[0] || text : text;
  if (!file.aiEvaluation) return 'Not captured';
  if (/optimal|matches|same as expected|as expected|efficient|o\(1\)/i.test(dimensionText)) return 'Best';
  if (/acceptable|reasonable|near|close/i.test(dimensionText)) return 'Good';
  if (/average|moderate/i.test(dimensionText)) return 'Better';
  if (/higher|extra|suboptimal|inefficient|worse/i.test(dimensionText)) return 'Worse';
  if (/exponential|very high|poor|bad|unbounded/i.test(dimensionText)) return 'Worst';
  return complexityWord(file.aiEvaluation.efficiencyScore);
}

function complexityWord(score?: number | null) {
  if (typeof score !== 'number') return 'Not captured';
  if (score >= 90) return 'Best';
  if (score >= 75) return 'Good';
  if (score >= 55) return 'Better';
  if (score >= 35) return 'Worse';
  return 'Worst';
}

function complexityWordTone(value: string): MetricTone {
  if (value === 'Best') return 'best';
  if (value === 'Good') return 'good';
  if (value === 'Better') return 'average';
  if (value === 'Worse') return 'worse';
  if (value === 'Worst') return 'worst';
  return 'average';
}

function scoreTone(score?: number | null): MetricTone {
  if (typeof score !== 'number') return 'average';
  if (score >= 85) return 'best';
  if (score >= 70) return 'good';
  if (score >= 50) return 'average';
  if (score >= 35) return 'worse';
  return 'worst';
}

function recommendationRatingTone(rating?: string | null): MetricTone {
  if (!rating) return 'average';
  switch (rating.toUpperCase()) {
    case 'EXCELLENT': return 'best';
    case 'GOOD': return 'good';
    case 'FAIR': return 'average';
    case 'BAD': return 'worse';
    case 'DISQUALIFIED': return 'worst';
    default: return 'average';
  }
}

function recommendationDecisionTone(decision?: string | null): MetricTone {
  if (decision === 'YES') return 'best';
  if (decision === 'REEVALUATION') return 'average';
  if (decision === 'NO') return 'worst';
  return 'average';
}

function hasIntegrityConcern(text: string) {
  return /changed|removed|tamper|mismatch|integrity concern/i.test(text);
}

function formatOptionalLabel(value?: string | null) {
  if (!value) {
    return 'Not captured';
  }
  return value
    .toLowerCase()
    .split('_')
    .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
    .join(' ');
}

function resultCodeTabLabel(technology: string, file: EditableCodeFile, index: number) {
  if (technology === 'JAVA' || technology === 'PYTHON') {
    return `Question ${guidedQuestionNumber(file.path, index + 1)}`;
  }
  return file.displayName;
}

function guidedQuestionNumber(path: string, fallbackIndex: number) {
  const match = basename(path).match(/(\d+)/);
  return match ? Number(match[1]) : fallbackIndex;
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
