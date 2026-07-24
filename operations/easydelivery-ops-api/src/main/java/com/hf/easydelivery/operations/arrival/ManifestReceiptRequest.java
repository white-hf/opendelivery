package com.hf.easydelivery.operations.arrival;

import jakarta.validation.constraints.NotBlank;

public record ManifestReceiptRequest(@NotBlank String trackingNumber) {}
