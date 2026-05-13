package com.altimetrik.interview.controller;

import com.altimetrik.interview.dto.PreparationAccessResponse;
import com.altimetrik.interview.dto.PreparationAttemptResponse;
import com.altimetrik.interview.dto.PreparationQuestionResponse;
import com.altimetrik.interview.dto.PreparationRegistrationRequest;
import com.altimetrik.interview.dto.PreparationRunRequest;
import com.altimetrik.interview.dto.PreparationRunResponse;
import com.altimetrik.interview.dto.PreparationSubmitResponse;
import com.altimetrik.interview.dto.VerifyOtpRequest;
import com.altimetrik.interview.service.PreparationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.eclipse.jdt.annotation.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/preparation")
@RequiredArgsConstructor
public class PreparationController {

    private final PreparationService preparationService;

    @PostMapping("/register")
    public ResponseEntity<PreparationAttemptResponse> register(@Valid @RequestBody PreparationRegistrationRequest request) {
        return ResponseEntity.ok(preparationService.register(request));
    }

    @GetMapping("/attempts")
    public ResponseEntity<Page<PreparationAttemptResponse>> listAttempts(Pageable pageable,
                                                                         @RequestParam(required = false) @Nullable String search) {
        Pageable effectivePageable = pageable == null || pageable.getSort().isUnsorted()
                ? PageRequest.of(
                pageable != null ? pageable.getPageNumber() : 0,
                pageable != null && pageable.getPageSize() > 0 ? pageable.getPageSize() : 20,
                Sort.by("createdAt").descending())
                : pageable;
        return ResponseEntity.ok(preparationService.listAttempts(search, effectivePageable));
    }

    @PostMapping("/attempts/{attemptId}/resend-otp")
    public ResponseEntity<PreparationAttemptResponse> resendOtp(@PathVariable String attemptId) {
        return ResponseEntity.ok(preparationService.resendOtp(attemptId));
    }

    @GetMapping("/access/{token}")
    public ResponseEntity<PreparationAccessResponse> getAccess(@PathVariable String token) {
        return ResponseEntity.ok(preparationService.getAccess(token));
    }

    @PostMapping("/access/{token}/disclaimer")
    public ResponseEntity<PreparationAccessResponse> acceptDisclaimer(@PathVariable String token) {
        return ResponseEntity.ok(preparationService.acceptDisclaimer(token));
    }

    @PostMapping("/access/{token}/verify-otp")
    public ResponseEntity<PreparationAccessResponse> verifyOtp(@PathVariable String token,
                                                               @Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(preparationService.verifyOtp(token, request));
    }

    @PostMapping("/access/{token}/expire")
    public ResponseEntity<PreparationAccessResponse> expireAttempt(@PathVariable String token) {
        return ResponseEntity.ok(preparationService.expireAttempt(token));
    }

    @GetMapping("/access/{token}/question")
    public ResponseEntity<PreparationQuestionResponse> currentQuestion(@PathVariable String token) {
        return ResponseEntity.ok(preparationService.currentQuestion(token));
    }

    @PostMapping("/access/{token}/run")
    public ResponseEntity<PreparationRunResponse> run(@PathVariable String token,
                                                      @Valid @RequestBody PreparationRunRequest request) {
        return ResponseEntity.ok(preparationService.run(token, request));
    }

    @PostMapping("/access/{token}/submit")
    public ResponseEntity<PreparationSubmitResponse> submit(@PathVariable String token,
                                                            @Valid @RequestBody PreparationRunRequest request) {
        return ResponseEntity.ok(preparationService.submit(token, request));
    }
}
