# Pending Test Scenarios

This document tracks scenarios that are still pending validation because the current local setup has environment limitations.

## 1. Cross-Network Media Recovery After Brief Internet Drop

### Scenario

- Disconnect the internet connection for about 10 seconds during an active interview session.
- Reconnect the internet and verify whether audio/video interruption, reconnection, and session recovery behave correctly.

### Why It Is Still Pending

- In the current setup, both browser instances are running on the same machine.
- During the attempted test, audio and video continued to work unexpectedly even after the temporary internet disconnection.
- This may be happening because both participants are effectively sharing the same host/network environment, so the test does not accurately represent a real interviewer/interviewee connectivity loss scenario.

### Recommended Future Test Setup

- Run interviewer and interviewee on separate machines, or at minimum on separate networks.
- Repeat the temporary internet drop on only one participant side.
- Verify:
  - live media interruption behavior
  - reconnect behavior
  - resume policy behavior
  - interviewer visibility into the interruption

## 2. True Two-Device Full Audio/Video Streaming Validation

### Scenario

- Validate a full interview session where both audio and video are enabled and both participant streams work smoothly throughout the session.

### Why It Is Still Pending

- This cannot be reliably validated in the current setup because both browser sessions are running on the same machine in separate browsers.
- That setup is not sufficient to confirm real-world interviewer/interviewee media behavior, device permissions, stream stability, echo behavior, and actual peer-to-peer media performance.

### Recommended Future Test Setup

- Run interviewer and interviewee on separate physical machines.
- Validate:
  - interviewer audio/video stream quality
  - interviewee audio/video stream quality
  - one participant unmuted while the other participant remains muted, and confirm the muted side can still hear the unmuted speaker correctly
  - mute/unmute behavior
  - camera enable/disable behavior
  - reconnect behavior after temporary disruptions
  - overall stream stability during the interview

## 3. Progressive Integrity Warning Threshold Validation

### Scenario

- Validate candidate-facing warnings and suspicious escalation for focus-away, paste, drag/drop, and in-app AV disablement.
- Confirm that the first low-risk event shows a warning or informational notice, while repeated or long-duration events are marked suspicious.

### Why It Is Still Pending

- Some behavior is difficult to validate accurately when interviewer and interviewee are running on the same machine.
- External AV focus-away needs real Teams/Zoom usage to confirm that legitimate AV interactions are not over-classified as suspicious.
- In-app AV audio/video-off duration testing should be done on separate devices to avoid local browser/device permission artifacts.

### Recommended Future Test Setup

- Run interviewer and interviewee on separate machines.
- Test both `IN_APP` and `EXTERNAL` AV sessions.
- Verify:
  - in-app tab away becomes suspicious after 10 seconds or the second occurrence
  - external AV tab away becomes suspicious after 30 seconds or the third occurrence
  - in-app microphone/camera off becomes suspicious after 15 seconds or the second occurrence
  - paste and drag/drop are warned first and suspicious on repeat
  - candidate sees the correct integrity notice
  - interviewer sees alerts only for confirmed suspicious events
  - Result Workspace summarizes `INFO`, `WARNING`, and `SUSPICIOUS` counts correctly
