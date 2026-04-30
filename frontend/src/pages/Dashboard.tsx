import React, { useDeferredValue, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useLocation } from 'react-router-dom';
import { InterviewRow } from '../components/InterviewRow';
import { sessionApi } from '../services/sessionApi';
import type { FeedbackRating, InterviewMode, SessionResponse, TechnologySkill } from '../types/session';
import './Dashboard.css';

type SortKey = 'createdAt' | 'status' | 'summary';
type DatePresetKey = 'today' | 'week' | 'month' | 'year' | 'financialYear';
type DashboardFilters = {
  from: string;
  to: string;
  modes: InterviewMode[];
  technologies: TechnologySkill[];
  ratings: FeedbackRating[];
};

const TECHNOLOGY_OPTIONS: TechnologySkill[] = ['JAVA', 'PYTHON', 'ANGULAR', 'REACT', 'SQL'];
const MODE_OPTIONS: Array<{ value: InterviewMode; label: string }> = [
  { value: 'HUMAN_INTERVIEWER', label: 'Human' },
  { value: 'AI_INTERVIEWER', label: 'AI' },
];
const RATING_OPTIONS: FeedbackRating[] = ['EXCELLENT', 'GOOD', 'FAIR', 'BAD', 'DISQUALIFIED'];
const TODAY_DATE = toDateValue(new Date());
const TABLE_COLUMN_WIDTHS = ['12%', '8%', '18%', '18%', '13%', '17%', '14%'];

const Dashboard: React.FC = () => {
  const location = useLocation();
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [searchInput, setSearchInput] = useState('');
  const [sortBy, setSortBy] = useState<SortKey>('createdAt');
  const [direction, setDirection] = useState<'asc' | 'desc'>('desc');
  const [filtersEnabled, setFiltersEnabled] = useState(false);
  const [selectedPreset, setSelectedPreset] = useState<DatePresetKey | null>(null);
  const [filters, setFilters] = useState<DashboardFilters>({
    from: '',
    to: '',
    modes: [],
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
      modes: filters.modes,
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
  const registrationCreated = Boolean((location.state as { registrationCreated?: boolean } | null)?.registrationCreated);
  const presetOptions = useMemo(() => buildPresetOptions(), []);
  const fromMax = filters.to ? minDate(filters.to, TODAY_DATE) : TODAY_DATE;
  const toMin = filters.from || undefined;
  const activeFilterCount = useMemo(() => {
    if (!filtersEnabled) {
      return 0;
    }

    return [
      filters.from || filters.to,
      filters.modes.length > 0,
      filters.technologies.length > 0,
      filters.ratings.length > 0,
    ].filter(Boolean).length;
  }, [filters, filtersEnabled]);

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

  const clearFilterField = (key: 'modes' | 'technologies' | 'ratings' | 'from' | 'to') => {
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
      modes: [],
      technologies: [],
      ratings: [],
    });
    setPage(0);
  };

  const toggleModeFilter = (mode: InterviewMode) => {
    setFilters((previous) => ({
      ...previous,
      modes: toggleListValue(previous.modes, mode),
    }));
    setPage(0);
  };

  const toggleTechnologyFilter = (technology: TechnologySkill) => {
    setFilters((previous) => ({
      ...previous,
      technologies: toggleListValue(previous.technologies, technology),
    }));
    setPage(0);
  };

  const toggleRatingFilter = (rating: FeedbackRating) => {
    setFilters((previous) => ({
      ...previous,
      ratings: toggleListValue(previous.ratings, rating),
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
      {registrationCreated && (
        <div className="grid-refreshing">Interview registration created. Start the secure session later from the dashboard.</div>
      )}

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
              {activeFilterCount > 0 ? `Filter (${activeFilterCount})` : 'Filter'}
            </button>
            <Link to="/start" className="btn btn-primary start-interview-btn">
              Register
            </Link>
          </div>
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
          <div className="filter-group-block">
            <div className="filter-group-label-row">
              <div className="filter-group-label">Date</div>
              <button
                type="button"
                className={`filter-clear-button ${filters.from || filters.to ? '' : 'is-invisible'}`}
                onClick={() => {
                  clearFilterField('from');
                  clearFilterField('to');
                }}
                aria-hidden={filters.from || filters.to ? undefined : true}
                tabIndex={filters.from || filters.to ? 0 : -1}
              >
                Clear
              </button>
            </div>
            <div className="filter-section filter-section-wide filter-date-section">
              <div className="filter-date-line">
                <div className="filter-presets filter-presets-top">
                  {renderPresetChip(presetOptions, 'today', selectedPreset, handlePresetSelection)}
                  {renderPresetChip(presetOptions, 'month', selectedPreset, handlePresetSelection)}
                  {renderPresetChip(presetOptions, 'week', selectedPreset, handlePresetSelection)}
                </div>
                <div className="filter-field-date">
                  <label htmlFor="filter-from">From</label>
                  <div className="filter-date-control-row">
                    <input
                      id="filter-from"
                      type="date"
                      value={filters.from}
                      max={fromMax}
                      onChange={(event) => handleDateChange('from', event.target.value)}
                    />
                    <span className={`filter-date-readable ${filters.from ? '' : 'is-invisible'}`}>
                      {filters.from ? formatReadableDate(filters.from) : 'January 01, 2026'}
                    </span>
                  </div>
                </div>
              </div>

              <div className="filter-date-line">
                <div className="filter-presets filter-presets-bottom">
                  {renderPresetChip(presetOptions, 'year', selectedPreset, handlePresetSelection)}
                  {renderPresetChip(presetOptions, 'financialYear', selectedPreset, handlePresetSelection)}
                </div>
                <div className="filter-field-date">
                  <label htmlFor="filter-to">To</label>
                  <div className="filter-date-control-row">
                    <input
                      id="filter-to"
                      type="date"
                      value={filters.to}
                      min={toMin}
                      max={TODAY_DATE}
                      onChange={(event) => handleDateChange('to', event.target.value)}
                    />
                    <span className={`filter-date-readable ${filters.to ? '' : 'is-invisible'}`}>
                      {filters.to ? formatReadableDate(filters.to) : 'January 01, 2026'}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="filter-group-block">
            <div className="filter-grid">
              <div className="filter-group-block">
                <div className="filter-group-label-row">
                  <div className="filter-group-label">Mode</div>
                  <button
                    type="button"
                    className={`filter-clear-button ${filters.modes.length > 0 ? '' : 'is-invisible'}`}
                    onClick={() => clearFilterField('modes')}
                    aria-hidden={filters.modes.length > 0 ? undefined : true}
                    tabIndex={filters.modes.length > 0 ? 0 : -1}
                  >
                    Clear
                  </button>
                </div>
                <div className="filter-section filter-section-mode">
                  <div className="filter-chip-row">
                    {MODE_OPTIONS.map((mode) => (
                      <button
                        key={mode.value}
                        type="button"
                        className={`filter-chip ${filters.modes.includes(mode.value) ? 'is-selected' : ''}`}
                        onClick={() => toggleModeFilter(mode.value)}
                        aria-pressed={filters.modes.includes(mode.value)}
                      >
                        {mode.label}
                      </button>
                    ))}
                  </div>
                </div>
              </div>

              <div className="filter-group-block">
                <div className="filter-group-label-row">
                  <div className="filter-group-label">Technology</div>
                  <button
                    type="button"
                    className={`filter-clear-button ${filters.technologies.length > 0 ? '' : 'is-invisible'}`}
                    onClick={() => clearFilterField('technologies')}
                    aria-hidden={filters.technologies.length > 0 ? undefined : true}
                    tabIndex={filters.technologies.length > 0 ? 0 : -1}
                  >
                    Clear
                  </button>
                </div>
                <div className="filter-section filter-section-technology">
                  <div className="filter-chip-row">
                    {TECHNOLOGY_OPTIONS.map((technology) => (
                      <button
                        key={technology}
                        type="button"
                        className={`filter-chip ${filters.technologies.includes(technology) ? 'is-selected' : ''}`}
                        onClick={() => toggleTechnologyFilter(technology)}
                        aria-pressed={filters.technologies.includes(technology)}
                      >
                        {formatTechnology(technology)}
                      </button>
                    ))}
                  </div>
                </div>
              </div>

              <div className="filter-group-block">
                <div className="filter-group-label-row">
                  <div className="filter-group-label">Rating</div>
                  <button
                    type="button"
                    className={`filter-clear-button ${filters.ratings.length > 0 ? '' : 'is-invisible'}`}
                    onClick={() => clearFilterField('ratings')}
                    aria-hidden={filters.ratings.length > 0 ? undefined : true}
                    tabIndex={filters.ratings.length > 0 ? 0 : -1}
                  >
                    Clear
                  </button>
                </div>
                <div className="filter-section filter-section-rating">
                  <div className="filter-chip-row">
                    {RATING_OPTIONS.map((rating) => (
                      <button
                        key={rating}
                        type="button"
                        className={`filter-chip ${filters.ratings.includes(rating) ? 'is-selected' : ''}`}
                        onClick={() => toggleRatingFilter(rating)}
                        aria-pressed={filters.ratings.includes(rating)}
                      >
                        {formatRating(rating)}
                      </button>
                    ))}
                  </div>
                </div>
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
                    Interview Start{renderSortIndicator('createdAt', sortBy, direction)}
                  </button>
                </th>
                <th>Skill</th>
                <th>Interviewer</th>
                <th>Interviewee</th>
                <th>
                  <button type="button" className="sort-button" onClick={() => toggleSort('status', sortBy, direction, setSortBy, setDirection)}>
                    State{renderSortIndicator('status', sortBy, direction)}
                  </button>
                </th>
                <th>
                  <button type="button" className="sort-button" onClick={() => toggleSort('summary', sortBy, direction, setSortBy, setDirection)}>
                    Session Summary{renderSortIndicator('summary', sortBy, direction)}
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

function renderPresetChip(
  presetOptions: Array<{ key: DatePresetKey; label: string }>,
  key: DatePresetKey,
  selectedPreset: DatePresetKey | null,
  onSelect: (preset: DatePresetKey) => void
) {
  const preset = presetOptions.find((option) => option.key === key);
  if (!preset) {
    return null;
  }

  return (
    <button
      key={preset.key}
      type="button"
      className={`preset-chip ${selectedPreset === preset.key ? 'is-selected' : ''}`}
      onClick={() => onSelect(preset.key)}
    >
      {preset.label}
    </button>
  );
}

function minDate(first: string, second: string) {
  return first <= second ? first : second;
}

function toggleListValue<T extends string>(values: T[], value: T) {
  return values.includes(value) ? values.filter((current) => current !== value) : [...values, value];
}

function formatReadableDate(value: string) {
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) {
    return '';
  }

  return new Intl.DateTimeFormat(undefined, {
    month: 'long',
    day: 'numeric',
    year: 'numeric',
  }).format(date);
}

function formatTechnology(value: TechnologySkill) {
  return value.charAt(0) + value.slice(1).toLowerCase();
}

function formatRating(value: FeedbackRating) {
  return value.charAt(0) + value.slice(1).toLowerCase();
}
