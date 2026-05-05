import React, { useEffect, useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Button } from '../components/Button';
import { sessionApi } from '../services/sessionApi';
import type { AvMode, CreateSessionRequest, EvaluationStyle, InterviewMode, TechnologySkill } from '../types/session';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { getBrowserTimeZone } from '../utils/dateTime';
import './StartInterview.css';

interface FormData extends CreateSessionRequest {}

const TARGET_ROLES_BY_TECHNOLOGY: Record<TechnologySkill, string[]> = {
  JAVA: [
    'Junior Java Developer',
    'Java Developer',
    'Senior Java Developer',
    'Backend Java Engineer',
    'Spring Boot Developer',
    'Java Microservices Engineer',
    'Lead Java Engineer',
    'Java Technical Architect',
  ],
  PYTHON: [
    'Junior Python Developer',
    'Python Developer',
    'Senior Python Developer',
    'Backend Python Engineer',
    'Django Developer',
    'FastAPI Developer',
    'Python Automation Engineer',
    'Lead Python Engineer',
  ],
  ANGULAR: [
    'Junior Angular Developer',
    'Angular Developer',
    'Senior Angular Developer',
    'Frontend Angular Engineer',
    'Angular UI Engineer',
    'Angular Enterprise Developer',
    'Lead Angular Engineer',
  ],
  REACT: [
    'Junior React Developer',
    'React Developer',
    'Senior React Developer',
    'Frontend React Engineer',
    'React UI Engineer',
    'React TypeScript Developer',
    'Lead React Engineer',
  ],
  SQL: [
    'SQL Developer',
    'Database Developer',
    'Data Analyst',
    'Data Engineer',
    'Backend SQL Engineer',
    'Senior SQL Developer',
  ],
};

const StartInterview: React.FC = () => {
  const [searchParams] = useSearchParams();
  const technology = (searchParams.get('technology') as TechnologySkill | null) ?? 'JAVA';
  const [formData, setFormData] = useState<FormData>({
    interviewerName: '',
    interviewerEmail: '',
    intervieweeName: '',
    intervieweeEmail: '',
    interviewerTimeZone: getBrowserTimeZone(),
    technology,
    avMode: 'EXTERNAL',
    interviewMode: 'HUMAN_INTERVIEWER',
    evaluationStyle: 'STANDARD_MULTIPLE_QUESTIONS',
    yearsOfExperience: 0,
    targetRole: '',
    startingDifficultyLevel: 1,
    maxQuestions: 5,
  });
  const [registrationStep, setRegistrationStep] = useState<1 | 2 | 3>(1);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const targetRoleOptions = useMemo(() => TARGET_ROLES_BY_TECHNOLOGY[technology] || TARGET_ROLES_BY_TECHNOLOGY.JAVA, [technology]);
  const supportsEvaluationStyle = technology === 'JAVA' || technology === 'PYTHON';

  useEffect(() => {
    if (!formData.targetRole || !targetRoleOptions.includes(formData.targetRole)) {
      setFormData((previous) => ({
        ...previous,
        targetRole: targetRoleOptions[0] || '',
      }));
    }
  }, [formData.targetRole, targetRoleOptions]);

  useEffect(() => {
    if (!supportsEvaluationStyle && formData.evaluationStyle !== 'STANDARD_MULTIPLE_QUESTIONS') {
      setFormData((previous) => ({
        ...previous,
        evaluationStyle: 'STANDARD_MULTIPLE_QUESTIONS',
      }));
    }
  }, [formData.evaluationStyle, supportsEvaluationStyle]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value
    });
  };

  const handleInterviewModeChange = (interviewMode: InterviewMode) => {
    setFormData((previous) => ({
      ...previous,
      interviewMode,
      avMode: interviewMode === 'AI_INTERVIEWER' ? 'EXTERNAL' : previous.avMode,
    }));
  };

  const handleAvModeChange = (avMode: AvMode) => {
    setFormData((previous) => ({
      ...previous,
      avMode,
    }));
  };

  const handleEvaluationStyleChange = (evaluationStyle: EvaluationStyle) => {
    setFormData((previous) => ({
      ...previous,
      evaluationStyle,
    }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
      e.preventDefault();
      try {
        const submittedForm = new FormData(e.currentTarget as HTMLFormElement);
        const avMode = (submittedForm.get('avMode') as AvMode | null) ?? formData.avMode;
        const interviewMode = (submittedForm.get('interviewMode') as InterviewMode | null) ?? formData.interviewMode ?? 'HUMAN_INTERVIEWER';
        const request: CreateSessionRequest = {
          ...formData,
          interviewMode,
          interviewerName: interviewMode === 'AI_INTERVIEWER' ? 'AI Interviewer' : formData.interviewerName,
          interviewerEmail: interviewMode === 'AI_INTERVIEWER' ? 'ai-interviewer@interview.local' : formData.interviewerEmail,
          interviewerTimeZone: interviewMode === 'AI_INTERVIEWER' ? undefined : formData.interviewerTimeZone,
          avMode: interviewMode === 'AI_INTERVIEWER' ? 'EXTERNAL' : avMode,
          evaluationStyle: supportsEvaluationStyle ? formData.evaluationStyle ?? 'STANDARD_MULTIPLE_QUESTIONS' : 'STANDARD_MULTIPLE_QUESTIONS',
          yearsOfExperience: Number(formData.yearsOfExperience ?? 0),
          targetRole: formData.targetRole?.trim(),
          startingDifficultyLevel: Number(formData.startingDifficultyLevel ?? 1),
          maxQuestions: Number(formData.maxQuestions ?? 5),
        };
        await sessionApi.createSession(request);
        await queryClient.invalidateQueries({ queryKey: ['sessions'] });
        navigate('/', {
          replace: true,
          state: {
            registrationCreated: true,
          },
        });
      } catch (error) {
        console.error('Failed to create session:', error);
        alert('Failed to register interview');
      }
    };

  return (
    <div className="page-shell">
      <div className="page-card form-card registration-card">
      <div className="page-kicker">Register Interview</div>
      <form onSubmit={handleSubmit} className={`stack-form start-interview-form registration-step-${registrationStep}`} autoComplete="off">
        <input type="text" name="ghostUser" autoComplete="username" tabIndex={-1} aria-hidden="true" className="sr-only-input" />
        <input type="password" name="ghostPassword" autoComplete="new-password" tabIndex={-1} aria-hidden="true" className="sr-only-input" />
        {registrationStep === 1 ? (
          <>
          <div className="registration-section form-group-full">
            <div>
              <span className="registration-section-kicker">Interview Mode</span>
            </div>
            <div className="registration-mode-layout">
            <div className="mode-toggle-options" role="radiogroup" aria-label="Interview mode">
              <label className={`av-mode-option ${formData.interviewMode === 'HUMAN_INTERVIEWER' ? 'selected' : ''}`}>
                <input
                  type="radio"
                  name="interviewMode"
                  value="HUMAN_INTERVIEWER"
                  checked={formData.interviewMode === 'HUMAN_INTERVIEWER'}
                  onChange={() => handleInterviewModeChange('HUMAN_INTERVIEWER')}
                />
                <span className="av-mode-option-title">Human Interview Mode</span>
              </label>
              <label className={`av-mode-option ${formData.interviewMode === 'AI_INTERVIEWER' ? 'selected' : ''}`}>
                <input
                  type="radio"
                  name="interviewMode"
                  value="AI_INTERVIEWER"
                  checked={formData.interviewMode === 'AI_INTERVIEWER'}
                  onChange={() => handleInterviewModeChange('AI_INTERVIEWER')}
                />
                <span className="av-mode-option-title">AI Interview Mode</span>
              </label>
            </div>
            <div className="registration-mode-detail">
              <strong>{modeDetailTitle(formData.interviewMode)}</strong>
              <p>{modeDetailCopy(formData.interviewMode)}</p>
            </div>
            </div>
          </div>
          {supportsEvaluationStyle ? (
          <div className="registration-section form-group-full">
            <div>
              <span className="registration-section-kicker">Evaluation Style</span>
            </div>
            <div className="registration-mode-layout">
              <div className="mode-toggle-options" role="radiogroup" aria-label="Evaluation style">
                <label className={`av-mode-option ${formData.evaluationStyle !== 'BANYAN' ? 'selected' : ''}`}>
                  <input
                    type="radio"
                    name="evaluationStyle"
                    value="STANDARD_MULTIPLE_QUESTIONS"
                    checked={formData.evaluationStyle !== 'BANYAN'}
                    onChange={() => handleEvaluationStyleChange('STANDARD_MULTIPLE_QUESTIONS')}
                  />
                  <span className="av-mode-option-title">Standard Multiple Questions</span>
                </label>
                <label className={`av-mode-option ${formData.evaluationStyle === 'BANYAN' ? 'selected' : ''}`}>
                  <input
                    type="radio"
                    name="evaluationStyle"
                    value="BANYAN"
                    checked={formData.evaluationStyle === 'BANYAN'}
                    onChange={() => handleEvaluationStyleChange('BANYAN')}
                  />
                  <span className="av-mode-option-title">Banyan Style</span>
                </label>
              </div>
              <div className="registration-mode-detail">
                <strong>{evaluationStyleTitle(formData.evaluationStyle)}</strong>
                <p>{evaluationStyleCopy(formData.evaluationStyle)}</p>
              </div>
            </div>
          </div>
          ) : null}
          <div className="start-interview-actions">
            <Button type="button" onClick={() => setRegistrationStep(2)}>Continue</Button>
          </div>
          </>
        ) : registrationStep === 2 ? (
          <>
          {formData.interviewMode !== 'AI_INTERVIEWER' ? (
            <div className="registration-section registration-interviewer-section form-group-full">
              <div>
                <span className="registration-section-kicker">Interviewer Details</span>
              </div>
              <div className="form-group">
                <label htmlFor="interviewerName">Interviewer Name</label>
                <input id="interviewerName" name="interviewerName" autoComplete="off" value={formData.interviewerName} onChange={handleChange} required />
              </div>
              <div className="form-group">
                <label htmlFor="interviewerEmail">Interviewer Email</label>
                <input id="interviewerEmail" name="interviewerEmail" type="email" autoComplete="new-password" inputMode="email" value={formData.interviewerEmail} onChange={handleChange} required />
              </div>
            </div>
          ) : null}
          <div className="registration-section registration-candidate-section form-group-full">
            <div>
              <span className="registration-section-kicker">Candidate Details</span>
            </div>
            <div className="form-group">
              <label htmlFor="intervieweeName">Interviewee Name</label>
              <input id="intervieweeName" name="intervieweeName" autoComplete="off" value={formData.intervieweeName} onChange={handleChange} required />
            </div>
            <div className="form-group">
              <label htmlFor="intervieweeEmail">Interviewee Email</label>
              <input id="intervieweeEmail" name="intervieweeEmail" type="email" autoComplete="new-password" inputMode="email" value={formData.intervieweeEmail} onChange={handleChange} required />
            </div>
            <div className="form-group">
              <label htmlFor="yearsOfExperience">Years of Experience</label>
              <input id="yearsOfExperience" name="yearsOfExperience" type="number" min="0" max="50" value={formData.yearsOfExperience ?? 0} onChange={handleChange} required />
            </div>
            <div className="form-group">
              <label htmlFor="targetRole">Target Role</label>
              <select id="targetRole" name="targetRole" value={formData.targetRole ?? targetRoleOptions[0] ?? ''} onChange={handleChange} required>
                {targetRoleOptions.map((roleOption) => (
                  <option key={roleOption} value={roleOption}>{roleOption}</option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label htmlFor="startingDifficultyLevel">Difficulty Level</label>
              <select id="startingDifficultyLevel" name="startingDifficultyLevel" value={formData.startingDifficultyLevel ?? 1} onChange={handleChange}>
                <option value={1}>Level 1</option>
                <option value={2}>Level 2</option>
                <option value={3}>Level 3</option>
                <option value={4}>Level 4</option>
                <option value={5}>Level 5</option>
              </select>
            </div>
            <div className="form-group">
              <label htmlFor="maxQuestions">{supportsEvaluationStyle && formData.evaluationStyle === 'BANYAN' ? 'Max Levels' : 'Max Questions'}</label>
              <input id="maxQuestions" name="maxQuestions" type="number" min="1" max="5" value={formData.maxQuestions ?? 5} onChange={handleChange} required />
            </div>
          </div>
          <div className="start-interview-actions">
            <Button type="button" variant="secondary" onClick={() => setRegistrationStep(1)}>Back</Button>
            <Button type="button" onClick={() => setRegistrationStep(3)}>Continue</Button>
          </div>
          </>
        ) : (
          <>
          <div className="registration-section form-group-full">
          <div>
            <span className="registration-section-kicker">Session Setup</span>
            <h3>Access and AV mode</h3>
          </div>
          {formData.interviewMode !== 'AI_INTERVIEWER' ? (
          <div className="av-mode-options" role="radiogroup" aria-label="Interview AV mode">
            <label className={`av-mode-option ${formData.avMode === 'EXTERNAL' ? 'selected' : ''}`}>
              <input
                type="radio"
                name="avMode"
                value="EXTERNAL"
                checked={formData.avMode === 'EXTERNAL'}
                onChange={() => handleAvModeChange('EXTERNAL')}
              />
              <span className="av-mode-option-title">Use Teams / Zoom</span>
              <span className="av-mode-option-copy">Recommended for the standard workflow. The coding session stays focused on the editor while AV is handled externally.</span>
            </label>
            <label className={`av-mode-option ${formData.avMode === 'IN_APP' ? 'selected' : ''}`}>
              <input
                type="radio"
                name="avMode"
                value="IN_APP"
                checked={formData.avMode === 'IN_APP'}
                onChange={() => handleAvModeChange('IN_APP')}
              />
              <span className="av-mode-option-title">Use In-App AV</span>
              <span className="av-mode-option-copy">Enable the built-in live audio and video panel during the interview session.</span>
            </label>
          </div>
          ) : (
            <p className="registration-review-copy">AI interviewer sessions use external AV controls and send candidate-only access.</p>
          )}
          <div className="registration-review">
            <ReviewChip label="Mode" value={formData.interviewMode === 'AI_INTERVIEWER' ? 'AI Interview' : 'Human Interview'} />
            {supportsEvaluationStyle ? <ReviewChip label="Style" value={formData.evaluationStyle === 'BANYAN' ? 'Banyan Style' : 'Standard Multiple Questions'} /> : null}
            <ReviewChip label="Candidate" value={formData.intervieweeName || 'Not entered'} />
            <ReviewChip label="Target Role" value={formData.targetRole || 'Not selected'} />
            <ReviewChip label="Difficulty" value={`Level ${formData.startingDifficultyLevel ?? 1}`} />
          </div>
          </div>
        <div className="start-interview-actions">
          <Button type="button" variant="secondary" onClick={() => setRegistrationStep(2)}>Back</Button>
          <Button type="submit">Register</Button>
        </div>
          </>
        )}
      </form>
      </div>
    </div>
  );
};

export default StartInterview;

function ReviewChip({ label, value }: { label: string; value: string }) {
  return (
    <span className="registration-review-chip">
      <small>{label}</small>
      <strong>{value}</strong>
    </span>
  );
}

function modeDetailTitle(mode?: InterviewMode) {
  return mode === 'AI_INTERVIEWER' ? 'AI-led interview' : 'Human-led interview';
}

function modeDetailCopy(mode?: InterviewMode) {
  if (mode === 'AI_INTERVIEWER') {
    return 'The AI interviewer starts the coding flow, generates validated questions, evaluates submissions, and prepares an advisory recommendation for mandatory human review.';
  }
  return 'A human interviewer leads the conversation, controls question selection, and can optionally use the AI Assistant to draft validated questions and reference solutions.';
}

function evaluationStyleTitle(style?: EvaluationStyle) {
  return style === 'BANYAN' ? 'Banyan Style' : 'Standard Multiple Questions';
}

function evaluationStyleCopy(style?: EvaluationStyle) {
  if (style === 'BANYAN') {
    return 'One coding challenge grows level by level just like Indian Banyan Tree. Each level must pass before the next requirement is unlocked.';
  }
  return 'Separate questions are generated across the interview. Each submitted question is evaluated independently.';
}
