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
import { QueryProvider } from './providers/QueryProvider';
import { SessionProvider } from './providers/SessionProvider';
import { ErrorBoundary } from './components/ErrorBoundary';
import './App.css';

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
  const isJavaFlow = location.pathname === '/java' || location.pathname.startsWith('/java/');
  const headerTitle = isJavaFlow ? 'Live Coding Interview - Java' : 'Live Coding Interview';

  return (
    <div className="App">
      <header className="app-header">
        <h1>{headerTitle}</h1>
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

