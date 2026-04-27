package com.altimetrik.interview.service;

import com.altimetrik.interview.dto.AccessLinkResponse;
import com.altimetrik.interview.dto.AccessVerificationResponse;
import com.altimetrik.interview.dto.SessionResponse;
import com.altimetrik.interview.dto.VerifyOtpRequest;
import com.altimetrik.interview.entity.InterviewSession;
import com.altimetrik.interview.entity.Participant;
import com.altimetrik.interview.entity.ParticipantAccessChallenge;
import com.altimetrik.interview.enums.ParticipantAccessStatus;
import com.altimetrik.interview.enums.ParticipantRole;
import com.altimetrik.interview.enums.SessionStatus;
import com.altimetrik.interview.repository.ParticipantAccessChallengeRepository;
import com.altimetrik.interview.repository.ParticipantRepository;
import com.altimetrik.interview.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionAccessService {

    private static final int OTP_WINDOW_SECONDS = 120;
    private static final int MAX_OTP_WINDOWS = 3;
    private static final String OTP_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final SessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final ParticipantAccessChallengeRepository participantAccessChallengeRepository;
    private final SessionService sessionService;
    private final EmailService emailService;

    @Value("${app.public-origin:http://localhost:3000}")
    private String publicOrigin;

    @Transactional
    public SessionResponse startSession(String sessionId) {
        InterviewSession session = getRequiredSession(sessionId);
        if (session.getStatus() == SessionStatus.EXPIRED
                || session.getStatus() == SessionStatus.ENDED
                || session.getStatus() == SessionStatus.AUTH_FAILED
                || session.getStatus() == SessionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This session cannot enter secure pre-session authentication.");
        }

        session.setStatus(SessionStatus.AUTH_IN_PROGRESS);
        if (session.getAuthStartedAt() == null) {
            session.setAuthStartedAt(nowUtc());
        }
        session.setReadyToStartAt(null);
        session.setAuthFailedAt(null);
        session.setAuthFailureReason(null);
        sessionRepository.save(session);

        List<Participant> participants = participantRepository.findBySessionId(sessionId);
        for (Participant participant : participants) {
            ParticipantAccessChallenge challenge = participantAccessChallengeRepository
                    .findBySessionIdAndParticipantRole(sessionId, participant.getRole())
                    .orElseGet(ParticipantAccessChallenge::new);

            resetChallengeForFreshStart(challenge);
            String otp = issueFreshOtp(challenge, participant, null, null, false);
            participantAccessChallengeRepository.save(challenge);
            sendSecureAccessEmail(session, participant, challenge, otp);
        }

        return sessionService.getSession(sessionId);
    }

    @Transactional
    public AccessLinkResponse getAccessLink(String secureToken) {
        ParticipantAccessChallenge challenge = getRequiredChallenge(secureToken);
        InterviewSession session = getRequiredSession(challenge.getSessionId());
        Participant participant = getRequiredParticipant(challenge.getSessionId(), challenge.getParticipantRole());
        ensureAccessSessionAvailable(session);
        activateOtpWindowIfNeeded(challenge);
        return buildAccessLinkResponse(session, participant, challenge, "Secure participant access is available.");
    }

    @Transactional
    public AccessVerificationResponse verifyOtp(String secureToken,
                                                VerifyOtpRequest request,
                                                String clientIp,
                                                String userAgent) {
        ParticipantAccessChallenge challenge = getRequiredChallenge(secureToken);
        InterviewSession session = getRequiredSession(challenge.getSessionId());
        Participant participant = getRequiredParticipant(challenge.getSessionId(), challenge.getParticipantRole());
        ensureAccessSessionAvailable(session);

        if (challenge.getStatus() == ParticipantAccessStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.GONE, challenge.getFailureReason() == null
                    ? "Secure access is no longer available for this participant."
                    : challenge.getFailureReason());
        }

        challenge.setLastKnownIp(clientIp);
        challenge.setLastKnownUserAgent(userAgent);
        participant.setJoinedAt(participant.getJoinedAt() == null ? nowUtc() : participant.getJoinedAt());
        participantRepository.save(participant);

        OffsetDateTime now = nowUtc();
        if (challenge.getOtpExpiresAt() == null || challenge.getOtpExpiresAt().isBefore(now)) {
            return handleExpiredOtp(session, participant, challenge, "The passcode expired. A new passcode has been sent to the registered email address.");
        }

        String normalizedOtp = request.getOtp().trim().toUpperCase(Locale.ROOT);
        if (!hashOtp(normalizedOtp).equals(challenge.getOtpHash())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The passcode is incorrect. Please enter the latest passcode sent to your registered email address.");
        }

        challenge.setOtpVerifiedAt(now);
        challenge.setStatus(ParticipantAccessStatus.OTP_VERIFIED);
        challenge.setFailureReason(null);
        participantAccessChallengeRepository.save(challenge);

        SessionResponse sessionResponse = sessionService.reevaluatePreSessionState(session.getId());
        return AccessVerificationResponse.builder()
                .success(true)
                .sessionReadyToStart(sessionResponse.getStatus() == SessionStatus.READY_TO_START)
                .retryAvailable(remainingOtpWindows(challenge) > 0)
                .remainingOtpWindows(remainingOtpWindows(challenge))
                .otpExpiresAt(challenge.getOtpExpiresAt())
                .message("Passcode verified successfully.")
                .access(buildAccessLinkResponse(getRequiredSession(session.getId()), participant, challenge, "Passcode verified successfully."))
                .session(sessionResponse)
                .build();
    }

    @Transactional
    public AccessVerificationResponse retryOtp(String secureToken, String clientIp, String userAgent) {
        ParticipantAccessChallenge challenge = getRequiredChallenge(secureToken);
        InterviewSession session = getRequiredSession(challenge.getSessionId());
        Participant participant = getRequiredParticipant(challenge.getSessionId(), challenge.getParticipantRole());
        ensureAccessSessionAvailable(session);

        return issueRetryOtp(session, participant, challenge, clientIp, userAgent, "A new passcode has been sent to the registered email address.");
    }

    @Transactional
    public SessionResponse acceptAccessDisclaimer(String secureToken, String clientIp, String userAgent) {
        ParticipantAccessChallenge challenge = getRequiredChallenge(secureToken);
        Participant participant = getRequiredParticipant(challenge.getSessionId(), challenge.getParticipantRole());
        InterviewSession session = getRequiredSession(challenge.getSessionId());
        ensureAccessSessionAvailable(session);

        challenge.setLastKnownIp(clientIp);
        challenge.setLastKnownUserAgent(userAgent);
        participant.setDisclaimerAcceptedAt(nowUtc());
        participantRepository.save(participant);
        participantAccessChallengeRepository.save(challenge);
        return sessionService.reevaluatePreSessionState(session.getId());
    }

    @Transactional(readOnly = true)
    public boolean isOtpVerified(String sessionId, ParticipantRole role) {
        return participantAccessChallengeRepository.findBySessionIdAndParticipantRole(sessionId, role)
                .map(challenge -> challenge.getStatus() == ParticipantAccessStatus.OTP_VERIFIED
                        || challenge.getStatus() == ParticipantAccessStatus.COMPLETED)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean areAllPreSessionRequirementsComplete(String sessionId) {
        Participant interviewer = getRequiredParticipant(sessionId, ParticipantRole.INTERVIEWER);
        Participant interviewee = getRequiredParticipant(sessionId, ParticipantRole.INTERVIEWEE);
        return interviewer.getDisclaimerAcceptedAt() != null
                && interviewee.getDisclaimerAcceptedAt() != null
                && isOtpVerified(sessionId, ParticipantRole.INTERVIEWER)
                && isOtpVerified(sessionId, ParticipantRole.INTERVIEWEE)
                && interviewee.getIdentityCaptureStatus() == com.altimetrik.interview.enums.IdentityCaptureStatus.SUCCESS;
    }

    @Transactional
    public void markParticipantCompleted(String sessionId, ParticipantRole role) {
        participantAccessChallengeRepository.findBySessionIdAndParticipantRole(sessionId, role)
                .ifPresent(challenge -> {
                    challenge.setStatus(ParticipantAccessStatus.COMPLETED);
                    participantAccessChallengeRepository.save(challenge);
                });
    }

    private AccessVerificationResponse handleExpiredOtp(InterviewSession session,
                                                        Participant participant,
                                                        ParticipantAccessChallenge challenge,
                                                        String message) {
        return issueRetryOtp(session, participant, challenge, challenge.getLastKnownIp(), challenge.getLastKnownUserAgent(), message);
    }

    private AccessVerificationResponse issueRetryOtp(InterviewSession session,
                                                     Participant participant,
                                                     ParticipantAccessChallenge challenge,
                                                     String clientIp,
                                                     String userAgent,
                                                     String message) {
        int remainingBeforeIssue = remainingOtpWindows(challenge);
        if (remainingBeforeIssue <= 0) {
            String failureReason = "Secure session authentication failed because the maximum passcode windows were exhausted.";
            markSessionAuthFailed(session, challenge, failureReason);
            throw new ResponseStatusException(HttpStatus.GONE, failureReason);
        }

        String otp = issueFreshOtp(challenge, participant, clientIp, userAgent, true);
        participantAccessChallengeRepository.save(challenge);
        sendSecureAccessEmail(session, participant, challenge, otp);

        SessionResponse sessionResponse = sessionService.reevaluatePreSessionState(session.getId());
        return AccessVerificationResponse.builder()
                .success(false)
                .sessionReadyToStart(sessionResponse.getStatus() == SessionStatus.READY_TO_START)
                .retryAvailable(remainingOtpWindows(challenge) > 0)
                .remainingOtpWindows(remainingOtpWindows(challenge))
                .otpExpiresAt(challenge.getOtpExpiresAt())
                .message(message)
                .access(buildAccessLinkResponse(getRequiredSession(session.getId()), participant, challenge, message))
                .session(sessionResponse)
                .build();
    }

    private void markSessionAuthFailed(InterviewSession session,
                                       ParticipantAccessChallenge failedChallenge,
                                       String failureReason) {
        failedChallenge.setStatus(ParticipantAccessStatus.FAILED);
        failedChallenge.setFailureReason(failureReason);
        participantAccessChallengeRepository.save(failedChallenge);

        session.setStatus(SessionStatus.AUTH_FAILED);
        session.setAuthFailedAt(nowUtc());
        session.setAuthFailureReason(failureReason);
        session.setEndedAt(nowUtc());
        sessionRepository.save(session);
    }

    private String issueFreshOtp(ParticipantAccessChallenge challenge,
                                 Participant participant,
                                 String clientIp,
                                 String userAgent,
                                 boolean isRetry) {
        OffsetDateTime now = nowUtc();
        String otp = generateOtp();
        challenge.setSessionId(participant.getSessionId());
        challenge.setParticipantRole(participant.getRole());
        challenge.setParticipantEmail(participant.getEmail());
        challenge.setSecureToken(challenge.getSecureToken() == null ? UUID.randomUUID().toString() : challenge.getSecureToken());
        challenge.setOtpHash(hashOtp(otp));
        challenge.setOtpIssuedAt(now);
        challenge.setOtpExpiresAt(isRetry ? now.plusSeconds(OTP_WINDOW_SECONDS) : null);
        challenge.setOtpVerifiedAt(null);
        challenge.setLastEmailSentAt(now);
        challenge.setFailureReason(null);
        challenge.setStatus(ParticipantAccessStatus.OTP_PENDING);
        challenge.setLastKnownIp(clientIp);
        challenge.setLastKnownUserAgent(userAgent);
        int nextWindowCount = challenge.getOtpWindowCount() == null ? 1 : challenge.getOtpWindowCount() + 1;
        challenge.setOtpWindowCount(nextWindowCount);
        return otp;
    }

    private void resetChallengeForFreshStart(ParticipantAccessChallenge challenge) {
        challenge.setOtpWindowCount(0);
        challenge.setOtpExpiresAt(null);
        challenge.setOtpVerifiedAt(null);
        challenge.setFailureReason(null);
        challenge.setStatus(ParticipantAccessStatus.NOT_STARTED);
    }

    private void sendSecureAccessEmail(InterviewSession session,
                                       Participant participant,
                                       ParticipantAccessChallenge challenge,
                                       String otp) {
        String accessUrl = buildAccessUrl(challenge.getSecureToken());
        String subject = "Interview Session Access Details";
        String textBody = """
                Dear %s,

                Your interview session access has been initiated for the %s interview.

                Open the secure session using this link:
                <%s>

                One-time passcode: %s
                Passcode validity: 120 seconds after you open the secure session link

                Please verify your details (name and email) and proceed with the required disclaimer and verification steps before joining the session.

                Regards,
                Interview Platform
                """.formatted(participant.getName(), session.getTechnology(), accessUrl, otp);
        String htmlBody = """
                <html>
                  <body style="font-family:Segoe UI, Arial, sans-serif; color:#16324f; line-height:1.6;">
                    <p>Dear %s,</p>
                    <p>Your interview session access has been initiated for the <strong>%s</strong> interview.</p>
                    <p>
                      <a href="%s" style="display:inline-block; padding:10px 18px; border-radius:999px; background:#0b7285; color:#ffffff; text-decoration:none; font-weight:600;">
                        Open Secure Session
                      </a>
                    </p>
                    <p>If the button does not open, use this secure link:<br><a href="%s">%s</a></p>
                    <p><strong>One-time passcode:</strong> %s<br>
                    <strong>Passcode validity:</strong> 120 seconds after you open the secure session link</p>
                    <p>Please verify your details (name and email) and proceed with the required disclaimer and verification steps before joining the session.</p>
                    <p>Regards,<br>Interview Platform</p>
                  </body>
                </html>
                """.formatted(participant.getName(), session.getTechnology(), accessUrl, accessUrl, accessUrl, otp);
        emailService.sendEmail(participant.getEmail(), subject, textBody, htmlBody);
    }

    private AccessLinkResponse buildAccessLinkResponse(InterviewSession session,
                                                       Participant participant,
                                                       ParticipantAccessChallenge challenge,
                                                       String message) {
        boolean identityCaptureRequired = participant.getRole() == ParticipantRole.INTERVIEWEE;
        boolean identityCaptureComplete = !identityCaptureRequired
                || participant.getIdentityCaptureStatus() == com.altimetrik.interview.enums.IdentityCaptureStatus.SUCCESS;
        return AccessLinkResponse.builder()
                .sessionId(session.getId())
                .role(participant.getRole())
                .participantName(participant.getName())
                .participantEmail(participant.getEmail())
                .avMode(session.getAvMode())
                .sessionStatus(session.getStatus())
                .accessStatus(challenge.getStatus())
                .otpExpiresAt(challenge.getOtpExpiresAt())
                .remainingOtpWindows(remainingOtpWindows(challenge))
                .disclaimerAccepted(participant.getDisclaimerAcceptedAt() != null)
                .otpVerified(challenge.getStatus() == ParticipantAccessStatus.OTP_VERIFIED
                        || challenge.getStatus() == ParticipantAccessStatus.COMPLETED)
                .identityCaptureRequired(identityCaptureRequired)
                .identityCaptureComplete(identityCaptureComplete)
                .sessionReadyToStart(session.getStatus() == SessionStatus.READY_TO_START)
                .message(message)
                .build();
    }

    private void ensureAccessSessionAvailable(InterviewSession session) {
        if (session.getStatus() == SessionStatus.EXPIRED
                || session.getStatus() == SessionStatus.ENDED
                || session.getStatus() == SessionStatus.AUTH_FAILED) {
            throw new ResponseStatusException(HttpStatus.GONE, "This session is no longer available for secure access.");
        }
    }

    private int remainingOtpWindows(ParticipantAccessChallenge challenge) {
        int used = challenge.getOtpWindowCount() == null ? 0 : challenge.getOtpWindowCount();
        return Math.max(0, MAX_OTP_WINDOWS - used);
    }

    private void activateOtpWindowIfNeeded(ParticipantAccessChallenge challenge) {
        if (challenge.getStatus() != ParticipantAccessStatus.OTP_PENDING || challenge.getOtpVerifiedAt() != null) {
            return;
        }
        if (challenge.getOtpExpiresAt() != null) {
            return;
        }
        challenge.setOtpExpiresAt(nowUtc().plusSeconds(OTP_WINDOW_SECONDS));
        participantAccessChallengeRepository.save(challenge);
    }

    private String buildAccessUrl(String secureToken) {
        return publicOrigin.replaceAll("/$", "") + "/java/access/" + secureToken;
    }

    private String generateOtp() {
        StringBuilder builder = new StringBuilder(5);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int index = 0; index < 5; index++) {
            builder.append(OTP_CHARS.charAt(random.nextInt(OTP_CHARS.length())));
        }
        return builder.toString();
    }

    private String hashOtp(String otp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(otp.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private InterviewSession getRequiredSession(String sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
    }

    private Participant getRequiredParticipant(String sessionId, ParticipantRole role) {
        return participantRepository.findBySessionIdAndRole(sessionId, role)
                .orElseThrow(() -> new IllegalArgumentException("Participant not found"));
    }

    private ParticipantAccessChallenge getRequiredChallenge(String secureToken) {
        return participantAccessChallengeRepository.findBySecureToken(secureToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Secure access link is invalid."));
    }

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
