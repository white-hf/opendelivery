package com.hf.easydelivery.config;

import com.hf.easydelivery.operations.auth.OperatorSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationsAccessTest {
    private final OperationsAccess access=new OperationsAccess();

    @AfterEach
    void resetContext(){RequestContextHolder.resetRequestAttributes();}

    @Test
    void administratorCanListAllStations() {
        requestWithRoles("ADMIN");
        assertTrue(access.canAccessAllStations());
    }

    @Test
    void stationOperatorCannotListAllStations() {
        requestWithRoles("DISPATCHER");
        assertFalse(access.canAccessAllStations());
    }

    private void requestWithRoles(String... roles) {
        var request=new MockHttpServletRequest();
        request.setAttribute("operatorPrincipal",new OperatorSessionService.Principal(
                201,"operator","Operator",1L,"YHZ-01",List.of(roles),"en-CA","en-CA"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
