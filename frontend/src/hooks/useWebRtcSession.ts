import React from 'react';
import type { ParticipantRole, SessionSocketMessage } from '../types/session';

interface SignalPayload {
  signalType: 'READY' | 'OFFER' | 'ANSWER' | 'ICE_CANDIDATE' | 'MEDIA_STATE';
  senderRole: ParticipantRole;
  targetRole: ParticipantRole;
  sdp?: string;
  candidate?: string;
  sdpMid?: string | null;
  sdpMLineIndex?: number | null;
  cameraEnabled?: boolean | null;
  microphoneEnabled?: boolean | null;
}

interface UseWebRtcSessionOptions {
  enabled: boolean;
  isSocketConnected: boolean;
  role: 'interviewer' | 'interviewee';
  incomingSignal: SessionSocketMessage | null;
  sendSignal: (payload: SignalPayload) => void;
}

type CameraConnectionState =
  | 'idle'
  | 'requesting'
  | 'ready'
  | 'connecting'
  | 'connected'
  | 'failed'
  | 'unavailable';

const RTC_CONFIG: RTCConfiguration = {
  iceServers: [{ urls: 'stun:stun.l.google.com:19302' }],
};
const DEBUG_WEBRTC = true;

function logInfo(...args: Parameters<typeof console.info>) {
  if (DEBUG_WEBRTC) {
    console.info(...args);
  }
}

function logWarn(...args: Parameters<typeof console.warn>) {
  if (DEBUG_WEBRTC) {
    console.warn(...args);
  }
}

function buildInitialMediaConstraints(captureAudio: boolean, captureVideo: boolean): MediaStreamConstraints {
  return {
    video: captureVideo
      ? {
          facingMode: 'user',
          width: { ideal: 640 },
          height: { ideal: 360 },
        }
      : false,
    audio: captureAudio
      ? {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        }
      : false,
  };
}

export function useWebRtcSession({
  enabled,
  isSocketConnected,
  role,
  incomingSignal,
  sendSignal,
}: UseWebRtcSessionOptions) {
  const participantRole: ParticipantRole = role === 'interviewer' ? 'INTERVIEWER' : 'INTERVIEWEE';
  const oppositeRole: ParticipantRole = role === 'interviewer' ? 'INTERVIEWEE' : 'INTERVIEWER';
  const peerConnectionRef = React.useRef<RTCPeerConnection | null>(null);
  const audioSenderRef = React.useRef<RTCRtpSender | null>(null);
  const videoSenderRef = React.useRef<RTCRtpSender | null>(null);
  const localStreamRef = React.useRef<MediaStream | null>(null);
  const remoteStreamSourceRef = React.useRef<MediaStream | null>(null);
  const pendingCandidatesRef = React.useRef<RTCIceCandidateInit[]>([]);
  const makingOfferRef = React.useRef(false);
  const hasRemoteAnswerRef = React.useRef(false);
  const [localStream, setLocalStream] = React.useState<MediaStream | null>(null);
  const [remoteStream, setRemoteStream] = React.useState<MediaStream | null>(null);
  const [hasRemoteVideo, setHasRemoteVideo] = React.useState(false);
  const [isRemoteCameraEnabled, setIsRemoteCameraEnabled] = React.useState(false);
  const [isRemoteMicrophoneEnabled, setIsRemoteMicrophoneEnabled] = React.useState(false);
  const [connectionState, setConnectionState] = React.useState<CameraConnectionState>('idle');
  const [streamError, setStreamError] = React.useState<string | null>(null);
  const [isMuted, setIsMuted] = React.useState(false);
  const [isCameraEnabled, setIsCameraEnabled] = React.useState(true);
  const isMutedRef = React.useRef(isMuted);
  const isCameraEnabledRef = React.useRef(isCameraEnabled);
  const isSocketConnectedRef = React.useRef(isSocketConnected);
  const isUpdatingCameraRef = React.useRef(false);
  const logPrefix = `[webrtc:${role}]`;

  React.useEffect(() => {
    isMutedRef.current = isMuted;
  }, [isMuted]);

  React.useEffect(() => {
    isCameraEnabledRef.current = isCameraEnabled;
  }, [isCameraEnabled]);

  React.useEffect(() => {
    isSocketConnectedRef.current = isSocketConnected;
  }, [isSocketConnected]);

  const sendMediaState = React.useCallback((cameraEnabled: boolean, microphoneEnabled: boolean) => {
    if (!isSocketConnectedRef.current) {
      return;
    }

    sendSignal({
      signalType: 'MEDIA_STATE',
      senderRole: participantRole,
      targetRole: oppositeRole,
      cameraEnabled,
      microphoneEnabled,
    });
  }, [oppositeRole, participantRole, sendSignal]);

  const closePeerConnection = React.useCallback(() => {
    if (peerConnectionRef.current) {
      if (
        peerConnectionRef.current.connectionState !== 'new' ||
        peerConnectionRef.current.iceConnectionState !== 'new'
      ) {
        logInfo(`${logPrefix} closing peer connection`, {
          connectionState: peerConnectionRef.current.connectionState,
          iceConnectionState: peerConnectionRef.current.iceConnectionState,
        });
      }
      peerConnectionRef.current.onicecandidate = null;
      peerConnectionRef.current.ontrack = null;
      peerConnectionRef.current.onconnectionstatechange = null;
      peerConnectionRef.current.oniceconnectionstatechange = null;
      peerConnectionRef.current.close();
      peerConnectionRef.current = null;
    }
    pendingCandidatesRef.current = [];
    makingOfferRef.current = false;
    hasRemoteAnswerRef.current = false;
    audioSenderRef.current = null;
    videoSenderRef.current = null;
  }, [logPrefix]);

  const stopLocalStream = React.useCallback(() => {
    if (localStreamRef.current) {
      if (localStreamRef.current.getTracks().length > 0) {
        logInfo(`${logPrefix} stopping local stream`, {
          tracks: localStreamRef.current.getTracks().map((track) => ({ kind: track.kind, enabled: track.enabled, readyState: track.readyState })),
        });
      }
      localStreamRef.current.getTracks().forEach((track) => track.stop());
      localStreamRef.current = null;
    }
    setLocalStream(null);
  }, [logPrefix]);

  const syncLocalStreamState = React.useCallback(() => {
    if (!localStreamRef.current) {
      localStreamRef.current = new MediaStream();
    }
    setLocalStream(new MediaStream(localStreamRef.current.getTracks()));
  }, []);

  const ensureLocalStreamRef = React.useCallback(() => {
    if (!localStreamRef.current) {
      localStreamRef.current = new MediaStream();
    }
    return localStreamRef.current;
  }, []);

  const refreshRemoteVideoState = React.useCallback((stream: MediaStream | null) => {
    if (!stream) {
      setHasRemoteVideo(false);
      return;
    }

    const hasActiveVideo = stream.getVideoTracks().some((track) => track.readyState === 'live' && !track.muted);
    setHasRemoteVideo(hasActiveVideo);
  }, []);

  const syncRemoteStreamState = React.useCallback((stream: MediaStream | null) => {
    if (!stream) {
      setRemoteStream(null);
      setHasRemoteVideo(false);
      return;
    }

    refreshRemoteVideoState(stream);
    setRemoteStream(new MediaStream(stream.getTracks()));
  }, [refreshRemoteVideoState]);

  const attachRemoteTrackLifecycle = React.useCallback((track: MediaStreamTrack, stream: MediaStream) => {
    const sync = () => {
      if (track.readyState === 'ended' && stream.getTracks().some((existingTrack) => existingTrack.id === track.id)) {
        stream.removeTrack(track);
      }
      syncRemoteStreamState(stream);
    };

    track.onmute = sync;
    track.onunmute = sync;
    track.onended = sync;
  }, [syncRemoteStreamState]);

  const bindRemoteStream = React.useCallback((stream: MediaStream) => {
    const sync = () => {
      remoteStreamSourceRef.current = stream;
      syncRemoteStreamState(stream);
    };

    const attachTrack = (track: MediaStreamTrack) => {
      attachRemoteTrackLifecycle(track, stream);
    };

    stream.getTracks().forEach(attachTrack);
    stream.onaddtrack = (event) => {
      attachTrack(event.track);
      sync();
    };
    stream.onremovetrack = sync;
    sync();
  }, [attachRemoteTrackLifecycle, syncRemoteStreamState]);

  const bindFallbackRemoteTrack = React.useCallback((track: MediaStreamTrack) => {
    let fallbackStream = remoteStreamSourceRef.current;
    if (!fallbackStream) {
      fallbackStream = new MediaStream();
      remoteStreamSourceRef.current = fallbackStream;
    }

    fallbackStream
      .getTracks()
      .filter((existingTrack) => existingTrack.kind === track.kind && existingTrack.id !== track.id)
      .forEach((existingTrack) => {
        fallbackStream?.removeTrack(existingTrack);
      });

    if (!fallbackStream.getTracks().some((existingTrack) => existingTrack.id === track.id)) {
      fallbackStream.addTrack(track);
    }

    attachRemoteTrackLifecycle(track, fallbackStream);
    fallbackStream.onaddtrack = () => syncRemoteStreamState(fallbackStream);
    fallbackStream.onremovetrack = () => syncRemoteStreamState(fallbackStream);
    syncRemoteStreamState(fallbackStream);
  }, [attachRemoteTrackLifecycle, syncRemoteStreamState]);

  const syncSenderTracks = React.useCallback(async () => {
    const connection = peerConnectionRef.current;
    if (!connection) {
      return;
    }

    const stream = ensureLocalStreamRef();
    const audioTrack = stream.getAudioTracks()[0] ?? null;
    const videoTrack = stream.getVideoTracks()[0] ?? null;

    if (audioSenderRef.current) {
      await audioSenderRef.current.replaceTrack(audioTrack);
    }
    if (videoSenderRef.current) {
      await videoSenderRef.current.replaceTrack(videoTrack);
    }
  }, [ensureLocalStreamRef]);

  const applyTrackStates = React.useCallback(() => {
    if (!localStreamRef.current) {
      return;
    }

    localStreamRef.current.getAudioTracks().forEach((track) => {
      track.enabled = !isMuted;
    });
    localStreamRef.current.getVideoTracks().forEach((track) => {
      track.enabled = isCameraEnabled;
    });
    logInfo(`${logPrefix} applied local track states`, {
      audio: localStreamRef.current.getAudioTracks().map((track) => ({ enabled: track.enabled, readyState: track.readyState })),
      video: localStreamRef.current.getVideoTracks().map((track) => ({ enabled: track.enabled, readyState: track.readyState })),
    });
  }, [isCameraEnabled, isMuted, logPrefix]);

  const ensurePeerConnection = React.useCallback(() => {
    if (peerConnectionRef.current) {
      return peerConnectionRef.current;
    }

    const connection = new RTCPeerConnection(RTC_CONFIG);
    logInfo(`${logPrefix} created peer connection`);
    const audioTransceiver = connection.addTransceiver('audio', { direction: 'sendrecv' });
    const videoTransceiver = connection.addTransceiver('video', { direction: 'sendrecv' });
    audioSenderRef.current = audioTransceiver.sender;
    videoSenderRef.current = videoTransceiver.sender;

    connection.onicecandidate = (event) => {
      if (!event.candidate) {
        return;
      }

      sendSignal({
        signalType: 'ICE_CANDIDATE',
        senderRole: participantRole,
        targetRole: oppositeRole,
        candidate: event.candidate.candidate,
        sdpMid: event.candidate.sdpMid,
        sdpMLineIndex: event.candidate.sdpMLineIndex,
      });
    };

    connection.ontrack = (event) => {
      const firstStream = event.streams[0];
      logInfo(`${logPrefix} received remote track`, {
        kind: event.track.kind,
        streamCount: event.streams.length,
      });
      if (firstStream) {
        bindRemoteStream(firstStream);
        return;
      }

      bindFallbackRemoteTrack(event.track);
    };

    connection.onconnectionstatechange = () => {
      const state = connection.connectionState;
      logInfo(`${logPrefix} peer connection state changed`, { state });
      if (state === 'connected') {
        setConnectionState('connected');
        return;
      }
      if (state === 'connecting') {
        setConnectionState('connecting');
        return;
      }
      if (state === 'failed' || state === 'closed') {
        setConnectionState('failed');
      }
    };

    connection.oniceconnectionstatechange = () => {
      logInfo(`${logPrefix} ICE connection state changed`, {
        state: connection.iceConnectionState,
      });
      if (connection.iceConnectionState === 'disconnected') {
        setConnectionState('connecting');
      }
      if (connection.iceConnectionState === 'failed') {
        setConnectionState('failed');
      }
    };

    peerConnectionRef.current = connection;
    return connection;
  }, [bindFallbackRemoteTrack, bindRemoteStream, logPrefix, oppositeRole, participantRole, sendSignal]);

  const flushPendingCandidates = React.useCallback(async () => {
    if (!peerConnectionRef.current?.remoteDescription) {
      return;
    }

    while (pendingCandidatesRef.current.length > 0) {
      const candidate = pendingCandidatesRef.current.shift();
      if (!candidate) {
        continue;
      }

      try {
        await peerConnectionRef.current.addIceCandidate(candidate);
      } catch {
        // best-effort only
      }
    }
  }, []);

  const createAndSendOffer = React.useCallback(async (reason: string) => {
    if (!isSocketConnectedRef.current || !localStreamRef.current) {
      logInfo(`${logPrefix} skipping offer creation`, { reason, isSocketConnected, hasLocalStream: Boolean(localStreamRef.current) });
      return;
    }

    const connection = ensurePeerConnection();
    if (makingOfferRef.current || connection.signalingState !== 'stable') {
      logInfo(`${logPrefix} skipping offer creation because signaling is busy`, {
        reason,
        signalingState: connection.signalingState,
        makingOffer: makingOfferRef.current,
      });
      return;
    }

    makingOfferRef.current = true;
    try {
      const offer = await connection.createOffer();
      await connection.setLocalDescription(offer);
      setConnectionState('connecting');
      logInfo(`${logPrefix} created offer`, { reason });
      sendSignal({
        signalType: 'OFFER',
        senderRole: participantRole,
        targetRole: oppositeRole,
        sdp: offer.sdp || '',
      });
    } finally {
      makingOfferRef.current = false;
    }
  }, [ensurePeerConnection, isSocketConnected, logPrefix, oppositeRole, participantRole, sendSignal]);

  React.useEffect(() => {
    if (!enabled) {
      closePeerConnection();
      stopLocalStream();
      remoteStreamSourceRef.current = null;
      setRemoteStream(null);
      setHasRemoteVideo(false);
      setIsRemoteCameraEnabled(false);
      setIsRemoteMicrophoneEnabled(false);
      setConnectionState('idle');
      setStreamError(null);
      return;
    }

    let cancelled = false;
    closePeerConnection();
    stopLocalStream();
    remoteStreamSourceRef.current = null;
    setRemoteStream(null);
    setHasRemoteVideo(false);
    setIsRemoteCameraEnabled(false);
    setIsRemoteMicrophoneEnabled(false);
    setConnectionState('requesting');
    setStreamError(null);

    const initialAudioEnabled = !isMutedRef.current;
    const initialVideoEnabled = isCameraEnabledRef.current;
    if (!initialAudioEnabled && !initialVideoEnabled) {
      localStreamRef.current = new MediaStream();
      syncLocalStreamState();
      ensurePeerConnection();
      setConnectionState('ready');
      sendMediaState(false, false);
      return () => {
        cancelled = true;
      };
    }

    navigator.mediaDevices
      .getUserMedia(buildInitialMediaConstraints(initialAudioEnabled, initialVideoEnabled))
      .then((stream) => {
        if (cancelled) {
          stream.getTracks().forEach((track) => track.stop());
          return;
        }

        localStreamRef.current = stream;
        logInfo(`${logPrefix} acquired local media`, {
          audioTracks: stream.getAudioTracks().length,
          videoTracks: stream.getVideoTracks().length,
          isMuted: isMutedRef.current,
          isCameraEnabled: isCameraEnabledRef.current,
        });
        stream.getAudioTracks().forEach((track) => {
          track.enabled = !isMutedRef.current;
        });
        stream.getVideoTracks().forEach((track) => {
          track.enabled = isCameraEnabledRef.current;
        });
        setLocalStream(new MediaStream(stream.getTracks()));
        ensurePeerConnection();
        void syncSenderTracks();
        setConnectionState('ready');
        sendMediaState(initialVideoEnabled, initialAudioEnabled);
      })
      .catch((error: unknown) => {
        if (cancelled) {
          return;
        }

        const message =
          error instanceof Error && error.message
            ? error.message
            : 'Unable to access the camera and microphone.';
        logWarn(`${logPrefix} failed to acquire local media`, {
          error,
          message,
          isCameraEnabled: isCameraEnabledRef.current,
        });
        setStreamError(message);
        setConnectionState('unavailable');
      });

    return () => {
      cancelled = true;
    };
  }, [closePeerConnection, enabled, ensurePeerConnection, logPrefix, sendMediaState, stopLocalStream, syncLocalStreamState, syncSenderTracks]);

  React.useEffect(() => {
    applyTrackStates();
  }, [applyTrackStates]);

  React.useEffect(() => {
    if (!enabled || !isSocketConnected) {
      return;
    }

    sendMediaState(isCameraEnabledRef.current, !isMutedRef.current);

    const interval = window.setInterval(() => {
      const connection = peerConnectionRef.current;
      if (
        connection?.connectionState === 'connected' ||
        Boolean(connection?.remoteDescription) ||
        hasRemoteAnswerRef.current
      ) {
        return;
      }

      sendSignal({
        signalType: 'READY',
        senderRole: participantRole,
        targetRole: oppositeRole,
      });
    }, 4000);

    sendSignal({
      signalType: 'READY',
      senderRole: participantRole,
      targetRole: oppositeRole,
    });

    return () => window.clearInterval(interval);
  }, [enabled, isSocketConnected, logPrefix, oppositeRole, participantRole, sendMediaState, sendSignal]);

  React.useEffect(() => {
    if (!enabled || !incomingSignal || incomingSignal.type !== 'WEBRTC_SIGNAL') {
      return;
    }

    if (incomingSignal.targetRole !== participantRole || incomingSignal.senderRole === participantRole) {
      return;
    }

    const processSignal = async () => {
      const connection = ensurePeerConnection();
      if (incomingSignal.signalType === 'OFFER' || incomingSignal.signalType === 'ANSWER') {
        logInfo(`${logPrefix} received signal`, {
          signalType: incomingSignal.signalType,
          senderRole: incomingSignal.senderRole,
          targetRole: incomingSignal.targetRole,
        });
      }

      switch (incomingSignal.signalType) {
        case 'READY': {
          if (role !== 'interviewee' || !localStreamRef.current) {
            return;
          }
          if (hasRemoteAnswerRef.current || connection.connectionState === 'connected' || connection.connectionState === 'connecting') {
            return;
          }
          await createAndSendOffer('initial-ready');
          return;
        }
        case 'OFFER': {
          if (!incomingSignal.sdp) {
            return;
          }

          await connection.setRemoteDescription({
            type: 'offer',
            sdp: incomingSignal.sdp,
          });
          logInfo(`${logPrefix} applied remote offer`);
          await flushPendingCandidates();
          const answer = await connection.createAnswer();
          await connection.setLocalDescription(answer);
          setConnectionState('connecting');
          logInfo(`${logPrefix} created answer`);
          sendSignal({
            signalType: 'ANSWER',
            senderRole: participantRole,
            targetRole: oppositeRole,
            sdp: answer.sdp || '',
          });
          return;
        }
        case 'ANSWER': {
          if (!incomingSignal.sdp) {
            return;
          }

          await connection.setRemoteDescription({
            type: 'answer',
            sdp: incomingSignal.sdp,
          });
          logInfo(`${logPrefix} applied remote answer`);
          hasRemoteAnswerRef.current = true;
          await flushPendingCandidates();
          return;
        }
        case 'ICE_CANDIDATE': {
          if (!incomingSignal.candidate) {
            return;
          }

          const candidate: RTCIceCandidateInit = {
            candidate: incomingSignal.candidate,
            sdpMid: incomingSignal.sdpMid ?? undefined,
            sdpMLineIndex: incomingSignal.sdpMLineIndex ?? undefined,
          };

          if (!connection.remoteDescription) {
            logInfo(`${logPrefix} queueing ICE candidate until remote description is ready`);
            pendingCandidatesRef.current.push(candidate);
            return;
          }

          try {
            await connection.addIceCandidate(candidate);
          } catch {
            // best-effort only
          }
          return;
        }
        case 'MEDIA_STATE': {
          setIsRemoteCameraEnabled(Boolean(incomingSignal.cameraEnabled));
          setIsRemoteMicrophoneEnabled(Boolean(incomingSignal.microphoneEnabled));
          if (!incomingSignal.cameraEnabled) {
            setHasRemoteVideo(false);
          } else {
            refreshRemoteVideoState(remoteStreamSourceRef.current);
          }
          return;
        }
      }
    };

    void processSignal();
  }, [
    enabled,
    ensurePeerConnection,
    flushPendingCandidates,
    incomingSignal,
    logPrefix,
    oppositeRole,
    participantRole,
    role,
    refreshRemoteVideoState,
    sendSignal,
    createAndSendOffer,
  ]);

  React.useEffect(() => {
    return () => {
      closePeerConnection();
      stopLocalStream();
    };
  }, [closePeerConnection, logPrefix, stopLocalStream]);

  const toggleMute = React.useCallback(() => {
    if (!enabled || isUpdatingCameraRef.current) {
      return;
    }

    const next = !isMutedRef.current;
    isUpdatingCameraRef.current = true;
    setStreamError(null);

    const applyMuteToggle = async () => {
      try {
        const stream = ensureLocalStreamRef();

        if (!next) {
          const audioStream = await navigator.mediaDevices.getUserMedia({
            audio: {
              echoCancellation: true,
              noiseSuppression: true,
              autoGainControl: true,
            },
            video: false,
          });
          const [audioTrack] = audioStream.getAudioTracks();
          if (!audioTrack) {
            throw new Error('No microphone track was returned.');
          }

          audioTrack.enabled = true;
          stream.addTrack(audioTrack);
          ensurePeerConnection();
          await syncSenderTracks();
          syncLocalStreamState();
          setIsMuted(false);
          isMutedRef.current = false;
          logInfo(`${logPrefix} toggled microphone`, { muted: false });
          sendMediaState(isCameraEnabledRef.current, true);
          await createAndSendOffer('microphone-enabled');
          return;
        }

        stream.getAudioTracks().forEach((track) => {
          stream.removeTrack(track);
          track.stop();
        });

        await syncSenderTracks();
        syncLocalStreamState();
        setIsMuted(true);
        isMutedRef.current = true;
        logInfo(`${logPrefix} toggled microphone`, { muted: true });
        sendMediaState(isCameraEnabledRef.current, false);
        await createAndSendOffer('microphone-disabled');
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Could not update the microphone state.';
        logWarn(`${logPrefix} failed to toggle microphone`, { error, message, muted: next });
        setStreamError(message);
      } finally {
        isUpdatingCameraRef.current = false;
      }
    };

    void applyMuteToggle();
  }, [createAndSendOffer, enabled, ensureLocalStreamRef, ensurePeerConnection, logPrefix, sendMediaState, syncLocalStreamState, syncSenderTracks]);

  const toggleCamera = React.useCallback(() => {
    if (!enabled || isUpdatingCameraRef.current) {
      return;
    }

    const next = !isCameraEnabledRef.current;
    isUpdatingCameraRef.current = true;
    setStreamError(null);

    const applyCameraToggle = async () => {
      try {
        if (!localStreamRef.current) {
          return;
        }

        if (next) {
          const videoStream = await navigator.mediaDevices.getUserMedia({
            video: {
              facingMode: 'user',
              width: { ideal: 640 },
              height: { ideal: 360 },
            },
          });
          const [videoTrack] = videoStream.getVideoTracks();
          if (!videoTrack) {
            throw new Error('No camera track was returned.');
          }

          videoTrack.enabled = true;
          const stream = ensureLocalStreamRef();
          stream.addTrack(videoTrack);
          ensurePeerConnection();
          await syncSenderTracks();
          syncLocalStreamState();
          setIsCameraEnabled(true);
          isCameraEnabledRef.current = true;
          logInfo(`${logPrefix} toggled camera`, { enabled: true });
          sendMediaState(true, !isMutedRef.current);
          await createAndSendOffer('camera-enabled');
          return;
        }

        const stream = ensureLocalStreamRef();
        stream.getVideoTracks().forEach((track) => {
          stream.removeTrack(track);
          track.stop();
        });

        await syncSenderTracks();
        syncLocalStreamState();
        setIsCameraEnabled(false);
        isCameraEnabledRef.current = false;
        logInfo(`${logPrefix} toggled camera`, { enabled: false });
        sendMediaState(false, !isMutedRef.current);
        await createAndSendOffer('camera-disabled');
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Could not update the camera state.';
        logWarn(`${logPrefix} failed to toggle camera`, { error, message, enabled: next });
        setStreamError(message);
      } finally {
        isUpdatingCameraRef.current = false;
      }
    };

    void applyCameraToggle();
  }, [createAndSendOffer, enabled, ensureLocalStreamRef, ensurePeerConnection, logPrefix, sendMediaState, syncLocalStreamState, syncSenderTracks]);

  return {
    connectionState,
    localStream,
    remoteStream,
    hasRemoteVideo,
    isRemoteCameraEnabled,
    isRemoteMicrophoneEnabled,
    streamError,
    isMuted,
    isCameraEnabled,
    canToggleCamera: true,
    toggleMute,
    toggleCamera,
  };
}
