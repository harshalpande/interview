import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '../components/Button';
import './TechnologySelection.css';

type TechnologyKey = 'java' | 'python' | 'angular' | 'react' | 'sql';

type TechnologyOption = {
  id: TechnologyKey;
  label: string;
  caption: string;
  status: 'available' | 'coming-soon';
  icon: React.ReactNode;
};

const technologyOptions: TechnologyOption[] = [
  {
    id: 'java',
    label: 'Java',
    caption: 'Live coding interviews',
    status: 'available',
    icon: (
      <svg viewBox="0 0 64 64" aria-hidden="true">
        <path d="M22 42h20c4.4 0 8 3.6 8 8H14c0-4.4 3.6-8 8-8Z" fill="currentColor" opacity="0.18" />
        <path d="M22 39h20" stroke="currentColor" strokeWidth="4" strokeLinecap="round" />
        <path d="M18 47h28" stroke="currentColor" strokeWidth="4" strokeLinecap="round" />
        <path d="M29 16c5 4 1 7-1 10-2 3-2 6 2 9" stroke="currentColor" strokeWidth="4" strokeLinecap="round" fill="none" />
        <path d="M39 12c4 4 2 6 0 9-2 3-2 5 1 8" stroke="currentColor" strokeWidth="4" strokeLinecap="round" fill="none" />
      </svg>
    ),
  },
  {
    id: 'python',
    label: 'Python',
    caption: 'Coming soon',
    status: 'coming-soon',
    icon: (
      <svg viewBox="0 0 64 64" aria-hidden="true">
        <rect x="14" y="14" width="22" height="18" rx="8" fill="currentColor" opacity="0.88" />
        <rect x="28" y="32" width="22" height="18" rx="8" fill="currentColor" opacity="0.22" />
        <circle cx="24" cy="23" r="2.5" fill="#fff" />
        <circle cx="40" cy="41" r="2.5" fill="currentColor" />
      </svg>
    ),
  },
  {
    id: 'angular',
    label: 'Angular',
    caption: 'Coming soon',
    status: 'coming-soon',
    icon: (
      <svg viewBox="0 0 64 64" aria-hidden="true">
        <path d="M32 10 48 16l-3 28-13 8-13-8-3-28 16-6Z" fill="currentColor" opacity="0.2" />
        <path d="M32 16 43 42h-5l-2-5h-8l-2 5h-5l11-26h0Z" fill="currentColor" />
        <path d="M30 33h4l-2-6-2 6Z" fill="#fff" />
      </svg>
    ),
  },
  {
    id: 'react',
    label: 'React',
    caption: 'Coming soon',
    status: 'coming-soon',
    icon: (
      <svg viewBox="0 0 64 64" aria-hidden="true">
        <circle cx="32" cy="32" r="4.5" fill="currentColor" />
        <ellipse cx="32" cy="32" rx="20" ry="8.5" stroke="currentColor" strokeWidth="3.5" fill="none" />
        <ellipse cx="32" cy="32" rx="20" ry="8.5" stroke="currentColor" strokeWidth="3.5" fill="none" transform="rotate(60 32 32)" />
        <ellipse cx="32" cy="32" rx="20" ry="8.5" stroke="currentColor" strokeWidth="3.5" fill="none" transform="rotate(120 32 32)" />
      </svg>
    ),
  },
  {
    id: 'sql',
    label: 'SQL',
    caption: 'Coming soon',
    status: 'coming-soon',
    icon: (
      <svg viewBox="0 0 64 64" aria-hidden="true">
        <ellipse cx="32" cy="18" rx="16" ry="7" fill="currentColor" opacity="0.28" />
        <path d="M16 18v20c0 3.9 7.2 7 16 7s16-3.1 16-7V18" fill="none" stroke="currentColor" strokeWidth="4" />
        <path d="M16 28c0 3.9 7.2 7 16 7s16-3.1 16-7" fill="none" stroke="currentColor" strokeWidth="4" />
      </svg>
    ),
  },
];

const TechnologySelection: React.FC = () => {
  const navigate = useNavigate();
  const [selectedTechnology, setSelectedTechnology] = React.useState<TechnologyKey | null>('java');

  const selectedOption = technologyOptions.find((option) => option.id === selectedTechnology) ?? null;
  const canProceed = selectedOption?.status === 'available';

  return (
    <div className="page-shell">
      <div className="page-card tech-selection-card">
        <div className="page-kicker">Technology Setup</div>
        <h2>Select the interview technology</h2>
        <p className="page-subtitle">
          Choose the stack for this interview. Java is available now, and the remaining technologies are visible as upcoming options.
        </p>

        <div className="tech-grid" role="list" aria-label="Available technologies">
          {technologyOptions.map((option) => {
            const isSelected = option.id === selectedTechnology;
            const isDisabled = option.status !== 'available';

            return (
              <button
                key={option.id}
                type="button"
                className={`tech-tile ${isSelected ? 'selected' : ''} ${isDisabled ? 'disabled' : ''}`}
                onClick={() => setSelectedTechnology(option.id)}
                aria-pressed={isSelected}
              >
                <span className="tech-icon">{option.icon}</span>
                <span className="tech-label">{option.label}</span>
                <span className="tech-caption">{option.caption}</span>
                <span className={`tech-pill ${isDisabled ? 'soon' : 'live'}`}>
                  {isDisabled ? 'Coming Soon' : 'Live'}
                </span>
              </button>
            );
          })}
        </div>

        <div className="tech-selection-footer">
          <div className="hint-panel tech-hint">
            <p>
              <strong>Selected:</strong> {selectedOption?.label ?? 'None'}
            </p>
            <p>
              {canProceed
                ? 'Proceed to create a Java interview session.'
                : 'This technology is not enabled yet. Please select Java to continue.'}
            </p>
          </div>
          <div className="tech-selection-actions">
            <Button disabled={!canProceed} onClick={() => navigate('/java/start')}>
              Next
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default TechnologySelection;
