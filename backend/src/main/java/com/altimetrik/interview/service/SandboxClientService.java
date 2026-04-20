package com.altimetrik.interview.service;

import com.altimetrik.interview.dto.CompileRequest;
import com.altimetrik.interview.dto.CompileResponse;
import com.altimetrik.interview.dto.ExecuteRequest;
import com.altimetrik.interview.dto.ExecuteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
@RequiredArgsConstructor
@Slf4j
public class SandboxClientService {

    @Qualifier("sandboxRestClient")
    private final RestClient sandboxRestClient;

    public CompileResponse compile(CompileRequest request) {
        return post("/compile", request, CompileResponse.class);
    }

    public ExecuteResponse execute(ExecuteRequest request) {
        return post("/compile/run", request, ExecuteResponse.class);
    }

    private <T> T post(String path, Object request, Class<T> responseType) {
        try {
            return Objects.requireNonNull(sandboxRestClient.post()
                    .uri(path)
                    .body(request)
                    .retrieve()
                    .body(responseType), "Sandbox response was empty for " + path);
        } catch (RestClientException ex) {
            log.error("Sandbox request failed for {}", path, ex);
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Sandbox service is unavailable");
        }
    }
}
