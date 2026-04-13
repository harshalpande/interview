import React from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import Dashboard from './pages/Dashboard';
import StartInterview from './pages/StartInterview';
import Disclaimer from './pages/Disclaimer';
import Session from './pages/Session';
import Join from './pages/Join';
import Result from './pages/Result';
import { QueryProvider } from './providers/QueryProvider';
import { SessionProvider } from './providers/SessionProvider';
import { ErrorBoundary } from './components/ErrorBoundary';
import './App.css';

function AppContent() {
  return (
    <div className="App">
      <header className="app-header">
        <h1>Live Coding Interview</h1>
      </header>
      <main className="app-main">
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/start" element={<StartInterview />} />
          <Route path="/disclaimer/:role" element={<Disclaimer />} />
          <Route path="/session/:sessionId" element={<Session />} />
          <Route path="/join/:token" element={<Join />} />
          <Route path="/result/:sessionId" element={<Result />} />
          <Route path="/interview/:token" element={<StartInterview />} /> {/* Legacy */}
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

