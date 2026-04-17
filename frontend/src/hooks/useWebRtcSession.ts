import React from 'react';
import type { ParticipantRole, SessionSocketMessage } from '../types/session';

interface SignalPayload {
  signalType: 'READY' | 'OFFER' | 'ANSWER' | 'ICE_CANDIDATE';
  senderRole: ParticipantRole;
  targetRole: ParticipantRole;
  sdp?: string;
  candidate?: string;
  sdpMid?: string | null;
  sdpMLineIndex?: number | null;
}

interface UseWebRtcSessionOptions {
  enabled: boolean;
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

export function useWebRtcSession({
  enabled,
  role,
  incomingSignal,
  sendSignal,
}: UseWebRtcSessionOptions) {
  const participantRole: ParticipantRole = role === 'interviewer' ? 'INTERVIEWER' : 'INTERVIEWEE';
  const oppositeRole: ParticipantRole = role === 'interviewer' ? 'INTERVIEWEE' : 'INTERVIEWER';
  const peerConnectionRef = React.useRef<RTCPeerConnection | null>(null);
  const localStreamRef = React.useRef<MediaStream | null>(null);
  const pendingCandidatesRef = React.useRef<RTCIceCandidateInit[]>([]);
  const makingOfferRef = React.useRef(false);
  const hasRemoteAnswerRef = React.useRef(false);
  const [localStream, setLocalStream] = React.useState<MediaStream | null>(null);
  const [remoteStream, setRemoteStream] = React.useState<MediaStream | null>(null);
  const [connectionState, setConnectionState] = React.useState<CameraConnectionState>('idle');
  const [streamError, setStreamError] = React.useState<string | null>(null);

  const closePeerConnection = React.useCallback(() => {
    if (peerConnectionRef.current) {
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
  }, []);

  const stopLocalStream = React.useCallback(() => {
    if (localStreamRef.current) {
      localStreamRef.current.getTracks().forEach((track) => track.stop());
      localStreamRef.current = null;
    }
    setLocalStream(null);
  }, []);

  const attachLocalTracks = React.useCallback((connection: RTCPeerConnection) => {
    if (!localStreamRef.current || role !== 'interviewee') {
      return;
    }

    const existingTrackIds = new Set(
      connection.getSenders()
        .map((sender) => sender.track?.id)
        .filter((trackId): trackId is string => Boolean(trackId))
    );

    const newTracks = localStreamRef.current.getTracks().filter((track) => !existingTrackIds.has(track.id));
    if (newTracks.length === 0) {
      return;
    }

    newTracks.forEach((track) => {
      connection.addTrack(track, localStreamRef.current!);
    });
  }, [role]);

  const ensurePeerConnection = React.useCallback(() => {
    if (peerConnectionRef.current) {
      attachLocalTracks(peerConnectionRef.current);
      return peerConnectionRef.current;
    }

    const connection = new RTCPeerConnection(RTC_CONFIG);

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
      if (firstStream) {
        setRemoteStream(firstStream);
        return;
      }

      setRemoteStream((previous) => {
        const fallback = previous ?? new MediaStream();
        fallback.addTrack(event.track);
        return fallback;
      });
    };

    connection.onconnectionstatechange = () => {
      const state = connection.connectionState;
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
      if (connection.iceConnectionState === 'disconnected') {
        setConnectionState('connecting');
      }
      if (connection.iceConnectionState === 'failed') {
        setConnectionState('failed');
      }
    };

    if (role === 'interviewer') {
      connection.addTransceiver('video', { direction: 'recvonly' });
    }

    attachLocalTracks(connection);

    peerConnectionRef.current = connection;
    return connection;
  }, [attachLocalTracks, oppositeRole, participantRole, role, sendSignal]);

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

  React.useEffect(() => {
    if (!enabled) {
      closePeerConnection();
      stopLocalStream();
      setRemoteStream(null);
      setConnectionState('idle');
      setStreamError(null);
      return;
    }

    if (role !== 'interviewee') {
      ensurePeerConnection();
      setConnectionState((previous) => (previous === 'idle' ? 'ready' : previous));
      return;
    }

    let cancelled = false;
    setConnectionState('requesting');
    setStreamError(null);

    navigator.mediaDevices
      .getUserMedia({
        video: {
          facingMode: 'user',
          width: { ideal: 640 },
          height: { ideal: 360 },
        },
        audio: false,
      })
      .then((stream) => {
        if (cancelled) {
          stream.getTracks().forEach((track) => track.stop());
          return;
        }

        localStreamRef.current = stream;
        setLocalStream(stream);
        const connection = ensurePeerConnection();
        attachLocalTracks(connection);
        setConnectionState('ready');
      })
      .catch((error: unknown) => {
        if (cancelled) {
          return;
        }

        const message =
          error instanceof Error && error.message
            ? error.message
            : 'Unable to access the interviewee camera.';
        setStreamError(message);
        setConnectionState('unavailable');
      });

    return () => {
      cancelled = true;
    };
  }, [attachLocalTracks, closePeerConnection, enabled, ensurePeerConnection, role, stopLocalStream]);

  React.useEffect(() => {
    if (!enabled) {
      return;
    }

    const interval = window.setInterval(() => {
      if (peerConnectionRef.current?.connectionState === 'connected') {
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
  }, [enabled, oppositeRole, participantRole, sendSignal]);

  React.useEffect(() => {
    if (!enabled || !incomingSignal || incomingSignal.type !== 'WEBRTC_SIGNAL') {
      return;
    }

    if (incomingSignal.targetRole !== participantRole || incomingSignal.senderRole === participantRole) {
      return;
    }

    const processSignal = async () => {
      const connection = ensurePeerConnection();

      switch (incomingSignal.signalType) {
        case 'READY': {
          if (role !== 'interviewee' || !localStreamRef.current) {
            return;
          }
          if (makingOfferRef.current || connection.signalingState !== 'stable') {
            return;
          }
          if (hasRemoteAnswerRef.current || connection.connectionState === 'connected' || connection.connectionState === 'connecting') {
            return;
          }

          makingOfferRef.current = true;
          try {
            const offer = await connection.createOffer();
            await connection.setLocalDescription(offer);
            setConnectionState('connecting');
            sendSignal({
              signalType: 'OFFER',
              senderRole: participantRole,
              targetRole: oppositeRole,
              sdp: offer.sdp || '',
            });
          } finally {
            makingOfferRef.current = false;
          }
          return;
        }
        case 'OFFER': {
          if (role !== 'interviewer' || !incomingSignal.sdp) {
            return;
          }

          await connection.setRemoteDescription({
            type: 'offer',
            sdp: incomingSignal.sdp,
          });
          await flushPendingCandidates();
          const answer = await connection.createAnswer();
          await connection.setLocalDescription(answer);
          setConnectionState('connecting');
          sendSignal({
            signalType: 'ANSWER',
            senderRole: participantRole,
            targetRole: oppositeRole,
            sdp: answer.sdp || '',
          });
          return;
        }
        case 'ANSWER': {
          if (role !== 'interviewee' || !incomingSignal.sdp) {
            return;
          }

          await connection.setRemoteDescription({
            type: 'answer',
            sdp: incomingSignal.sdp,
          });
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
            pendingCandidatesRef.current.push(candidate);
            return;
          }

          try {
            await connection.addIceCandidate(candidate);
          } catch {
            // best-effort only
          }
        }
      }
    };

    void processSignal();
  }, [
    enabled,
    ensurePeerConnection,
    flushPendingCandidates,
    incomingSignal,
    oppositeRole,
    participantRole,
    role,
    sendSignal,
  ]);

  React.useEffect(() => {
    return () => {
      closePeerConnection();
      stopLocalStream();
    };
  }, [closePeerConnection, stopLocalStream]);

  return {
    connectionState,
    localStream,
    remoteStream,
    streamError,
  };
}
