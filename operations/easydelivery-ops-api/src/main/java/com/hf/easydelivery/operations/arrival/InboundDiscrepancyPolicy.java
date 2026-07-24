package com.hf.easydelivery.operations.arrival;

import com.hf.easydelivery.common.exception.BizException;

import java.util.Map;
import java.util.Set;

final class InboundDiscrepancyPolicy {
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "MISSING", Set.of("CONFIRM_MISSING"),
            "EXTRA", Set.of("QUARANTINE"),
            "WRONG_STATION", Set.of("QUARANTINE"),
            "DAMAGED", Set.of("ACCEPT_DAMAGED")
    );

    private InboundDiscrepancyPolicy() {}

    static String validate(String receiptStatus, String decision, String reason) {
        String normalized = decision == null ? "" : decision.trim().toUpperCase();
        if (reason == null || reason.isBlank()) {
            throw new BizException("MANIFEST.DECISION.REASON.REQUIRED", "A discrepancy decision requires a reason");
        }
        if (!ALLOWED.getOrDefault(receiptStatus, Set.of()).contains(normalized)) {
            throw new BizException("MANIFEST.DECISION.INVALID",
                    "Decision " + normalized + " is not valid for receipt status " + receiptStatus);
        }
        return normalized;
    }
}
