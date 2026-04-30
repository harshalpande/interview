import React from 'react';
import { sessionApi } from '../services/sessionApi';
import type {
  AiInterviewerQuestionDraftResponse,
  AiQuestionComplexityDirection,
  AiQuestionSectionMode,
  EditableCodeFile,
  SessionResponse,
} from '../types/session';

interface HumanAiAssistantDrawerProps {
  session: SessionResponse;
  sessionId: string;
  interviewerName: string;
  activeFile?: EditableCodeFile | null;
  activeFilePath?: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onAccepted: (session: SessionResponse, activePath: string) => void;
  onMessage: (message: string, tone?: 'warning' | 'danger' | 'info') => void;
}

type AssistantStatus = 'idle' | 'generating' | 'accepting';
type AssistantDraftTab = 'question' | 'solution' | 'complexity' | 'time';
type QuestionMeta = { level: string; section: string };

const shortcutLabel = 'Ctrl + Alt + Q';

const HumanAiAssistantDrawer: React.FC<HumanAiAssistantDrawerProps> = ({
  session,
  sessionId,
  interviewerName,
  activeFile,
  activeFilePath,
  open,
  onOpenChange,
  onAccepted,
  onMessage,
}) => {
  const [status, setStatus] = React.useState<AssistantStatus>('idle');
  const [draft, setDraft] = React.useState<AiInterviewerQuestionDraftResponse | null>(null);
  const [activeDraftTab, setActiveDraftTab] = React.useState<AssistantDraftTab>('question');
  const [complexityDirection, setComplexityDirection] = React.useState<AiQuestionComplexityDirection>('INCREASE');
  const [sectionMode, setSectionMode] = React.useState<AiQuestionSectionMode>('SAME');
  const [confirmAccept, setConfirmAccept] = React.useState(false);
  const [previousAcceptedMeta, setPreviousAcceptedMeta] = React.useState<QuestionMeta | null>(null);
  const currentSection = draft?.section || draft?.question.concepts?.[0] || '';
  const candidateName = session.participants.find((participant) => participant.role === 'INTERVIEWEE')?.name || 'the candidate';
  const shouldCreateNewTab = Boolean(activeFile?.submitted || activeFile?.editable === false);
  const isBusy = status === 'generating' || status === 'accepting';

  const generateDraft = async (mode: 'fresh' | 'regenerate' = 'fresh') => {
    if (status !== 'idle') {
      return;
    }
    onOpenChange(true);
    setStatus('generating');
    try {
      const response = await sessionApi.draftInterviewerAssistedQuestion(sessionId, mode === 'regenerate' ? {
        complexityDirection,
        sectionMode,
        currentSection,
      } : undefined);
      setDraft(response);
      setActiveDraftTab('question');
    } catch (error) {
      onMessage(`AI question assistant failed: ${error instanceof Error ? error.message : 'Unable to generate question'}`, 'warning');
    } finally {
      setStatus('idle');
    }
  };

  const acceptDraft = async () => {
    if (!draft) {
      return;
    }
    setStatus('accepting');
    try {
      const response = await sessionApi.acceptInterviewerAssistedQuestion(sessionId, draft.draftId, {
        activeFilePath: activeFilePath || undefined,
        createNewTab: shouldCreateNewTab,
      });
      onAccepted(response.session, response.question.filePath);
      setPreviousAcceptedMeta(toQuestionMeta(draft));
      setConfirmAccept(false);
      setDraft(null);
    } catch (error) {
      onMessage(`Unable to accept AI question: ${error instanceof Error ? error.message : 'Request failed'}`, 'warning');
    } finally {
      setStatus('idle');
    }
  };

  return (
    <>
      <aside className={`human-ai-assistant ${open ? 'is-open' : ''}`} aria-label="AI question assistant">
        <button
          type="button"
          className="human-ai-assistant-ribbon"
          onClick={() => onOpenChange(!open)}
          title={`AI question assistant (${shortcutLabel})`}
          aria-expanded={open}
        >
          <span className="human-ai-ribbon-title">AI Assistant</span>
          <span className="human-ai-ribbon-shortcut">{shortcutLabel}</span>
        </button>
        <div className="human-ai-assistant-panel">
          <div className="human-ai-assistant-header">
            <div>
              <span className="human-ai-assistant-kicker">AI Assistant ({shortcutLabel})</span>
              <h3>Hello {firstName(interviewerName, 'Interviewer')}</h3>
            </div>
            <button type="button" className="human-ai-assistant-close" onClick={() => onOpenChange(false)} aria-label="Collapse AI assistant">x</button>
          </div>
          <p className="human-ai-assistant-context">
            Evaluating {candidateName}, {session.technology} candidate with {session.yearsOfExperience ?? 0} year(s) of experience for {session.targetRole || 'the selected role'}.
          </p>
          {!draft && previousAcceptedMeta ? (
            <div className="human-ai-draft-meta human-ai-previous-meta" aria-label="Previous accepted question">
              <span>Previous: {previousAcceptedMeta.level}, {previousAcceptedMeta.section}</span>
            </div>
          ) : null}
          {!draft ? (
            <div className="human-ai-assistant-actions human-ai-primary-action">
              <button type="button" className="control-btn btn-start" onClick={() => generateDraft()} disabled={status !== 'idle'}>
                {status === 'generating' ? 'Generating...' : 'Generate Question'}
              </button>
            </div>
          ) : null}
          {isBusy ? <AssistantMiniLoader status={status} /> : null}

          {draft ? (
            <div className="human-ai-draft">
              <div className="human-ai-draft-meta">
                <span>Level {draft.question.difficultyLevel ?? draft.question.difficulty}</span>
                <span>Section: {draft.section || 'General'}</span>
                {previousAcceptedMeta ? <span>Previous: {previousAcceptedMeta.level}, {previousAcceptedMeta.section}</span> : null}
              </div>
              <div className="human-ai-draft-tabs" role="tablist" aria-label="AI draft details">
                <button type="button" className={activeDraftTab === 'question' ? 'is-active' : ''} onClick={() => setActiveDraftTab('question')}>Question</button>
                <button type="button" className={activeDraftTab === 'solution' ? 'is-active' : ''} onClick={() => setActiveDraftTab('solution')}>Solution</button>
                <button type="button" className={activeDraftTab === 'complexity' ? 'is-active' : ''} onClick={() => setActiveDraftTab('complexity')}>Complexity</button>
                <button type="button" className={activeDraftTab === 'time' ? 'is-active' : ''} onClick={() => setActiveDraftTab('time')}>Solve Time</button>
              </div>
              {activeDraftTab === 'question' || activeDraftTab === 'solution' ? (
                <pre className="human-ai-draft-content">
                  {activeDraftTab === 'question'
                    ? draft.question.starterCode
                    : draft.question.referenceSolution || '(reference solution not available)'}
                </pre>
              ) : (
                <div className="human-ai-draft-info-panel">
                  {activeDraftTab === 'complexity' ? (
                    <>
                      <InfoLine label="Expected Time" value={draft.question.expectedTimeComplexity || 'Not captured'} />
                      <InfoLine label="Expected Space" value={draft.question.expectedSpaceComplexity || 'Not captured'} />
                    </>
                  ) : (
                    <>
                      <InfoLine label="Approximate Time" value={expectedSolveTimeLabel(session.yearsOfExperience, draft.question.difficultyLevel, draft.question.idealDurationMinutes)} />
                      <InfoLine label="Profile Basis" value={`${session.technology}, ${session.targetRole || 'selected role'}, ${session.yearsOfExperience ?? 0} year(s), Level ${draft.question.difficultyLevel ?? draft.question.difficulty}`} />
                    </>
                  )}
                </div>
              )}
              <div className="human-ai-regenerate-options">
                <div>
                  <strong>Complexity Level</strong>
                  <label><input type="radio" checked={complexityDirection === 'INCREASE'} onChange={() => setComplexityDirection('INCREASE')} /> Increase</label>
                  <label><input type="radio" checked={complexityDirection === 'DECREASE'} onChange={() => setComplexityDirection('DECREASE')} /> Decrease</label>
                </div>
                <div>
                  <strong>Section: {currentSection || 'General'}</strong>
                  <label><input type="radio" checked={sectionMode === 'SAME'} onChange={() => setSectionMode('SAME')} /> Same</label>
                  <label><input type="radio" checked={sectionMode === 'CHANGE'} onChange={() => setSectionMode('CHANGE')} /> Change</label>
                </div>
              </div>
              <div className="human-ai-assistant-actions">
                <button type="button" className="control-btn btn-start" onClick={() => setConfirmAccept(true)} disabled={status !== 'idle'}>Accept</button>
                <button type="button" className="control-btn btn-extend" onClick={() => generateDraft('regenerate')} disabled={status !== 'idle'}>
                  {status === 'generating' ? 'Regenerating...' : 'Regenerate'}
                </button>
              </div>
            </div>
          ) : (
            <p className="human-ai-empty">Generate a validated question for interviewer review. The candidate sees it only after you accept.</p>
          )}
        </div>
      </aside>
      {confirmAccept && draft ? (
        <div className="workspace-modal-backdrop human-ai-confirm-backdrop" role="presentation" onClick={() => setConfirmAccept(false)}>
          <div className="workspace-modal confirmation-modal" role="dialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>
            <h3>Accept AI question?</h3>
            <p className="workspace-modal-copy">
              {shouldCreateNewTab
                ? 'The active tab is already submitted or read-only. A new question tab will be created for this question.'
                : 'The active tab content will be replaced with this question. The reference solution will stay hidden from the editor.'}
            </p>
            <div className="workspace-modal-actions">
              <button type="button" className="btn btn-secondary" onClick={() => setConfirmAccept(false)}>Cancel</button>
              <button type="button" className="btn btn-primary" onClick={acceptDraft} disabled={status === 'accepting'}>
                {status === 'accepting' ? 'Accepting...' : 'Accept'}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
};

function AssistantMiniLoader({ status }: { status: AssistantStatus }) {
  const isAccepting = status === 'accepting';
  return (
    <div className="human-ai-mini-loader" role="status" aria-live="polite">
      <div>
        <span className="human-ai-mini-kicker">{isAccepting ? 'Publishing' : 'Preparing draft'}</span>
        <strong>{isAccepting ? 'Adding question to editor' : 'Checking sandbox readiness'}</strong>
      </div>
      <div className="human-ai-mini-track" aria-hidden="true">
        <div className={`human-ai-mini-fill ${isAccepting ? 'is-accepting' : ''}`} />
      </div>
      <div className="human-ai-mini-steps" aria-hidden="true">
        <span>Generate</span>
        <span>Check</span>
        <span>Ready</span>
      </div>
    </div>
  );
}

function InfoLine({ label, value }: { label: string; value: string }) {
  return (
    <div className="human-ai-info-line">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function toQuestionMeta(draft: AiInterviewerQuestionDraftResponse): QuestionMeta {
  return {
    level: `Level ${draft.question.difficultyLevel ?? draft.question.difficulty ?? '-'}`,
    section: draft.section || draft.question.concepts?.[0] || 'General',
  };
}

function expectedSolveTimeLabel(years?: number | null, difficulty?: number | null, idealDuration?: number | null) {
  const level = Math.max(1, Math.min(5, difficulty || 1));
  const baseline = idealDuration && idealDuration > 0 ? idealDuration : level >= 4 ? 15 : level >= 3 ? 12 : 10;
  const experience = typeof years === 'number' ? years : 0;
  const adjustment = experience >= 8 ? -2 : experience >= 4 ? -1 : experience <= 1 ? 2 : 0;
  const midpoint = Math.max(5, baseline + adjustment);
  const min = Math.max(5, midpoint - 2);
  const max = Math.max(min + 1, midpoint + 2);
  return `${min}-${max} minutes for a similar profile`;
}

function firstName(value: string | undefined, fallback: string) {
  const normalized = value?.trim();
  if (!normalized) {
    return fallback;
  }
  return normalized.split(/\s+/)[0] || fallback;
}

export default HumanAiAssistantDrawer;
