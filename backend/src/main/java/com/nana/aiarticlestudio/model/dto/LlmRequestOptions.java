package com.nana.aiarticlestudio.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlmRequestOptions {

    private String model;

    private Double temperature;

    private Integer maxCompletionTokens;
}