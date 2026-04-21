import React from 'react';
import './Console.css';

interface ConsoleProps {
  stdout: string;
  stderr: string;
  previewUrl?: string | null;
}

const Console: React.FC<ConsoleProps> = ({ stdout, stderr, previewUrl }) => {
  const hasOutput = stdout.trim().length > 0;
  const hasError = stderr.trim().length > 0;
  const hasPreview = Boolean(previewUrl);
  const [activeTab, setActiveTab] = React.useState<'output' | 'error' | 'preview'>(() => {
    if (stderr) return 'error';
    if (previewUrl) return 'preview';
    return 'output';
  });
  const previousFlagsRef = React.useRef({
    hasError,
    hasPreview,
  });

  React.useEffect(() => {
    const previousFlags = previousFlagsRef.current;

    if (hasError && !previousFlags.hasError) {
      setActiveTab('error');
    } else if (hasPreview && !previousFlags.hasPreview && !hasError) {
      setActiveTab('preview');
    } else if (activeTab === 'preview' && !hasPreview) {
      setActiveTab(hasError ? 'error' : 'output');
    } else if (activeTab === 'error' && !hasError) {
      setActiveTab(hasPreview ? 'preview' : 'output');
    }

    previousFlagsRef.current = {
      hasError,
      hasPreview,
    };
  }, [activeTab, hasError, hasPreview]);

  React.useEffect(() => {
    if (previewUrl) {
      console.info('[frontend-preview] preview url', previewUrl);
    }
  }, [previewUrl]);

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
        {hasPreview && (
          <button
            className={`console-tab ${activeTab === 'preview' ? 'active' : ''}`}
            onClick={() => setActiveTab('preview')}
          >
            <span className="tab-label">
              Preview
              <span className="tab-indicator preview">&#9679;</span>
            </span>
          </button>
        )}
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
        {activeTab === 'preview' && hasPreview && (
          <div className="console-preview">
            <iframe
              title="Frontend Preview"
              src={previewUrl!}
              className="console-preview-frame"
              onLoad={() => console.info('[frontend-preview] iframe loaded', previewUrl)}
            />
          </div>
        )}
      </div>
    </div>
  );
};

export default Console;
