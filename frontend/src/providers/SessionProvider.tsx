import { ReactNode, useEffect } from 'react';
import { useSessionStore } from '../stores/sessionStore';

export function SessionProvider({ children }: { children: ReactNode }) {
  const reset = useSessionStore((state) => state.reset);

  useEffect(() => {
    // Optional: Reset on unmount or page change
    return () => {
      reset();
    };
  }, [reset]);

  return <>{children}</>;
}

