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
  - mute/unmute behavior
  - camera enable/disable behavior
  - reconnect behavior after temporary disruptions
  - overall stream stability during the interview
