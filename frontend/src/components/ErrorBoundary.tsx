import React from 'react';

type ErrorBoundaryProps = {
  children: React.ReactNode;
};

type ErrorBoundaryState = {
  error: Error | null;
};

export class ErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error) {
    return { error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    // Keep a breadcrumb in the console for quick diagnosis.
    // eslint-disable-next-line no-console
    console.error('UI crashed:', error, info);
  }

  render() {
    if (!this.state.error) {
      return this.props.children;
    }

    return (
      <div className="page-shell">
        <div className="page-card">
          <div className="page-kicker">Something went wrong</div>
          <h2>We hit an unexpected UI error</h2>
          <p className="page-subtitle">
            This screen replaces the blank page so we can diagnose the issue quickly.
          </p>
          <div className="error-banner">
            {this.state.error.message || 'Unknown error'}
          </div>
          <button className="btn btn-primary" onClick={() => window.location.assign('/')}>
            Go to Dashboard
          </button>
        </div>
      </div>
    );
  }
}

