import React from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Editor from '../components/Editor';
import ShareUrlToggle from '../components/ShareUrlToggle';
import ToastStack, { ToastItem } from '../components/ToastStack';
import VideoPanel from '../components/VideoPanel';
import { useCameraMonitor } from '../hooks/useCameraMonitor';
import { useSessionStore } from '../stores/sessionStore';
import { sessionApi } from '../services/sessionApi';
import { useWebSocket } from '../hooks/useWebSocket';
import { useWebRtcSession } from '../hooks/useWebRtcSession';
import { useBackGuard } from '../hooks/useBackGuard';
import type { ActivityEventType, FeedbackRating, ParticipantRole, RecommendationDecision, SessionResponse, SessionSocketMessage, SessionStatus } from '../types/session';
import { formatDateTime, formatTimeZoneLabel } from '../utils/dateTime';

import './Session.css';

const FEEDBACK_COMMENT_LIMIT = 4000;

const STATUS_LABELS: Record<SessionStatus, string> = {
  CREATED: 'Ready to Start',
  WAITING_JOIN: 'Waiting for Interviewee to Join',
  ACTIVE: 'Interview In Progress',
  ENDED: 'Interview Ended',
  EXPIRED: 'Join Link Expired',
};

const Session: React.FC = () => {
  const { sessionId } = useParams<{ sessionId: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const storedRole = useSessionStore((state) => state.role);
  const role = storedRole || (searchParams.get('role') as 'interviewer' | 'interviewee' | null);
  const currentSession = useSessionStore((state) => state.currentSession);
  const setSession = useSessionStore((state) => state.setSession);
  const setRole = useSessionStore((state) => state.setRole);
  const currentCode = useSessionStore((state) => state.currentCode);
  const setCurrentCode = useSessionStore((state) => state.setCurrentCode);
  const [timeLeft, setTimeLeft] = React.useState<number | null>(null);
  const [isFullscreen, setIsFullscreen] = React.useState(false);
  const [toastItems, setToastItems] = React.useState<ToastItem[]>([]);
  const [incomingSignal, setIncomingSignal] = React.useState<SessionSocketMessage | null>(null);
  const [feedback, setFeedback] = React.useState({
    rating: '' as FeedbackRating | '',
    comments: '',
    recommendationDecision: '' as RecommendationDecision | '',
  });
  const [closeCountdown, setCloseCountdown] = React.useState<number | null>(null);
  const lastTabHiddenAtRef = React.useRef(0);
  const lastBlockedDropAtRef = React.useRef(0);
  const lastCameraStreamIssueRef = React.useRef('');
  const internalClipboardTextsRef = React.useRef<Map<string, number>>(new Map());

  const { data: session, isLoading, error } = useQuery({
    queryKey: ['session', sessionId],
    queryFn: () => sessionApi.getSession(sessionId!),
    enabled: !!sessionId,
    // Keep interviewer UI fresh pre-start so joinInfo disappears right after interviewee joins,
    // even if a websocket connection is not yet established.
    refetchInterval: (query) => {
      const data = query.state.data as SessionResponse | undefined;
      if (!data) return false;
      if (data.status === 'ACTIVE' || data.status === 'ENDED' || data.status === 'EXPIRED') return false;
      return 1500;
    },
  });

  const interviewer = session?.participants.find((participant) => participant.role === 'INTERVIEWER');
  const interviewee = session?.participants.find((participant) => participant.role === 'INTERVIEWEE');
  const intervieweeName = interviewee?.name?.trim() || 'Interviewee';
  const interviewerFirstName = firstName(interviewer?.name, 'Interviewer');
  const intervieweeFirstName = firstName(interviewee?.name, 'Interviewee');

  useBackGuard({
    enabled: true,
    message: 'Leave the interview session? You will be returned to the dashboard.',
    redirectTo: '/java',
  });

  React.useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'auto' });
  }, []);

  React.useEffect(() => {
    if (role && storedRole !== role) {
      setRole(role);
    }
  }, [role, setRole, storedRole]);

  React.useEffect(() => {
    if (session) {
      setSession(session);
      setTimeLeft(session.remainingSec);
      if (session.feedback) {
        setFeedback({
          rating: session.feedback.rating,
          comments: session.feedback.comments,
          recommendationDecision: session.feedback.recommendationDecision,
        });
      }
    }
  }, [session, setSession]);

  React.useEffect(() => {
    if (feedback.rating === 'BAD' && feedback.recommendationDecision !== 'NO') {
      setFeedback((prev) => ({ ...prev, recommendationDecision: 'NO' }));
    }
  }, [feedback.rating, feedback.recommendationDecision]);

  const refreshSession = React.useCallback(
    (nextSession: SessionResponse) => {
      queryClient.setQueryData(['session', sessionId], nextSession);
      queryClient.invalidateQueries({ queryKey: ['sessions'] });
      setSession(nextSession);
      setTimeLeft(nextSession.remainingSec);
    },
    [queryClient, sessionId, setSession]
  );

  const pushPersistentToast = React.useCallback((message: string, tone: ToastItem['tone'] = 'warning') => {
    const id = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
    const prefixedMessage = message.startsWith('Suspicious Alert :') ? message : `Suspicious Alert : ${message}`;
    setToastItems((prev) => [...prev, { id, message: prefixedMessage, tone, persistent: true, createdAt: Date.now(), autoCloseMs: 60000 }]);
  }, []);

  const dismissToast = React.useCallback((id: string) => {
    setToastItems((prev) => prev.filter((toast) => toast.id !== id));
  }, []);

  const rememberInternalClipboardText = React.useCallback((text: string) => {
    const normalized = normalizeClipboardText(text);
    if (!normalized) {
      return;
    }

    const next = new Map(internalClipboardTextsRef.current);
    next.set(normalized, Date.now());

    if (next.size > 25) {
      const oldestKey = next.keys().next().value;
      if (oldestKey) {
        next.delete(oldestKey);
      }
    }

    internalClipboardTextsRef.current = next;
  }, []);

  const toggleFullscreen = React.useCallback(() => {
    setIsFullscreen((prev) => !prev);
  }, []);

  React.useEffect(() => {
    if (isFullscreen) {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }, [isFullscreen]);

  const recordActivityEvent = React.useCallback(
    async (eventType: ActivityEventType, detail: string) => {
      if (!sessionId || role !== 'interviewee' || session?.status !== 'ACTIVE') {
        return;
      }

      try {
        await sessionApi.recordActivityEvent(sessionId, {
          participantRole: 'INTERVIEWEE',
          eventType,
          detail,
        });
      } catch {
        // best-effort only
      }
    },
    [role, session?.status, sessionId]
  );

  const handleSocketMessage = React.useCallback(
    (message: SessionSocketMessage) => {
      if (message.session) {
        refreshSession(message.session);
      }
      if (message.type === 'CODE_UPDATE' && message.code) {
        setCurrentCode(message.code);
      }
      if (typeof message.timeLeft === 'number') {
        setTimeLeft(message.timeLeft);
      }
      if (message.type === 'ACTIVITY_EVENT' && message.activityEvent && role === 'interviewer') {
        pushPersistentToast(message.activityEvent.detail, 'warning');
      }
      if (message.type === 'WEBRTC_SIGNAL') {
        setIncomingSignal(message);
      }
    },
    [pushPersistentToast, refreshSession, role, setCurrentCode]
  );

  const { isReconnecting, sendCode, sendSignal } = useWebSocket(sessionId || '', handleSocketMessage);
  const isCameraSessionActive = session?.status === 'ACTIVE';
  const handleCameraStreamLost = React.useCallback(() => {
    void recordActivityEvent('CAMERA_STREAM_LOST', `${formatPossessiveLabel(intervieweeName)} camera stream was interrupted.`);
  }, [intervieweeName, recordActivityEvent]);
  const handleNoFaceDetected = React.useCallback(() => {
    void recordActivityEvent('NO_FACE_DETECTED', `${formatPossessiveLabel(intervieweeName)} face was not visible in the camera frame.`);
  }, [intervieweeName, recordActivityEvent]);
  const handleMultipleFacesDetected = React.useCallback(() => {
    void recordActivityEvent('MULTIPLE_FACES_DETECTED', `Multiple faces were detected in ${formatPossessiveLabel(intervieweeName)} camera frame.`);
  }, [intervieweeName, recordActivityEvent]);
  const {
    connectionState: cameraConnectionState,
    localStream,
    remoteStream,
    streamError,
    isMuted,
    isCameraEnabled,
    canToggleCamera,
    toggleMute,
    toggleCamera,
  } = useWebRtcSession({
    enabled: Boolean(sessionId && role && isCameraSessionActive),
    role: role === 'interviewer' ? 'interviewer' : 'interviewee',
    incomingSignal,
    sendSignal,
  });
  const { status: cameraMonitorStatus } = useCameraMonitor({
    enabled: role === 'interviewee' && isCameraSessionActive,
    stream: localStream,
    onStreamLost: handleCameraStreamLost,
    onNoFaceDetected: handleNoFaceDetected,
    onMultipleFacesDetected: handleMultipleFacesDetected,
  });

  const startMutation = useMutation({
    mutationFn: () => sessionApi.startSession(sessionId!),
    onSuccess: refreshSession,
  });

  const extendMutation = useMutation({
    mutationFn: () => sessionApi.extendSession(sessionId!),
    onSuccess: refreshSession,
  });

  const endMutation = useMutation({
    mutationFn: () =>
      sessionApi.endSession(sessionId!, {
        finalCode: currentCode || currentSession?.latestCode || '',
      }),
    onSuccess: refreshSession,
  });

  const feedbackMutation = useMutation({
    mutationFn: () =>
      sessionApi.submitFeedback(sessionId!, {
        rating: feedback.rating as FeedbackRating,
        comments: feedback.comments,
        recommendationDecision: feedback.recommendationDecision as RecommendationDecision,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sessions'] });
      navigate('/java');
    },
  });

  const canFullscreen = role === 'interviewee';

  React.useEffect(() => {
    if (!canFullscreen && isFullscreen) {
      setIsFullscreen(false);
    }
  }, [canFullscreen, isFullscreen]);

  React.useEffect(() => {
    if (!canFullscreen || session?.status !== 'ACTIVE') {
      return;
    }

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.ctrlKey && event.shiftKey && event.key.toLowerCase() === 'f') {
        event.preventDefault();
        event.stopPropagation();
        toggleFullscreen();
      }
    };

    window.addEventListener('keydown', onKeyDown, true);
    return () => window.removeEventListener('keydown', onKeyDown, true);
  }, [canFullscreen, session?.status, toggleFullscreen]);

  React.useEffect(() => {
    if (!sessionId) return;
    if (!session || session.status !== 'ACTIVE') return;

    const message =
      'Interview in progress. Leaving will mark this interview as INCOMPLETE. Do you want to continue?';

    const beforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = message;
      return message;
    };

    const sendAbandon = () => {
      try {
        const apiBase = process.env.REACT_APP_API_URL || '/api';
        const url = `${apiBase.replace(/\/$/, '')}/sessions/${sessionId}/abandon`;

        const payload = JSON.stringify({
          finalCode: currentCode || session.latestCode || '',
        });
        const blob = new Blob([payload], { type: 'application/json' });
        navigator.sendBeacon(url, blob);
      } catch {
        // best-effort only
      }
    };

    const pageHide = () => {
      sendAbandon();
    };

    window.addEventListener('beforeunload', beforeUnload);
    window.addEventListener('pagehide', pageHide);
    return () => {
      window.removeEventListener('beforeunload', beforeUnload);
      window.removeEventListener('pagehide', pageHide);
    };
  }, [currentCode, session, sessionId]);

  React.useEffect(() => {
    if (role !== 'interviewee' || !sessionId || session?.status !== 'ACTIVE') {
      return;
    }

    const onVisibilityChange = () => {
      if (document.visibilityState !== 'hidden') {
        return;
      }

      const now = Date.now();
      if (now - lastTabHiddenAtRef.current < 1200) {
        return;
      }
      lastTabHiddenAtRef.current = now;

      void recordActivityEvent('TAB_HIDDEN', `${interviewee?.name?.trim() || 'Interviewee'} switched away from the interview tab or window.`);
    };

    document.addEventListener('visibilitychange', onVisibilityChange);
    return () => document.removeEventListener('visibilitychange', onVisibilityChange);
  }, [interviewee?.name, recordActivityEvent, role, session?.status, sessionId]);

  React.useEffect(() => {
    if (role !== 'interviewee' || session?.status !== 'ENDED') {
      setCloseCountdown(null);
      return;
    }

    setCloseCountdown((previous) => previous ?? 10);
    const interval = window.setInterval(() => {
      setCloseCountdown((previous) => {
        if (previous === null) {
          return 10;
        }
        if (previous <= 1) {
          window.clearInterval(interval);
          window.location.assign('https://www.altimetrik.com/');
          return 0;
        }
        return previous - 1;
      });
    }, 1000);

    return () => window.clearInterval(interval);
  }, [role, session?.status]);

  React.useEffect(() => {
    if (role !== 'interviewee' || session?.status !== 'ACTIVE' || !streamError) {
      if (session?.status !== 'ACTIVE') {
        lastCameraStreamIssueRef.current = '';
      }
      return;
    }

    if (lastCameraStreamIssueRef.current === streamError) {
      return;
    }

    lastCameraStreamIssueRef.current = streamError;
    void recordActivityEvent(
      'CAMERA_STREAM_LOST',
      `${formatPossessiveLabel(intervieweeName)} camera stream was interrupted.`
    );
  }, [intervieweeName, recordActivityEvent, role, session?.status, streamError]);

  if (isLoading) {
    return <div className="page-shell"><div className="page-card">Loading session...</div></div>;
  }

  if (error || !session || !role) {
    return <div className="page-shell"><div className="page-card">Unable to load session.</div></div>;
  }

  const isInterviewer = role === 'interviewer';
  const wsRole: ParticipantRole = isInterviewer ? 'INTERVIEWER' : 'INTERVIEWEE';
  const displayedTimeLeft = timeLeft ?? session.remainingSec;
  const canStart = isInterviewer && session.status === 'CREATED';
  const canEnd = isInterviewer && session.status === 'ACTIVE';
  const canExtend =
    isInterviewer && session.status === 'ACTIVE' && !session.extensionUsed && displayedTimeLeft <= 15 * 60;
  const showEditor = session.status !== 'ENDED' && session.status !== 'EXPIRED';
  const showPreStartState = showEditor && session.status !== 'ACTIVE';
  const waitingForFeedback = isInterviewer && session.status === 'ENDED' && !session.feedback;
  const waitingJoinLabel = `Waiting for ${interviewee?.name || 'Interviewee'} to join`;
  const hasCompleteFeedback = Boolean(feedback.rating && feedback.recommendationDecision && feedback.comments.trim());

  const timerLabel =
    session.status === 'ACTIVE'
      ? `${Math.floor(displayedTimeLeft / 60)}:${(displayedTimeLeft % 60).toString().padStart(2, '0')}`
      : null;
  const feedbackCharsRemaining = FEEDBACK_COMMENT_LIMIT - feedback.comments.length;
  const statusTimestampLabel = session.startedAt ? 'Started' : 'Created';
  const statusTimestampValue = session.startedAt ? formatDateTime(session.startedAt) : formatDateTime(session.createdAt);
  const interviewerCameraStatus = remoteStream
    ? cameraConnectionState === 'connected'
      ? remoteStream.getVideoTracks().length > 0
        ? 'Live audio and video active'
        : 'Live audio active'
      : 'Connecting live media'
    : isInterviewer
      ? 'Waiting for the interviewee media stream'
      : 'Waiting for the interviewer media stream';
  const localMediaStatus = streamError
    ? streamError
    : isInterviewer
      ? `${isMuted ? 'Mic muted' : 'Mic live'} • ${isCameraEnabled ? 'Camera live' : 'Camera off'}`
      : cameraMonitorStatus === 'multiple-faces'
        ? 'Mic live • Multiple faces detected'
        : cameraMonitorStatus === 'no-face'
          ? 'Mic live • Face not visible'
          : cameraMonitorStatus === 'stream-lost'
            ? 'Mic live • Camera interrupted'
            : cameraMonitorStatus === 'face-checks-unavailable'
              ? `${isMuted ? 'Mic muted' : 'Mic live'} • Camera active`
              : `${isMuted ? 'Mic muted' : 'Mic live'} • Camera live`;
  const activeStageClassName = `interviewer-stage ${session.status === 'ACTIVE' ? 'active' : ''}`;
  const activeBannerClassName = `session-banner session-banner-interviewer ${session.status === 'ACTIVE' ? 'active' : ''}`;
  const remotePanelTitle = isInterviewer
    ? `${intervieweeFirstName} live stream`
    : `${interviewerFirstName} live stream`;
  const remoteEmptyMessage = isInterviewer
    ? 'Waiting for the interviewee audio and camera stream to connect.'
    : 'Waiting for the interviewer stream to connect.';
  const remoteVideoStream = remoteStream && remoteStream.getVideoTracks().length > 0 ? remoteStream : null;
  const localVideoStream = localStream && localStream.getVideoTracks().length > 0 ? localStream : null;
  const displayedPanelStream =
    remoteVideoStream ?? localVideoStream;
  const panelStatus = remoteStream
    ? interviewerCameraStatus
    : localStream
      ? localMediaStatus
      : streamError || 'Preparing local media';

  return (
    <div className={`session-page polished-page ${isFullscreen ? 'fullscreen' : ''}`}>
      {isReconnecting && <div className="reconnecting">Reconnecting to the live session...</div>}

      {!isFullscreen && session.status === 'ACTIVE' ? (
        <div className={activeStageClassName}>
          <div className={activeBannerClassName}>
            <div className="banner-participants">
              <div className="participant-card">
                <span className="participant-role">Interviewer</span>
                <span className="participant-name">{interviewer?.name}{interviewer?.timeZone ? ` (${formatTimeZoneLabel(interviewer.timeZone)})` : ''}</span>
                <span>{interviewer?.email}</span>
              </div>
              <div className="participant-card">
                <span className="participant-role">Interviewee</span>
                <span className="participant-name">{interviewee?.name}{interviewee?.timeZone ? ` (${formatTimeZoneLabel(interviewee.timeZone)})` : ''}</span>
                <span>{interviewee?.email}</span>
                {interviewee?.identityCaptureStatus && (
                  <span className="participant-meta">
                    Identity snapshot: {formatIdentityCaptureStatus(interviewee.identityCaptureStatus, interviewee.identityCaptureFailureReason)}
                  </span>
                )}
              </div>
            </div>

            <div className="session-status session-status-interviewer">
              <div className="session-status-row">
                <div className={`status-chip status-chip-${session.status.toLowerCase()}`}>
                  {STATUS_LABELS[session.status]}
                </div>
                {timerLabel && <div className="timer timer-live">Time left: {timerLabel}</div>}
                <div className={`role-indicator ${role}`}>Role: {role}</div>
                {canStart && (
                  <button
                    className="control-btn btn-start session-status-inline-action"
                    onClick={() => startMutation.mutate()}
                    disabled={startMutation.isPending}
                  >
                    {startMutation.isPending ? 'Starting...' : 'Start Interview'}
                  </button>
                )}
                {canEnd && (
                  <button
                    className="control-btn btn-end session-status-inline-action"
                    onClick={() => endMutation.mutate()}
                    disabled={endMutation.isPending}
                  >
                    {endMutation.isPending ? 'Ending...' : 'End Interview'}
                  </button>
                )}
              </div>
              <div className="status-meta">{statusTimestampLabel}: {statusTimestampValue}</div>
            </div>
          </div>

          <VideoPanel
            title={remotePanelTitle}
            stream={displayedPanelStream}
            audioStream={remoteStream}
            status={panelStatus}
            emptyMessage={remoteEmptyMessage}
            mirror={!remoteVideoStream && Boolean(localVideoStream)}
            muted
            headerActions={
              <div className="media-action-group">
                <button
                  type="button"
                  className={`media-icon-button ${isMuted ? 'off' : 'on'}`}
                  onClick={toggleMute}
                  aria-label={isMuted ? 'Unmute microphone' : 'Mute microphone'}
                  title={isMuted ? 'Unmute microphone' : 'Mute microphone'}
                >
                  <MicrophoneIcon muted={isMuted} />
                </button>
                {canToggleCamera ? (
                  <button
                    type="button"
                    className={`media-icon-button ${isCameraEnabled ? 'on' : 'off'}`}
                    onClick={toggleCamera}
                    aria-label={isCameraEnabled ? 'Turn camera off' : 'Turn camera on'}
                    title={isCameraEnabled ? 'Turn camera off' : 'Turn camera on'}
                  >
                    <CameraIcon disabled={!isCameraEnabled} />
                  </button>
                ) : null}
              </div>
            }
          />
        </div>
      ) : !isFullscreen && (
        <div className="session-banner">
          <div className="banner-participants">
            <div className="participant-card">
              <span className="participant-role">Interviewer</span>
              <span className="participant-name">{interviewer?.name}{interviewer?.timeZone ? ` (${formatTimeZoneLabel(interviewer.timeZone)})` : ''}</span>
              <span>{interviewer?.email}</span>
            </div>
            <div className="participant-card">
              <span className="participant-role">Interviewee</span>
              <span className="participant-name">{interviewee?.name}{interviewee?.timeZone ? ` (${formatTimeZoneLabel(interviewee.timeZone)})` : ''}</span>
              <span>{interviewee?.email}</span>
              {interviewee?.identityCaptureStatus && (
                <span className="participant-meta">
                  Identity snapshot: {formatIdentityCaptureStatus(interviewee.identityCaptureStatus, interviewee.identityCaptureFailureReason)}
                </span>
              )}
            </div>
          </div>

          <div className="session-status">
            <div className="session-status-row">
              <div className={`status-chip status-chip-${session.status.toLowerCase()}`}>
                {session.status === 'WAITING_JOIN' ? waitingJoinLabel : STATUS_LABELS[session.status]}
              </div>
              {session.status === 'ACTIVE' && timerLabel && (
                <div className="timer timer-live">Time left: {timerLabel}</div>
              )}
              <div className={`role-indicator ${role}`}>Role: {role}</div>
              {canStart && (
                <button
                  className="control-btn btn-start session-status-inline-action"
                  onClick={() => startMutation.mutate()}
                  disabled={startMutation.isPending}
                >
                  {startMutation.isPending ? 'Starting...' : 'Start Interview'}
                </button>
              )}
              {canEnd && (
                <button
                  className="control-btn btn-end session-status-inline-action"
                  onClick={() => endMutation.mutate()}
                  disabled={endMutation.isPending}
                >
                  {endMutation.isPending ? 'Ending...' : 'End Interview'}
                </button>
              )}
            </div>
            <div className="status-meta">{statusTimestampLabel}: {statusTimestampValue}</div>
          </div>
        </div>
      )}

      {isFullscreen && session.status === 'ACTIVE' && timerLabel && (
        <div className="fullscreen-hud">
          <div className="fullscreen-timer">Time left: {timerLabel}</div>
          {canFullscreen && (
            <button className="btn btn-secondary btn-small" onClick={() => setIsFullscreen(false)}>
              Exit
            </button>
          )}
        </div>
      )}

      {isInterviewer && session.joinInfo && session.status !== 'ACTIVE' && (
        <ShareUrlToggle token={session.joinInfo.token} />
      )}

      {showEditor ? (
        <>
          {canExtend && (
            <div className="session-controls">
              <button className="control-btn btn-extend" onClick={() => extendMutation.mutate()} disabled={extendMutation.isPending}>
                {extendMutation.isPending ? 'Extending...' : 'Extend Once by 15 Minutes'}
              </button>
            </div>
          )}

          {showPreStartState && (
            <div className="waiting-panel">
              <h3>{session.status === 'WAITING_JOIN' ? waitingJoinLabel : 'Interview is ready to start'}</h3>
              <p>
                The editor is visible in read-only mode until the interviewer starts the interview. Once started, both
                participants can collaborate live and the 60 minute countdown begins.
              </p>
            </div>
          )}

          <Editor
            readOnly={session.status !== 'ACTIVE'}
            canRun={session.status === 'ACTIVE'}
            showResetButton={isInterviewer}
            onCopyFromEditor={rememberInternalClipboardText}
            onCutFromEditor={rememberInternalClipboardText}
            onExternalDropBlocked={() => {
              if (role !== 'interviewee' || session.status !== 'ACTIVE') {
                return;
              }

              const now = Date.now();
              if (now - lastBlockedDropAtRef.current < 1200) {
                return;
              }
              lastBlockedDropAtRef.current = now;

              void recordActivityEvent(
                'EXTERNAL_DROP_BLOCKED',
                `${interviewee?.name?.trim() || 'Interviewee'} tried to drag text into the editor.`
              );
            }}
            onPasteInEditor={(text) => {
              if (role !== 'interviewee' || session.status !== 'ACTIVE') {
                return true;
              }

              const normalized = normalizeClipboardText(text);
              if (normalized && internalClipboardTextsRef.current.has(normalized)) {
                return true;
              }

              const normalizedCurrentCode = normalizeClipboardText(currentCode || session.latestCode || '');
              const normalizedWithoutIndent = normalizeClipboardShape(text);
              const currentCodeWithoutIndent = normalizeClipboardShape(currentCode || session.latestCode || '');
              if (
                normalized &&
                normalized.includes('\n') &&
                (normalizedCurrentCode.includes(normalized) || currentCodeWithoutIndent.includes(normalizedWithoutIndent))
              ) {
                return true;
              }

              const lineCount = text.split(/\r?\n/).length;
              const charCount = text.length;
              void recordActivityEvent(
                'PASTE_IN_EDITOR',
                `${interviewee?.name?.trim() || 'Interviewee'} pasted ${charCount} characters across ${lineCount} line${lineCount === 1 ? '' : 's'} into the editor.`
              );
              return false;
            }}
            emptyStateMessage={
              session.status !== 'ACTIVE'
                ? 'Editing and code execution are disabled until the interviewer starts the interview.'
                : undefined
            }
            initialCode={currentCode || session.latestCode || ''}
            showFullscreenToggle={canFullscreen}
            isFullscreen={isFullscreen}
            onToggleFullscreen={toggleFullscreen}
            onCodeChange={(code) => {
              setCurrentCode(code);
              if (session.status === 'ACTIVE') {
                const nextVersion = ((currentSession?.codeVersion ?? session.codeVersion) || 0) + 1;
                const nextSession = {
                  ...session,
                  latestCode: code,
                  codeVersion: nextVersion,
                };
                refreshSession(nextSession);
                sendCode(code, nextVersion, wsRole);
              }
            }}
          />
        </>
      ) : (
        <div className="page-card session-end-card">
          <div className="page-kicker">Interview Complete</div>
          <h2>{isInterviewer ? 'Submit final feedback' : 'Interview has ended'}</h2>
          <p className="page-subtitle">
            {isInterviewer
              ? 'The editor has been frozen, final code/output have been saved, and your feedback is required before returning to the dashboard.'
              : `The coding test is now complete. ${interviewer?.name || 'The interviewer'} is submitting the feedback. You can check the results with ${interviewer?.name || 'the interviewer'} or the HR.${typeof closeCountdown === 'number' ? ` This tab will automatically close in ${closeCountdown}.` : ''}`}
          </p>

          {waitingForFeedback ? (
            <div className="feedback-section end-screen">
              <div className="feedback-form">
                <div className="feedback-field">
                  <label htmlFor="rating">Rating</label>
                  <select
                    id="rating"
                    value={feedback.rating}
                    onChange={(event) =>
                      setFeedback((prev) => ({
                        ...prev,
                        rating: event.target.value as FeedbackRating | '',
                        recommendationDecision:
                          event.target.value === 'BAD' ? 'NO' : prev.recommendationDecision,
                      }))
                    }
                  >
                    <option value="">Select rating</option>
                    <option value="EXCELLENT">Excellent</option>
                    <option value="GOOD">Good</option>
                    <option value="FAIR">Fair</option>
                    <option value="BAD">Bad</option>
                  </select>
                </div>
                <div className="feedback-field">
                  <label htmlFor="comments">Comments</label>
                  <textarea
                    id="comments"
                    className="feedback-textarea"
                    maxLength={FEEDBACK_COMMENT_LIMIT}
                    value={feedback.comments}
                    onChange={(event) =>
                      setFeedback((prev) => ({
                        ...prev,
                        comments: event.target.value.slice(0, FEEDBACK_COMMENT_LIMIT),
                      }))
                    }
                  />
                  <div className={`feedback-counter ${feedbackCharsRemaining <= 200 ? 'near-limit' : ''}`}>
                    {feedbackCharsRemaining} characters remaining
                  </div>
                </div>
                <div className="feedback-field">
                  <label htmlFor="recommendation">Recommendation</label>
                  <select
                    id="recommendation"
                    value={feedback.recommendationDecision}
                    onChange={(event) => setFeedback((prev) => ({ ...prev, recommendationDecision: event.target.value as RecommendationDecision | '' }))}
                    disabled={feedback.rating === 'BAD'}
                  >
                    <option value="">Select recommendation</option>
                    <option value="YES">Next round yes</option>
                    <option value="NO">Next round no</option>
                    <option value="REEVALUATION">Reevaluation</option>
                  </select>
                </div>
                <div className="feedback-actions">
                  <button
                    className="control-btn btn-start"
                    onClick={() => feedbackMutation.mutate()}
                    disabled={feedbackMutation.isPending || !hasCompleteFeedback}
                  >
                    {feedbackMutation.isPending ? 'Submitting...' : 'Submit Feedback'}
                  </button>
                </div>
              </div>
            </div>
          ) : (
            isInterviewer ? (
              <div className="feedback-actions">
                <button className="control-btn btn-start" onClick={() => navigate('/java')}>
                  Back to Dashboard
                </button>
              </div>
            ) : null
          )}
        </div>
      )}
      <ToastStack toasts={toastItems} onDismiss={dismissToast} />
    </div>
  );
};

export default Session;

function normalizeClipboardText(value: string) {
  return value
    .replace(/\r\n/g, '\n')
    .replace(/\r/g, '\n')
    .replace(/\u00a0/g, ' ')
    .replace(/\n{3,}/g, '\n\n')
    .replace(/[ \t]+\n/g, '\n')
    .trim();
}

function normalizeClipboardShape(value: string) {
  return normalizeClipboardText(value)
    .split('\n')
    .map((line) => line.trim())
    .join('\n')
    .trim();
}

function formatIdentityCaptureStatus(status?: string | null, reason?: string | null) {
  if (status === 'SUCCESS') {
    return 'Captured';
  }
  if (status === 'FAILED') {
    return reason ? `Issue recorded (${formatCaptureReason(reason)})` : 'Issue recorded';
  }
  if (status === 'SKIPPED') {
    return 'Skipped';
  }
  return 'Pending';
}

function formatCaptureReason(reason: string) {
  return reason
    .toLowerCase()
    .split('_')
    .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
    .join(' ');
}

function formatPossessiveLabel(name: string) {
  return name.endsWith('s') ? `${name}'` : `${name}'s`;
}

function firstName(value: string | undefined, fallback: string) {
  const normalized = value?.trim();
  if (!normalized) {
    return fallback;
  }
  return normalized.split(/\s+/)[0] || fallback;
}

function MicrophoneIcon({ muted }: { muted: boolean }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path
        d="M12 15.25a3.25 3.25 0 0 0 3.25-3.25V7a3.25 3.25 0 1 0-6.5 0v5A3.25 3.25 0 0 0 12 15.25Z"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M6.75 11.75a5.25 5.25 0 0 0 8.92 3.75M17.25 11.75A5.25 5.25 0 0 1 12 17v2.25M9.75 19.25h4.5"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      {muted ? (
        <path
          d="M5 5l14 14"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
        />
      ) : null}
    </svg>
  );
}

function CameraIcon({ disabled }: { disabled: boolean }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path
        d="M4.75 8.5A1.75 1.75 0 0 1 6.5 6.75h8A1.75 1.75 0 0 1 16.25 8.5v7A1.75 1.75 0 0 1 14.5 17.25h-8A1.75 1.75 0 0 1 4.75 15.5v-7Z"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M16.25 10.25 19.25 8.5v7l-3-1.75"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      {disabled ? (
        <path
          d="M5 5l14 14"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
        />
      ) : null}
    </svg>
  );
}
