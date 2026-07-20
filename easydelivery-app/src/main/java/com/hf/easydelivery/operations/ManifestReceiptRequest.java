package com.hf.easydelivery.operations;

import jakarta.validation.constraints.NotBlank;

public record ManifestReceiptRequest(@NotBlank String trackingNumber) {}
