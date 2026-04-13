import { create } from 'zustand';
import { devtools } from 'zustand/middleware';
import type { SessionResponse } from '../types/session';

type AppRole = 'interviewer' | 'interviewee' | null;

interface SessionState {
  sessionId: string | null;
  role: AppRole;
  currentSession: SessionResponse | null;
  currentCode: string;
  showReconnecting: boolean;

  setSession: (session: SessionResponse | null) => void;
  setRole: (role: AppRole) => void;
  setCurrentCode: (code: string) => void;
  setReconnecting: (show: boolean) => void;
  reset: () => void;
}

export const useSessionStore = create<SessionState>()(
  devtools(
    (set) => ({
      sessionId: null,
      role: null,
      currentSession: null,
      currentCode: '',
      showReconnecting: false,

      setSession: (session) =>
        set({
          sessionId: session?.id ?? null,
          currentSession: session,
          currentCode: session?.latestCode ?? '',
        }),
      setRole: (role) => set({ role }),
      setCurrentCode: (code) => set({ currentCode: code }),
      setReconnecting: (show) => set({ showReconnecting: show }),
      reset: () =>
        set({
          sessionId: null,
          role: null,
          currentSession: null,
          currentCode: '',
          showReconnecting: false,
        }),
    }),
    { name: 'SessionStore' }
  )
);
