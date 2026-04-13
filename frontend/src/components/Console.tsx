import React from 'react';
import './Console.css';

interface ConsoleProps {
  stdout: string;
  stderr: string;
}

const Console: React.FC<ConsoleProps> = ({ stdout, stderr }) => {
  const [activeTab, setActiveTab] = React.useState<'output' | 'error'>(() => {
    if (stderr) return 'error';
    return 'output';
  });

  React.useEffect(() => {
    if (stderr && activeTab === 'output') {
      setActiveTab('error');
    } else if (!stderr && activeTab === 'error') {
      setActiveTab('output');
    }
  }, [activeTab, stderr]);

  const hasOutput = stdout.trim().length > 0;
  const hasError = stderr.trim().length > 0;

  return (
    <div className="console">
      <div className="console-tabs">
        <button
          className={`console-tab ${activeTab === 'output' ? 'active' : ''}`}
          onClick={() => setActiveTab('output')}
        >
          <span className="tab-label">
            Output
            {hasOutput && <span className="tab-indicator">&#9679;</span>}
          </span>
        </button>
        <button
          className={`console-tab ${activeTab === 'error' ? 'active' : ''}`}
          onClick={() => setActiveTab('error')}
        >
          <span className="tab-label">
            Errors
            {hasError && <span className="tab-indicator error">&#9679;</span>}
          </span>
        </button>
      </div>

      <div className="console-content">
        {activeTab === 'output' && (
          <pre className={`console-text ${hasOutput ? '' : 'empty'}`}>
            {hasOutput ? stdout.replace(/\\n/g, '\n') : '(no output)'}
          </pre>
        )}
        {activeTab === 'error' && (
          <pre className={`console-text error-text ${hasError ? '' : 'empty'}`}>
            {hasError ? stderr.replace(/\\n/g, '\n') : '(no errors)'}
          </pre>
        )}
      </div>
    </div>
  );
};

export default Console;
