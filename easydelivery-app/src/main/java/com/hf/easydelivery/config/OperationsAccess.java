package com.hf.easydelivery.config;

import com.hf.easydelivery.common.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class OperationsAccess {
    public void requireStation(long resourceStationId) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return;
        HttpServletRequest request = attributes.getRequest();
        if (Boolean.TRUE.equals(request.getAttribute("legacyOpsApiKey"))) return;
        Object selected = request.getAttribute("operatorStationId");
        if (!(selected instanceof Long stationId) || stationId != resourceStationId) {
            throw new ForbiddenException("Resource belongs to another station");
        }
    }

    public Long selectedStationId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return null;
        Object value = attributes.getRequest().getAttribute("operatorStationId");
        return value instanceof Long stationId ? stationId : null;
    }
}
