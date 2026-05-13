import React from 'react';
import { useParams } from 'react-router-dom';
import Editor from '../components/Editor';
import { preparationApi } from '../services/preparationApi';
import type { ExecuteResponse } from '../types/api';
import type { EditableCodeFile } from '../types/session';
import type { PreparationAccess, PreparationQuestion } from '../types/preparation';
import './Preparation.css';

const OTP_LENGTH = 5;
const EXPIRY_WARNING_SECONDS = 120;
const ALTIMETRIK_REDIRECT_URL = process.env.REACT_APP_POST_INTERVIEW_REDIRECT_URL || 'https://www.altimetrik.com/';
type PreparationInfoTab = 'question' | 'output' | 'alerts';
type PreparationAction = 'access' | 'disclaimer' | 'question' | 'verify' | 'run' | 'submit' | null;

const preparationDisclaimerStatements = [
  'Preparation Mode is intended to help you attempt a structured Java or Python coding exercise independently.',
  'Your access window is time-bound. The overall preparation session runs for 60 minutes after passcode verification, and each Banyan level has its own 20-minute timer.',
  'The platform monitors editor integrity events such as paste, cut, copy, drag-drop, browser focus loss, and tab switching during the active preparation workspace.',
  'Problem statements, validation checks, assert statements, and provided code structure must remain intact unless the question explicitly asks you to modify a method implementation.',
  'Run attempts are counted for the current problem. Output is provided only to help you validate your own work during the session.',
];

const preparationDos = [
  'Read the problem statement carefully before changing code.',
  'Use the in-app editor and Run button to validate your solution.',
  'Keep earlier Banyan level behavior working when the next level is unlocked.',
  'Use normal editor navigation, search, indentation, undo, redo, and code-completion shortcuts as needed.',
  'Report a platform issue from the More menu if the workspace or validation service behaves unexpectedly.',
];

const preparationDonts = [
  'Do not use external help, copied solutions, AI-generated answers, hidden communication tools, or another person\'s assistance.',
  'Do not paste, drag, drop, copy, or cut content inside the editor. These actions are blocked and may be shown as integrity alerts.',
  'Do not remove, weaken, rename, or bypass validation checks or assert statements.',
  'Do not switch tabs, leave the browser, or move focus away from the workspace unless it is genuinely necessary.',
  'Do not refresh or close the browser during an active question unless instructed by support.',
];

const preparationShortcuts = [
  { label: 'Run / Ctrl + Enter', description: 'Runs the current solution and updates the output panel.' },
  { label: 'Full Screen / Ctrl + Shift + F', description: 'Expands the coding workspace for focused editing.' },
  { label: 'Clear Output / Esc', description: 'Clears output and messages without changing your code.' },
  { label: 'Find / Ctrl + F', description: 'Searches within the editor.' },
  { label: 'Replace / Ctrl + H', description: 'Opens editor replace within the current file.' },
  { label: 'Undo / Ctrl + Z', description: 'Reverts the last editor change.' },
  { label: 'Redo / Ctrl + Y', description: 'Restores a reverted editor change.' },
  { label: 'Suggest / Ctrl + Space', description: 'Shows language-aware editor suggestions.' },
  { label: 'Toggle Comment / Ctrl + /', description: 'Comments or uncomments the selected line.' },
  { label: 'Format / Alt + Shift + F', description: 'Formats the current editor content.' },
  { label: 'Indent / Tab', description: 'Indents the current line or selected block.' },
];

export default function PreparationSession() {
  const { token = '' } = useParams();
  const otpFieldId = React.useId().replace(/:/g, '');
  const [access, setAccess] = React.useState<PreparationAccess | null>(null);
  const [question, setQuestion] = React.useState<PreparationQuestion | null>(null);
  const [otp, setOtp] = React.useState('');
  const [code, setCode] = React.useState('');
  const [output, setOutput] = React.useState('');
  const [error, setError] = React.useState('');
  const [message, setMessage] = React.useState('');
  const [passed, setPassed] = React.useState(false);
  const [loadingAction, setLoadingAction] = React.useState<PreparationAction>('access');
  const [ended, setEnded] = React.useState(false);
  const [disclaimerAccepted, setDisclaimerAccepted] = React.useState(false);
  const [alerts, setAlerts] = React.useState<string[]>([]);
  const [remainingSeconds, setRemainingSeconds] = React.useState(0);
  const [overallRemainingSeconds, setOverallRemainingSeconds] = React.useState(0);
  const [activeInfoTab, setActiveInfoTab] = React.useState<PreparationInfoTab>('question');
  const [isFullscreen, setIsFullscreen] = React.useState(false);
  const suppressEditorIntegrityUntilRef = React.useRef(0);
  const redirectScheduledRef = React.useRef(false);
  const latestCodeRef = React.useRef('');

  const loading = loadingAction !== null;
  const levelTimerCritical = remainingSeconds <= EXPIRY_WARNING_SECONDS;
  const overallTimerCritical = overallRemainingSeconds <= EXPIRY_WARNING_SECONDS;
  const pushAlert = React.useCallback((text: string) => {
    const stamped = `${new Date().toLocaleTimeString()} - ${text}`;
    setAlerts((previous) => [stamped, ...previous].slice(0, 8));
  }, []);
  const suppressEditorIntegrityBriefly = React.useCallback(() => {
    suppressEditorIntegrityUntilRef.current = Date.now() + 1500;
  }, []);
  const updatePreparationCode = React.useCallback((nextCode: string) => {
    latestCodeRef.current = nextCode;
    setCode(nextCode);
  }, []);
  const toggleFullscreen = React.useCallback(() => {
    setIsFullscreen((previous) => !previous);
  }, []);
  const finishPreparationAndRedirect = React.useCallback((reason: string) => {
    if (redirectScheduledRef.current) {
      return;
    }
    redirectScheduledRef.current = true;
    setEnded(true);
    setPassed(false);
    setLoadingAction(null);
    setMessage(reason);
    setActiveInfoTab('question');
    void preparationApi.expireAttempt(token).catch(() => undefined);
    window.setTimeout(() => {
      window.location.assign(ALTIMETRIK_REDIRECT_URL);
    }, 1400);
  }, [token]);

  React.useEffect(() => {
    let mounted = true;
    setLoadingAction('access');
    preparationApi.getAccess(token)
      .then((response) => {
        if (!mounted) {
          return;
        }
        setAccess(response);
        setDisclaimerAccepted(Boolean(response.disclaimerAccepted));
        setOverallRemainingSeconds(response.remainingAttemptSeconds || 0);
        if (response.otpVerified && response.status === 'ACTIVE') {
          setLoadingAction('question');
          loadQuestion(token, setQuestion, updatePreparationCode, setRemainingSeconds, setOverallRemainingSeconds, setMessage, setEnded, suppressEditorIntegrityBriefly)
            .catch((err) => {
              if (mounted) {
                setMessage(err instanceof Error ? err.message : 'Unable to prepare the question.');
              }
            })
            .finally(() => {
              if (mounted) {
                setLoadingAction(null);
              }
            });
        } else {
          setLoadingAction(null);
        }
      })
      .catch((err) => {
        setMessage(err instanceof Error ? err.message : 'Candidate access is unavailable.');
        setLoadingAction(null);
      });
    return () => {
      mounted = false;
    };
  }, [suppressEditorIntegrityBriefly, token, updatePreparationCode]);

  React.useEffect(() => {
    if (!question?.questionExpiresAt || ended) {
      return;
    }
    const timer = window.setInterval(() => {
      const next = remainingFromIso(question.questionExpiresAt);
      const overallNext = remainingFromIso(question.attemptExpiresAt);
      setRemainingSeconds(next);
      setOverallRemainingSeconds(overallNext);
      if (overallNext <= 0) {
        finishPreparationAndRedirect('The 60-minute preparation session has ended. Redirecting to Altimetrik.');
      } else if (next <= 0) {
        finishPreparationAndRedirect('The level time has ended. Redirecting to Altimetrik.');
      }
    }, 1000);
    return () => window.clearInterval(timer);
  }, [ended, finishPreparationAndRedirect, question?.attemptExpiresAt, question?.questionExpiresAt]);

  React.useEffect(() => {
    const visibilityHandler = () => {
      if (document.visibilityState === 'hidden') {
        pushAlert('Tab switched or browser lost focus.');
      }
    };
    document.addEventListener('visibilitychange', visibilityHandler);
    return () => {
      document.removeEventListener('visibilitychange', visibilityHandler);
    };
  }, [pushAlert]);

  React.useEffect(() => {
    if (isFullscreen) {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }, [isFullscreen]);

  React.useEffect(() => {
    if (!isFullscreen) {
      return undefined;
    }
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [isFullscreen]);

  React.useEffect(() => {
    const shortcutHandler = (event: KeyboardEvent) => {
      if (event.ctrlKey && event.shiftKey && event.key.toLowerCase() === 'f') {
        event.preventDefault();
        toggleFullscreen();
      }
    };
    window.addEventListener('keydown', shortcutHandler);
    return () => {
      window.removeEventListener('keydown', shortcutHandler);
    };
  }, [toggleFullscreen]);

  const acceptPreparationDisclaimer = async () => {
    setLoadingAction('disclaimer');
    setMessage('');
    try {
      const response = await preparationApi.acceptDisclaimer(token);
      setAccess(response);
      setDisclaimerAccepted(Boolean(response.disclaimerAccepted));
      setMessage(response.message || '');
      if (response.otpVerified && response.status === 'ACTIVE') {
        setLoadingAction('question');
        await loadQuestion(token, setQuestion, updatePreparationCode, setRemainingSeconds, setOverallRemainingSeconds, setMessage, setEnded, suppressEditorIntegrityBriefly);
      }
    } catch (err) {
      setMessage(err instanceof Error ? err.message : 'Unable to save the preparation guidelines.');
    } finally {
      setLoadingAction(null);
    }
  };

  const verifyOtp = async (event: React.FormEvent) => {
    event.preventDefault();
    setLoadingAction('verify');
    setMessage('');
    try {
      const response = await preparationApi.verifyOtp(token, otp);
      setAccess(response);
      setLoadingAction('question');
      setOverallRemainingSeconds(response.remainingAttemptSeconds || 0);
      await loadQuestion(token, setQuestion, updatePreparationCode, setRemainingSeconds, setOverallRemainingSeconds, setMessage, setEnded, suppressEditorIntegrityBriefly);
    } catch (err) {
      setMessage(err instanceof Error ? err.message : 'Unable to verify passcode.');
    } finally {
      setLoadingAction(null);
    }
  };

  const runCode = React.useCallback(async (sourceCode: string): Promise<ExecuteResponse> => {
    setLoadingAction('run');
    setOutput('');
    setError('');
    setMessage('');
    setActiveInfoTab('output');
    try {
      const response = await preparationApi.run(token, sourceCode);
      setPassed(response.passed);
      setOutput(response.execution?.stdout || '');
      setError(executionErrorText(response.execution));
      setMessage(response.message);
      if (response.question) {
        setQuestion(response.question);
        setRemainingSeconds(response.question.remainingSeconds || 0);
        setOverallRemainingSeconds(response.question.remainingAttemptSeconds || 0);
      }
      if (response.attemptEnded) {
        finishPreparationAndRedirect(response.message || 'Preparation Mode has ended. Redirecting to Altimetrik.');
      }
      return response.execution || {
        success: false,
        stdout: '',
        stderr: response.message,
        executionTimeMs: 0,
        message: response.message,
      };
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Unable to run the program.';
      setMessage(errorMessage);
      setError(errorMessage);
      throw err instanceof Error ? err : new Error(errorMessage);
    } finally {
      setLoadingAction(null);
    }
  }, [finishPreparationAndRedirect, token]);

  const handleEditorCodeFilesChange = React.useCallback((files: EditableCodeFile[]) => {
    const activeFile = files.find((file) => file.path === question?.filePath) ?? files[0];
    updatePreparationCode(activeFile?.content || '');
    setPassed(false);
  }, [question?.filePath, updatePreparationCode]);

  const submitCode = async () => {
    setLoadingAction('submit');
    setOutput('');
    setError('');
    setMessage('');
    setActiveInfoTab('output');
    try {
      const sourceCode = latestCodeRef.current || code;
      const response = await preparationApi.submit(token, sourceCode);
      setPassed(response.passed);
      setOutput(response.execution?.stdout || '');
      setError(executionErrorText(response.execution));
      setMessage(response.message);
      if (response.attemptEnded) {
        finishPreparationAndRedirect(response.message || 'Preparation Mode has ended. Redirecting to Altimetrik.');
      }
      if (response.nextQuestion) {
        setQuestion(response.nextQuestion);
        setRemainingSeconds(response.nextQuestion.remainingSeconds || 0);
        setOverallRemainingSeconds(response.nextQuestion.remainingAttemptSeconds || 0);
        if (response.passed) {
          suppressEditorIntegrityBriefly();
          updatePreparationCode(response.nextQuestion.starterCode || '');
          setPassed(false);
          setActiveInfoTab('question');
        }
      }
    } catch (err) {
      setMessage(err instanceof Error ? err.message : 'Unable to submit the question.');
    } finally {
      setLoadingAction(null);
    }
  };

  if (!access && loadingAction === 'access') {
    return <PreparationLoader label="Loading candidate access..." />;
  }

  if (!access) {
    return (
      <div className="preparation-page preparation-access">
        <div className="preparation-panel preparation-message">{message || 'Candidate access is unavailable.'}</div>
      </div>
    );
  }

  if (!access.disclaimerAccepted) {
    return (
      <div className="preparation-page preparation-access">
        <section className="preparation-panel preparation-disclaimer">
          <p className="page-kicker">Preparation Agreement</p>
          <h2>Candidate Guidelines</h2>
          <p className="page-subtitle">
            Review the preparation conduct expectations and available editor shortcuts before entering the workspace.
          </p>

          <div className="preparation-disclaimer-identity" aria-label="Candidate details">
            <div>
              <span>Candidate</span>
              <strong>{access.candidateName}</strong>
            </div>
            <div>
              <span>Email</span>
              <strong>{access.email}</strong>
            </div>
            <div>
              <span>Experience</span>
              <strong>{formatExperience(access.yearsOfExperience, access.experienceBand)}</strong>
            </div>
          </div>

          <section className="preparation-disclaimer-section">
            <h3>Disclaimer</h3>
            <ol className="preparation-disclaimer-list">
              {preparationDisclaimerStatements.map((statement) => <li key={statement}>{statement}</li>)}
            </ol>
          </section>

          <div className="preparation-disclaimer-grid">
            <section className="preparation-disclaimer-section">
              <h3>Do's</h3>
              <ul className="preparation-disclaimer-list">
                {preparationDos.map((item) => <li key={item}>{item}</li>)}
              </ul>
            </section>
            <section className="preparation-disclaimer-section">
              <h3>Don'ts</h3>
              <ul className="preparation-disclaimer-list">
                {preparationDonts.map((item) => <li key={item}>{item}</li>)}
              </ul>
            </section>
          </div>

          <section className="preparation-disclaimer-section">
            <h3>Available Shortcuts</h3>
            <div className="preparation-shortcut-grid">
              {preparationShortcuts.map((shortcut) => (
                <div className="preparation-shortcut-item" key={shortcut.label}>
                  <strong>{shortcut.label}</strong>
                  <span>{shortcut.description}</span>
                </div>
              ))}
            </div>
          </section>

          <label className="preparation-agreement-check">
            <input
              type="checkbox"
              checked={disclaimerAccepted}
              onChange={(event) => setDisclaimerAccepted(event.target.checked)}
            />
            I understand and agree to follow the Preparation Mode guidelines, integrity controls, and independent work expectations.
          </label>
          {message && <div className="preparation-message">{message}</div>}
          <button className="btn btn-primary" type="button" onClick={acceptPreparationDisclaimer} disabled={!disclaimerAccepted || loadingAction === 'disclaimer'}>
            {loadingAction === 'disclaimer' ? 'Saving...' : 'Accept and Continue'}
          </button>
        </section>
      </div>
    );
  }

  if (!access.otpVerified) {
    return (
      <div className="preparation-page preparation-access">
        <form className="preparation-panel preparation-otp" onSubmit={verifyOtp} autoComplete="off">
          <p className="page-kicker">Preparation Access</p>
          <h2>Verify Passcode</h2>
          <p className="page-subtitle">Enter the one-time passcode sent to {access.email}. This access remains available for 72 hours.</p>
          <label className="preparation-otp-code">
            <span>Passcode</span>
            <input
              id={`${otpFieldId}-passcode`}
              name={`${otpFieldId}-candidate-passcode`}
              type="text"
              autoComplete="new-password"
              autoCorrect="off"
              autoCapitalize="characters"
              spellCheck={false}
              data-form-type="other"
              data-lpignore="true"
              data-1p-ignore="true"
              inputMode="text"
              aria-label="One-time passcode"
              placeholder="Enter code"
              value={otp}
              maxLength={OTP_LENGTH}
              onChange={(event) => setOtp(event.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, OTP_LENGTH))}
            />
          </label>
          <button className="btn btn-primary" type="submit" disabled={loading || otp.length !== OTP_LENGTH}>
            {loadingAction === 'verify' || loadingAction === 'question' ? 'Verifying...' : 'Verify'}
          </button>
          {(loadingAction === 'verify' || loadingAction === 'question') && <InlineLoader label={loadingAction === 'question' ? 'Preparing workspace...' : 'Verifying passcode...'} />}
          {message && <div className="preparation-message">{message}</div>}
        </form>
      </div>
    );
  }

  return (
    <div className="preparation-workspace">
      <section className="preparation-topbar">
        <div className="preparation-context">
          <span className="preparation-mode-chip">Banyan Preparation</span>
          <strong>{formatLevelLabel(question?.displayName)}</strong>
          <div className="preparation-candidate-strip" aria-label="Candidate details">
            <span className="preparation-context-name">{access.candidateName}</span>
            <span className="preparation-context-email">{access.email}</span>
            <span className="preparation-context-experience">{formatExperience(access.yearsOfExperience, access.experienceBand)}</span>
          </div>
        </div>
        <div className="preparation-status-chips" aria-label="Preparation timing and progress">
          <span className={`preparation-compact-chip ${overallTimerCritical ? 'is-critical' : ''}`}>
            <span>Interview</span>
            <strong>{formatTime(overallRemainingSeconds)}</strong>
          </span>
          <span className={`preparation-compact-chip is-level ${levelTimerCritical ? 'is-critical' : ''}`}>
            <span>Level</span>
            <strong>{formatTime(remainingSeconds)}</strong>
          </span>
          <span className="preparation-compact-chip">
            <span>Runs</span>
            <strong>{question?.executeAttemptCount ?? 0}</strong>
          </span>
        </div>
      </section>

      {loadingAction === 'question' ? (
        <PreparationLoader label="Preparing question..." compact />
      ) : (
      <section className="preparation-editor-layout">
        <Editor
          key={question?.questionId || 'preparation-editor'}
          sessionId={access.attemptId}
          executionLanguage={question?.technology === 'PYTHON' ? 'PYTHON' : 'JAVA'}
          participantRole={null}
          evaluationStyle="BANYAN"
          readOnly={ended}
          initialCode={code}
          initialCodeFiles={question ? [buildPreparationCodeFile(question, code)] : undefined}
          initialCodeVersion={question?.sequenceNumber || 0}
          preferredActiveFilePath={question?.filePath}
          onCodeChange={updatePreparationCode}
          onCodeFilesChange={handleEditorCodeFilesChange}
          onRunCode={(sourceCode) => runCode(sourceCode)}
          onClearOutput={() => {
            setOutput('');
            setError('');
            setMessage('');
          }}
          onPasteInEditor={() => {
            if (Date.now() < suppressEditorIntegrityUntilRef.current) {
              return true;
            }
            pushAlert('Paste action blocked in the editor.');
            setActiveInfoTab('alerts');
            return false;
          }}
          onCopyFromEditor={() => {
            if (Date.now() < suppressEditorIntegrityUntilRef.current) {
              return true;
            }
            pushAlert('Copy action blocked in the editor.');
            setActiveInfoTab('alerts');
            return false;
          }}
          onCutFromEditor={() => {
            if (Date.now() < suppressEditorIntegrityUntilRef.current) {
              return true;
            }
            pushAlert('Cut action blocked in the editor.');
            setActiveInfoTab('alerts');
            return false;
          }}
          onExternalDropBlocked={() => {
            if (Date.now() < suppressEditorIntegrityUntilRef.current) {
              return;
            }
            pushAlert('Drag and drop action blocked in the editor.');
            setActiveInfoTab('alerts');
          }}
          showResetButton={false}
          showFullscreenToggle
          isFullscreen={isFullscreen}
          onToggleFullscreen={toggleFullscreen}
          canRun={!ended && (!loading || loadingAction === 'run')}
          runButtonLabel="Run"
          headerRightSlot={
            <button className="btn btn-primary" onClick={submitCode} disabled={loading || ended || !passed}>
              {loadingAction === 'submit' ? 'Submitting...' : 'Submit'}
            </button>
          }
          outputPanelSlot={
            <PreparationInfoPanel
              activeInfoTab={activeInfoTab}
              setActiveInfoTab={setActiveInfoTab}
              question={question}
              message={message}
              ended={ended}
              output={output}
              error={error}
              alerts={alerts}
            />
          }
        />
        {(loadingAction === 'run' || loadingAction === 'submit') && <InlineLoader label={loadingAction === 'run' ? 'Running validation...' : 'Submitting level...'} />}
      </section>
      )}
    </div>
  );
}

function PreparationInfoPanel({
  activeInfoTab,
  setActiveInfoTab,
  question,
  message,
  ended,
  output,
  error,
  alerts,
}: {
  activeInfoTab: PreparationInfoTab;
  setActiveInfoTab: (tab: PreparationInfoTab) => void;
  question: PreparationQuestion | null;
  message: string;
  ended: boolean;
  output: string;
  error: string;
  alerts: string[];
}) {
  return (
    <div className="preparation-info-panel preparation-info-panel-embedded">
      <div className="preparation-info-tabs" role="tablist" aria-label="Preparation details">
        <button type="button" className={activeInfoTab === 'question' ? 'is-active' : ''} onClick={() => setActiveInfoTab('question')} role="tab" aria-selected={activeInfoTab === 'question'}>Question</button>
        <button type="button" className={activeInfoTab === 'output' ? 'is-active' : ''} onClick={() => setActiveInfoTab('output')} role="tab" aria-selected={activeInfoTab === 'output'}>Output</button>
        <button type="button" className={activeInfoTab === 'alerts' ? 'is-active' : ''} onClick={() => setActiveInfoTab('alerts')} role="tab" aria-selected={activeInfoTab === 'alerts'}>Alerts</button>
      </div>
      <div className={`preparation-info-body ${activeInfoTab === 'output' ? 'is-output' : ''}`}>
        {activeInfoTab === 'question' && (
          <div className="preparation-question-tab">
            <h3>{question?.title || 'Question'}</h3>
            <p>{question?.problemStatement || 'Question details are being prepared.'}</p>
            {message && <div className="preparation-message">{message}</div>}
            {ended && <div className="preparation-ended">Preparation ended.</div>}
          </div>
        )}
        {activeInfoTab === 'output' && (
          <pre className="preparation-output-text">{output || error || 'Run the active tab to see validation output.'}</pre>
        )}
        {activeInfoTab === 'alerts' && (
          <div className="preparation-alerts">
            {alerts.length ? alerts.map((alert) => <span key={alert}>{alert}</span>) : <span>No integrity alerts captured in this browser tab.</span>}
          </div>
        )}
      </div>
    </div>
  );
}

function buildPreparationCodeFile(question: PreparationQuestion, code: string): EditableCodeFile {
  return {
    path: question.filePath || (question.technology === 'PYTHON' ? 'question-1.py' : 'Question1.java'),
    displayName: question.displayName || 'Question',
    content: code || question.starterCode || '',
    editable: true,
    sortOrder: 0,
    enabledForCandidate: true,
    activeQuestion: true,
    submitted: false,
    idealDurationMinutes: 20,
    candidateStartedAt: question.questionStartedAt || null,
    submittedAt: null,
    solveDurationSeconds: null,
    executeAttemptCount: question.executeAttemptCount || 0,
  };
}

function PreparationLoader({ label, compact = false }: { label: string; compact?: boolean }) {
  return (
    <div className={`preparation-loading-shell ${compact ? 'is-compact' : ''}`}>
      <div className="preparation-loader" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

function InlineLoader({ label }: { label: string }) {
  return (
    <div className="preparation-inline-loader" role="status">
      <span className="preparation-loader" aria-hidden="true" />
      {label}
    </div>
  );
}

async function loadQuestion(
  token: string,
  setQuestion: (question: PreparationQuestion) => void,
  setCode: (code: string) => void,
  setRemainingSeconds: (seconds: number) => void,
  setOverallRemainingSeconds: (seconds: number) => void,
  setMessage: (message: string) => void,
  setEnded: (ended: boolean) => void,
  suppressEditorIntegrityBriefly: () => void,
) {
  const response = await preparationApi.currentQuestion(token);
  setQuestion(response);
  suppressEditorIntegrityBriefly();
  setCode(response.starterCode || '');
  setRemainingSeconds(response.remainingSeconds || 0);
  setOverallRemainingSeconds(response.remainingAttemptSeconds || 0);
  setMessage(response.message || '');
  setEnded(response.attemptEnded);
}

function executionErrorText(execution?: { stderr?: string; compileErrors?: string[]; message?: string }) {
  if (!execution) {
    return '';
  }
  if (execution.compileErrors?.length) {
    return execution.compileErrors.join('\n');
  }
  return execution.stderr || '';
}

function remainingFromIso(value?: string) {
  if (!value) {
    return 0;
  }
  const timestamp = new Date(value).getTime();
  if (Number.isNaN(timestamp)) {
    return 0;
  }
  return Math.max(0, Math.floor((timestamp - Date.now()) / 1000));
}

function formatTime(totalSeconds: number) {
  const minutes = Math.floor(Math.max(0, totalSeconds) / 60);
  const seconds = Math.max(0, totalSeconds) % 60;
  return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
}

function formatLevelLabel(displayName?: string) {
  if (!displayName) {
    return 'Question';
  }
  return displayName.replace(/^Banyan\s+/i, '').trim() || displayName;
}

function formatExperience(yearsOfExperience?: number, experienceBand?: string) {
  if (experienceBand) {
    return `${experienceBand} years experience`;
  }
  if (typeof yearsOfExperience === 'number') {
    return `${yearsOfExperience} year${yearsOfExperience === 1 ? '' : 's'} experience`;
  }
  return 'Experience not captured';
}
