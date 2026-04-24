import React from 'react';
import './ToastStack.css';

export type ToastTone = 'info' | 'warning' | 'danger';

export interface ToastItem {
  id: string;
  message: string;
  tone?: ToastTone;
  persistent?: boolean;
  createdAt?: number;
  autoCloseMs?: number;
}

interface ToastStackProps {
  toasts: ToastItem[];
  onDismiss?: (id: string) => void;
}

const ToastStack: React.FC<ToastStackProps> = ({ toasts, onDismiss }) => {
  const [now, setNow] = React.useState(() => Date.now());

  React.useEffect(() => {
    if (!toasts.some((toast) => toast.autoCloseMs && toast.createdAt)) {
      return;
    }

    const interval = window.setInterval(() => {
      setNow(Date.now());
    }, 250);

    return () => window.clearInterval(interval);
  }, [toasts]);

  React.useEffect(() => {
    if (!onDismiss) {
      return;
    }

    toasts.forEach((toast) => {
      const expiresAt = getToastExpiresAt(toast);
      if (expiresAt && expiresAt <= now) {
        onDismiss(toast.id);
      }
    });
  }, [now, onDismiss, toasts]);

  if (!toasts.length) {
    return null;
  }

  return (
    <div className="toast-stack" aria-live="polite" aria-atomic="true">
      {toasts.map((toast) => {
        const expiresAt = getToastExpiresAt(toast);
        const remainingMs = expiresAt ? Math.max(0, expiresAt - now) : null;
        const showCountdown = remainingMs !== null && remainingMs <= 5000;
        const countdownValue = remainingMs !== null ? Math.max(1, Math.ceil(remainingMs / 1000)) : null;
        const { title, body } = splitToastMessage(toast.message);

        return (
        <div key={toast.id} className={`toast-card ${toast.tone ?? 'info'}`}>
          <div className="toast-content">
            <div className="toast-message-group">
              <span className="toast-title">{title}</span>
              <span className="toast-message">{body}</span>
            </div>
            <div className="toast-actions">
              {onDismiss ? (
                <button
                  type="button"
                  className="toast-close"
                  onClick={() => onDismiss(toast.id)}
                  aria-label="Dismiss notification"
                >
                  X
                </button>
              ) : null}
              <span
                className={`toast-countdown ${showCountdown && countdownValue !== null ? 'is-visible' : 'is-hidden'}`}
                aria-label={showCountdown && countdownValue !== null ? `Notification closes in ${countdownValue} seconds` : undefined}
              >
                {showCountdown && countdownValue !== null ? countdownValue : ''}
              </span>
            </div>
          </div>
        </div>
      )})}
    </div>
  );
};

export default ToastStack;

function getToastExpiresAt(toast: ToastItem) {
  if (!toast.createdAt || !toast.autoCloseMs) {
    return null;
  }
  return toast.createdAt + toast.autoCloseMs;
}

function splitToastMessage(message: string) {
  const prefixes = ['Suspicious Alert :', 'Integrity Warning :', 'Integrity Notice :'];
  const prefix = prefixes.find((candidate) => message.startsWith(candidate));
  if (!prefix) {
    return {
      title: 'Alert',
      body: message,
    };
  }

  return {
    title: prefix,
    body: message.slice(prefix.length).trim(),
  };
}
