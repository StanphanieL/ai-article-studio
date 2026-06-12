package com.nana.aiarticlestudio.service;

import com.nana.aiarticlestudio.common.PageResult;
import com.nana.aiarticlestudio.model.dto.ArticleCreateRequest;
import com.nana.aiarticlestudio.model.dto.ArticleListRequest;
import com.nana.aiarticlestudio.model.dto.ConfirmTitleRequest;
import com.nana.aiarticlestudio.model.vo.ArticleVO;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.nana.aiarticlestudio.model.dto.SaveOutlineRequest;

import com.nana.aiarticlestudio.model.dto.SaveTitleRequest;

import com.nana.aiarticlestudio.model.dto.SaveContentRequest;

public interface ArticleService {

    String createArticle(ArticleCreateRequest request);

    ArticleVO getByTaskId(String taskId);

    PageResult<ArticleVO> listArticles(ArticleListRequest request);

    Boolean deleteByTaskId(String taskId);

    ArticleVO generateTitles(String taskId);

    ArticleVO confirmTitleAndGenerateOutline(ConfirmTitleRequest request);

    ArticleVO saveTitle(SaveTitleRequest request);

    ArticleVO saveOutline(SaveOutlineRequest request);

    ArticleVO generateContent(String taskId);

    ArticleVO saveContent(SaveContentRequest request);

    ArticleVO generateImagePrompts(String taskId);

    ArticleVO continueWorkflow(String taskId);

    ArticleVO generateImageResults(String taskId, String provider);

    ArticleVO composeArticle(String taskId);

    SseEmitter streamGenerateContent(String taskId);

    SseEmitter realStreamGenerateContent(String taskId);

}