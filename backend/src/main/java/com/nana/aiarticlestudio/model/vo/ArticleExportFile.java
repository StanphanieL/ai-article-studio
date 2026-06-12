package com.nana.aiarticlestudio.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ArticleExportFile {

    private String fileName;

    private String contentType;

    private byte[] content;
}