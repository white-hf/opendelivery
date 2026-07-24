package com.hf.easydelivery.operations;

import com.hf.easydelivery.common.exception.BizException;

final class FailedParcelReturnPolicy {
    private FailedParcelReturnPolicy() {}

    static boolean isAlreadyReceived(String status, String custodyType) {
        return "RETURNED_TO_STATION".equals(status) && "STATION".equals(custodyType);
    }

    static void requireReceivable(String status, String custodyType, Long taskItemId,
                                  Long taskId, Long taskDriverId, Long custodyId) {
        if (!"DELIVERY_FAILED".equals(status) || !"DRIVER".equals(custodyType) || taskItemId == null) {
            throw new BizException("RETURN.PARCEL.NOT_RECEIVABLE", "Parcel is not a failed parcel in driver custody");
        }
        if (taskId == null || taskDriverId == null || !taskDriverId.equals(custodyId)) {
            throw new BizException("RETURN.CUSTODY.MISMATCH", "Driver task and parcel custody do not match");
        }
    }
}
