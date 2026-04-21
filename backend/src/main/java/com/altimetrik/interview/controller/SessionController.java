package com.altimetrik.interview.controller;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.altimetrik.interview.dto.AbandonSessionRequest;
import com.altimetrik.interview.dto.AcceptDisclaimerRequest;
import com.altimetrik.interview.dto.ActivityEventDto;
import com.altimetrik.interview.dto.ActivityEventRequest;
import com.altimetrik.interview.dto.CodeUpdateRequest;
import com.altimetrik.interview.dto.CreateSessionRequest;
import com.altimetrik.interview.dto.DisconnectParticipantRequest;
import com.altimetrik.interview.dto.EndSessionRequest;
import com.altimetrik.interview.dto.FeedbackRequest;
import com.altimetrik.interview.dto.HeartbeatRequest;
import com.altimetrik.interview.dto.JoinSessionRequest;
import com.altimetrik.interview.dto.ResumeApprovalRequest;
import com.altimetrik.interview.dto.ResumeRequest;
import com.altimetrik.interview.dto.ResumeResponse;
import com.altimetrik.interview.dto.SessionResponse;
import com.altimetrik.interview.dto.SessionSocketMessage;
import com.altimetrik.interview.dto.ValidateTokenResponse;
import com.altimetrik.interview.enums.FeedbackRating;
import com.altimetrik.interview.enums.TechnologySkill;
import com.altimetrik.interview.service.SessionService;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping
    public ResponseEntity<SessionResponse> createSession(@Valid @RequestBody CreateSessionRequest request) {
        SessionResponse response = sessionService.createSession(request);
        broadcastSession(response, "SESSION_STATE", "Session created");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable String id) {
        return ResponseEntity.ok(sessionService.getSession(id));
    }

    @GetMapping
    public ResponseEntity<?> listSessions(Pageable pageable,
                                          @RequestParam(required = false) @Nullable String search,
                                          @RequestParam(required = false) @Nullable OffsetDateTime from,
                                          @RequestParam(required = false) @Nullable OffsetDateTime to,
                                          @RequestParam(required = false) @Nullable List<TechnologySkill> technologies,
                                          @RequestParam(required = false) @Nullable List<FeedbackRating> ratings) {
        if (pageable == null || pageable.getSort().isUnsorted()) {
            Pageable defaultPageable = PageRequest.of(
                pageable != null ? pageable.getPageNumber() : 0,
                pageable != null && pageable.getPageSize() > 0 ? pageable.getPageSize() : 20,
                Sort.by("createdAt").descending()
            );
            return ResponseEntity.ok(sessionService.listSessions(defaultPageable, search, from, to, technologies, ratings));
        }
        return ResponseEntity.ok(sessionService.listSessions(pageable, search, from, to, technologies, ratings));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportSessions(@RequestParam(required = false) @Nullable String search,
                                                 @RequestParam(required = false) @Nullable OffsetDateTime from,
                                                 @RequestParam(required = false) @Nullable OffsetDateTime to,
                                                 @RequestParam(required = false) @Nullable List<TechnologySkill> technologies,
                                                 @RequestParam(required = false) @Nullable List<FeedbackRating> ratings,
                                                 @RequestParam(defaultValue = "createdAt") String sortBy,
                                                 @RequestParam(defaultValue = "desc") String direction) {
        SessionService.CsvExport export = sessionService.exportSessionsCsv(
                search,
                from,
                to,
                technologies,
                ratings,
                sortBy,
                Sort.Direction.fromOptionalString(direction).orElse(Sort.Direction.DESC)
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + export.filename() + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(export.content().getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/{id}/disclaimer")
    public ResponseEntity<SessionResponse> acceptDisclaimer(@PathVariable String id,
                                                            @Valid @RequestBody AcceptDisclaimerRequest request) {
        SessionResponse response = sessionService.acceptDisclaimer(id, request);
        broadcastSession(response, "SESSION_STATE", "Disclaimer accepted");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/join/{token}")
    public ResponseEntity<ValidateTokenResponse> validateToken(@PathVariable String token) {
        return ResponseEntity.ok(sessionService.validateToken(token));
    }

    @PostMapping("/join/{token}")
    public ResponseEntity<SessionResponse> joinSession(@PathVariable String token,
                                                       @Valid @RequestBody JoinSessionRequest request,
                                                       HttpServletRequest servletRequest) {
        SessionResponse response = sessionService.joinSession(token, request, extractClientIp(servletRequest), servletRequest.getHeader("User-Agent"));
        broadcastSession(response, "USER_JOINED", "Interviewee joined");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<ResumeResponse> requestResume(@PathVariable String id,
                                                        @Valid @RequestBody ResumeRequest request,
                                                        HttpServletRequest servletRequest) {
        ResumeResponse response = sessionService.requestResume(id, request, extractClientIp(servletRequest), servletRequest.getHeader("User-Agent"));
        if (response.getSession() != null) {
            String message = response.getMessage();
            String type = ResumeResponse.REJECTED.equals(response.getStatus()) ? "SESSION_END" : "SESSION_STATE";
            broadcastSession(response.getSession(), type, message);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/resume/approve")
    public ResponseEntity<SessionResponse> approveResume(@PathVariable String id,
                                                         @Valid @RequestBody ResumeApprovalRequest request,
                                                         HttpServletRequest servletRequest) {
        SessionResponse response = sessionService.approveResume(id, request, extractClientIp(servletRequest), servletRequest.getHeader("User-Agent"));
        broadcastSession(response, "SESSION_STATE", "Interviewee resume approved");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/resume/reject")
    public ResponseEntity<SessionResponse> rejectResume(@PathVariable String id,
                                                        @Valid @RequestBody ResumeApprovalRequest request,
                                                        HttpServletRequest servletRequest) {
        SessionResponse response = sessionService.rejectResume(id, request, extractClientIp(servletRequest), servletRequest.getHeader("User-Agent"));
        broadcastSession(response, "SESSION_STATE", "Interviewee resume rejected");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/presence")
    public ResponseEntity<SessionResponse> registerHeartbeat(@PathVariable String id,
                                                             @Valid @RequestBody HeartbeatRequest request,
                                                             HttpServletRequest servletRequest) {
        SessionResponse response = sessionService.registerHeartbeat(id, request, extractClientIp(servletRequest), servletRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/disconnect")
    public ResponseEntity<SessionResponse> disconnectParticipant(@PathVariable String id,
                                                                 @Valid @RequestBody DisconnectParticipantRequest request) {
        SessionResponse response = sessionService.disconnectParticipant(id, request);
        broadcastSession(response, "SESSION_STATE", "Participant disconnected");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<SessionResponse> startSession(@PathVariable String id) {
        SessionResponse response = sessionService.startSession(id);
        broadcastSession(response, "SESSION_START", "Interview started");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/extend")
    public ResponseEntity<SessionResponse> extendSession(@PathVariable String id) {
        SessionResponse response = sessionService.extendSession(id);
        broadcastSession(response, "SESSION_EXTEND", "Interview extended");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/feedback")
    public ResponseEntity<SessionResponse> submitFeedback(@PathVariable String id,
                                                          @Valid @RequestBody FeedbackRequest request) {
        SessionResponse response = sessionService.submitFeedback(id, request);
        broadcastSession(response, "SESSION_STATE", "Feedback submitted");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<SessionResponse> endSession(@PathVariable String id,
                                                      @Valid @RequestBody EndSessionRequest request) {
        SessionResponse response = sessionService.endSession(id, request);
        broadcastSession(response, "SESSION_END", "Interview ended");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/abandon")
    public ResponseEntity<SessionResponse> abandonSession(@PathVariable String id,
                                                          @RequestBody(required = false) @Nullable AbandonSessionRequest request) {
        String finalCode = request == null ? "" : request.getFinalCode();
        SessionResponse response = sessionService.abandonSession(id, finalCode);
        broadcastSession(response, "SESSION_END", "Interview marked incomplete");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/activity-events")
    public ResponseEntity<ActivityEventDto> recordActivityEvent(@PathVariable String id,
                                                                @Valid @RequestBody ActivityEventRequest request) {
        ActivityEventDto event = sessionService.recordActivityEvent(id, request);
        messagingTemplate.convertAndSend("/topic/session/" + id, SessionSocketMessage.builder()
                .type("ACTIVITY_EVENT")
                .sessionId(id)
                .activityEvent(event)
                .message(event.getDetail())
                .build());
        return ResponseEntity.ok(event);
    }

    @PostMapping(path = "/{id}/identity-capture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SessionResponse> updateIdentityCapture(@PathVariable String id,
                                                                 @RequestParam com.altimetrik.interview.enums.ParticipantRole role,
                                                                 @RequestParam com.altimetrik.interview.enums.IdentityCaptureStatus status,
                                                                 @RequestParam(required = false) com.altimetrik.interview.enums.IdentityCaptureFailureReason failureReason,
                                                                 @RequestPart(required = false) @Nullable MultipartFile image) {
        SessionResponse response = sessionService.updateIdentityCapture(id, role, status, failureReason, image);
        broadcastSession(response, "SESSION_STATE", "Identity capture updated");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/identity-capture/{role}")
    public ResponseEntity<org.springframework.core.io.Resource> getIdentityCapture(@PathVariable String id,
                                                                                    @PathVariable com.altimetrik.interview.enums.ParticipantRole role) {
        SessionService.ResourceWithMetadata resource = sessionService.getIdentityCaptureResource(id, role);
        MediaType contentType = resource.contentType() == null || resource.contentType().isBlank()
                ? MediaType.IMAGE_JPEG
                : MediaType.parseMediaType(resource.contentType());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(contentType)
                .body(resource.resource());
    }

    @GetMapping({"/{id}/final-preview", "/{id}/final-preview/", "/{id}/final-preview/**"})
    public ResponseEntity<org.springframework.core.io.Resource> getFinalPreview(@PathVariable String id,
                                                                                 HttpServletRequest request) {
        String assetPath = extractFinalPreviewAssetPath(request, id);
        SessionService.ResourceWithMetadata resource = sessionService.getFinalPreviewResource(id, assetPath);
        MediaType contentType = resource.contentType() == null || resource.contentType().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(resource.contentType());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(contentType)
                .body(resource.resource());
    }

    @MessageMapping("/session/{id}/code")
    public void handleCodeUpdate(@DestinationVariable String id, @Valid CodeUpdateRequest request) {
        SessionResponse response = sessionService.updateCodeState(id, request);
        messagingTemplate.convertAndSend("/topic/session/" + id, SessionSocketMessage.builder()
                .type("CODE_UPDATE")
                .sessionId(id)
                .code(response.getLatestCode())
                .version(response.getCodeVersion())
                .timeLeft(response.getRemainingSec())
                .session(response)
                .message("Code updated")
                .build());
    }

    @MessageMapping("/session/{id}/signal")
    public void relaySignal(@DestinationVariable String id, SessionSocketMessage request) {
        messagingTemplate.convertAndSend("/topic/session/" + id, SessionSocketMessage.builder()
                .type("WEBRTC_SIGNAL")
                .sessionId(id)
                .signalType(request.getSignalType())
                .senderRole(request.getSenderRole())
                .targetRole(request.getTargetRole())
                .sdp(request.getSdp())
                .candidate(request.getCandidate())
                .sdpMid(request.getSdpMid())
                .sdpMLineIndex(request.getSdpMLineIndex())
                .cameraEnabled(request.getCameraEnabled())
                .microphoneEnabled(request.getMicrophoneEnabled())
                .message("Realtime signal relayed")
                .build());
    }

    private void broadcastSession(SessionResponse response, String type, String message) {
        messagingTemplate.convertAndSend("/topic/session/" + response.getId(), SessionSocketMessage.builder()
                .type(type)
                .sessionId(response.getId())
                .code(response.getLatestCode())
                .version(response.getCodeVersion())
                .timeLeft(response.getRemainingSec())
                .session(response)
                .message(message)
                .build());
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractFinalPreviewAssetPath(HttpServletRequest request, String sessionId) {
        String requestUri = request.getRequestURI();
        String marker = "/sessions/" + sessionId + "/final-preview";
        int markerIndex = requestUri.indexOf(marker);
        if (markerIndex < 0) {
            return "";
        }

        String suffix = requestUri.substring(markerIndex + marker.length());
        if (suffix.startsWith("/")) {
            suffix = suffix.substring(1);
        }
        return suffix;
    }
}
