package com.nana.aiarticlestudio.model.dto;

import lombok.Data;

@Data
public class ArticleListRequest {

    private int pageNo = 1;

    private int pageSize = 10;
}