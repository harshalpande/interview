import React from 'react';

interface VideoPanelProps {
  title: string;
  stream: MediaStream | null;
  status: string;
  emptyMessage: string;
  mirror?: boolean;
  muted?: boolean;
}

const VideoPanel: React.FC<VideoPanelProps> = ({
  title,
  stream,
  status,
  emptyMessage,
  mirror = false,
  muted = false,
}) => {
  const videoRef = React.useRef<HTMLVideoElement | null>(null);

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

  return (
    <div className="video-panel">
      <div className="video-panel-header">
        <span className="video-panel-title">{title}</span>
        <span className="video-panel-status">{status}</span>
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
