import React, { useState } from 'react';
import { Button } from '../components/Button';
import { sessionApi } from '../services/sessionApi';
import type { AvMode, CreateSessionRequest, TechnologySkill } from '../types/session';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useSessionStore } from '../stores/sessionStore';
import { getBrowserTimeZone } from '../utils/dateTime';
import './StartInterview.css';

interface FormData extends CreateSessionRequest {}

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
  });
  const navigate = useNavigate();
  const setSession = useSessionStore((state) => state.setSession);
  const setRole = useSessionStore((state) => state.setRole);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value
    });
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
        const request: CreateSessionRequest = {
          ...formData,
          avMode,
        };
        const response = await sessionApi.createSession(request);
        setSession(response);
        setRole('interviewer');
        navigate(`/java/disclaimer/interviewer?sessionId=${response.id}`);
      } catch (error) {
        console.error('Failed to create session:', error);
        alert('Failed to start interview');
      }
    };

  return (
    <div className="page-shell">
      <div className="page-card form-card">
      <div className="page-kicker">Create Interview</div>
      <h2>Register interviewer and interviewee</h2>
      <p className="page-subtitle">
        The interviewer creates the session for both participants. A secure join link will be generated for the interviewee after disclaimer acceptance.
      </p>
      <form onSubmit={handleSubmit} className="stack-form start-interview-form" autoComplete="off">
        <input type="text" name="ghostUser" autoComplete="username" tabIndex={-1} aria-hidden="true" className="sr-only-input" />
        <input type="password" name="ghostPassword" autoComplete="new-password" tabIndex={-1} aria-hidden="true" className="sr-only-input" />
        <div className="form-group">
          <label htmlFor="interviewerName">Interviewer Name</label>
          <input id="interviewerName" name="interviewerName" autoComplete="off" value={formData.interviewerName} onChange={handleChange} required />
        </div>
        <div className="form-group">
          <label htmlFor="interviewerEmail">Interviewer Email</label>
          <input id="interviewerEmail" name="interviewerEmail" type="email" autoComplete="new-password" inputMode="email" value={formData.interviewerEmail} onChange={handleChange} required />
        </div>
        <div className="form-group">
          <label htmlFor="intervieweeName">Interviewee Name</label>
          <input id="intervieweeName" name="intervieweeName" autoComplete="off" value={formData.intervieweeName} onChange={handleChange} required />
        </div>
        <div className="form-group">
          <label htmlFor="intervieweeEmail">Interviewee Email</label>
          <input id="intervieweeEmail" name="intervieweeEmail" type="email" autoComplete="new-password" inputMode="email" value={formData.intervieweeEmail} onChange={handleChange} required />
        </div>
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
        <div className="start-interview-actions">
          <Button type="submit">Start</Button>
        </div>
      </form>
      </div>
    </div>
  );
};

export default StartInterview;

