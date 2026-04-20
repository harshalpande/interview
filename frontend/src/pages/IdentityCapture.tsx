import React from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '../components/Button';
import { sessionApi } from '../services/sessionApi';
import { useSessionStore } from '../stores/sessionStore';
import { useBackGuard } from '../hooks/useBackGuard';
import type { IdentityCaptureFailureReason, IdentityCaptureStatus } from '../types/session';
import './IdentityCapture.css';

type CaptureStep = 'intro' | 'starting' | 'preview' | 'countdown' | 'captured' | 'issue' | 'saving';

const IdentityCapture: React.FC = () => {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const storedSession = useSessionStore((state) => state.currentSession);
  const setSession = useSessionStore((state) => state.setSession);
  const setRole = useSessionStore((state) => state.setRole);
  const role = useSessionStore((state) => state.role);
  const videoRef = React.useRef<HTMLVideoElement | null>(null);
  const streamRef = React.useRef<MediaStream | null>(null);
  const countdownTimerRef = React.useRef<number | null>(null);
  const [step, setStep] = React.useState<CaptureStep>('intro');
  const [countdown, setCountdown] = React.useState(5);
  const [capturedBlob, setCapturedBlob] = React.useState<Blob | null>(null);
  const [previewUrl, setPreviewUrl] = React.useState<string | null>(null);
  const [issueMessage, setIssueMessage] = React.useState('');
  const [issueReason, setIssueReason] = React.useState<IdentityCaptureFailureReason>('UNKNOWN');
  const [submitError, setSubmitError] = React.useState('');

  const { data: session, isLoading } = useQuery({
    queryKey: ['session', sessionId],
    queryFn: () => sessionApi.getSession(sessionId!),
    enabled: !!sessionId,
    initialData: storedSession && storedSession.id === sessionId ? storedSession : undefined,
  });

  const interviewee = session?.participants.find((participant) => participant.role === 'INTERVIEWEE');

  useBackGuard({
    enabled: true,
    message: 'Leave identity capture? You will be returned to the dashboard.',
    redirectTo: '/',
  });

  React.useEffect(() => {
    setRole('interviewee');
  }, [setRole]);

  React.useEffect(() => {
    if (session) {
      setSession(session);
    }
  }, [session, setSession]);

  React.useEffect(() => {
    const video = videoRef.current;
    const stream = streamRef.current;
    if (!video || !stream || (step !== 'preview' && step !== 'countdown')) {
      return;
    }

    video.srcObject = stream;

    const handleLoadedMetadata = () => {
      void video.play().catch(() => {
        // Edge/Safari can sometimes delay playback; keep the stream attached and let the user retry if needed.
      });
    };

    video.addEventListener('loadedmetadata', handleLoadedMetadata);
    handleLoadedMetadata();

    return () => {
      video.removeEventListener('loadedmetadata', handleLoadedMetadata);
      if (video.srcObject === stream) {
        video.srcObject = null;
      }
    };
  }, [step]);

  React.useEffect(() => {
    return () => {
      stopCamera(streamRef);
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
      }
      if (countdownTimerRef.current) {
        window.clearInterval(countdownTimerRef.current);
      }
    };
  }, [previewUrl]);

  const goToDisclaimer = React.useCallback(
    (nextSessionId: string) => {
      navigate(`/java/disclaimer/interviewee?sessionId=${nextSessionId}`);
    },
    [navigate]
  );

  const persistCaptureState = React.useCallback(
    async (status: IdentityCaptureStatus, failureReason?: IdentityCaptureFailureReason, image?: Blob) => {
      if (!sessionId) {
        return;
      }

      setStep('saving');
      setSubmitError('');

      try {
        const updated = await sessionApi.submitIdentityCapture(sessionId, {
          role: 'INTERVIEWEE',
          status,
          failureReason,
          image,
          filename: image ? 'identity-snapshot.jpg' : undefined,
        });
        queryClient.setQueryData(['session', sessionId], updated);
        queryClient.invalidateQueries({ queryKey: ['sessions'] });
        setSession(updated);
        goToDisclaimer(updated.id);
      } catch (error) {
        setSubmitError(error instanceof Error ? error.message : 'Unable to save identity capture');
        setStep(status === 'SUCCESS' ? 'captured' : 'issue');
      }
    },
    [goToDisclaimer, queryClient, sessionId, setSession]
  );

  const handleStartCamera = React.useCallback(async () => {
    if (!navigator.mediaDevices?.getUserMedia) {
      setIssueReason('UNSUPPORTED');
      setIssueMessage('This browser does not support camera access. You can continue and the issue will be recorded.');
      setStep('issue');
      return;
    }

    stopCamera(streamRef);
    setCapturedBlob(null);
    setStep('starting');
    setSubmitError('');

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: 'user',
          width: { ideal: 1280 },
          height: { ideal: 720 },
        },
        audio: false,
      });

      streamRef.current = stream;
      setStep('preview');
    } catch (error) {
      const { reason, message } = mapCameraError(error);
      setIssueReason(reason);
      setIssueMessage(message);
      setStep('issue');
    }
  }, []);

  const handleCapture = React.useCallback(() => {
    if (!videoRef.current) {
      return;
    }

    setCountdown(5);
    setStep('countdown');
    countdownTimerRef.current = window.setInterval(() => {
      setCountdown((previous) => {
        if (previous <= 1) {
          if (countdownTimerRef.current) {
            window.clearInterval(countdownTimerRef.current);
            countdownTimerRef.current = null;
          }
          void captureFrame(videoRef.current!, setCapturedBlob, setPreviewUrl, () => setStep('captured'));
          stopCamera(streamRef);
          return 1;
        }
        return previous - 1;
      });
    }, 1000);
  }, []);

  const handleRetake = React.useCallback(() => {
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
    }
    setPreviewUrl(null);
    setCapturedBlob(null);
    void handleStartCamera();
  }, [handleStartCamera, previewUrl]);

  if (isLoading || !session || role !== 'interviewee') {
    return <div className="page-shell"><div className="page-card">Preparing identity capture...</div></div>;
  }

  return (
    <div className="page-shell">
      <div className="page-card identity-capture-card">
        <div className="page-kicker">Identity Snapshot</div>
        <h2>Capture a quick photo before entering the interview</h2>
        <p className="page-subtitle">
          {interviewee?.name ? `${interviewee.name}, ` : ''}
          allow camera access and center your face in the frame. If the camera is unavailable, you can still continue and we will record the issue.
        </p>

        {submitError && <div className="error-banner">{submitError}</div>}

        <div className="identity-capture-layout">
          <section className="identity-capture-stage">
            <div className={`capture-frame-shell step-${step}`}>
              {(step === 'preview' || step === 'countdown') && (
                <>
                  <video ref={videoRef} className="capture-video" playsInline muted autoPlay />
                  <div className="capture-overlay">
                    <div className="capture-face-frame" />
                    <div className="capture-guidance">Center your face inside the frame</div>
                  </div>
                  {step === 'countdown' && <div className="capture-countdown">{countdown}</div>}
                </>
              )}

              {step === 'captured' && previewUrl && (
                <div className="capture-preview">
                  <img src={previewUrl} alt="Interviewee identity snapshot preview" className="capture-image" />
                </div>
              )}

              {(step === 'intro' || step === 'starting' || step === 'saving') && (
                <div className="capture-placeholder">
                  <div className="capture-placeholder-frame" />
                  <p>
                    {step === 'saving'
                      ? 'Saving your capture details...'
                      : step === 'starting'
                        ? 'Starting the camera preview...'
                        : 'A guided camera preview will appear here once you approve access.'}
                  </p>
                </div>
              )}

              {step === 'issue' && (
                <div className="capture-issue">
                  <div className="capture-issue-badge">Camera unavailable</div>
                  <p>{issueMessage}</p>
                </div>
              )}
            </div>
          </section>

          <aside className="identity-capture-panel hint-panel">
            <p><strong>Quick steps</strong></p>
            <p>Press <strong>OK</strong> to start the camera.</p>
            <p>A 5 second countdown captures the photo automatically.</p>
            <p>If the camera does not work, continue and the issue will be stored with the session.</p>
            <div className="identity-capture-actions identity-capture-panel-actions">
              {step === 'intro' && (
                <>
                  <Button onClick={handleStartCamera}>OK</Button>
                  <Button variant="secondary" onClick={() => void persistCaptureState('SKIPPED', 'USER_SKIPPED')}>
                    Continue without photo
                  </Button>
                </>
              )}

              {step === 'preview' && (
                <>
                  <Button onClick={handleCapture}>Capture Photo</Button>
                  <Button variant="secondary" onClick={() => void persistCaptureState('SKIPPED', 'USER_SKIPPED')}>
                    Continue without photo
                  </Button>
                </>
              )}

              {step === 'captured' && (
                <>
                  <Button onClick={() => capturedBlob && void persistCaptureState('SUCCESS', undefined, capturedBlob)} disabled={!capturedBlob}>
                    Continue
                  </Button>
                  <Button variant="secondary" onClick={handleRetake}>Retake</Button>
                </>
              )}

              {step === 'issue' && (
                <>
                  <Button onClick={handleStartCamera}>Retry Camera</Button>
                  <Button variant="secondary" onClick={() => void persistCaptureState('FAILED', issueReason)}>
                    Continue and Record Issue
                  </Button>
                </>
              )}
            </div>
          </aside>
        </div>
      </div>
    </div>
  );
};

export default IdentityCapture;

async function captureFrame(
  video: HTMLVideoElement,
  setCapturedBlob: (blob: Blob | null) => void,
  setPreviewUrl: (url: string | null) => void,
  onDone: () => void
) {
  const canvas = document.createElement('canvas');
  canvas.width = video.videoWidth || 1280;
  canvas.height = video.videoHeight || 720;
  const context = canvas.getContext('2d');
  if (!context) {
    onDone();
    return;
  }

  context.drawImage(video, 0, 0, canvas.width, canvas.height);

  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.92));
  if (!blob) {
    onDone();
    return;
  }

  setCapturedBlob(blob);
  setPreviewUrl(URL.createObjectURL(blob));
  onDone();
}

function stopCamera(streamRef: React.MutableRefObject<MediaStream | null>) {
  if (!streamRef.current) {
    return;
  }
  streamRef.current.getTracks().forEach((track) => track.stop());
  streamRef.current = null;
}

function mapCameraError(error: unknown): { reason: IdentityCaptureFailureReason; message: string } {
  if (!(error instanceof DOMException)) {
    return {
      reason: 'UNKNOWN',
      message: 'We could not access the camera because of an unexpected device or browser issue.',
    };
  }

  switch (error.name) {
    case 'NotAllowedError':
    case 'SecurityError':
      return {
        reason: 'PERMISSION_DENIED',
        message: 'Camera permission was denied. Please allow access and try again, or continue with the issue recorded.',
      };
    case 'NotFoundError':
    case 'DevicesNotFoundError':
      return {
        reason: 'NO_CAMERA',
        message: 'No usable camera was found on this device. You can continue and the issue will be recorded.',
      };
    case 'NotReadableError':
    case 'TrackStartError':
      return {
        reason: 'CAMERA_IN_USE',
        message: 'The camera could not be started. Another app may already be using it, or the device may be blocked.',
      };
    case 'OverconstrainedError':
    case 'ConstraintNotSatisfiedError':
      return {
        reason: 'DEVICE_ERROR',
        message: 'The camera is present but could not start with the current device settings. Please retry or continue with the issue recorded.',
      };
    default:
      return {
        reason: 'UNKNOWN',
        message: 'The camera could not be started because of a device or browser issue. You can retry or continue with the issue recorded.',
      };
  }
}
