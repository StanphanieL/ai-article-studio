package com.nana.aiarticlestudio.agent;

@FunctionalInterface
public interface StreamChunkHandler {

    void onChunk(String chunk);
}