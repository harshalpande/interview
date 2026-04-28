import React, { useEffect, useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Button } from '../components/Button';
import { sessionApi } from '../services/sessionApi';
import type { AvMode, CreateSessionRequest, InterviewMode, TechnologySkill } from '../types/session';
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
    yearsOfExperience: 0,
    targetRole: '',
    startingDifficultyLevel: 1,
    maxQuestions: 5,
  });
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const targetRoleOptions = useMemo(() => TARGET_ROLES_BY_TECHNOLOGY[technology] || TARGET_ROLES_BY_TECHNOLOGY.JAVA, [technology]);

  useEffect(() => {
    if (formData.interviewMode !== 'AI_INTERVIEWER') {
      return;
    }
    if (!formData.targetRole || !targetRoleOptions.includes(formData.targetRole)) {
      setFormData((previous) => ({
        ...previous,
        targetRole: targetRoleOptions[0] || '',
      }));
    }
  }, [formData.interviewMode, formData.targetRole, targetRoleOptions]);

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
          yearsOfExperience: interviewMode === 'AI_INTERVIEWER' ? Number(formData.yearsOfExperience ?? 0) : undefined,
          targetRole: interviewMode === 'AI_INTERVIEWER' ? formData.targetRole?.trim() : undefined,
          startingDifficultyLevel: interviewMode === 'AI_INTERVIEWER' ? Number(formData.startingDifficultyLevel ?? 1) : undefined,
          maxQuestions: interviewMode === 'AI_INTERVIEWER' ? Number(formData.maxQuestions ?? 5) : undefined,
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
      <div className="page-card form-card">
      <div className="page-kicker">Register Interview</div>
      <h2>Register interview</h2>
      <p className="page-subtitle">
        This step creates the interview record only. The secure participant session flow will be started later from the dashboard.
      </p>
      <form onSubmit={handleSubmit} className="stack-form start-interview-form" autoComplete="off">
        <input type="text" name="ghostUser" autoComplete="username" tabIndex={-1} aria-hidden="true" className="sr-only-input" />
        <input type="password" name="ghostPassword" autoComplete="new-password" tabIndex={-1} aria-hidden="true" className="sr-only-input" />
        <div className="form-group form-group-full">
          <label>Interview Mode</label>
          <div className="av-mode-options" role="radiogroup" aria-label="Interview mode">
            <label className={`av-mode-option ${formData.interviewMode === 'HUMAN_INTERVIEWER' ? 'selected' : ''}`}>
              <input
                type="radio"
                name="interviewMode"
                value="HUMAN_INTERVIEWER"
                checked={formData.interviewMode === 'HUMAN_INTERVIEWER'}
                onChange={() => handleInterviewModeChange('HUMAN_INTERVIEWER')}
              />
              <span className="av-mode-option-title">Human Interviewer</span>
              <span className="av-mode-option-copy">Keep the existing two-participant workflow with interviewer-led questions and feedback.</span>
            </label>
            <label className={`av-mode-option ${formData.interviewMode === 'AI_INTERVIEWER' ? 'selected' : ''}`}>
              <input
                type="radio"
                name="interviewMode"
                value="AI_INTERVIEWER"
                checked={formData.interviewMode === 'AI_INTERVIEWER'}
                onChange={() => handleInterviewModeChange('AI_INTERVIEWER')}
              />
              <span className="av-mode-option-title">AI Interviewer</span>
              <span className="av-mode-option-copy">Send candidate-only access and let the AI interviewer generate and evaluate questions.</span>
            </label>
          </div>
        </div>
        {formData.interviewMode !== 'AI_INTERVIEWER' ? (
          <>
            <div className="form-group">
              <label htmlFor="interviewerName">Interviewer Name</label>
              <input id="interviewerName" name="interviewerName" autoComplete="off" value={formData.interviewerName} onChange={handleChange} required />
            </div>
            <div className="form-group">
              <label htmlFor="interviewerEmail">Interviewer Email</label>
              <input id="interviewerEmail" name="interviewerEmail" type="email" autoComplete="new-password" inputMode="email" value={formData.interviewerEmail} onChange={handleChange} required />
            </div>
          </>
        ) : null}
        <div className="form-group">
          <label htmlFor="intervieweeName">Interviewee Name</label>
          <input id="intervieweeName" name="intervieweeName" autoComplete="off" value={formData.intervieweeName} onChange={handleChange} required />
        </div>
        <div className="form-group">
          <label htmlFor="intervieweeEmail">Interviewee Email</label>
          <input id="intervieweeEmail" name="intervieweeEmail" type="email" autoComplete="new-password" inputMode="email" value={formData.intervieweeEmail} onChange={handleChange} required />
        </div>
        {formData.interviewMode === 'AI_INTERVIEWER' ? (
          <>
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
              <label htmlFor="maxQuestions">Max Questions</label>
              <input id="maxQuestions" name="maxQuestions" type="number" min="1" max="5" value={formData.maxQuestions ?? 5} onChange={handleChange} required />
            </div>
          </>
        ) : null}
        {formData.interviewMode !== 'AI_INTERVIEWER' ? (
        <div className="form-group form-group-full">
          <label>Interview AV Mode</label>
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
        </div>
        ) : null}
        <div className="start-interview-actions">
          <Button type="submit">Register</Button>
        </div>
      </form>
      </div>
    </div>
  );
};

export default StartInterview;

