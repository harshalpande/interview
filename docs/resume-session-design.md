# Persistent Session Resume

## Goal

Allow active interview sessions to survive backend/frontend redeploys, refreshes, reconnects, and participant recovery scenarios by keeping resumable session state in the database instead of relying on in-memory runtime state.

## Core Rules

- Session continuity is restored from persisted session, participant, and code state.
- Interviewee resume always requires registered name and email verification.
- Interviewer resume requires registered name and email verification.
- Interviewee can resume at most once during the entire interview.
- Interviewee resume requires interviewer approval when:
  - the resume follows a browser/tab close or refresh interruption
  - the resume comes from a new device
  - the resume comes from a different network/IP
- Interviewee resume is only allowed within 120 seconds of the interruption/recovery window.
- A second interviewee resume attempt or a late resume attempt triggers automatic rejection due to suspicious activity.
- The interviewer is taken to a prefilled feedback review state and only needs to review and submit.
- The prefilled rejection feedback is selected from a static scenario library based on the suspicious interruption pattern observed in the session.
- Suspicious auto-rejections use the dedicated `Disqualified` rating with recommendation set to `No`.
- If an interviewee browser/tab interruption occurs and both participants remain absent for 120 seconds, the session is marked `INCOMPLETE`.

## Use Cases

### 1. Backend redeploy during an active interview

- Session remains active in the database.
- Interviewee resumes by verifying name and email.
- Interviewer resumes by verifying name and email.
- Session restores the same code, timer, and state.

### 2. Frontend redeploy or browser refresh

- Session remains active in the database.
- Participants can resume by re-verifying their registered details.
- Session restores from the persisted state.

### 3. Network switch or temporary outage

- Interviewee resume requires stronger name/email verification.
- If the reconnect appears to come from a new network or device, interviewer approval is required.
- If the interviewee has already resumed once earlier in the same interview, a new resume attempt immediately rejects the candidate for suspicious activity.
- If the resume attempt happens after 120 seconds, the candidate is automatically rejected for suspicious activity.
- Interviewer resume is allowed after name/email verification.

### 4. Browser tab accidentally closed / browser closed / disruptive refresh

- For the interviewee, this is treated as suspicious activity.
- Interviewee must re-verify name/email and obtain interviewer approval before resuming.
- The interruption is recorded for interviewer visibility.
- Interviewee can only use this resume path once across the full interview session.
- Any second resume attempt, or any attempt after the 120-second recovery window, automatically rejects the candidate for suspicious activity.
- The interviewer sees a prefilled rejection feedback draft and only needs to review and submit.
- If neither participant rejoins within 120 seconds of the interruption, the session is automatically marked `INCOMPLETE`.

### 5. Interviewer interruption scenarios

- Interviewer can resume after verifying registered name and email.
- No second-party approval is required.

## Implementation Summary

- Participant presence, device ID, last seen time, connection state, resume approval state, and pending resume reason are persisted in the database.
- Active timer ticks are derived from `startedAt` and `durationSec`, not an in-memory tracker.
- Resume requests and approvals are handled through explicit backend APIs.
- The session UI sends heartbeats while active and uses a disconnect event on page unload.
- The interviewer can approve or reject pending interviewee resume requests from the active session screen.
