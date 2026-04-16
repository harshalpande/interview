import React from 'react';
import { BrowserRouter as Router, Navigate, Routes, Route, useParams } from 'react-router-dom';
import Dashboard from './pages/Dashboard';
import StartInterview from './pages/StartInterview';
import Disclaimer from './pages/Disclaimer';
import Session from './pages/Session';
import Join from './pages/Join';
import Result from './pages/Result';
import TechnologySelection from './pages/TechnologySelection';
import { QueryProvider } from './providers/QueryProvider';
import { SessionProvider } from './providers/SessionProvider';
import { ErrorBoundary } from './components/ErrorBoundary';
import './App.css';

function LegacyDisclaimerRedirect() {
  const { role } = useParams();
  return <Navigate to={role ? `/java/disclaimer/${role}` : '/java'} replace />;
}

function LegacySessionRedirect() {
  const { sessionId } = useParams();
  return <Navigate to={sessionId ? `/java/session/${sessionId}` : '/java'} replace />;
}

function LegacyJoinRedirect() {
  const { token } = useParams();
  return <Navigate to={token ? `/java/join/${token}` : '/java'} replace />;
}

function LegacyResultRedirect() {
  const { sessionId } = useParams();
  return <Navigate to={sessionId ? `/java/result/${sessionId}` : '/java'} replace />;
}

function AppContent() {
  return (
    <div className="App">
      <header className="app-header">
        <h1>Live Coding Interview</h1>
      </header>
      <main className="app-main">
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/java" element={<Dashboard />} />
          <Route path="/start" element={<TechnologySelection />} />
          <Route path="/java/start" element={<StartInterview />} />
          <Route path="/java/disclaimer/:role" element={<Disclaimer />} />
          <Route path="/java/session/:sessionId" element={<Session />} />
          <Route path="/java/join/:token" element={<Join />} />
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

