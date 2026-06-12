package com.nana.aiarticlestudio.agent;

public interface StreamingLlmClient {

    String chatStream(String prompt, StreamChunkHandler handler);
}