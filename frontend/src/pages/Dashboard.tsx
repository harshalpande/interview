import React, { useDeferredValue, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { InterviewRow } from '../components/InterviewRow';
import { sessionApi } from '../services/sessionApi';
import type { SessionResponse } from '../types/session';
import './Dashboard.css';

const Dashboard: React.FC = () => {
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [searchInput, setSearchInput] = useState('');
  const [sortBy, setSortBy] = useState<'createdAt' | 'status' | 'summary'>('createdAt');
  const [direction, setDirection] = useState<'asc' | 'desc'>('desc');
  const deferredSearch = useDeferredValue(searchInput.trim());
  const activeSearch = deferredSearch.length >= 3 ? deferredSearch : '';

  const { data, isLoading, error } = useQuery({
    queryKey: ['sessions', page, pageSize, sortBy, direction, activeSearch],
    queryFn: () => sessionApi.listSessions(page, pageSize, sortBy, direction, activeSearch),
  });

  const sessions = data?.content || [];
  const totalPages = data?.totalPages || 0;

  return (
    <div className="dashboard">
      <div className="dashboard-header">
        <h2>Recent Interview Sessions (newest first)</h2>
      </div>

      <div className="dashboard-toolbar">
        <div className="dashboard-search">
          <label htmlFor="session-search">Search participants</label>
          <div className="dashboard-search-row">
            <input
              id="session-search"
              type="search"
              value={searchInput}
              onChange={(event) => {
                setSearchInput(event.target.value);
                setPage(0);
              }}
              placeholder="Type at least 3 characters"
            />
            <Link to="/start" className="btn btn-primary start-interview-btn">
              Start Interview
            </Link>
          </div>
          <span className="search-hint">Minimum 3 characters are needed to search.</span>
        </div>
      </div>

      {error && <div className="error">Error loading sessions: {error.message}</div>}
      {isLoading && !data ? (
        <div>Loading...</div>
      ) : (
        <>
          <table className="interviews-table">
            <thead>
              <tr>
                <th>
                  <button type="button" className="sort-button" onClick={() => toggleSort('createdAt', sortBy, direction, setSortBy, setDirection)}>
                    Date{renderSortIndicator('createdAt', sortBy, direction)}
                  </button>
                </th>
                <th>Technology/Skill</th>
                <th>Interviewer</th>
                <th>Interviewee</th>
                <th>
                  <button type="button" className="sort-button" onClick={() => toggleSort('status', sortBy, direction, setSortBy, setDirection)}>
                    Status{renderSortIndicator('status', sortBy, direction)}
                  </button>
                </th>
                <th>
                  <button type="button" className="sort-button" onClick={() => toggleSort('summary', sortBy, direction, setSortBy, setDirection)}>
                    Summary{renderSortIndicator('summary', sortBy, direction)}
                  </button>
                </th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {sessions.map((session: SessionResponse) => (
                <InterviewRow key={session.id} session={session} searchTerm={activeSearch} />
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
              <button className="btn btn-secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                Previous
              </button>
              <span>Page {page + 1}</span>
              <button className="btn btn-secondary" disabled={totalPages > 0 && page >= totalPages - 1} onClick={() => setPage((p) => p + 1)}>
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

function toggleSort(
  nextSort: 'createdAt' | 'status' | 'summary',
  currentSort: 'createdAt' | 'status' | 'summary',
  currentDirection: 'asc' | 'desc',
  setSortBy: React.Dispatch<React.SetStateAction<'createdAt' | 'status' | 'summary'>>,
  setDirection: React.Dispatch<React.SetStateAction<'asc' | 'desc'>>
) {
  if (nextSort === currentSort) {
    setDirection(currentDirection === 'asc' ? 'desc' : 'asc');
    return;
  }

  setSortBy(nextSort);
  setDirection(nextSort === 'createdAt' ? 'desc' : 'asc');
}

function renderSortIndicator(
  key: 'createdAt' | 'status' | 'summary',
  sortBy: 'createdAt' | 'status' | 'summary',
  direction: 'asc' | 'desc'
) {
  if (key !== sortBy) {
    return ' +/-';
  }
  return direction === 'asc' ? ' ^' : ' v';
}
