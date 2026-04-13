import React from 'react';
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';

import { InterviewRow } from '../components/InterviewRow';
import { sessionApi } from '../services/sessionApi';
import type { SessionResponse } from '../types/session';
import './Dashboard.css';

const Dashboard: React.FC = () => {
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);

  const { data, isLoading, error } = useQuery({
    queryKey: ['sessions', page, pageSize],
    queryFn: () => sessionApi.listSessions(page, pageSize),
  });

  const sessions = data?.content || [];
  const totalPages = data?.totalPages || 0;

  return (
    <div className="dashboard">
      <div className="dashboard-header">
        <h2>Recent Interview Sessions (newest first)</h2>
      </div>
      <div className="start-button-container">
        <Link to="/start" className="btn btn-primary start-interview-btn">
          Start Interview
        </Link>
      </div>
      {error && <div className="error">Error loading sessions: {error.message}</div>}
      {isLoading && !data ? (
        <div>Loading...</div>
      ) : (
        <>
          <table className="interviews-table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Interviewer</th>
                <th>Interviewee</th>
                <th>Status</th>
                <th>Summary</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {sessions.map((session: SessionResponse) => (
                <InterviewRow key={session.id} session={session} />
              ))}
            </tbody>
          </table>
          <div className="pagination">
            <label>
              Page Size:
              <select value={pageSize} onChange={(e) => { setPageSize(Number(e.target.value)); setPage(0); }}>
                <option value={10}>10</option>
                <option value={20}>20</option>
                <option value={50}>50</option>
              </select>
            </label>
            <div className="page-controls">
              <button 
                className="btn btn-secondary" 
                disabled={page === 0} 
                onClick={() => setPage(p => p - 1)}
              >
                Previous
              </button>
              <span>Page {page + 1}</span>
              <button 
                className="btn btn-secondary" 
                disabled={totalPages > 0 && page >= totalPages - 1}
                onClick={() => setPage(p => p + 1)}
              >
                Next
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
};

export default Dashboard;



