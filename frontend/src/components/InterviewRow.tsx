import React from 'react';
import { Link } from 'react-router-dom';
import type { SessionResponse } from '../types/session';
import { formatDateTimeCompact } from '../utils/dateTime';

const STATUS_LABELS: Record<string, string> = {
  CREATED: 'Ready to Start',
  WAITING_JOIN: 'Waiting for Join',
  ACTIVE: 'Active',
  ENDED: 'Ended',
  EXPIRED: 'Expired',
};

interface InterviewRowProps {
  session: SessionResponse;
  searchTerm?: string;
}

const InterviewRow: React.FC<InterviewRowProps> = ({ session, searchTerm = '' }) => {
  const interviewer = session.participants?.find((p) => p.role === 'INTERVIEWER');
  const interviewee = session.participants?.find((p) => p.role === 'INTERVIEWEE');

  return (
    <tr>
      <td className="date-cell">{formatDateTimeCompact(session.createdAt)}</td>
      <td>{formatTechnology(session.technology)}</td>
      <td>{interviewer ? highlightText(`${interviewer.name} (${interviewer.email})`, searchTerm) : 'N/A'}</td>
      <td>{interviewee ? highlightText(`${interviewee.name} (${interviewee.email})`, searchTerm) : 'N/A'}</td>
      <td>
        <span className={`status status-${session.status.toLowerCase()}`}>
          {STATUS_LABELS[session.status] || session.status}
        </span>
      </td>
      <td className="summary-cell">
        <div>{session.summary || 'No summary available yet'}</div>
      </td>
      <td>
        {session.status !== 'EXPIRED' ? (
          <Link to={`/java/result/${session.id}`} className="btn btn-small btn-secondary">
            Result
          </Link>
        ) : null}
      </td>
    </tr>
  );
};

export { InterviewRow };

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


