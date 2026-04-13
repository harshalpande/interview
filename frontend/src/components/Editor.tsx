import React, { useCallback, useEffect, useState } from 'react';
import MonacoEditor from '@monaco-editor/react';
import { ExecuteRequest } from '../types/api';
import ResizeHandle from './ResizeHandle';
import { compilerApi } from '../services/api';
import './Editor.css';
import Console from './Console';

const TEMPLATE_CODE = `public class Solution {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
}`;

interface EditorState {
  code: string;
  output: string;
  error: string;
  loading: boolean;
  executionTime: number;
}

interface EditorProps {
  readOnly?: boolean;
  initialCode?: string;
  onCodeChange?: (code: string) => void;
  canRun?: boolean;
  emptyStateMessage?: string;
  showFullscreenToggle?: boolean;
  isFullscreen?: boolean;
  onToggleFullscreen?: () => void;
  headerRightSlot?: React.ReactNode;
}

const Editor: React.FC<EditorProps> = ({
  readOnly = false,
  initialCode = TEMPLATE_CODE,
  onCodeChange,
  canRun = true,
  emptyStateMessage,
  showFullscreenToggle = false,
  isFullscreen = false,
  onToggleFullscreen,
  headerRightSlot,
}) => {
  const containerRef = React.useRef<HTMLDivElement | null>(null);
  const defaultOutputPct = 35;
  const minOutputPct = defaultOutputPct * 0.5;
  const defaultEditorPct = 100 - defaultOutputPct;
  const minEditorPct = defaultEditorPct * 0.2;
  const maxOutputPct = 100 - minEditorPct;
  const minOutputPx = 320;

  const [state, setState] = useState<EditorState>({
    code: initialCode,
    output: '',
    error: '',
    loading: false,
    executionTime: 0,
  });
  const [theme, setTheme] = useState<'vs-dark' | 'vs-light'>('vs-dark');
  const [outputPct, setOutputPct] = useState<number>(defaultOutputPct);
  const [isStacked, setIsStacked] = useState(() => window.matchMedia('(max-width: 980px)').matches);
  const runLatestRef = React.useRef<(() => void) | null>(null);

  useEffect(() => {
    setState((prev) => ({
      ...prev,
      code: initialCode,
    }));
  }, [initialCode]);

  useEffect(() => {
    const media = window.matchMedia('(max-width: 980px)');
    const handler = () => setIsStacked(media.matches);
    handler();
    if (typeof media.addEventListener === 'function') {
      media.addEventListener('change', handler);
      return () => media.removeEventListener('change', handler);
    }
    // Safari/older fallback
    media.addListener(handler);
    return () => media.removeListener(handler);
  }, []);

  const handleCodeChange = useCallback(
    (value: string | undefined) => {
      if (value !== undefined && !readOnly) {
        setState((prev) => ({ ...prev, code: value }));
        onCodeChange?.(value);
      }
    },
    [onCodeChange, readOnly]
  );

  const handleRun = useCallback(async () => {
    if (!canRun) {
      return;
    }
    setState((prev) => ({
      ...prev,
      loading: true,
      output: '',
      error: '',
      executionTime: 0,
    }));

    try {
      const request: ExecuteRequest = {
        sourceCode: state.code,
        timeoutMs: 5000,
        memoryLimitMb: 512,
      };

      const response = await compilerApi.execute(request);

      setState((prev) => ({
        ...prev,
        loading: false,
        output: response.stdout || '',
        error: response.stderr || (response.compileErrors?.join('\n') || ''),
        executionTime: response.executionTimeMs || 0,
      }));
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'An error occurred';
      setState((prev) => ({
        ...prev,
        loading: false,
        error: errorMessage,
      }));
    }
  }, [canRun, state.code]);

  useEffect(() => {
    runLatestRef.current = handleRun;
  }, [handleRun]);

  const handleClear = () => {
    setState((prev) => ({
      ...prev,
      output: '',
      error: '',
      executionTime: 0,
    }));
  };

  const handleReset = () => {
    setState((prev) => ({
      ...prev,
      code: TEMPLATE_CODE,
      output: '',
      error: '',
      executionTime: 0,
    }));
    onCodeChange?.(TEMPLATE_CODE);
  };

  const toggleTheme = () => {
    setTheme((prev) => (prev === 'vs-dark' ? 'vs-light' : 'vs-dark'));
  };

  const handleResizePointerDown = (event: React.PointerEvent<HTMLDivElement>) => {
    const node = containerRef.current;
    if (!node) return;
    if (isStacked) return;

    const startRect = node.getBoundingClientRect();
    const dynamicMinOutputPct = Math.max(minOutputPct, (minOutputPx / startRect.width) * 100);

    const move = (moveEvent: PointerEvent) => {
      const dx = moveEvent.clientX - startRect.left;
      const nextEditorPct = Math.max(0, Math.min(100, (dx / startRect.width) * 100));
      const nextOutputPct = 100 - nextEditorPct;
      const clampedOutputPct = Math.max(dynamicMinOutputPct, Math.min(maxOutputPct, nextOutputPct));
      setOutputPct(clampedOutputPct);
    };

    const up = () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
    };

    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
  };

  const codeKb = Math.max(0, new Blob([state.code]).size / 1024);
  const codeKbLabel = `${codeKb.toFixed(codeKb >= 10 ? 0 : 1)} KB`;

  return (
    <div
      className={`editor-container ${isFullscreen ? 'editor-fullscreen' : ''}`}
      ref={containerRef}
      style={
        isStacked
          ? undefined
          : { gridTemplateColumns: `minmax(0, 1fr) 8px minmax(${minOutputPct}%, ${outputPct}%)` }
      }
    >
      <div className="editor-panel">
        <div className="editor-header">
          <div className="editor-title">
            <span>Java Code Editor</span>
          </div>
          <div className="editor-actions">
            <button className="btn btn-secondary" onClick={toggleTheme} title="Toggle theme">
              {theme === 'vs-dark' ? 'Light' : 'Dark'}
            </button>
            <button className="btn btn-secondary" onClick={handleReset} title="Reset to template" disabled={readOnly}>
              Reset
            </button>
            {showFullscreenToggle && (
              <button className="btn btn-secondary" onClick={onToggleFullscreen} title="Toggle full screen">
                {isFullscreen ? 'Exit' : 'Full screen'}
              </button>
            )}
            <button
              className="btn btn-primary"
              onClick={handleRun}
              disabled={state.loading || !canRun}
              title="Run code (Ctrl+Enter)"
            >
              {state.loading ? 'Running...' : 'Run (Ctrl+Enter)'}
            </button>
            {headerRightSlot}
          </div>
        </div>

        <div className="editor-wrapper">
          {emptyStateMessage && (
            <div className="editor-empty-state">
              <p>{emptyStateMessage}</p>
            </div>
          )}
          <MonacoEditor
            height="100%"
            language="java"
            value={state.code}
            onChange={handleCodeChange}
            theme={theme}
            options={{
              readOnly,
              minimap: { enabled: false },
              fontSize: 14,
              lineNumbers: 'on',
              scrollBeyondLastLine: false,
              wordWrap: 'on',
              formatOnPaste: true,
              formatOnType: true,
              autoClosingBrackets: 'always',
              autoClosingQuotes: 'always',
              suggestOnTriggerCharacters: true,
              quickSuggestions: {
                other: true,
                comments: true,
                strings: true,
              },
              snippetSuggestions: 'inline',
            }}
            onMount={(editor, monaco) => {
              editor.addCommand(monoacoSafeKey(monaco), () => runLatestRef.current?.());
              editor.addCommand(monaco.KeyCode.Escape, () => handleClear());
            }}
          />
        </div>
      </div>

      {!isStacked && <ResizeHandle onPointerDown={handleResizePointerDown} />}
      <div className="output-panel" style={{ minWidth: 0 }}>
        <div className="output-header">
          <div className="output-title">
            Output
            <span className="execution-time">
              ({state.executionTime > 0 ? `${state.executionTime}ms` : 'not run'}, {codeKbLabel})
            </span>
          </div>
          <button
            className="btn btn-secondary btn-small"
            onClick={handleClear}
            disabled={!state.output && !state.error}
            title="Clear output"
          >
            Clear (Esc)
          </button>
        </div>

        <Console stdout={state.output} stderr={state.error} />
      </div>
    </div>
  );
};

function monoacoSafeKey(monaco: Parameters<NonNullable<React.ComponentProps<typeof MonacoEditor>['onMount']>>[1]) {
  return monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter;
}

export default Editor;
