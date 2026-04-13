import React from 'react';
import { Link } from 'react-router-dom';
import type { SessionResponse } from '../types/session';
import { formatDateTime } from '../utils/dateTime';

const STATUS_LABELS: Record<string, string> = {
  CREATED: 'Ready to Start',
  WAITING_JOIN: 'Waiting for Join',
  ACTIVE: 'Active',
  ENDED: 'Ended',
  EXPIRED: 'Expired',
};

interface InterviewRowProps {
  session: SessionResponse;
}

const InterviewRow: React.FC<InterviewRowProps> = ({ session }) => {
  const interviewer = session.participants?.find((p) => p.role === 'INTERVIEWER');
  const interviewee = session.participants?.find((p) => p.role === 'INTERVIEWEE');

  return (
    <tr>
      <td>{formatDateTime(session.createdAt)}</td>
      <td>{interviewer ? `${interviewer.name} (${interviewer.email})` : 'N/A'}</td>
      <td>{interviewee ? `${interviewee.name} (${interviewee.email})` : 'N/A'}</td>
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
          <Link to={`/result/${session.id}`} className="btn btn-small btn-secondary">
            Result
          </Link>
        ) : null}
      </td>
    </tr>
  );
};

export { InterviewRow };


