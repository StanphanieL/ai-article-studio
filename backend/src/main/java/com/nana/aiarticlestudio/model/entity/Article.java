package com.nana.aiarticlestudio.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Article {

    private Long id;

    private String taskId;

    private String topic;

    private String style;

    private String phase;

    private String status;

    private String titleOptions;

    private String selectedTitle;

    private String outline;

    private String content;

    private String fullContent;

    private String errorMessage;

    private Integer isDeleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String imagePrompts;

    private String imageResults;

    private String finalMarkdown;
}