package com.hf.easydelivery.integration.ingestion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CanonicalShipmentRequest(
        @NotBlank String externalEventId,
        @NotBlank String externalWaybillNo,
        String externalVersion,
        @NotBlank String recipientName,
        String recipientPhone,
        @NotBlank String addressLine1,
        String addressLine2,
        @NotBlank String city,
        @NotBlank String province,
        @NotBlank String postalCode,
        String countryCode,
        String serviceCode,
        LocalDateTime deliveryWindowStart,
        LocalDateTime deliveryWindowEnd,
        LocalDate promisedDate,
        String targetStationCode,
        String externalManifestNo,
        @NotEmpty List<@NotBlank String> trackingNumbers,
        Double deliveryLatitude,
        Double deliveryLongitude,
        @Valid List<UnitDeclaration> handlingUnits
) {
    public record UnitDeclaration(@NotBlank String externalUnitNo, String unitType,
                                  List<@NotBlank String> trackingNumbers) {}
}
