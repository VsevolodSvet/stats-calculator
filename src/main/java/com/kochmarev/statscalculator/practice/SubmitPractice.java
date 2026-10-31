package com.kochmarev.statscalculator.practice;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SubmitPractice(
        @NotBlank @Size(max = 60) String activityId,
        @Positive int activityVersion,
        @NotNull Mode mode,
        @NotNull InputMethod inputMethod,
        Long elapsedMs,
        @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid Selection> selections) {

    public enum Mode { UNTIMED, TIMED }
    public enum InputMethod { POINTER, KEYBOARD, MIXED }

    public record Selection(@NotBlank @Size(max = 100) String itemId,
                            @NotBlank @Size(max = 120) String optionId) {
    }
}
