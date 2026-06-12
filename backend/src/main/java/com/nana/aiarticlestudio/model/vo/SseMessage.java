package com.nana.aiarticlestudio.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SseMessage {

    private String type;

    private String message;

    private String content;

    private String phase;

    private String status;
}