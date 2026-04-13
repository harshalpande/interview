import React from 'react';
import { useNavigate } from 'react-router-dom';

type BackGuardOptions = {
  enabled: boolean;
  message: string;
  redirectTo?: string;
};

export function useBackGuard({ enabled, message, redirectTo = '/' }: BackGuardOptions) {
  const navigate = useNavigate();

  React.useEffect(() => {
    if (!enabled) return;

    // Push a synthetic state so the first "Back" triggers popstate in-app.
    window.history.pushState({ backGuard: true }, '');

    const handler = () => {
      const ok = window.confirm(message);
      if (ok) {
        navigate(redirectTo, { replace: true });
        return;
      }

      // User cancelled; restore the synthetic state so Back remains guarded.
      window.history.pushState({ backGuard: true }, '');
    };

    window.addEventListener('popstate', handler);
    return () => window.removeEventListener('popstate', handler);
  }, [enabled, message, navigate, redirectTo]);
}

