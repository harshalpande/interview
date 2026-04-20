import React, { useDeferredValue, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { InterviewRow } from '../components/InterviewRow';
import { sessionApi } from '../services/sessionApi';
import type { FeedbackRating, SessionResponse, TechnologySkill } from '../types/session';
import './Dashboard.css';

type SortKey = 'createdAt' | 'status' | 'summary';
type DatePresetKey = 'today' | 'week' | 'month' | 'year' | 'financialYear';

const TECHNOLOGY_OPTIONS: TechnologySkill[] = ['JAVA', 'PYTHON', 'ANGULAR', 'REACT', 'SQL'];
const RATING_OPTIONS: FeedbackRating[] = ['EXCELLENT', 'GOOD', 'FAIR', 'BAD', 'DISQUALIFIED'];
const TODAY_DATE = toDateValue(new Date());
const TABLE_COLUMN_WIDTHS = ['15%', '10%', '21%', '21%', '11%', '14%', '8%'];

const Dashboard: React.FC = () => {
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [searchInput, setSearchInput] = useState('');
  const [sortBy, setSortBy] = useState<SortKey>('createdAt');
  const [direction, setDirection] = useState<'asc' | 'desc'>('desc');
  const [filtersEnabled, setFiltersEnabled] = useState(false);
  const [selectedPreset, setSelectedPreset] = useState<DatePresetKey | null>(null);
  const [filters, setFilters] = useState<{
    from: string;
    to: string;
    technologies: TechnologySkill[];
    ratings: FeedbackRating[];
  }>({
    from: '',
    to: '',
    technologies: [],
    ratings: [],
  });
  const deferredSearch = useDeferredValue(searchInput.trim());
  const activeSearch = deferredSearch.length >= 3 ? deferredSearch : '';

  const activeFilters = useMemo(() => {
    if (!filtersEnabled) {
      return undefined;
    }

    return {
      from: filters.from ? toIsoString(filters.from, 'start') : undefined,
      to: filters.to ? toIsoString(filters.to, 'end') : undefined,
      technologies: filters.technologies,
      ratings: filters.ratings,
    };
  }, [filters, filtersEnabled]);

  const { data, isLoading, error, isFetching } = useQuery({
    queryKey: ['sessions', page, pageSize, sortBy, direction, activeSearch, activeFilters],
    queryFn: () => sessionApi.listSessions(page, pageSize, sortBy, direction, activeSearch, activeFilters),
  });

  const sessions = data?.content || [];
  const totalPages = data?.totalPages || 0;
  const totalElements = data?.totalElements || 0;
  const presetOptions = useMemo(() => buildPresetOptions(), []);
  const fromMax = filters.to ? minDate(filters.to, TODAY_DATE) : TODAY_DATE;
  const toMin = filters.from || undefined;

  const handlePresetSelection = (preset: DatePresetKey) => {
    if (selectedPreset === preset) {
      setSelectedPreset(null);
      setFilters((previous) => ({
        ...previous,
        from: '',
        to: '',
      }));
      setPage(0);
      return;
    }

    const nextRange = createPresetRange(preset);
    setSelectedPreset(preset);
    setFilters((previous) => ({
      ...previous,
      from: nextRange.from,
      to: nextRange.to,
    }));
    setPage(0);
  };

  const handleFilterToggle = () => {
    setFiltersEnabled((previous) => !previous);
    setPage(0);
  };

  const handleDateChange = (key: 'from' | 'to', value: string) => {
    setSelectedPreset(null);
    setFilters((previous) => ({
      ...previous,
      [key]: value,
    }));
    setPage(0);
  };

  const clearFilterField = (key: 'technologies' | 'ratings' | 'from' | 'to') => {
    if (key === 'from' || key === 'to') {
      setSelectedPreset(null);
      setFilters((previous) => ({
        ...previous,
        [key]: '',
      }));
    } else {
      setFilters((previous) => ({
        ...previous,
        [key]: [],
      }));
    }
    setPage(0);
  };

  const resetFilters = () => {
    setSelectedPreset(null);
    setFilters({
      from: '',
      to: '',
      technologies: [],
      ratings: [],
    });
    setPage(0);
  };

  const handleMultiSelectChange = (
    key: 'technologies' | 'ratings',
    event: React.ChangeEvent<HTMLSelectElement>
  ) => {
    const values = Array.from(event.target.selectedOptions).map((option) => option.value);
    setFilters((previous) => ({
      ...previous,
      [key]: values,
    }));
    setPage(0);
  };

  const handleDownload = async () => {
    try {
      const exportFilters = filtersEnabled ? activeFilters : undefined;
      const { blob, filename } = await sessionApi.exportSessionsCsv(sortBy, direction, activeSearch, exportFilters);
      const objectUrl = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = filename;
      document.body.appendChild(anchor);
      anchor.click();
      document.body.removeChild(anchor);
      URL.revokeObjectURL(objectUrl);
    } catch (downloadError) {
      window.alert(downloadError instanceof Error ? downloadError.message : 'Unable to download report');
    }
  };

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
              name="session-search-dashboard"
              autoComplete="off"
              autoCorrect="off"
              autoCapitalize="none"
              spellCheck={false}
              value={searchInput}
              onChange={(event) => {
                setSearchInput(event.target.value);
                setPage(0);
              }}
              placeholder="Type at least 3 characters"
            />
            <button
              type="button"
              className={`btn btn-secondary filter-toggle-btn ${filtersEnabled ? 'is-active' : ''}`}
              onClick={handleFilterToggle}
              aria-pressed={filtersEnabled}
            >
              Filter
            </button>
            <Link to="/start" className="btn btn-primary start-interview-btn">
              Start Interview
            </Link>
          </div>
          <span className="search-hint">Minimum 3 characters are needed to search.</span>
        </div>
      </div>

      <div className={`dashboard-filters ${filtersEnabled ? 'is-open' : ''}`} aria-hidden={!filtersEnabled}>
        <div className="filter-header">
          <div className="filter-header-title">Filters</div>
          <div className="filter-header-actions">
            <button type="button" className="filter-reset-button" onClick={resetFilters}>
              Reset
            </button>
            {sessions.length > 0 && (
              <button type="button" className="download-link-button" onClick={handleDownload}>
                Download CSV
              </button>
            )}
          </div>
        </div>

        <div className="filter-body">
          <div className="filter-presets">
            {presetOptions.map((preset) => (
              <button
                key={preset.key}
                type="button"
                className={`preset-chip ${selectedPreset === preset.key ? 'is-selected' : ''}`}
                onClick={() => handlePresetSelection(preset.key)}
              >
                {preset.label}
              </button>
            ))}
          </div>

          <div className="filter-grid">
            <div className="filter-group filter-group-date">
              <div className="filter-date-stack">
                <div className="filter-field filter-field-date filter-field-date-compact">
                  <div className="filter-label-row">
                    <label htmlFor="filter-from">From</label>
                    {filters.from && (
                      <button type="button" className="filter-clear-button" onClick={() => clearFilterField('from')}>
                        Clear
                      </button>
                    )}
                  </div>
                  <input
                    id="filter-from"
                    type="date"
                    value={filters.from}
                    max={fromMax}
                    onChange={(event) => handleDateChange('from', event.target.value)}
                  />
                </div>

                <div className="filter-field filter-field-date filter-field-date-compact">
                  <div className="filter-label-row">
                    <label htmlFor="filter-to">To</label>
                    {filters.to && (
                      <button type="button" className="filter-clear-button" onClick={() => clearFilterField('to')}>
                        Clear
                      </button>
                    )}
                  </div>
                  <input
                    id="filter-to"
                    type="date"
                    value={filters.to}
                    min={toMin}
                    max={TODAY_DATE}
                    onChange={(event) => handleDateChange('to', event.target.value)}
                  />
                </div>
              </div>
            </div>

            <div className="filter-group">
              <div className="filter-field">
                <div className="filter-label-row">
                  <label htmlFor="filter-technology">Technology</label>
                  {filters.technologies.length > 0 && (
                    <button
                      type="button"
                      className="filter-clear-button"
                      onClick={() => clearFilterField('technologies')}
                    >
                      Clear
                    </button>
                  )}
                </div>
                <select
                  id="filter-technology"
                  multiple
                  value={filters.technologies}
                  onChange={(event) => handleMultiSelectChange('technologies', event)}
                >
                  {TECHNOLOGY_OPTIONS.map((technology) => (
                    <option key={technology} value={technology}>
                      {formatTechnology(technology)}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div className="filter-group">
              <div className="filter-field">
                <div className="filter-label-row">
                  <label htmlFor="filter-rating">Summary</label>
                  {filters.ratings.length > 0 && (
                    <button type="button" className="filter-clear-button" onClick={() => clearFilterField('ratings')}>
                      Clear
                    </button>
                  )}
                </div>
                <select
                  id="filter-rating"
                  multiple
                  value={filters.ratings}
                  onChange={(event) => handleMultiSelectChange('ratings', event)}
                >
                  {RATING_OPTIONS.map((rating) => (
                    <option key={rating} value={rating}>
                      {formatRating(rating)}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </div>
        </div>
      </div>

      {error && <div className="error">Error loading sessions: {error.message}</div>}
      {(isLoading && !data) ? (
        <div>Loading...</div>
      ) : (
        <>
          {isFetching && <div className="grid-refreshing">Refreshing filtered results...</div>}
          <div className="grid-count">
            Total records: <strong>{totalElements}</strong>
          </div>

          <table className="interviews-table">
            <colgroup>
              {TABLE_COLUMN_WIDTHS.map((width, index) => (
                <col key={index} style={{ width }} />
              ))}
            </colgroup>
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
                    State{renderSortIndicator('status', sortBy, direction)}
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
              {sessions.length > 0 ? (
                sessions.map((session: SessionResponse) => (
                  <InterviewRow key={session.id} session={session} searchTerm={activeSearch} />
                ))
              ) : (
                <tr className="empty-grid-row">
                  <td colSpan={7}>No interview sessions match the current search and filter criteria.</td>
                </tr>
              )}
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
  nextSort: SortKey,
  currentSort: SortKey,
  currentDirection: 'asc' | 'desc',
  setSortBy: React.Dispatch<React.SetStateAction<SortKey>>,
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
  key: SortKey,
  sortBy: SortKey,
  direction: 'asc' | 'desc'
) {
  if (key !== sortBy) {
    return ' ↕';
  }
  return direction === 'asc' ? ' ↑' : ' ↓';
}

function toIsoString(value: string, boundary: 'start' | 'end') {
  const date = new Date(`${value}T00:00:00`);
  if (boundary === 'end') {
    date.setHours(23, 59, 59, 999);
  } else {
    date.setHours(0, 0, 0, 0);
  }
  return date.toISOString();
}

function toDateValue(date: Date) {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function createPresetRange(preset: DatePresetKey) {
  const now = new Date();
  const start = new Date(now);
  const end = new Date(now);

  switch (preset) {
    case 'today':
      start.setHours(0, 0, 0, 0);
      break;
    case 'week': {
      const day = now.getDay();
      const mondayOffset = day === 0 ? -6 : 1 - day;
      start.setDate(now.getDate() + mondayOffset);
      start.setHours(0, 0, 0, 0);
      break;
    }
    case 'month':
      start.setDate(1);
      start.setHours(0, 0, 0, 0);
      break;
    case 'year':
      start.setMonth(0, 1);
      start.setHours(0, 0, 0, 0);
      break;
    case 'financialYear': {
      const currentYear = now.getMonth() >= 3 ? now.getFullYear() : now.getFullYear() - 1;
      start.setFullYear(currentYear, 3, 1);
      start.setHours(0, 0, 0, 0);
      break;
    }
  }

  return {
    from: toDateValue(start),
    to: toDateValue(end),
  };
}

function buildPresetOptions() {
  const now = new Date();
  const monthName = new Intl.DateTimeFormat(undefined, { month: 'long' }).format(now);
  return [
    { key: 'today' as const, label: 'Today' },
    { key: 'week' as const, label: 'Curr. Week' },
    { key: 'month' as const, label: monthName },
    { key: 'year' as const, label: 'Curr. Year' },
    { key: 'financialYear' as const, label: 'Curr. Fin. Year' },
  ];
}

function minDate(first: string, second: string) {
  return first <= second ? first : second;
}

function formatTechnology(value: TechnologySkill) {
  return value.charAt(0) + value.slice(1).toLowerCase();
}

function formatRating(value: FeedbackRating) {
  return value.charAt(0) + value.slice(1).toLowerCase();
}
