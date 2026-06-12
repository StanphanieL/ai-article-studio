package com.nana.aiarticlestudio.controller;

import com.nana.aiarticlestudio.common.BaseResponse;
import com.nana.aiarticlestudio.common.PageResult;
import com.nana.aiarticlestudio.model.dto.ArticleCreateRequest;
import com.nana.aiarticlestudio.model.dto.ArticleListRequest;
import com.nana.aiarticlestudio.model.dto.ConfirmTitleRequest;
import com.nana.aiarticlestudio.model.dto.SaveContentRequest;
import com.nana.aiarticlestudio.model.dto.SaveModelConfigRequest;
import com.nana.aiarticlestudio.model.dto.SaveOutlineRequest;
import com.nana.aiarticlestudio.model.dto.SaveTitleRequest;
import com.nana.aiarticlestudio.model.vo.AgentLogVO;
import com.nana.aiarticlestudio.model.vo.ArticleVO;
import com.nana.aiarticlestudio.service.AgentLogService;
import com.nana.aiarticlestudio.service.ArticleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.nana.aiarticlestudio.model.vo.ArticleExportFile;
import com.nana.aiarticlestudio.service.ArticleExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

import java.util.List;

@RestController
@RequestMapping("/api/article")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    private final AgentLogService agentLogService;

    private final ArticleExportService articleExportService;

    @PostMapping("/create")
    public BaseResponse<String> createArticle(
            @Valid
            @RequestBody
            ArticleCreateRequest request
    ) {
        return BaseResponse.success(
                articleService.createArticle(request)
        );
    }

    @GetMapping("/{taskId}")
    public BaseResponse<ArticleVO> getArticle(
            @PathVariable String taskId
    ) {
        return BaseResponse.success(
                articleService.getByTaskId(taskId)
        );
    }

    @PostMapping("/list")
    public BaseResponse<PageResult<ArticleVO>>
    listArticles(
            @RequestBody
            ArticleListRequest request
    ) {
        return BaseResponse.success(
                articleService.listArticles(request)
        );
    }

    @PostMapping("/delete/{taskId}")
    public BaseResponse<Boolean> deleteArticle(
            @PathVariable String taskId
    ) {
        return BaseResponse.success(
                articleService.deleteByTaskId(taskId)
        );
    }

    @PostMapping("/generate-titles/{taskId}")
    public BaseResponse<ArticleVO> generateTitles(
            @PathVariable String taskId
    ) {
        return BaseResponse.success(
                articleService.generateTitles(taskId)
        );
    }

    @PostMapping("/confirm-title")
    public BaseResponse<ArticleVO> confirmTitle(
            @Valid
            @RequestBody
            ConfirmTitleRequest request
    ) {
        return BaseResponse.success(
                articleService
                        .confirmTitleAndGenerateOutline(
                                request
                        )
        );
    }

    @PostMapping("/generate-content/{taskId}")
    public BaseResponse<ArticleVO> generateContent(
            @PathVariable String taskId
    ) {
        return BaseResponse.success(
                articleService.generateContent(taskId)
        );
    }

    @PostMapping("/save-content")
    public BaseResponse<ArticleVO> saveContent(
            @Valid
            @RequestBody
            SaveContentRequest request
    ) {
        return BaseResponse.success(
                articleService.saveContent(request)
        );
    }

    @GetMapping(
            value = "/stream-generate-content/{taskId}",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter streamGenerateContent(
            @PathVariable String taskId
    ) {
        return articleService
                .streamGenerateContent(taskId);
    }

    @GetMapping(
            value = "/real-stream-generate-content/{taskId}",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter realStreamGenerateContent(
            @PathVariable String taskId
    ) {
        return articleService
                .realStreamGenerateContent(taskId);
    }

    @GetMapping("/agent-logs/{taskId}")
    public BaseResponse<List<AgentLogVO>>
    listAgentLogs(
            @PathVariable String taskId
    ) {
        return BaseResponse.success(
                agentLogService
                        .listByTaskId(taskId)
        );
    }

    @PostMapping("/save-outline")
    public BaseResponse<ArticleVO> saveOutline(
            @Valid
            @RequestBody
            SaveOutlineRequest request
    ) {
        return BaseResponse.success(
                articleService.saveOutline(request)
        );
    }

    @PostMapping("/save-title")
    public BaseResponse<ArticleVO> saveTitle(
            @Valid
            @RequestBody
            SaveTitleRequest request
    ) {
        return BaseResponse.success(
                articleService.saveTitle(request)
        );
    }

    @PostMapping("/generate-image-prompts/{taskId}")
    public BaseResponse<ArticleVO>
    generateImagePrompts(
            @PathVariable String taskId
    ) {
        return BaseResponse.success(
                articleService
                        .generateImagePrompts(taskId)
        );
    }

    @PostMapping("/continue-workflow/{taskId}")
    public BaseResponse<ArticleVO>
    continueWorkflow(
            @PathVariable String taskId
    ) {
        return BaseResponse.success(
                articleService
                        .continueWorkflow(taskId)
        );
    }

    @PostMapping("/generate-image-results/{taskId}")
    public BaseResponse<ArticleVO>
    generateImageResults(
            @PathVariable String taskId,
            @RequestParam(
                    defaultValue = "AUTO"
            )
            String provider
    ) {
        return BaseResponse.success(
                articleService
                        .generateImageResults(
                                taskId,
                                provider
                        )
        );
    }

    @PostMapping("/compose/{taskId}")
    public BaseResponse<ArticleVO>
    composeArticle(
            @PathVariable String taskId
    ) {
        return BaseResponse.success(
                articleService
                        .composeArticle(taskId)
        );
    }

    @PostMapping("/save-model-config")
    public BaseResponse<ArticleVO>
    saveModelConfig(
            @Valid
            @RequestBody
            SaveModelConfigRequest request
    ) {
        return BaseResponse.success(
                articleService
                        .saveModelConfig(request)
        );
    }

    @GetMapping("/export-html/{taskId}")
    public ResponseEntity<byte[]> exportHtml(
            @PathVariable String taskId
    ) {
        ArticleExportFile file =
                articleExportService
                        .exportHtml(taskId);

        return buildExportResponse(file);
    }

    @GetMapping("/export-zip/{taskId}")
    public ResponseEntity<byte[]> exportZip(
            @PathVariable String taskId
    ) {
        ArticleExportFile file =
                articleExportService
                        .exportZip(taskId);

        return buildExportResponse(file);
    }

    private ResponseEntity<byte[]>
    buildExportResponse(
            ArticleExportFile file
    ) {
        ContentDisposition disposition =
                ContentDisposition
                        .attachment()
                        .filename(
                                file.getFileName(),
                                StandardCharsets.UTF_8
                        )
                        .build();

        return ResponseEntity
                .ok()
                .header(
                        HttpHeaders
                                .CONTENT_DISPOSITION,
                        disposition.toString()
                )
                .contentType(
                        MediaType.parseMediaType(
                                file.getContentType()
                        )
                )
                .contentLength(
                        file.getContent().length
                )
                .body(
                        file.getContent()
                );
    }
}