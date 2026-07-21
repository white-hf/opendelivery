package com.hf.easydelivery.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationsAuthorizationPolicyTest {
    @Test
    void inboundCanOperateManifestsButCannotDispatch() {
        assertTrue(OperationsAuthorizationPolicy.isAllowed(List.of("INBOUND"), "POST", "/ops/v1/manifests/1/start"));
        assertTrue(OperationsAuthorizationPolicy.isAllowed(List.of("INBOUND"), "GET", "/ops/v1/failed-returns"));
        assertTrue(OperationsAuthorizationPolicy.isAllowed(List.of("INBOUND"), "POST", "/ops/v1/failed-returns/1/receive"));
        assertFalse(OperationsAuthorizationPolicy.isAllowed(List.of("INBOUND"), "POST", "/ops/v1/dispatch/waves"));
    }

    @Test
    void dispatcherCanReadDriversAndPublishWavesButCannotReceive() {
        assertTrue(OperationsAuthorizationPolicy.isAllowed(List.of("DISPATCHER"), "GET", "/ops/v1/dispatch/drivers"));
        assertTrue(OperationsAuthorizationPolicy.isAllowed(List.of("DISPATCHER"), "POST", "/ops/v1/dispatch/waves/10/publish"));
        assertFalse(OperationsAuthorizationPolicy.isAllowed(List.of("DISPATCHER"), "POST", "/ops/v1/manifests/1/start"));
    }

    @Test
    void dispatcherCanResolveOrderReadinessWithoutBroadAreaMutationAccess() {
        assertTrue(OperationsAuthorizationPolicy.isAllowed(List.of("DISPATCHER"), "GET", "/ops/v1/planning/parcels"));
        assertTrue(OperationsAuthorizationPolicy.isAllowed(List.of("DISPATCHER"), "GET", "/ops/v1/delivery-areas"));
        assertTrue(OperationsAuthorizationPolicy.isAllowed(List.of("DISPATCHER"), "POST", "/ops/v1/parcels/42/area-match"));
        assertTrue(OperationsAuthorizationPolicy.isAllowed(List.of("DISPATCHER"), "POST", "/ops/v1/parcels/42/area-override"));
        assertFalse(OperationsAuthorizationPolicy.isAllowed(List.of("DISPATCHER"), "POST", "/ops/v1/delivery-areas"));
    }

    @Test
    void sharedReadViewsDoNotGrantMutationAccess() {
        assertTrue(OperationsAuthorizationPolicy.isAllowed(List.of("INBOUND"), "GET", "/ops/v1/cases"));
        assertFalse(OperationsAuthorizationPolicy.isAllowed(List.of("INBOUND"), "POST", "/ops/v1/cases/1/resolve"));
    }

    @Test
    void supervisorHasFullOperationsAccessButUserManagementRemainsAdminOnly() {
        assertTrue(OperationsAuthorizationPolicy.isAllowed(List.of("SUPERVISOR"), "POST", "/ops/v1/dispatch/waves"));
        assertFalse(OperationsAuthorizationPolicy.isAllowed(List.of("SUPERVISOR"), "POST", "/ops/v1/users"));
        assertTrue(OperationsAuthorizationPolicy.isAllowed(List.of("ADMIN"), "POST", "/ops/v1/users"));
    }
}
