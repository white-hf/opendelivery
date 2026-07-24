package com.hf.easydelivery.integration.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

@Component
@Profile("!memory")
public class OutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper;
    private final RestClient restClient;

    public OutboxDispatcher(JdbcTemplate jdbc, TransactionTemplate transactions, ObjectMapper mapper,
                            RestClient.Builder restClientBuilder) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.mapper = mapper;
        this.restClient = restClientBuilder.build();
    }

    @Scheduled(fixedDelayString = "${opendelivery.outbox.poll-delay-ms:5000}")
    public void dispatch() {
        Claim claim = transactions.execute(status -> claimOne());
        if (claim == null) return;
        send(claim);
    }

    private Claim claimOne() {
        // ESCAPE-HATCH (ADR-Persistence): Queue polling with FOR UPDATE SKIP LOCKED and JSON_EXTRACT dialect functions retained via JdbcTemplate
        List<Claim> rows = jdbc.query("""
                SELECT oe.id, oe.attempt_count, CAST(oe.payload_json AS CHAR) payload,
                       JSON_UNQUOTE(JSON_EXTRACT(up.config_json, '$.callbackUrl')) callback_url,
                       JSON_UNQUOTE(JSON_EXTRACT(up.config_json, '$.apiKey')) api_key
                FROM outbox_event oe JOIN upstream_partner up ON up.id=oe.partner_id
                WHERE oe.status IN ('PENDING','RETRY') AND oe.next_attempt_at<=CURRENT_TIMESTAMP(3)
                  AND JSON_EXTRACT(up.config_json, '$.callbackUrl') IS NOT NULL
                ORDER BY oe.id LIMIT 1 FOR UPDATE SKIP LOCKED
                """, (rs, n) -> new Claim(rs.getLong("id"), rs.getInt("attempt_count") + 1,
                rs.getString("payload"), rs.getString("callback_url"), rs.getString("api_key")));
        if (rows.isEmpty()) return null;
        Claim claim = rows.get(0);
        jdbc.update("""
                UPDATE outbox_event SET status='SENDING', attempt_count=?, locked_at=CURRENT_TIMESTAMP(3),
                    locked_by=? WHERE id=?
                """, claim.attemptNo(), hostname(), claim.id());
        jdbc.update("""
                INSERT INTO callback_attempt
                (outbox_event_id, attempt_no, request_url, request_sha256, outcome, started_at)
                VALUES (?, ?, ?, SHA2(?,256), 'SENDING', CURRENT_TIMESTAMP(3))
                """, claim.id(), claim.attemptNo(), claim.url(), claim.payload());
        return claim;
    }

    private void send(Claim claim) {
        try {
            RestClient.RequestBodySpec request = restClient.post().uri(claim.url()).contentType(MediaType.APPLICATION_JSON);
            if (claim.apiKey() != null && !claim.apiKey().isBlank()) request.header("X-OpenDelivery-Api-Key", claim.apiKey());
            int responseStatus = request.body(claim.payload()).retrieve().toBodilessEntity().getStatusCode().value();
            jdbc.update("""
                    UPDATE callback_attempt SET outcome='ACKNOWLEDGED', response_status=?, completed_at=CURRENT_TIMESTAMP(3)
                    WHERE outbox_event_id=? AND attempt_no=?
                    """, responseStatus, claim.id(), claim.attemptNo());
            jdbc.update("""
                    UPDATE outbox_event SET status='ACKNOWLEDGED', acknowledged_at=CURRENT_TIMESTAMP(3),
                      locked_at=NULL, locked_by=NULL, last_error=NULL WHERE id=?
                    """, claim.id());
        } catch (Exception ex) {
            boolean dead = claim.attemptNo() >= 8;
            long delaySeconds = Math.min(3600, (long) Math.pow(2, Math.min(claim.attemptNo(), 10)) * 5);
            jdbc.update("""
                    UPDATE callback_attempt SET outcome='FAILED', error_message=?, completed_at=CURRENT_TIMESTAMP(3)
                    WHERE outbox_event_id=? AND attempt_no=?
                    """, truncate(ex.getMessage()), claim.id(), claim.attemptNo());
            jdbc.update("""
                    UPDATE outbox_event SET status=?, next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL ? SECOND),
                      locked_at=NULL, locked_by=NULL, last_error=? WHERE id=?
                    """, dead ? "DEAD_LETTER" : "RETRY", delaySeconds, truncate(ex.getMessage()), claim.id());
            log.warn("Upstream callback failed for outbox id={}, attempt={}", claim.id(), claim.attemptNo());
        }
    }

    private String hostname() {
        return System.getenv().getOrDefault("HOSTNAME", "local-worker");
    }

    private String truncate(String value) {
        if (value == null) return "Unknown callback error";
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private record Claim(long id, int attemptNo, String payload, String url, String apiKey) {}
}
