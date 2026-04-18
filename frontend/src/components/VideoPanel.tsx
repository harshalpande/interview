import React from 'react';

interface VideoPanelProps {
  title: string;
  stream: MediaStream | null;
  audioStream?: MediaStream | null;
  status: string;
  emptyMessage: string;
  mirror?: boolean;
  muted?: boolean;
  compact?: boolean;
  headerActions?: React.ReactNode;
}

const VideoPanel: React.FC<VideoPanelProps> = ({
  title,
  stream,
  audioStream = null,
  status,
  emptyMessage,
  mirror = false,
  muted = false,
  compact = false,
  headerActions,
}) => {
  const videoRef = React.useRef<HTMLVideoElement | null>(null);
  const audioRef = React.useRef<HTMLAudioElement | null>(null);

  React.useEffect(() => {
    const video = videoRef.current;
    if (!video) {
      return;
    }

    video.srcObject = stream;
    if (!stream) {
      return;
    }

    const attemptPlay = () => {
      void video.play().catch(() => {
        // best-effort only
      });
    };

    const handleLoadedMetadata = () => {
      attemptPlay();
    };

    video.addEventListener('loadedmetadata', handleLoadedMetadata);
    attemptPlay();

    return () => {
      video.removeEventListener('loadedmetadata', handleLoadedMetadata);
      if (video.srcObject === stream) {
        video.srcObject = null;
      }
    };
  }, [stream]);

  React.useEffect(() => {
    const audio = audioRef.current;
    if (!audio) {
      return;
    }

    audio.srcObject = audioStream;
    if (!audioStream) {
      return;
    }

    void audio.play().catch(() => {
      // best-effort only
    });

    return () => {
      if (audio.srcObject === audioStream) {
        audio.srcObject = null;
      }
    };
  }, [audioStream]);

  return (
    <div className={`video-panel ${compact ? 'compact' : ''}`}>
      {audioStream ? <audio ref={audioRef} autoPlay playsInline /> : null}
      <div className="video-panel-header">
        <div className="video-panel-meta">
          <span className="video-panel-title">{title}</span>
          <span className="video-panel-status">{status}</span>
        </div>
        {headerActions ? <div className="video-panel-actions">{headerActions}</div> : null}
      </div>
      <div className={`video-frame ${mirror ? 'mirror' : ''}`}>
        {stream ? (
          <video ref={videoRef} autoPlay playsInline muted={muted} />
        ) : (
          <div className="video-placeholder">{emptyMessage}</div>
        )}
      </div>
    </div>
  );
};

export default VideoPanel;
