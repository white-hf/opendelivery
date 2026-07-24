package com.hf.easydelivery.operations;

import com.hf.easydelivery.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SystemConfigPolicyTest {

    @Test
    void validatesDriverInputs() {
        assertThrows(BizException.class, () -> SystemConfigPolicy.validateDriverInput(null, "John"));
        assertThrows(BizException.class, () -> SystemConfigPolicy.validateDriverInput("DRV001", ""));
        assertDoesNotThrow(() -> SystemConfigPolicy.validateDriverInput("DRV001", "John"));
    }

    @Test
    void validatesServiceAreaInputs() {
        assertThrows(BizException.class, () -> SystemConfigPolicy.validateServiceAreaInput("", "NS", "Halifax"));
        assertThrows(BizException.class, () -> SystemConfigPolicy.validateServiceAreaInput("CA", null, "Halifax"));
        assertDoesNotThrow(() -> SystemConfigPolicy.validateServiceAreaInput("CA", "NS", "Halifax"));
    }
}
