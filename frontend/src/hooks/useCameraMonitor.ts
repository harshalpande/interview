import React from 'react';

interface UseCameraMonitorOptions {
  enabled: boolean;
  stream: MediaStream | null;
  onStreamLost: () => void;
  onNoFaceDetected: () => void;
  onMultipleFacesDetected: () => void;
}

type CameraMonitorStatus =
  | 'idle'
  | 'active'
  | 'stream-lost'
  | 'no-face'
  | 'multiple-faces'
  | 'face-checks-unavailable';

type BrowserFaceDetector = {
  detect: (input: CanvasImageSource) => Promise<Array<unknown>>;
};

declare global {
  interface Window {
    FaceDetector?: new (options?: { fastMode?: boolean; maxDetectedFaces?: number }) => BrowserFaceDetector;
  }
}

export function useCameraMonitor({
  enabled,
  stream,
  onStreamLost,
  onNoFaceDetected,
  onMultipleFacesDetected,
}: UseCameraMonitorOptions) {
  const [status, setStatus] = React.useState<CameraMonitorStatus>('idle');
  const [faceDetectionSupported, setFaceDetectionSupported] = React.useState(true);
  const lastNoFaceEventAtRef = React.useRef(0);
  const lastMultipleFacesEventAtRef = React.useRef(0);
  const lastStreamLostEventAtRef = React.useRef(0);

  React.useEffect(() => {
    if (!enabled || !stream) {
      setStatus('idle');
      return;
    }

    const track = stream.getVideoTracks()[0];
    if (!track) {
      setStatus('stream-lost');
      return;
    }

    const emitStreamLost = () => {
      const now = Date.now();
      if (now - lastStreamLostEventAtRef.current < 15000) {
        return;
      }
      lastStreamLostEventAtRef.current = now;
      setStatus('stream-lost');
      onStreamLost();
    };

    const onTrackEnded = () => emitStreamLost();
    track.addEventListener('ended', onTrackEnded);

    const interval = window.setInterval(() => {
      if (!stream.active || track.readyState === 'ended' || track.muted) {
        emitStreamLost();
      }
    }, 1500);

    setStatus('active');

    return () => {
      track.removeEventListener('ended', onTrackEnded);
      window.clearInterval(interval);
    };
  }, [enabled, onStreamLost, stream]);

  React.useEffect(() => {
    if (!enabled || !stream) {
      setFaceDetectionSupported(true);
      return;
    }

    if (typeof window.FaceDetector !== 'function') {
      setFaceDetectionSupported(false);
      setStatus((previous) => (previous === 'stream-lost' ? previous : 'face-checks-unavailable'));
      return;
    }

    setFaceDetectionSupported(true);

    const video = document.createElement('video');
    video.autoplay = true;
    video.muted = true;
    video.playsInline = true;
    video.srcObject = stream;

    const detector = new window.FaceDetector({ fastMode: true, maxDetectedFaces: 3 });
    let cancelled = false;
    let noFaceDurationMs = 0;
    let multiFaceStreak = 0;

    const tick = async () => {
      if (cancelled) {
        return;
      }

      if (video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA || video.videoWidth === 0 || video.videoHeight === 0) {
        return;
      }

      try {
        const faces = await detector.detect(video);
        if (cancelled) {
          return;
        }

        if (faces.length === 0) {
          noFaceDurationMs += 1000;
          multiFaceStreak = 0;
          setStatus('no-face');
          if (noFaceDurationMs >= 5000 && Date.now() - lastNoFaceEventAtRef.current >= 30000) {
            lastNoFaceEventAtRef.current = Date.now();
            onNoFaceDetected();
          }
          return;
        }

        noFaceDurationMs = 0;

        if (faces.length > 1) {
          multiFaceStreak += 1;
          setStatus('multiple-faces');
          if (multiFaceStreak >= 3 && Date.now() - lastMultipleFacesEventAtRef.current >= 30000) {
            lastMultipleFacesEventAtRef.current = Date.now();
            onMultipleFacesDetected();
          }
          return;
        }

        multiFaceStreak = 0;
        setStatus('active');
      } catch {
        if (!cancelled) {
          setFaceDetectionSupported(false);
          setStatus((previous) => (previous === 'stream-lost' ? previous : 'face-checks-unavailable'));
        }
      }
    };

    void video.play().catch(() => {
      // Some browsers delay playback until metadata is ready.
    });

    const readyListener = () => {
      void video.play().catch(() => {
        // best-effort only
      });
    };

    video.addEventListener('loadedmetadata', readyListener);
    const interval = window.setInterval(() => {
      void tick();
    }, 1000);

    return () => {
      cancelled = true;
      video.pause();
      video.srcObject = null;
      video.removeEventListener('loadedmetadata', readyListener);
      window.clearInterval(interval);
    };
  }, [enabled, onMultipleFacesDetected, onNoFaceDetected, stream]);

  return {
    faceDetectionSupported,
    status,
  };
}
