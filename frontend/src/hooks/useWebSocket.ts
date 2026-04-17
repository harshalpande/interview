import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { debounce } from 'lodash';
import type { ParticipantRole, SessionSocketMessage, WebRtcSignalType } from '../types/session';
import { buildSocketUrlFromApiBase, resolveApiBaseUrl } from '../utils/apiUrls';

interface CodePayload {
  code: string;
  version: number;
  updatedByRole: ParticipantRole;
}

interface SignalPayload {
  signalType: WebRtcSignalType;
  senderRole: ParticipantRole;
  targetRole: ParticipantRole;
  sdp?: string;
  candidate?: string;
  sdpMid?: string | null;
  sdpMLineIndex?: number | null;
}

export const useWebSocket = (
  sessionId: string,
  onMessage: (msg: SessionSocketMessage) => void
) => {
  const clientRef = useRef<Client | null>(null);
  const onMessageRef = useRef(onMessage);
  const [isReconnecting, setIsReconnecting] = useState(false);

  useEffect(() => {
    onMessageRef.current = onMessage;
  }, [onMessage]);

  const debouncedSendCode = useMemo(
    () =>
      debounce((payload: CodePayload) => {
        if (clientRef.current?.active) {
          clientRef.current.publish({
            destination: `/app/session/${sessionId}/code`,
            body: JSON.stringify(payload),
          });
        }
      }, 300),
    [sessionId]
  );

  useEffect(() => {
    if (!sessionId) {
      return undefined;
    }

    const socketUrl = buildSocketUrl();

    const client = new Client({
      webSocketFactory: () => new SockJS(socketUrl),
      reconnectDelay: 1000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        setIsReconnecting(false);
        client.subscribe(`/topic/session/${sessionId}`, (message: IMessage) => {
          const data = JSON.parse(message.body) as SessionSocketMessage;
          onMessageRef.current(data);
        });
      },
      onStompError: () => {
        setIsReconnecting(true);
      },
      onWebSocketClose: () => {
        setIsReconnecting(true);
      },
      onWebSocketError: () => {
        setIsReconnecting(true);
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      debouncedSendCode.cancel();
      client.deactivate();
      clientRef.current = null;
    };
  }, [debouncedSendCode, sessionId]);

  const sendCode = useCallback((code: string, version: number, updatedByRole: ParticipantRole) => {
    debouncedSendCode({ code, version, updatedByRole });
  }, [debouncedSendCode]);

  const sendSignal = useCallback((payload: SignalPayload) => {
    if (!clientRef.current?.active) {
      return;
    }

    clientRef.current.publish({
      destination: `/app/session/${sessionId}/signal`,
      body: JSON.stringify(payload),
    });
  }, [sessionId]);

  return {
    sendCode,
    sendSignal,
    isReconnecting,
  };
};

function buildSocketUrl() {
  const apiBaseUrl = resolveApiBaseUrl();
  return buildSocketUrlFromApiBase(apiBaseUrl);
}
