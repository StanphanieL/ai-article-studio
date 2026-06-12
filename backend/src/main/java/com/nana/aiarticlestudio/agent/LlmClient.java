package com.nana.aiarticlestudio.agent;

import com.nana.aiarticlestudio.model.dto.LlmRequestOptions;

public interface LlmClient {

    String chat(String prompt);

    default String chat(
            String prompt,
            LlmRequestOptions options
    ) {
        return chat(prompt);
    }
}