package com.nana.aiarticlestudio.agent;

import com.nana.aiarticlestudio.model.dto.LlmRequestOptions;

public interface StreamingLlmClient {

    String chatStream(
            String prompt,
            StreamChunkHandler handler
    );

    default String chatStream(
            String prompt,
            LlmRequestOptions options,
            StreamChunkHandler handler
    ) {
        return chatStream(
                prompt,
                handler
        );
    }
}