import React from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Button } from './Button';
import { sessionApi } from '../services/sessionApi';
import type { SessionResponse } from '../types/session';
import { formatDateTimeSplit } from '../utils/dateTime';

const STATUS_LABELS: Record<string, string> = {
  REGISTERED: 'Registered',
  AUTH_IN_PROGRESS: 'Auth In Progress',
  READY_TO_START: 'Ready to Start',
  ACTIVE: 'Active',
  ENDED: 'Ended',
  AUTH_FAILED: 'Authentication Failed',
  EXPIRED: 'Expired',
};

interface InterviewRowProps {
  session: SessionResponse;
  searchTerm?: string;
}

const InterviewRow: React.FC<InterviewRowProps> = ({ session, searchTerm = '' }) => {
  const queryClient = useQueryClient();
  const [actionMessage, setActionMessage] = React.useState<{ tone: 'success' | 'error'; text: string } | null>(null);
  const interviewer = session.participants?.find((p) => p.role === 'INTERVIEWER');
  const interviewee = session.participants?.find((p) => p.role === 'INTERVIEWEE');
  const startDate = session.startedAt ? formatDateTimeSplit(session.startedAt) : null;
  const resumeSessionMutation = useMutation({
    mutationFn: () => sessionApi.startSecureSession(session.id),
    onMutate: () => {
      setActionMessage(null);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['sessions'] });
      await queryClient.invalidateQueries({ queryKey: ['session', session.id] });
      setActionMessage({
        tone: 'success',
        text: 'Secure access sent to both participants.',
      });
    },
    onError: async (error) => {
      await queryClient.invalidateQueries({ queryKey: ['sessions'] });
      await queryClient.invalidateQueries({ queryKey: ['session', session.id] });
      setActionMessage({
        tone: 'error',
        text: error instanceof Error ? error.message : 'Unable to start secure session access.',
      });
    },
  });

  return (
    <tr>
      <td className="date-cell">
        {startDate ? (
          <div className="date-stack">
            <div className="date-primary">{startDate.dateLabel}</div>
            <div className="date-secondary">{startDate.timeLabel}</div>
          </div>
        ) : (
          'Not started'
        )}
      </td>
      <td>
        {session.technology ? (
          <span className={`technology-pill technology-pill-${session.technology.toLowerCase()}`}>
            {formatTechnology(session.technology)}
          </span>
        ) : (
          'N/A'
        )}
      </td>
      <td>{renderParticipant(interviewer?.name, interviewer?.email, searchTerm)}</td>
      <td>{renderParticipant(interviewee?.name, interviewee?.email, searchTerm)}</td>
      <td>
        <span className={`status status-${session.status.toLowerCase()}`}>
          {STATUS_LABELS[session.status] || session.status}
        </span>
      </td>
      <td className="summary-cell">
        <div>{session.summary || 'No summary available yet'}</div>
      </td>
      <td className="action-cell">
        {session.status === 'REGISTERED' || session.status === 'AUTH_IN_PROGRESS' || session.status === 'READY_TO_START' ? (
          <Button
            variant="secondary"
            className="btn-small dashboard-action-btn"
            onClick={() => resumeSessionMutation.mutate()}
            disabled={resumeSessionMutation.isPending}
          >
            {resumeSessionMutation.isPending ? 'Sending...' : 'Resume'}
          </Button>
        ) : null}
        {actionMessage ? (
          <div className={`dashboard-action-message ${actionMessage.tone}`} role="status">
            {actionMessage.text}
          </div>
        ) : null}
        {(session.status === 'ENDED' || session.status === 'AUTH_FAILED') ? (
          <Link to={`/java/result/${session.id}`} className="btn btn-small btn-secondary">
            {session.status === 'AUTH_FAILED' ? 'Details' : 'Result'}
          </Link>
        ) : null}
      </td>
    </tr>
  );
};

export { InterviewRow };

function renderParticipant(name?: string, email?: string, searchTerm = '') {
  if (!name && !email) {
    return 'N/A';
  }

  return (
    <div className="participant-cell">
      {name ? <div className="participant-name">{highlightText(name, searchTerm)}</div> : null}
      {email ? <div className="participant-email">{highlightText(email, searchTerm)}</div> : null}
    </div>
  );
}

function highlightText(value: string, searchTerm: string) {
  const normalized = searchTerm.trim();
  if (normalized.length < 3) {
    return value;
  }

  const escaped = normalized.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const parts = value.split(new RegExp(`(${escaped})`, 'ig'));

  return parts.map((part, index) =>
    part.toLowerCase() === normalized.toLowerCase() ? <mark key={`${part}-${index}`}>{part}</mark> : part
  );
}

function formatTechnology(value?: SessionResponse['technology']) {
  if (!value) {
    return 'N/A';
  }
  return value.charAt(0) + value.slice(1).toLowerCase();
}


