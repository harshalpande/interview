package com.altimetrik.interview.controller;

import com.altimetrik.interview.dto.AcceptDisclaimerRequest;
import com.altimetrik.interview.dto.ActivityEventDto;
import com.altimetrik.interview.dto.ActivityEventRequest;
import com.altimetrik.interview.dto.CodeUpdateRequest;
import com.altimetrik.interview.dto.CreateSessionRequest;
import com.altimetrik.interview.dto.AbandonSessionRequest;
import com.altimetrik.interview.dto.EndSessionRequest;
import com.altimetrik.interview.dto.FeedbackRequest;
import com.altimetrik.interview.dto.JoinSessionRequest;
import com.altimetrik.interview.dto.SessionResponse;
import com.altimetrik.interview.dto.SessionSocketMessage;
import com.altimetrik.interview.dto.ValidateTokenResponse;
import com.altimetrik.interview.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

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
                                          @RequestParam(required = false) String search) {
        if (pageable == null || pageable.getSort().isUnsorted()) {
            Pageable defaultPageable = PageRequest.of(
                pageable != null ? pageable.getPageNumber() : 0,
                pageable != null && pageable.getPageSize() > 0 ? pageable.getPageSize() : 20,
                Sort.by("createdAt").descending()
            );
            return ResponseEntity.ok(sessionService.listSessions(defaultPageable, search));
        }
        return ResponseEntity.ok(sessionService.listSessions(pageable, search));
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
                                                       @Valid @RequestBody JoinSessionRequest request) {
        SessionResponse response = sessionService.joinSession(token, request);
        broadcastSession(response, "USER_JOINED", "Interviewee joined");
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
                                                          @RequestBody(required = false) AbandonSessionRequest request) {
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
                                                                 @RequestPart(required = false) MultipartFile image) {
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
}
