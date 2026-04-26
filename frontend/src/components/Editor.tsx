import React, { useCallback, useEffect, useMemo, useState } from 'react';
import MonacoEditor from '@monaco-editor/react';
import { ExecuteRequest, ExecuteResponse, ExecutionLanguage } from '../types/api';
import type { EditableCodeFile } from '../types/session';
import ResizeHandle from './ResizeHandle';
import { compilerApi } from '../services/api';
import './Editor.css';
import Console from './Console';

const JAVA_TEMPLATE_CODE = `import org.junit.Assert;

public class Solution {
    static int add(int a, int b) {
        return a + b;
    }

    public static void main(String[] args) {
        Assert.assertEquals(5, add(2, 3));
        System.out.println("All assertions passed");
    }
}`;

const PYTHON_TEMPLATE_CODE = `def add(a, b):
    return a + b

def main():
    assert add(2, 3) == 5
    print("All assertions passed")

if __name__ == "__main__":
    main()
`;

const ANGULAR_COMPONENT_TS = `import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  title = 'Angular interview sandbox';
}
`;

const ANGULAR_COMPONENT_HTML = `<main class="app-shell">
  <h1>{{ title }}</h1>
  <p>Start building your Angular solution here.</p>
</main>
`;

const ANGULAR_COMPONENT_CSS = `.app-shell {
  display: grid;
  gap: 12px;
  padding: 24px;
  font-family: Arial, sans-serif;
}

h1 {
  margin: 0;
  color: #0f3d59;
}

p {
  margin: 0;
  color: #4f6474;
}
`;

const ANGULAR_PACKAGE_JSON = `{
  "name": "interview-angular-sandbox",
  "version": "0.0.1",
  "private": true,
  "scripts": {
    "build": "ng build"
  },
  "dependencies": {
    "@angular/common": "~21.2.0",
    "@angular/compiler": "~21.2.0",
    "@angular/core": "~21.2.0",
    "@angular/platform-browser": "~21.2.0",
    "rxjs": "^7.8.0",
    "tslib": "^2.8.0",
    "zone.js": "~0.15.0"
  },
  "devDependencies": {
    "@angular/build": "~21.2.0",
    "@angular/cli": "~21.2.0",
    "@angular/compiler-cli": "~21.2.0",
    "typescript": "~5.9.0"
  }
}
`;

const REACT_APP_TSX = `import React from 'react';
import './App.css';

export default function App() {
  return (
    <main className="app-shell">
      <h1>React interview sandbox</h1>
      <p>Start building your React solution here.</p>
    </main>
  );
}
`;

const REACT_APP_CSS = `.app-shell {
  display: grid;
  gap: 12px;
  padding: 24px;
  font-family: Arial, sans-serif;
}

h1 {
  margin: 0;
  color: #0f3d59;
}

p {
  margin: 0;
  color: #4f6474;
}
`;

const REACT_MAIN_TSX = `import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import './index.css';

const container = document.getElementById('root');

if (!container) {
  throw new Error('React root container was not found.');
}

createRoot(container).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
`;

const REACT_PACKAGE_JSON = `{
  "name": "interview-react-sandbox",
  "private": true,
  "version": "0.0.1",
  "type": "module",
  "scripts": {
    "build": "vite build"
  },
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1"
  },
  "devDependencies": {
    "@types/react": "^18.3.12",
    "@types/react-dom": "^18.3.1",
    "@vitejs/plugin-react": "^4.3.1",
    "typescript": "^5.6.3",
    "vite": "^5.4.10"
  }
}
`;

const ANGULAR_MONACO_SHIMS = {
  core: `
declare module '@angular/core' {
  export interface ComponentMetadata {
    selector?: string;
    standalone?: boolean;
    imports?: any[];
    template?: string;
    templateUrl?: string;
    styles?: string[];
    styleUrl?: string;
    styleUrls?: string[];
  }

  export declare function Component(metadata: ComponentMetadata): ClassDecorator;
  export declare function Injectable(metadata?: any): ClassDecorator;
  export declare function Input(alias?: string): PropertyDecorator;
  export declare function Output(alias?: string): PropertyDecorator;

  export declare class EventEmitter<T = any> {
    emit(value?: T): void;
    subscribe(next: (value: T) => void): { unsubscribe(): void };
  }

  export interface OnInit {
    ngOnInit(): void;
  }

  export interface OnDestroy {
    ngOnDestroy(): void;
  }
}
`,
  common: `
declare module '@angular/common' {
  export declare class CommonModule {}
}
`,
  rxjs: `
declare module 'rxjs' {
  export declare class Observable<T> {
    subscribe(next: (value: T) => void): { unsubscribe(): void };
  }
}
  `,
};

const REACT_MONACO_SHIMS = {
  react: `
declare module 'react' {
  export type ReactNode = any;
  export type JSXElementConstructor<P> = (props: P) => ReactNode;
  export const Fragment: any;
  export interface FC<P = {}> {
    (props: P): ReactNode;
  }
  export interface StrictModeProps {
    children?: ReactNode;
  }
  export const StrictMode: FC<StrictModeProps>;
  export function createElement(type: any, props?: any, ...children: any[]): any;
  const React: {
    StrictMode: FC<StrictModeProps>;
    Fragment: any;
    createElement(type: any, props?: any, ...children: any[]): any;
  };
  export default React;
}

declare global {
  namespace JSX {
    type Element = any;
    interface ElementClass {}
    interface ElementAttributesProperty {
      props: {};
    }
    interface ElementChildrenAttribute {
      children: {};
    }
    interface IntrinsicAttributes {
      key?: string | number;
    }
    interface IntrinsicElements {
      [elemName: string]: any;
    }
  }
}
  `,
  reactDomClient: `
declare module 'react-dom/client' {
  export interface Root {
    render(children: any): void;
  }
  export function createRoot(container: Element | DocumentFragment): Root;
}
  `,
  css: `
declare module '*.css' {
  const content: string;
  export default content;
}
`,
};

type MonacoTypeScriptApi = {
  ModuleKind: {
    ESNext: number;
  };
  ModuleResolutionKind: {
    NodeJs: number;
  };
  ScriptTarget: {
    ES2022: number;
  };
    JsxEmit: {
      React: number;
      ReactJSX: number;
    };
  typescriptDefaults: {
    setCompilerOptions(options: Record<string, unknown>): void;
    setDiagnosticsOptions(options: Record<string, unknown>): void;
    addExtraLib(content: string, filePath?: string): IDisposable;
  };
};

type IDisposable = {
  dispose(): void;
};

const SANDBOX_LABELS: Record<ExecutionLanguage, string> = {
  JAVA: 'Sandbox: Eclipse Temurin JDK 17',
  PYTHON: 'Sandbox: CPython 3.12',
  ANGULAR: 'Sandbox: Angular 21 build workspace',
  REACT: 'Sandbox: React 18.3 + Vite 5 build workspace (.tsx, .ts, .css)',
};

const EDITOR_TITLES: Record<ExecutionLanguage, string> = {
  JAVA: 'Java Code Editor',
  PYTHON: 'Python Code Editor',
  ANGULAR: 'Angular Workspace',
  REACT: 'React Workspace',
};

const DEFAULT_TEMPLATES: Record<Exclude<ExecutionLanguage, 'ANGULAR' | 'REACT'>, string> = {
  JAVA: JAVA_TEMPLATE_CODE,
  PYTHON: PYTHON_TEMPLATE_CODE,
};

type AngularWorkspaceFile = EditableCodeFile & {
  readOnlyHint?: string;
};

const DEFAULT_ANGULAR_FILES: AngularWorkspaceFile[] = [
  {
    path: 'src/app/app.component.ts',
    displayName: 'app.component.ts',
    content: ANGULAR_COMPONENT_TS,
    editable: true,
    sortOrder: 0,
  },
  {
    path: 'src/app/app.component.html',
    displayName: 'app.component.html',
    content: ANGULAR_COMPONENT_HTML,
    editable: true,
    sortOrder: 1,
  },
  {
    path: 'src/app/app.component.css',
    displayName: 'app.component.css',
    content: ANGULAR_COMPONENT_CSS,
    editable: true,
    sortOrder: 2,
  },
  {
    path: 'package.json',
    displayName: 'package.json',
    content: ANGULAR_PACKAGE_JSON,
    editable: false,
    sortOrder: 99,
    readOnlyHint: 'Read only',
  },
];

const DEFAULT_REACT_FILES: AngularWorkspaceFile[] = [
  {
    path: 'src/App.tsx',
    displayName: 'App.tsx',
    content: REACT_APP_TSX,
    editable: true,
    sortOrder: 0,
  },
  {
    path: 'src/App.css',
    displayName: 'App.css',
    content: REACT_APP_CSS,
    editable: true,
    sortOrder: 1,
  },
  {
    path: 'src/main.tsx',
    displayName: 'main.tsx',
    content: REACT_MAIN_TSX,
    editable: true,
    sortOrder: 2,
  },
  {
    path: 'package.json',
    displayName: 'package.json',
    content: REACT_PACKAGE_JSON,
    editable: false,
    sortOrder: 99,
    readOnlyHint: 'Read only',
  },
];

interface EditorState {
  code: string;
  output: string;
  error: string;
  loading: boolean;
  executionTime: number;
  previewUrl: string | null;
}

interface FileCreationModalState {
  open: boolean;
  value: string;
  error: string;
}

interface ConfirmationModalState {
  title: string;
  message: string;
  confirmLabel: string;
  tone?: 'normal' | 'danger';
  onConfirm: () => void;
}

interface EditorProps {
  sessionId?: string;
  executionLanguage?: ExecutionLanguage;
  participantRole?: 'interviewer' | 'interviewee' | null;
  readOnly?: boolean;
  initialCode?: string;
  initialCodeFiles?: EditableCodeFile[];
  initialCodeVersion?: number;
  onCodeChange?: (code: string) => void;
  onCodeFilesChange?: (files: EditableCodeFile[]) => void;
  onActiveFileChange?: (path: string) => void;
  onPasteInEditor?: (text: string) => boolean | void;
  onCopyFromEditor?: (text: string) => void;
  onCutFromEditor?: (text: string) => void;
  onExternalDropBlocked?: () => void;
  showResetButton?: boolean;
  canRun?: boolean;
  emptyStateMessage?: string;
  showFullscreenToggle?: boolean;
  isFullscreen?: boolean;
  onToggleFullscreen?: () => void;
  headerRightSlot?: React.ReactNode;
}

const Editor: React.FC<EditorProps> = ({
  sessionId,
  executionLanguage = 'JAVA',
  participantRole,
  readOnly = false,
  initialCode,
  initialCodeFiles,
  initialCodeVersion,
  onCodeChange,
  onCodeFilesChange,
  onActiveFileChange,
  onPasteInEditor,
  onCopyFromEditor,
  onCutFromEditor,
  onExternalDropBlocked,
  showResetButton = true,
  canRun = true,
  emptyStateMessage,
  showFullscreenToggle = false,
  isFullscreen = false,
  onToggleFullscreen,
  headerRightSlot,
}) => {
  const isFrontendWorkspace = executionLanguage === 'ANGULAR' || executionLanguage === 'REACT';
  const isGuidedQuestionWorkspace = executionLanguage === 'JAVA' || executionLanguage === 'PYTHON';
  const isWorkspaceSession = isFrontendWorkspace || isGuidedQuestionWorkspace;
  const canManageQuestionTabs = participantRole === 'interviewer' && isGuidedQuestionWorkspace && !readOnly;
  const canCreateWorkspaceFiles = isFrontendWorkspace ? !readOnly : canManageQuestionTabs;
  const canDeleteWorkspaceFiles = isFrontendWorkspace && !readOnly;
  const defaultTemplate = !isFrontendWorkspace ? DEFAULT_TEMPLATES[executionLanguage] : '';
  const resolvedInitialCode = !isFrontendWorkspace && initialCode && initialCode.trim().length > 0 ? initialCode : defaultTemplate;
  const editorTitle = EDITOR_TITLES[executionLanguage];
  const sandboxLabel = SANDBOX_LABELS[executionLanguage];
  const containerRef = React.useRef<HTMLDivElement | null>(null);
  const editorMountRef = React.useRef<HTMLDivElement | null>(null);
  const defaultOutputPct = 35;
  const minOutputPct = defaultOutputPct * 0.5;
  const defaultEditorPct = 100 - defaultOutputPct;
  const minEditorPct = defaultEditorPct * 0.5;
  const maxOutputPct = 100 - minEditorPct;
  const minOutputPx = 320;

  const [state, setState] = useState<EditorState>({
    code: resolvedInitialCode,
    output: '',
    error: '',
    loading: false,
    executionTime: 0,
    previewUrl: null,
  });
  const [workspaceFiles, setWorkspaceFiles] = useState<AngularWorkspaceFile[]>(() =>
    buildWorkspaceFiles(executionLanguage, initialCodeFiles)
  );
  const [dirtyWorkspacePaths, setDirtyWorkspacePaths] = useState<string[]>([]);
  const [activeFilePath, setActiveFilePath] = useState<string>(() =>
    buildWorkspaceFiles(executionLanguage, initialCodeFiles)[0]?.path ?? defaultWorkspaceFiles(executionLanguage)[0].path
  );
  const [theme, setTheme] = useState<'vs-dark' | 'vs-light'>('vs-dark');
  const [outputPct, setOutputPct] = useState<number>(defaultOutputPct);
  const [isStacked, setIsStacked] = useState(() => window.matchMedia('(max-width: 980px)').matches);
  const [createFileModal, setCreateFileModal] = useState<FileCreationModalState>({
    open: false,
    value: '',
    error: '',
  });
  const [confirmationModal, setConfirmationModal] = useState<ConfirmationModalState | null>(null);
  const runLatestRef = React.useRef<(() => void) | null>(null);
  const angularExtraLibsRef = React.useRef<IDisposable[]>([]);
  const workspaceExtraLibsRef = React.useRef<IDisposable[]>([]);
  const monacoTypeDefaultsRef = React.useRef<MonacoTypeScriptApi['typescriptDefaults'] | null>(null);
  const workspaceFilesRef = React.useRef<AngularWorkspaceFile[]>(buildWorkspaceFiles(executionLanguage, initialCodeFiles));
  const dirtyWorkspacePathsRef = React.useRef<string[]>([]);
  const activeFilePathRef = React.useRef<string>(buildWorkspaceFiles(executionLanguage, initialCodeFiles)[0]?.path ?? defaultWorkspaceFiles(executionLanguage)[0].path);
  const appliedInitialCodeVersionRef = React.useRef(initialCodeVersion ?? 0);
  const previousExecutionLanguageRef = React.useRef(executionLanguage);

  useEffect(() => {
    const languageChanged = previousExecutionLanguageRef.current !== executionLanguage;
    const hasVersion = typeof initialCodeVersion === 'number';
    const shouldApplyInitialCode = languageChanged
      || !hasVersion
      || initialCodeVersion > appliedInitialCodeVersionRef.current;

    if (!shouldApplyInitialCode) {
      return;
    }

    previousExecutionLanguageRef.current = executionLanguage;
    if (hasVersion) {
      appliedInitialCodeVersionRef.current = initialCodeVersion;
    }

    if (!isWorkspaceSession) {
      setState((prev) => ({
        ...prev,
        code: resolvedInitialCode,
      }));
      return;
    }

    const nextFiles = buildWorkspaceFiles(executionLanguage, initialCodeFiles);
    workspaceFilesRef.current = nextFiles;
    dirtyWorkspacePathsRef.current = [];
    setWorkspaceFiles(nextFiles);
    setDirtyWorkspacePaths([]);
    setActiveFilePath((previous) => {
      if (nextFiles.some((file) => file.path === previous)) {
        return previous;
      }
      return nextFiles[0]?.path ?? defaultWorkspaceFiles(executionLanguage)[0].path;
    });
    }, [executionLanguage, initialCodeFiles, initialCodeVersion, isWorkspaceSession, resolvedInitialCode]);

  useEffect(() => {
    workspaceFilesRef.current = workspaceFiles;
  }, [workspaceFiles]);

  useEffect(() => {
    dirtyWorkspacePathsRef.current = dirtyWorkspacePaths;
  }, [dirtyWorkspacePaths]);

  const visibleWorkspaceFiles = useMemo(
    () => isGuidedQuestionWorkspace && participantRole === 'interviewee'
      ? workspaceFiles.filter((file) => file.enabledForCandidate !== false)
      : workspaceFiles,
    [isGuidedQuestionWorkspace, participantRole, workspaceFiles]
  );

  useEffect(() => {
    activeFilePathRef.current = activeFilePath;
    onActiveFileChange?.(activeFilePath);
  }, [activeFilePath, onActiveFileChange]);

  useEffect(() => {
    if (!isGuidedQuestionWorkspace || participantRole !== 'interviewee' || !visibleWorkspaceFiles.length) {
      return;
    }
    const activeQuestion = visibleWorkspaceFiles.find((file) => file.activeQuestion === true && file.submitted !== true);
    if (activeQuestion && activeQuestion.path !== activeFilePath) {
      setActiveFilePath(activeQuestion.path);
      return;
    }
    if (!visibleWorkspaceFiles.some((file) => file.path === activeFilePath)) {
      setActiveFilePath(visibleWorkspaceFiles[0].path);
    }
  }, [activeFilePath, isGuidedQuestionWorkspace, participantRole, visibleWorkspaceFiles]);

  useEffect(() => {
    if ((executionLanguage !== 'ANGULAR' && executionLanguage !== 'REACT') || !monacoTypeDefaultsRef.current) {
      workspaceExtraLibsRef.current.forEach((disposable) => disposable.dispose());
      workspaceExtraLibsRef.current = [];
      return;
    }

    workspaceExtraLibsRef.current.forEach((disposable) => disposable.dispose());
    workspaceExtraLibsRef.current = workspaceFiles
      .filter((file) => file.editable && editableWorkspacePath(executionLanguage, file.path))
      .map((file) => monacoTypeDefaultsRef.current!.addExtraLib(
        file.content,
        `file:///${file.path.replace(/^\/+/, '')}`
      ));
  }, [executionLanguage, workspaceFiles]);

  useEffect(() => {
    const media = window.matchMedia('(max-width: 980px)');
    const handler = () => setIsStacked(media.matches);
    handler();
    if (typeof media.addEventListener === 'function') {
      media.addEventListener('change', handler);
      return () => media.removeEventListener('change', handler);
    }
    media.addListener(handler);
    return () => media.removeListener(handler);
  }, []);

  const activeAngularFile = useMemo(
    () => {
      const candidateFiles = isGuidedQuestionWorkspace && participantRole === 'interviewee'
        ? visibleWorkspaceFiles
        : workspaceFiles;
      return candidateFiles.find((file) => file.path === activeFilePath)
        ?? candidateFiles[0]
        ?? defaultWorkspaceFiles(executionLanguage)[0];
    },
    [activeFilePath, executionLanguage, isGuidedQuestionWorkspace, participantRole, visibleWorkspaceFiles, workspaceFiles]
  );

  const monacoLanguage = useMemo(() => {
    if (!isFrontendWorkspace) {
      return executionLanguage === 'PYTHON' ? 'python' : 'java';
    }
    return fileLanguage(activeAngularFile.path);
  }, [activeAngularFile.path, executionLanguage, isFrontendWorkspace]);

  const activeEditorValue = isWorkspaceSession ? activeAngularFile.content : state.code;
  const activeEditorReadOnly = readOnly || (isWorkspaceSession && (activeAngularFile.editable === false || activeAngularFile.submitted === true));
  const runButtonLabel = isFrontendWorkspace
    ? (state.loading ? 'Building...' : 'Build (Ctrl+Enter)')
    : isGuidedQuestionWorkspace
      ? (state.loading ? 'Running...' : 'Run Active Tab (Ctrl+Enter)')
      : (state.loading ? 'Running...' : 'Run (Ctrl+Enter)');
  const runButtonTitle = isFrontendWorkspace
    ? `Build ${executionLanguage === 'REACT' ? 'React' : 'Angular'} workspace (Ctrl+Enter)`
    : isGuidedQuestionWorkspace
      ? 'Run the currently selected question tab (Ctrl+Enter)'
      : 'Run code (Ctrl+Enter)';
  const canFreezeActiveQuestion = isGuidedQuestionWorkspace
    && participantRole === 'interviewee'
    && !readOnly
    && activeAngularFile.enabledForCandidate !== false
    && activeAngularFile.activeQuestion === true
    && activeAngularFile.submitted !== true;
  const canRunCurrentEditor = canRun
    && (!isGuidedQuestionWorkspace || (activeAngularFile.activeQuestion === true && activeAngularFile.submitted !== true));

  const handleCodeChange = useCallback(
    (value: string | undefined) => {
      if (value === undefined || activeEditorReadOnly) {
        return;
      }

        if (isWorkspaceSession) {
          setWorkspaceFiles((previous) => {
            const nextFiles = previous.map((file) => (
              file.path === activeFilePath
                ? { ...file, content: value }
                : file
            ));
            workspaceFilesRef.current = nextFiles;
            onCodeFilesChange?.(toPersistedWorkspaceFiles(executionLanguage, nextFiles));
            return nextFiles;
          });
          setDirtyWorkspacePaths((previous) => {
            const nextPaths = previous.includes(activeFilePath) ? previous : [...previous, activeFilePath];
            dirtyWorkspacePathsRef.current = nextPaths;
            return nextPaths;
          });
          return;
        }

      setState((prev) => ({ ...prev, code: value }));
      onCodeChange?.(value);
    },
    [activeEditorReadOnly, activeFilePath, executionLanguage, isWorkspaceSession, onCodeChange, onCodeFilesChange]
  );

  const executeActiveGuidedQuestion = useCallback(async (): Promise<ExecuteResponse> => {
    const latestWorkspaceFiles = workspaceFilesRef.current;
    const persistedFiles = toPersistedWorkspaceFiles(executionLanguage, latestWorkspaceFiles);
    const activeFile = persistedFiles.find((file) => file.path === activeFilePathRef.current) ?? persistedFiles[0];
    return compilerApi.execute({
      sourceCode: activeFile?.content || '',
      sessionId,
      language: executionLanguage,
      codeFiles: persistedFiles,
      activeFilePath: activeFile?.path,
      timeoutMs: 5000,
      memoryLimitMb: executionLanguage === 'PYTHON' ? 512 : 512,
    });
  }, [executionLanguage, sessionId]);

  const handleRun = useCallback(async () => {
    if (!canRunCurrentEditor) {
      return;
    }

    setState((prev) => ({
      ...prev,
      loading: true,
      output: '',
      error: '',
      executionTime: 0,
      previewUrl: prev.previewUrl,
    }));

      if (isFrontendWorkspace) {
        try {
          const latestWorkspaceFiles = workspaceFilesRef.current;
          const latestDirtyWorkspacePaths = dirtyWorkspacePathsRef.current;
          const persistedFiles = toPersistedWorkspaceFiles(executionLanguage, latestWorkspaceFiles);
          const changedFiles = latestDirtyWorkspacePaths.length > 0
            ? persistedFiles.filter((file) => latestDirtyWorkspacePaths.includes(file.path))
            : persistedFiles;
          console.info('[frontend-build] request', {
            language: executionLanguage,
            sessionId,
            persistedFileCount: persistedFiles.length,
            changedFileCount: changedFiles.length,
            changedPaths: changedFiles.map((file) => file.path),
            activeFilePath: activeFilePathRef.current,
          });
          const response = await compilerApi.execute({
            sourceCode: resolvePrimaryWorkspaceCode(executionLanguage, persistedFiles),
            sessionId,
            language: executionLanguage,
            codeFiles: changedFiles,
            timeoutMs: 15000,
            memoryLimitMb: 1024,
            livePreviewMode: isFrontendWorkspace,
          });
          console.info('[frontend-build] response', {
            language: executionLanguage,
            success: response.success,
            executionTimeMs: response.executionTimeMs,
            exitCode: response.exitCode,
            stdoutLength: response.stdout?.length ?? 0,
            stderrLength: response.stderr?.length ?? 0,
            compileErrorCount: response.compileErrors?.length ?? 0,
            previewUrl: response.previewUrl,
            message: response.message,
          });

          setState((prev) => ({
            ...prev,
            loading: false,
            output: response.stdout || '',
          error: response.stderr || (response.compileErrors?.join('\n') || ''),
            executionTime: response.executionTimeMs || 0,
            previewUrl: response.previewUrl ? `${response.previewUrl}${response.previewUrl.includes('?') ? '&' : '?'}ts=${Date.now()}` : null,
          }));
          dirtyWorkspacePathsRef.current = [];
          setDirtyWorkspacePaths([]);
        } catch (err) {
        const errorMessage = err instanceof Error ? err.message : 'An error occurred';
        setState((prev) => ({
          ...prev,
          loading: false,
          error: errorMessage,
        }));
      }
      return;
    }

    if (isGuidedQuestionWorkspace) {
      try {
        const response = await executeActiveGuidedQuestion();

        setState((prev) => ({
          ...prev,
          loading: false,
          output: response.stdout || '',
          error: response.stderr || (response.compileErrors?.join('\n') || ''),
          executionTime: response.executionTimeMs || 0,
          previewUrl: null,
        }));
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : 'An error occurred';
        setState((prev) => ({
          ...prev,
          loading: false,
          error: errorMessage,
        }));
      }
      return;
    }

    try {
      const request: ExecuteRequest = {
        sourceCode: state.code,
        language: executionLanguage,
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
        previewUrl: null,
      }));
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'An error occurred';
      setState((prev) => ({
        ...prev,
        loading: false,
        error: errorMessage,
      }));
    }
  }, [canRunCurrentEditor, executeActiveGuidedQuestion, executionLanguage, isFrontendWorkspace, isGuidedQuestionWorkspace, sessionId, state.code]);

  useEffect(() => {
    runLatestRef.current = handleRun;
  }, [handleRun]);

  const freezeActiveQuestion = async () => {
    if (!canFreezeActiveQuestion) {
      return;
    }

    setState((prev) => ({
      ...prev,
      loading: true,
      output: '',
      error: '',
      executionTime: 0,
      previewUrl: null,
    }));

    try {
      const response = await executeActiveGuidedQuestion();
      setState((prev) => ({
        ...prev,
        loading: false,
        output: response.stdout || '',
        error: response.stderr || (response.compileErrors?.join('\n') || ''),
        executionTime: response.executionTimeMs || 0,
        previewUrl: null,
      }));
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Unable to capture the final run for this question.';
      setState((prev) => ({
        ...prev,
        loading: false,
        error: errorMessage,
      }));
    }

    setWorkspaceFiles((previous) => {
      const activeQuestionPath = activeFilePathRef.current;
      const questionFiles = previous.filter((file) => editableWorkspacePath(executionLanguage, file.path));
      const currentIndex = questionFiles.findIndex((file) => file.path === activeQuestionPath);
      const nextQuestion = currentIndex >= 0
        ? questionFiles.slice(currentIndex + 1).find((file) => file.submitted !== true)
        : undefined;
      const nextFiles = previous.map((file) => {
        if (file.path === activeQuestionPath) {
          return {
            ...file,
            activeQuestion: false,
            editable: false,
            enabledForCandidate: true,
            submitted: true,
          };
        }

        if (nextQuestion && file.path === nextQuestion.path) {
          return {
            ...file,
            activeQuestion: true,
            editable: true,
            enabledForCandidate: true,
            submitted: false,
          };
        }

        return {
          ...file,
          activeQuestion: false,
        };
      });
      workspaceFilesRef.current = nextFiles;
      if (nextQuestion) {
        activeFilePathRef.current = nextQuestion.path;
        setActiveFilePath(nextQuestion.path);
      }
      onCodeFilesChange?.(toPersistedWorkspaceFiles(executionLanguage, nextFiles));
      return nextFiles;
    });
  };

  const handleFreezeQuestion = () => {
    if (!canFreezeActiveQuestion) {
      return;
    }

    const hasChangedAfterRun = activeAngularFile.runResult && activeAngularFile.changedAfterLastRun;
    setConfirmationModal({
      title: 'Freeze solution?',
      message: hasChangedAfterRun
        ? 'This solution has changes that were not run after the last edit. Freezing will submit this answer permanently and move to the next prepared question if one exists.'
        : 'Freezing will submit this answer permanently and move to the next prepared question if one exists. This action cannot be undone.',
      confirmLabel: 'Freeze',
      tone: 'danger',
      onConfirm: () => {
        setConfirmationModal(null);
        void freezeActiveQuestion();
      },
    });
  };

  useEffect(() => {
    const mountNode = editorMountRef.current;
    if (!mountNode || !onExternalDropBlocked) {
      return;
    }

    const handleBlockedDrag = (event: DragEvent) => {
      const target = event.target;
      if (!(target instanceof Node) || !mountNode.contains(target)) {
        return;
      }

      event.preventDefault();
      event.stopPropagation();
      if (event.type === 'drop') {
        onExternalDropBlocked();
      }
    };

    window.addEventListener('dragenter', handleBlockedDrag, true);
    window.addEventListener('dragover', handleBlockedDrag, true);
    window.addEventListener('drop', handleBlockedDrag, true);

    return () => {
      window.removeEventListener('dragenter', handleBlockedDrag, true);
      window.removeEventListener('dragover', handleBlockedDrag, true);
      window.removeEventListener('drop', handleBlockedDrag, true);
    };
  }, [onExternalDropBlocked]);

  const handleClear = () => {
    setState((prev) => ({
      ...prev,
      output: '',
      error: '',
      executionTime: 0,
      previewUrl: null,
    }));
  };

  const resetActiveEditor = () => {
    if (isWorkspaceSession) {
      const resetContent = resetContentForWorkspaceFile(executionLanguage, activeAngularFile.path);
      const nextFiles = workspaceFilesRef.current.map((file) => (
        file.path === activeFilePathRef.current
          ? { ...file, content: resetContent }
          : file
      ));
      workspaceFilesRef.current = nextFiles;
      dirtyWorkspacePathsRef.current = dirtyWorkspacePathsRef.current.includes(activeFilePathRef.current)
        ? dirtyWorkspacePathsRef.current
        : [...dirtyWorkspacePathsRef.current, activeFilePathRef.current];
      setWorkspaceFiles(nextFiles);
      setDirtyWorkspacePaths(dirtyWorkspacePathsRef.current);
      onCodeFilesChange?.(toPersistedWorkspaceFiles(executionLanguage, nextFiles));
      setState((prev) => ({
        ...prev,
        output: '',
        error: '',
        executionTime: 0,
        previewUrl: null,
      }));
      return;
    }

    setState((prev) => ({
        ...prev,
        code: defaultTemplate,
        output: '',
        error: '',
        executionTime: 0,
        previewUrl: null,
      }));
    onCodeChange?.(defaultTemplate);
  };

  const handleReset = () => {
    if (isWorkspaceSession && activeEditorReadOnly) {
      return;
    }

    setConfirmationModal({
      title: 'Reset selected tab?',
      message: isWorkspaceSession
        ? `This will replace only "${activeAngularFile.displayName}" with the default ${executionLanguage.toLowerCase()} template. Other tabs will not be changed.`
        : 'This will replace the current editor content with the default template.',
      confirmLabel: 'Reset',
      tone: 'danger',
      onConfirm: () => {
        setConfirmationModal(null);
        resetActiveEditor();
      },
    });
  };

  const handleOpenCreateFileModal = () => {
    setCreateFileModal({
      open: true,
      value: '',
      error: '',
    });
  };

  const handleCloseCreateFileModal = () => {
    setCreateFileModal((previous) => ({
      ...previous,
      open: false,
      error: '',
    }));
  };

  const handleCreateFile = () => {
    const rawName = createFileModal.value;
    const nextQuestionIndex = nextGuidedQuestionIndex(executionLanguage, workspaceFiles);
    const normalizedName = normalizeNewWorkspaceFileName(executionLanguage, rawName, nextQuestionIndex);
    if (!normalizedName) {
      setCreateFileModal((previous) => ({
        ...previous,
        error: 'Enter a file path.',
      }));
      return;
    }

    if (!isAllowedWorkspaceFileName(executionLanguage, normalizedName)) {
      setCreateFileModal((previous) => ({
        ...previous,
        error: workspaceFileHelpText(executionLanguage),
      }));
      return;
    }

    const nextPath = resolveWorkspacePath(executionLanguage, normalizedName);
    if (workspaceFiles.some((file) => file.path === nextPath)) {
      setCreateFileModal((previous) => ({
        ...previous,
        error: 'A file with that name already exists.',
      }));
      return;
    }

    const shouldAutoActivateNewQuestion = isGuidedQuestionWorkspace
      && !workspaceFiles.some((file) => file.activeQuestion === true && file.submitted !== true);

    const nextFile: AngularWorkspaceFile = {
      path: nextPath,
      displayName: isGuidedQuestionWorkspace ? guidedQuestionDisplayName(nextPath, nextQuestionIndex) : normalizedName,
      content: isGuidedQuestionWorkspace ? DEFAULT_TEMPLATES[executionLanguage] : '',
      editable: true,
      sortOrder: nextWorkspaceSortOrder(workspaceFiles),
      enabledForCandidate: shouldAutoActivateNewQuestion,
      activeQuestion: shouldAutoActivateNewQuestion,
      submitted: false,
      idealDurationMinutes: isGuidedQuestionWorkspace ? 10 : undefined,
    };

      setWorkspaceFiles((previous) => {
        const nextFiles = sortWorkspaceFiles([...previous.filter((file) => file.path !== 'package.json'), nextFile, ...previous.filter((file) => file.path === 'package.json')]);
        workspaceFilesRef.current = nextFiles;
        onCodeFilesChange?.(toPersistedWorkspaceFiles(executionLanguage, nextFiles));
        return nextFiles;
      });
    setDirtyWorkspacePaths((previous) => {
      const nextPaths = previous.includes(nextPath) ? previous : [...previous, nextPath];
      dirtyWorkspacePathsRef.current = nextPaths;
      return nextPaths;
    });
    setActiveFilePath(nextPath);
    handleCloseCreateFileModal();
  };

  const handleDeleteFile = (path: string) => {
    if (isGuidedQuestionWorkspace) {
      const targetFile = workspaceFilesRef.current.find((file) => file.path === path);
      if (!canManageQuestionTabs || !targetFile || !isPreparedGuidedQuestionTab(targetFile)) {
        return;
      }
    } else if (!isDeletableWorkspaceFile(executionLanguage, path)) {
      return;
    }

    setWorkspaceFiles((previous) => {
      const nextFiles = previous.filter((file) => file.path !== path);
      workspaceFilesRef.current = nextFiles;
      onCodeFilesChange?.(toPersistedWorkspaceFiles(executionLanguage, nextFiles));
      return nextFiles;
    });
    setDirtyWorkspacePaths((previous) => {
      const nextPaths = previous.includes(path)
        ? previous.filter((entry) => entry !== path)
        : previous;
      dirtyWorkspacePathsRef.current = nextPaths;
      return nextPaths;
    });
    setActiveFilePath((previous) => {
      if (previous !== path) {
        return previous;
      }
      const remainingFiles = workspaceFilesRef.current.filter((file) => file.path !== 'package.json');
      return remainingFiles[0]?.path ?? workspaceFilesRef.current[0]?.path ?? previous;
    });
  };

  const toggleTheme = () => {
    setTheme((prev) => (prev === 'vs-dark' ? 'vs-light' : 'vs-dark'));
  };

  const handleResizePointerDown = () => {
    const node = containerRef.current;
    if (!node || isStacked) return;

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

  const workspaceBytes = isWorkspaceSession
    ? workspaceFiles.reduce((total, file) => total + new Blob([file.content]).size, 0)
    : new Blob([state.code]).size;
  const codeKbLabel = `${(workspaceBytes / 1024).toFixed(workspaceBytes / 1024 >= 10 ? 0 : 1)} KB`;
  const monacoModelPath = isWorkspaceSession
    ? `file:///${activeAngularFile.path.replace(/^\/+/, '')}`
    : executionLanguage === 'PYTHON'
      ? 'file:///main.py'
      : 'file:///Solution.java';

  useEffect(() => () => {
    angularExtraLibsRef.current.forEach((disposable) => disposable.dispose());
    angularExtraLibsRef.current = [];
    workspaceExtraLibsRef.current.forEach((disposable) => disposable.dispose());
    workspaceExtraLibsRef.current = [];
  }, []);

  return (
    <div
      className={`editor-container ${isFullscreen ? 'editor-fullscreen' : ''}`}
      ref={containerRef}
      style={
        isStacked
          ? undefined
          : {
              gridTemplateColumns: `minmax(${minEditorPct}%, ${100 - outputPct}%) 8px minmax(${minOutputPct}%, ${outputPct}%)`,
            }
      }
    >
      <div className="editor-panel">
        <div className="editor-header">
          <div className="editor-title">
            <span>{editorTitle}</span>
            <span className="editor-sandbox-label">{sandboxLabel}</span>
          </div>
          <div className="editor-actions">
            <button className="btn btn-secondary" onClick={toggleTheme} title="Toggle theme">
              {theme === 'vs-dark' ? 'Light' : 'Dark'}
            </button>
            {showResetButton && (
              <button className="btn btn-secondary" onClick={handleReset} title="Reset selected tab" disabled={readOnly || activeEditorReadOnly}>
                Reset
              </button>
            )}
            {showFullscreenToggle && (
              <button
                className="btn btn-secondary fullscreen-toggle"
                onClick={onToggleFullscreen}
                title="Toggle full screen (Ctrl+Shift+F)"
              >
                <span className={`fullscreen-icon ${isFullscreen ? 'is-active' : ''}`} aria-hidden="true">
                  <span className="fullscreen-icon-corner top-left" />
                  <span className="fullscreen-icon-corner top-right" />
                  <span className="fullscreen-icon-corner bottom-left" />
                  <span className="fullscreen-icon-corner bottom-right" />
                </span>
                <span className="fullscreen-shortcut">Ctrl+Shift+F</span>
              </button>
            )}
            <button
              className="btn btn-primary"
              onClick={handleRun}
              disabled={state.loading || !canRunCurrentEditor}
              title={runButtonTitle}
            >
              {runButtonLabel}
            </button>
            {headerRightSlot}
          </div>
        </div>

        {isWorkspaceSession && (
          <div className="workspace-tabs-bar">
            <div className="workspace-tabs" role="tablist" aria-label={`${executionLanguage} workspace files`}>
              {visibleWorkspaceFiles.map((file) => {
                const isActive = file.path === activeFilePath;
                return (
                  <button
                    key={file.path}
                    type="button"
                    role="tab"
                    aria-selected={isActive}
                    className={`workspace-tab ${isActive ? 'is-active' : ''} ${file.editable ? '' : 'is-readonly'}`}
                    onClick={() => setActiveFilePath(file.path)}
                  >
                    <span>{file.displayName}</span>
                    {isGuidedQuestionWorkspace && file.activeQuestion ? <span className="workspace-tab-meta">Active</span> : null}
                    {isGuidedQuestionWorkspace && file.submitted ? <span className="workspace-tab-meta">Submitted</span> : null}
                    {isGuidedQuestionWorkspace && file.enabledForCandidate === false ? <span className="workspace-tab-meta">Prepared</span> : null}
                    {isGuidedQuestionWorkspace && file.runResult ? <span className="workspace-tab-meta">{file.changedAfterLastRun ? 'Changed' : 'Ran'}</span> : null}
                    {!file.editable && <span className="workspace-tab-meta">{file.readOnlyHint || 'Read only'}</span>}
                    {isGuidedQuestionWorkspace && participantRole === 'interviewee' && file.path === activeFilePath && canFreezeActiveQuestion ? (
                      <span
                        className="workspace-tab-action"
                        role="button"
                        aria-label={`Freeze ${file.displayName}`}
                        title="Freeze this solution and submit it for review"
                        onClick={(event) => {
                          event.preventDefault();
                          event.stopPropagation();
                          handleFreezeQuestion();
                        }}
                      >
                        Freeze
                      </span>
                    ) : null}
                    {((isGuidedQuestionWorkspace && canManageQuestionTabs && isPreparedGuidedQuestionTab(file))
                      || (!isGuidedQuestionWorkspace && isDeletableWorkspaceFile(executionLanguage, file.path) && canDeleteWorkspaceFiles)) ? (
                      <span
                        className="workspace-tab-delete"
                        role="button"
                        aria-label={`Delete ${file.displayName}`}
                        onClick={(event) => {
                          event.preventDefault();
                          event.stopPropagation();
                          handleDeleteFile(file.path);
                        }}
                      >
                        ×
                      </span>
                    ) : null}
                  </button>
                );
              })}
            </div>
            {canCreateWorkspaceFiles ? (
              <button
                type="button"
                className="workspace-add-button"
                aria-label={isGuidedQuestionWorkspace ? 'Add question' : 'Add file'}
                title={isGuidedQuestionWorkspace ? 'Add question' : 'Add file'}
                onClick={handleOpenCreateFileModal}
              >
                +
              </button>
            ) : null}
          </div>
        )}

        <div className="editor-wrapper">
          {emptyStateMessage && (
            <div className="editor-empty-state">
              <p>{emptyStateMessage}</p>
            </div>
          )}
          <div
            ref={editorMountRef}
            className="editor-mount"
            onDragEnterCapture={(event) => {
              event.preventDefault();
              event.stopPropagation();
            }}
            onDropCapture={(event) => {
              event.preventDefault();
              event.stopPropagation();
              onExternalDropBlocked?.();
            }}
            onDragOverCapture={(event) => {
              event.preventDefault();
              event.stopPropagation();
            }}
            onPasteCapture={(event) => {
              const pastedText = event.clipboardData?.getData('text') ?? '';
              if (pastedText && !activeEditorReadOnly) {
                const allowPaste = onPasteInEditor?.(pastedText);
                if (allowPaste === false) {
                  event.preventDefault();
                  event.stopPropagation();
                }
              }
            }}
          >
            <MonacoEditor
              key={monacoModelPath}
              height="100%"
              language={monacoLanguage}
              path={monacoModelPath}
              value={activeEditorValue}
              onChange={handleCodeChange}
              theme={theme}
              beforeMount={(monaco) => {
                if (executionLanguage !== 'ANGULAR' && executionLanguage !== 'REACT') {
                  monacoTypeDefaultsRef.current = null;
                  return;
                }

                const tsApi = (monaco.languages as unknown as { typescript?: MonacoTypeScriptApi }).typescript;
                if (!tsApi) {
                  console.warn(`[${executionLanguage.toLowerCase()}-monaco] TypeScript API unavailable on monaco.languages`);
                  return;
                }

                const defaults = tsApi.typescriptDefaults as unknown as {
                  setCompilerOptions?: (options: Record<string, unknown>) => void;
                  setDiagnosticsOptions?: (options: Record<string, unknown>) => void;
                  addExtraLib?: (content: string, filePath?: string) => IDisposable;
                };
                console.info(`[${executionLanguage.toLowerCase()}-monaco] defaults capabilities`, {
                  hasTypescriptApi: Boolean(tsApi),
                  hasCompilerOptions: typeof defaults?.setCompilerOptions === 'function',
                  hasDiagnosticsOptions: typeof defaults?.setDiagnosticsOptions === 'function',
                  hasAddExtraLib: typeof defaults?.addExtraLib === 'function',
                  languageKeys: Object.keys((monaco.languages as Record<string, unknown>) || {}),
                });

                if (
                  typeof defaults?.setCompilerOptions !== 'function' ||
                  typeof defaults?.setDiagnosticsOptions !== 'function' ||
                  typeof defaults?.addExtraLib !== 'function'
                ) {
                  console.warn(`[${executionLanguage.toLowerCase()}-monaco] Skipping Monaco typing setup because required defaults APIs are unavailable`);
                  return;
                }

                defaults.setCompilerOptions(
                  executionLanguage === 'ANGULAR'
                    ? {
                        target: tsApi.ScriptTarget.ES2022,
                        allowNonTsExtensions: true,
                        module: tsApi.ModuleKind.ESNext,
                        moduleResolution: tsApi.ModuleResolutionKind.NodeJs,
                        experimentalDecorators: true,
                        useDefineForClassFields: false,
                        noEmit: true,
                        strict: false,
                        baseUrl: 'file:///',
                        paths: {
                          '@angular/core': ['file:///node_modules/@angular/core/index.d.ts'],
                          '@angular/common': ['file:///node_modules/@angular/common/index.d.ts'],
                          rxjs: ['file:///node_modules/rxjs/index.d.ts'],
                        },
                      }
                    : {
                        target: tsApi.ScriptTarget.ES2022,
                        allowNonTsExtensions: true,
                        module: tsApi.ModuleKind.ESNext,
                        moduleResolution: tsApi.ModuleResolutionKind.NodeJs,
                        noEmit: true,
                        strict: false,
                        jsx: tsApi.JsxEmit.React,
                        allowSyntheticDefaultImports: true,
                        esModuleInterop: true,
                        baseUrl: 'file:///',
                        paths: {
                          react: ['file:///node_modules/react/index.d.ts'],
                          'react-dom/client': ['file:///node_modules/react-dom/client.d.ts'],
                        },
                      }
                );
                defaults.setDiagnosticsOptions({
                  noSemanticValidation: false,
                  noSyntaxValidation: false,
                  noSuggestionDiagnostics: false,
                });
                monacoTypeDefaultsRef.current = defaults as MonacoTypeScriptApi['typescriptDefaults'];
                const addExtraLib = defaults.addExtraLib;

                angularExtraLibsRef.current.forEach((disposable) => disposable.dispose());
                angularExtraLibsRef.current = executionLanguage === 'ANGULAR'
                  ? [
                      addExtraLib.call(
                        defaults,
                        ANGULAR_MONACO_SHIMS.core,
                        'file:///node_modules/@angular/core/index.d.ts'
                      ),
                      addExtraLib.call(
                        defaults,
                        ANGULAR_MONACO_SHIMS.common,
                        'file:///node_modules/@angular/common/index.d.ts'
                      ),
                      addExtraLib.call(
                        defaults,
                        ANGULAR_MONACO_SHIMS.rxjs,
                        'file:///node_modules/rxjs/index.d.ts'
                      ),
                    ]
                  : [
                        addExtraLib.call(
                          defaults,
                          REACT_MONACO_SHIMS.react,
                          'file:///node_modules/react/index.d.ts'
                        ),
                        addExtraLib.call(
                          defaults,
                          REACT_MONACO_SHIMS.reactDomClient,
                          'file:///node_modules/react-dom/client.d.ts'
                      ),
                      addExtraLib.call(
                        defaults,
                        REACT_MONACO_SHIMS.css,
                        'file:///node_modules/css/index.d.ts'
                      ),
                    ];
                workspaceExtraLibsRef.current.forEach((disposable) => disposable.dispose());
                workspaceExtraLibsRef.current = workspaceFilesRef.current
                  .filter((file) => file.editable && editableWorkspacePath(executionLanguage, file.path))
                  .map((file) => addExtraLib.call(
                    defaults,
                    file.content,
                    `file:///${file.path.replace(/^\/+/, '')}`
                  ));
              }}
              options={{
                readOnly: activeEditorReadOnly,
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
                editor.addCommand(monacoSafeKey(monaco), () => runLatestRef.current?.());
                editor.addCommand(monaco.KeyCode.Escape, () => handleClear());

                const getSelectedEditorText = () => {
                  const selection = editor.getSelection();
                  const model = editor.getModel();
                  if (!selection || !model || selection.isEmpty()) {
                    return '';
                  }
                  return model.getValueInRange(selection);
                };

                const domNode = editor.getDomNode();
                if (domNode) {
                  domNode.addEventListener('copy', () => {
                    const copiedText = getSelectedEditorText();
                    if (copiedText) {
                      onCopyFromEditor?.(copiedText);
                    }
                  }, true);
                  domNode.addEventListener('cut', () => {
                    const cutText = getSelectedEditorText();
                    if (cutText) {
                      onCutFromEditor?.(cutText);
                    }
                  }, true);
                  domNode.addEventListener('dragover', (event: DragEvent) => {
                    event.preventDefault();
                    event.stopPropagation();
                  }, true);
                  domNode.addEventListener('dragenter', (event: DragEvent) => {
                    event.preventDefault();
                    event.stopPropagation();
                  }, true);
                  domNode.addEventListener('drop', (event: DragEvent) => {
                    event.preventDefault();
                    event.stopPropagation();
                    onExternalDropBlocked?.();
                  }, true);
                }
              }}
            />
          </div>
        </div>
      </div>

      {!isStacked && <ResizeHandle onPointerDown={handleResizePointerDown} />}
      <div className="output-panel" style={{ minWidth: 0 }}>
        <div className="output-header">
        <div className="output-title">
            {isFrontendWorkspace ? 'Output & Preview' : 'Output'}
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
        <Console stdout={state.output} stderr={state.error} previewUrl={state.previewUrl} />
      </div>

      {createFileModal.open && (
        <div className="workspace-modal-backdrop" role="presentation" onClick={handleCloseCreateFileModal}>
          <div
            className="workspace-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="workspace-modal-title"
            onClick={(event) => event.stopPropagation()}
          >
            <h3 id="workspace-modal-title">{isGuidedQuestionWorkspace ? 'Create New Question' : 'Create New File'}</h3>
            <p className="workspace-modal-copy">
                {workspaceFileHelpText(executionLanguage)}
              </p>
            <input
              className="workspace-modal-input"
              autoFocus
              value={createFileModal.value}
              onChange={(event) => setCreateFileModal((previous) => ({
                ...previous,
                value: event.target.value,
                error: '',
              }))}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  event.preventDefault();
                  handleCreateFile();
                }
                if (event.key === 'Escape') {
                  event.preventDefault();
                  handleCloseCreateFileModal();
                }
              }}
              placeholder={workspaceFilePlaceholder(executionLanguage, nextGuidedQuestionIndex(executionLanguage, workspaceFiles))}
            />
            {createFileModal.error ? <div className="workspace-modal-error">{createFileModal.error}</div> : null}
            <div className="workspace-modal-actions">
              <button type="button" className="btn btn-secondary" onClick={handleCloseCreateFileModal}>
                Cancel
              </button>
              <button type="button" className="btn btn-primary" onClick={handleCreateFile}>
                Create
              </button>
            </div>
          </div>
        </div>
      )}

      {confirmationModal && (
        <ConfirmationModal
          modal={confirmationModal}
          onCancel={() => setConfirmationModal(null)}
        />
      )}
    </div>
  );
};

function ConfirmationModal({
  modal,
  onCancel,
}: {
  modal: ConfirmationModalState;
  onCancel: () => void;
}) {
  return (
    <div className="workspace-modal-backdrop" role="presentation">
      <div
        className="workspace-modal confirmation-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirmation-modal-title"
      >
        <h3 id="confirmation-modal-title">{modal.title}</h3>
        <p className="workspace-modal-copy">{modal.message}</p>
        <div className="workspace-modal-actions">
          <button type="button" className="btn btn-secondary" onClick={onCancel}>
            Cancel
          </button>
          <button
            type="button"
            className={modal.tone === 'danger' ? 'btn btn-danger' : 'btn btn-primary'}
            onClick={modal.onConfirm}
          >
            {modal.confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

function monacoSafeKey(monaco: Parameters<NonNullable<React.ComponentProps<typeof MonacoEditor>['onMount']>>[1]) {
  return monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter;
}

function buildWorkspaceFiles(executionLanguage: ExecutionLanguage, initialCodeFiles?: EditableCodeFile[]) {
  const defaults = defaultWorkspaceFiles(executionLanguage);
  const incomingFiles = initialCodeFiles && initialCodeFiles.length > 0
    ? initialCodeFiles.map((file) => ({ ...file }))
    : defaults.filter((file) => file.path !== 'package.json').map((file) => ({ ...file }));

  const hasPackageJson = incomingFiles.some((file) => file.path === 'package.json');
  const hasActiveQuestion = incomingFiles.some((file) => file.activeQuestion === true);
  const normalized = incomingFiles.map((file, index) => ({
    ...file,
    displayName: file.displayName || basename(file.path),
    editable: file.path === 'package.json' ? false : file.editable !== false,
    sortOrder: typeof file.sortOrder === 'number' ? file.sortOrder : nextWorkspaceSortOrder(incomingFiles),
    readOnlyHint: file.path === 'package.json' ? 'Read only' : undefined,
    enabledForCandidate: file.enabledForCandidate !== false,
    activeQuestion: file.activeQuestion === true || (!hasActiveQuestion && index === 0 && file.path !== 'package.json'),
    submitted: file.submitted === true,
  }));

  if ((executionLanguage === 'ANGULAR' || executionLanguage === 'REACT') && !hasPackageJson) {
    const defaultPackageFile = defaults.find((file) => file.path === 'package.json')!;
    normalized.push({
      path: defaultPackageFile.path,
      displayName: 'package.json',
      content: defaultPackageFile.content,
      editable: false,
      sortOrder: defaultPackageFile.sortOrder,
      readOnlyHint: 'Read only',
      enabledForCandidate: true,
      activeQuestion: false,
      submitted: false,
    });
  }

  return sortWorkspaceFiles(normalized);
}

function defaultWorkspaceFiles(executionLanguage: ExecutionLanguage) {
  if (executionLanguage === 'REACT') return DEFAULT_REACT_FILES;
  if (executionLanguage === 'ANGULAR') return DEFAULT_ANGULAR_FILES;
  if (executionLanguage === 'PYTHON') {
    return [{
      path: 'question-1.py',
      displayName: 'Question 1',
      content: DEFAULT_TEMPLATES.PYTHON,
      editable: true,
      sortOrder: 0,
      enabledForCandidate: true,
      activeQuestion: true,
      submitted: false,
      idealDurationMinutes: 10,
    }];
  }
  return [{
    path: 'Question1.java',
    displayName: 'Question 1',
    content: DEFAULT_TEMPLATES.JAVA,
    editable: true,
    sortOrder: 0,
    enabledForCandidate: true,
    activeQuestion: true,
    submitted: false,
    idealDurationMinutes: 10,
  }];
}

function resetContentForWorkspaceFile(executionLanguage: ExecutionLanguage, path: string) {
  if (executionLanguage === 'JAVA') return DEFAULT_TEMPLATES.JAVA;
  if (executionLanguage === 'PYTHON') return DEFAULT_TEMPLATES.PYTHON;
  return defaultWorkspaceFiles(executionLanguage).find((file) => file.path === path)?.content || '';
}

function isDeletableWorkspaceFile(executionLanguage: ExecutionLanguage, path: string) {
  return !defaultWorkspaceFiles(executionLanguage).some((file) => file.path === path);
}

function isPreparedGuidedQuestionTab(file: Pick<EditableCodeFile, 'enabledForCandidate' | 'activeQuestion' | 'submitted'>) {
  return file.enabledForCandidate === false && file.activeQuestion !== true && file.submitted !== true;
}

function sortWorkspaceFiles(files: AngularWorkspaceFile[]) {
  return [...files].sort((left, right) => {
    if (left.path === 'package.json') return -1;
    if (right.path === 'package.json') return 1;
    return (left.sortOrder ?? 0) - (right.sortOrder ?? 0);
  });
}

function nextWorkspaceSortOrder(files: Array<Pick<EditableCodeFile, 'sortOrder'>>) {
  return files.reduce((highest, file) => Math.max(highest, file.sortOrder ?? 0), 0) + 1;
}

function stripWorkspaceHints(file: AngularWorkspaceFile): EditableCodeFile {
  return {
    path: file.path,
    displayName: file.displayName,
    content: file.content,
    editable: file.editable,
    sortOrder: file.sortOrder,
    enabledForCandidate: file.enabledForCandidate,
    activeQuestion: file.activeQuestion,
    submitted: file.submitted,
    idealDurationMinutes: file.idealDurationMinutes,
  };
}

function toPersistedWorkspaceFiles(executionLanguage: ExecutionLanguage, files: AngularWorkspaceFile[]) {
  return files
    .filter((file) => editableWorkspacePath(executionLanguage, file.path))
    .map(stripWorkspaceHints);
}

function editableWorkspacePath(executionLanguage: ExecutionLanguage, path: string) {
  if (executionLanguage === 'REACT') return path.startsWith('src/');
  if (executionLanguage === 'ANGULAR') return path.startsWith('src/app/');
  if (executionLanguage === 'PYTHON') return path.endsWith('.py');
  return path.endsWith('.java');
}

function resolvePrimaryWorkspaceCode(executionLanguage: ExecutionLanguage, files: EditableCodeFile[]) {
  const primaryPath = executionLanguage === 'REACT'
    ? 'src/App.tsx'
    : executionLanguage === 'ANGULAR'
      ? 'src/app/app.component.ts'
      : undefined;
  return (primaryPath ? files.find((file) => file.path === primaryPath)?.content : undefined)
    || files.find((file) => file.activeQuestion)?.content
    || files.find((file) => file.editable)?.content
    || '';
}

function basename(path: string) {
  const normalized = path.replace(/\\/g, '/');
  const segments = normalized.split('/');
  return segments[segments.length - 1] || normalized;
}

function fileLanguage(path: string) {
  if (path.endsWith('.html')) return 'html';
  if (path.endsWith('.css')) return 'css';
  if (path.endsWith('.json')) return 'json';
  if (path.endsWith('.py')) return 'python';
  if (path.endsWith('.java')) return 'java';
  return 'typescript';
}

function normalizeNewWorkspaceFileName(executionLanguage: ExecutionLanguage, rawName: string, fileCount: number) {
  const trimmed = rawName.trim();
  if (executionLanguage === 'REACT') return trimmed.replace(/^src\//, '');
  if (executionLanguage === 'ANGULAR') return trimmed.replace(/^src\/app\//, '');
  if (executionLanguage === 'PYTHON') return trimmed || `question-${fileCount}.py`;
  return trimmed || `Question${fileCount}.java`;
}

function isAllowedWorkspaceFileName(executionLanguage: ExecutionLanguage, name: string) {
  if (executionLanguage === 'REACT') return /\.(tsx|ts|css)$/i.test(name);
  if (executionLanguage === 'ANGULAR') return /\.(ts|html|css)$/i.test(name);
  if (executionLanguage === 'PYTHON') return /\.py$/i.test(name);
  return /\.java$/i.test(name);
}

function resolveWorkspacePath(executionLanguage: ExecutionLanguage, name: string) {
  if (executionLanguage === 'REACT') return `src/${name}`;
  if (executionLanguage === 'ANGULAR') return `src/app/${name}`;
  return name;
}

function workspaceFileHelpText(executionLanguage: ExecutionLanguage) {
  if (executionLanguage === 'REACT') {
    return 'Add a file inside src. React sandbox supports only .tsx, .ts, and .css files, for example components/SimpleCard.tsx.';
  }
  if (executionLanguage === 'ANGULAR') {
    return 'Add a file inside src/app, for example simple-card.component.ts.';
  }
  if (executionLanguage === 'PYTHON') {
    return 'Add an interviewer-controlled Python question tab. Leave blank to create the next question automatically, or enter a .py file name.';
  }
  return 'Add an interviewer-controlled Java question tab. Leave blank to create the next question automatically, or enter a .java file name.';
}

function workspaceFilePlaceholder(executionLanguage: ExecutionLanguage, nextIndex: number) {
  if (executionLanguage === 'REACT') return 'components/SimpleCard.tsx';
  if (executionLanguage === 'ANGULAR') return 'simple-card.component.ts';
  if (executionLanguage === 'PYTHON') return `question-${nextIndex}.py`;
  return `Question${nextIndex}.java`;
}

function nextGuidedQuestionIndex(executionLanguage: ExecutionLanguage, files: AngularWorkspaceFile[]) {
  if (executionLanguage !== 'JAVA' && executionLanguage !== 'PYTHON') {
    return files.length + 1;
  }

  return files
    .filter((file) => editableWorkspacePath(executionLanguage, file.path))
    .reduce((highest, file, index) => Math.max(highest, guidedQuestionNumber(file.path, index + 1)), 0) + 1;
}

function guidedQuestionDisplayName(path: string, fallbackIndex: number) {
  return `Question ${guidedQuestionNumber(path, fallbackIndex)}`;
}

function guidedQuestionNumber(path: string, fallbackIndex: number) {
  const match = basename(path).match(/(\d+)/);
  return match ? Number(match[1]) : fallbackIndex;
}

export default Editor;
