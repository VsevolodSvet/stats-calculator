package com.kochmarev.statscalculator.result;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SubmitResult(
        @NotBlank @Size(max = 60) String assessmentId,
        @Positive int assessmentVersion,
        @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid Answer> answers) {

    public record Answer(
            @NotBlank @Size(max = 100) String questionId,
            @NotBlank @Size(max = 120) String optionId) {
    }
}
