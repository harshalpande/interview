import React from 'react';
import { BrowserRouter as Router, Navigate, Routes, Route, useLocation, useParams } from 'react-router-dom';
import Dashboard from './pages/Dashboard';
import StartInterview from './pages/StartInterview';
import Disclaimer from './pages/Disclaimer';
import AccessEntry from './pages/AccessEntry';
import Session from './pages/Session';
import Resume from './pages/Resume';
import Result from './pages/Result';
import TechnologySelection from './pages/TechnologySelection';
import IdentityCapture from './pages/IdentityCapture';
import PreparationDashboard from './pages/PreparationDashboard';
import PreparationSession from './pages/PreparationSession';
import { QueryProvider } from './providers/QueryProvider';
import { SessionProvider } from './providers/SessionProvider';
import { ErrorBoundary } from './components/ErrorBoundary';
import './App.css';

const ISSUE_REPORT_TO = 'hpande@altimetrik.com';
const ISSUE_REPORT_SUBJECT = 'Issue observed';
const ISSUE_REPORT_BODY = 'Add description and screenshot(s) to report the challenge you are facing. The more detailed, the better.';
const QUIET_INPUT_SELECTOR = [
  'input:not([type="hidden"]):not([type="radio"]):not([type="checkbox"]):not([type="password"]):not([type="file"]):not(.sr-only-input)',
  'textarea',
].join(',');

function LegacyDisclaimerRedirect() {
  const { role } = useParams();
  return <Navigate to={role ? `/java/disclaimer/${role}` : '/'} replace />;
}

function LegacySessionRedirect() {
  const { sessionId } = useParams();
  return <Navigate to={sessionId ? `/java/session/${sessionId}` : '/'} replace />;
}

function LegacyJoinRedirect() {
  const { token } = useParams();
  return <Navigate to={token ? `/java/access/${token}` : '/'} replace />;
}

function LegacyResultRedirect() {
  const { sessionId } = useParams();
  return <Navigate to={sessionId ? `/java/result/${sessionId}` : '/'} replace />;
}

function AppContent() {
  const location = useLocation();
  const [showIssueFallback, setShowIssueFallback] = React.useState(false);
  const [moreOpen, setMoreOpen] = React.useState(false);
  const fallbackTimerRef = React.useRef<number | null>(null);
  const autoHideTimerRef = React.useRef<number | null>(null);
  const moreMenuRef = React.useRef<HTMLDivElement | null>(null);
  const isJavaFlow = location.pathname === '/java' || location.pathname.startsWith('/java/');
  const isDashboard = location.pathname === '/';
  const isPreparationFlow = location.pathname.startsWith('/preparation');
  const isPreparationDashboard = location.pathname === '/preparation';
  const isCandidateAccessFlow = location.pathname.startsWith('/preparation/access/')
    || location.pathname.startsWith('/java/access/')
    || location.pathname.startsWith('/java/join/');
  const showHomeMenuItem = isPreparationDashboard;
  const homeMenuHref = '/';
  const showPreparationMenuItem = !isCandidateAccessFlow && !isPreparationDashboard;
  const headerTitle = isJavaFlow
    ? 'Live Coding Interview - Java'
    : isPreparationFlow
      ? 'Preparation Mode'
    : isDashboard
      ? 'Live Coding Interview - Recent Sessions'
      : 'Live Coding Interview';

  React.useEffect(() => () => {
    if (fallbackTimerRef.current) {
      window.clearTimeout(fallbackTimerRef.current);
    }
    if (autoHideTimerRef.current) {
      window.clearTimeout(autoHideTimerRef.current);
    }
  }, []);

  React.useEffect(() => {
    setMoreOpen(false);
  }, [location.pathname]);

  React.useEffect(() => {
    const applyQuietInputAttributes = (root: ParentNode = document) => {
      root.querySelectorAll<HTMLInputElement | HTMLTextAreaElement>(QUIET_INPUT_SELECTOR).forEach((field) => {
        if (field.getAttribute('aria-hidden') === 'true') {
          return;
        }

        field.setAttribute('autocomplete', 'new-password');
        field.setAttribute('autocorrect', 'off');
        if (!field.hasAttribute('autocapitalize')) {
          field.setAttribute('autocapitalize', 'none');
        }
        field.setAttribute('spellcheck', 'false');
        field.spellcheck = false;
      });
    };

    applyQuietInputAttributes();
    const observer = new MutationObserver((mutations) => {
      mutations.forEach((mutation) => {
        mutation.addedNodes.forEach((node) => {
          if (node instanceof HTMLElement) {
            if (node.matches(QUIET_INPUT_SELECTOR)) {
              applyQuietInputAttributes(node.parentElement || document);
            } else {
              applyQuietInputAttributes(node);
            }
          }
        });
      });
    });

    observer.observe(document.body, { childList: true, subtree: true });
    return () => observer.disconnect();
  }, []);

  React.useEffect(() => {
    if (!moreOpen) {
      return undefined;
    }

    const closeOnOutsideClick = (event: MouseEvent) => {
      if (moreMenuRef.current && !moreMenuRef.current.contains(event.target as Node)) {
        setMoreOpen(false);
      }
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setMoreOpen(false);
      }
    };

    document.addEventListener('mousedown', closeOnOutsideClick);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('mousedown', closeOnOutsideClick);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [moreOpen]);

  const handleReportIssue = () => {
    setMoreOpen(false);
    setShowIssueFallback(false);
    if (fallbackTimerRef.current) {
      window.clearTimeout(fallbackTimerRef.current);
    }
    if (autoHideTimerRef.current) {
      window.clearTimeout(autoHideTimerRef.current);
    }

    window.location.href = issueReportMailto();
    fallbackTimerRef.current = window.setTimeout(() => {
      if (document.visibilityState === 'visible') {
        setShowIssueFallback(true);
        autoHideTimerRef.current = window.setTimeout(() => {
          setShowIssueFallback(false);
        }, 10000);
      }
    }, 1200);
  };

  return (
    <div className="App">
      <header className="app-header">
        <h1>{headerTitle}</h1>
        <div className="app-header-actions" ref={moreMenuRef}>
          <button
            type="button"
            className={`app-more-button ${moreOpen ? 'is-open' : ''}`}
            onClick={() => setMoreOpen((open) => !open)}
            aria-expanded={moreOpen}
            aria-haspopup="menu"
            aria-controls="app-more-menu"
          >
            More
            <span className="app-more-chevron" aria-hidden="true" />
          </button>
          {moreOpen && (
            <div className="app-more-menu" id="app-more-menu" role="menu">
              {showHomeMenuItem && (
                <a className="app-more-item is-home" href={homeMenuHref} role="menuitem">
                  <span className="app-more-item-icon" aria-hidden="true" />
                  <span>Home</span>
                </a>
              )}
              {showPreparationMenuItem && (
                <a className="app-more-item is-preparation" href="/preparation" role="menuitem">
                  <span className="app-more-item-icon" aria-hidden="true" />
                  <span>Preparation</span>
                </a>
              )}
              <button className="app-more-item is-issue" type="button" onClick={handleReportIssue} role="menuitem">
                <span className="app-more-item-icon" aria-hidden="true" />
                <span>Report an Issue</span>
              </button>
            </div>
          )}
        </div>
        {showIssueFallback && (
          <div className="app-issue-fallback" role="status">
            If your mail client did not open, send an email to <strong>{ISSUE_REPORT_TO}</strong> with subject <strong>{ISSUE_REPORT_SUBJECT}</strong>.
          </div>
        )}
      </header>
      <main className="app-main">
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/java" element={<Navigate to="/" replace />} />
          <Route path="/start" element={<TechnologySelection />} />
          <Route path="/java/start" element={<StartInterview />} />
          <Route path="/java/identity-capture/:sessionId" element={<IdentityCapture />} />
          <Route path="/java/disclaimer/:role" element={<Disclaimer />} />
          <Route path="/java/session/:sessionId" element={<Session />} />
          <Route path="/java/access/:token" element={<AccessEntry />} />
          <Route path="/java/join/:token" element={<AccessEntry />} />
          <Route path="/java/resume/:sessionId" element={<Resume />} />
          <Route path="/java/result/:sessionId" element={<Result />} />
          <Route path="/preparation" element={<PreparationDashboard />} />
          <Route path="/preparation/access/:token" element={<PreparationSession />} />
          <Route path="/disclaimer/:role" element={<LegacyDisclaimerRedirect />} />
          <Route path="/session/:sessionId" element={<LegacySessionRedirect />} />
          <Route path="/join/:token" element={<LegacyJoinRedirect />} />
          <Route path="/result/:sessionId" element={<LegacyResultRedirect />} />
          <Route path="/interview/:token" element={<LegacyJoinRedirect />} />
        </Routes>
      </main>
    </div>
  );
}

function issueReportMailto() {
  return `mailto:${ISSUE_REPORT_TO}?subject=${encodeURIComponent(ISSUE_REPORT_SUBJECT)}&body=${encodeURIComponent(ISSUE_REPORT_BODY)}`;
}

function App() {
  return (
    <Router>
      <ErrorBoundary>
        <QueryProvider>
          <SessionProvider>
            <AppContent />
          </SessionProvider>
        </QueryProvider>
      </ErrorBoundary>
    </Router>
  );
}

export default App;

