package com.hf.easydelivery.operations;

import com.hf.easydelivery.common.exception.BizException;

import java.math.BigDecimal;
import java.time.LocalDate;

final class DeliveryAreaInputPolicy {
    private DeliveryAreaInputPolicy() {}

    static void coordinates(double longitude,double latitude,BigDecimal confidence) {
        if(!Double.isFinite(longitude)||!Double.isFinite(latitude)||longitude < -180||longitude > 180||latitude < -90||latitude > 90)
            throw new BizException("AREA.COORDINATE.INVALID","Longitude or latitude is outside WGS84 bounds");
        if(confidence!=null&&(confidence.compareTo(BigDecimal.ZERO)<0||confidence.compareTo(BigDecimal.ONE)>0))
            throw new BizException("AREA.CONFIDENCE.INVALID","Geocode confidence must be between 0 and 1");
    }

    static void preference(Integer priority,LocalDate from,LocalDate to) {
        if(priority!=null&&(priority<1||priority>1000))
            throw new BizException("AREA.PREFERENCE.INVALID","Preference priority must be between 1 and 1000");
        if(from!=null&&to!=null&&to.isBefore(from))
            throw new BizException("AREA.PREFERENCE.INVALID","Preference effectiveTo cannot precede effectiveFrom");
    }
}
