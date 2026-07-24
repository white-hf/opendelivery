package com.hf.easydelivery.config;

import com.hf.easydelivery.common.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class ApiKeyVerifier {
    private final byte[] upstreamKey;
    private final byte[] operationsKey;

    public ApiKeyVerifier(@Value("${opendelivery.security.upstream-api-key}") String upstreamKey,
                          @Value("${opendelivery.security.operations-api-key}") String operationsKey) {
        this.upstreamKey = upstreamKey.getBytes(StandardCharsets.UTF_8);
        this.operationsKey = operationsKey.getBytes(StandardCharsets.UTF_8);
    }

    public void requireUpstream(String supplied) {
        require(supplied, upstreamKey, "Invalid upstream API key");
    }

    public void requireOperations(String supplied) {
        require(supplied, operationsKey, "Invalid operations API key");
    }

    private void require(String supplied, byte[] expected, String message) {
        byte[] candidate = supplied == null ? new byte[0] : supplied.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, candidate)) throw new UnauthorizedException(message);
    }
}
