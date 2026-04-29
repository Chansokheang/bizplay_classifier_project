package com.api.bizplay_classifier_api.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TrainingDataTrainRequest {
    @NotNull(message = "Corp no can not be null.")
    private String corpNo;

    @Valid
    @NotEmpty(message = "Training data list can not be empty.")
    private List<TrainingDataRowRequest> trainingData;
}
