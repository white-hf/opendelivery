package com.hf.easydelivery.operations;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CreateWaveRequest(
        @NotBlank String stationCode,
        @NotBlank String waveCode,
        @NotNull LocalDate serviceDate,
        String routeCode,
        @NotNull Long driverId,
        @NotEmpty List<@NotBlank String> trackingNumbers
) {}
