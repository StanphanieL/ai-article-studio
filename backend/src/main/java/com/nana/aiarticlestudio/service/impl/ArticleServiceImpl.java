package com.nana.aiarticlestudio.service.impl;

import com.nana.aiarticlestudio.common.PageResult;
import com.nana.aiarticlestudio.mapper.ArticleMapper;
import com.nana.aiarticlestudio.model.dto.ArticleCreateRequest;
import com.nana.aiarticlestudio.model.dto.ArticleListRequest;
import com.nana.aiarticlestudio.model.entity.Article;
import com.nana.aiarticlestudio.model.vo.ArticleVO;
import com.nana.aiarticlestudio.service.ArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nana.aiarticlestudio.agent.OutlineGeneratorAgent;
import com.nana.aiarticlestudio.agent.TitleGeneratorAgent;
import com.nana.aiarticlestudio.model.dto.ConfirmTitleRequest;
import com.nana.aiarticlestudio.model.enums.ArticlePhase;
import com.nana.aiarticlestudio.model.enums.ArticleStatus;
import com.nana.aiarticlestudio.model.vo.OutlineItem;
import com.nana.aiarticlestudio.model.vo.TitleOption;
import com.nana.aiarticlestudio.agent.ContentGeneratorAgent;

import com.nana.aiarticlestudio.model.vo.SseMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import com.nana.aiarticlestudio.service.AgentLogService;

import com.nana.aiarticlestudio.agent.StreamingLlmClient;

import com.nana.aiarticlestudio.model.dto.SaveOutlineRequest;

import com.nana.aiarticlestudio.model.dto.SaveTitleRequest;

import com.nana.aiarticlestudio.model.dto.SaveContentRequest;

import com.nana.aiarticlestudio.agent.ImagePromptAgent;
import com.nana.aiarticlestudio.model.vo.ImagePromptOption;

import com.nana.aiarticlestudio.agent.ImageResultAgent;
import com.nana.aiarticlestudio.model.vo.ImageResultOption;

import com.nana.aiarticlestudio.agent.ArticleComposeAgent;

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private final ArticleMapper articleMapper;

    private final TitleGeneratorAgent titleGeneratorAgent;

    private final OutlineGeneratorAgent outlineGeneratorAgent;

    private final ObjectMapper objectMapper;

    private final ContentGeneratorAgent contentGeneratorAgent;

    private final AgentLogService agentLogService;

    private final StreamingLlmClient streamingLlmClient;

    private final ImagePromptAgent imagePromptAgent;

    private final ImageResultAgent imageResultAgent;

    private final ArticleComposeAgent articleComposeAgent;

    private void sendSse(SseEmitter emitter, String eventName, SseMessage message) throws IOException {
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(message));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String createArticle(ArticleCreateRequest request) {
        Article article = new Article();

        String taskId = UUID.randomUUID().toString().replace("-", "");
        String style = StringUtils.hasText(request.getStyle()) ? request.getStyle() : "TECH";

        article.setTaskId(taskId);
        article.setTopic(request.getTopic());
        article.setStyle(style);
        article.setPhase(ArticlePhase.CREATED.name());
        article.setStatus(ArticleStatus.INIT.name());

        int result = articleMapper.insert(article);
        if (result != 1) {
            throw new RuntimeException("创建文章任务失败");
        }

        return taskId;
    }

    @Override
    public ArticleVO getByTaskId(String taskId) {
        Article article = articleMapper.selectByTaskId(taskId);
        if (article == null) {
            throw new RuntimeException("文章任务不存在");
        }
        return ArticleVO.fromEntity(article);
    }

    @Override
    public PageResult<ArticleVO> listArticles(ArticleListRequest request) {
        int pageNo = request.getPageNo() <= 0 ? 1 : request.getPageNo();
        int pageSize = request.getPageSize() <= 0 ? 10 : request.getPageSize();

        if (pageSize > 50) {
            pageSize = 50;
        }

        int offset = (pageNo - 1) * pageSize;

        List<ArticleVO> records = articleMapper.list(offset, pageSize)
                .stream()
                .map(ArticleVO::fromEntity)
                .toList();

        long total = articleMapper.count();

        return new PageResult<>(total, pageNo, pageSize, records);
    }

    @Override
    public Boolean deleteByTaskId(String taskId) {
        int result = articleMapper.deleteByTaskId(taskId);
        return result > 0;
    }

    @Override
    public ArticleVO generateTitles(String taskId) {
        String prompt = null;
        long start = System.currentTimeMillis();

        try {
            Article article = articleMapper.selectByTaskId(taskId);
            if (article == null) {
                throw new RuntimeException("文章任务不存在");
            }

            prompt = titleGeneratorAgent.buildPrompt(
                    article.getTopic(),
                    article.getStyle()
            );

            String response = titleGeneratorAgent.callRaw(prompt);

            List<TitleOption> titleOptions = titleGeneratorAgent.parseWithRepair(response);

            long costMs = System.currentTimeMillis() - start;
            agentLogService.saveSuccess(
                    taskId,
                    "TitleGeneratorAgent",
                    prompt,
                    response,
                    costMs
            );

            String titleOptionsJson = objectMapper.writeValueAsString(titleOptions);

            int result = articleMapper.updateTitleOptions(
                    taskId,
                    titleOptionsJson,
                    ArticlePhase.TITLE_SELECTION.name(),
                    ArticleStatus.SUCCESS.name()
            );

            if (result != 1) {
                throw new RuntimeException("保存标题候选失败");
            }

            return getByTaskId(taskId);
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - start;
            agentLogService.saveFailed(
                    taskId,
                    "TitleGeneratorAgent",
                    prompt,
                    e.getMessage(),
                    costMs
            );
            articleMapper.updateFailedStatus(
                    taskId,
                    ArticlePhase.TITLE_SELECTION.name(),
                    ArticleStatus.FAILED.name(),
                    e.getMessage()
            );
            throw new RuntimeException("生成标题失败：" + e.getMessage());
        }
    }

    @Override
    public ArticleVO saveTitle(SaveTitleRequest request) {
        try {
            Article article = articleMapper.selectByTaskId(request.getTaskId());
            if (article == null) {
                throw new RuntimeException("文章任务不存在");
            }

            if (!StringUtils.hasText(request.getSelectedTitle())) {
                throw new RuntimeException("标题不能为空");
            }

            int result = articleMapper.updateSelectedTitle(
                    request.getTaskId(),
                    request.getSelectedTitle().trim(),
                    ArticlePhase.OUTLINE_EDITING.name(),
                    ArticleStatus.SUCCESS.name()
            );

            if (result != 1) {
                throw new RuntimeException("保存标题失败");
            }

            return getByTaskId(request.getTaskId());
        } catch (Exception e) {
            throw new RuntimeException("保存标题失败：" + e.getMessage());
        }
    }

    @Override
    public ArticleVO confirmTitleAndGenerateOutline(ConfirmTitleRequest request) {
        String prompt = null;
        long start = System.currentTimeMillis();

        try {
            Article article = articleMapper.selectByTaskId(request.getTaskId());
            if (article == null) {
                throw new RuntimeException("文章任务不存在");
            }

            prompt = outlineGeneratorAgent.buildPrompt(
                    article.getTopic(),
                    request.getSelectedTitle(),
                    article.getStyle()
            );

            String response = outlineGeneratorAgent.callRaw(prompt);

            List<OutlineItem> outline = outlineGeneratorAgent.parseWithRepair(response);

            long costMs = System.currentTimeMillis() - start;
            agentLogService.saveSuccess(
                    request.getTaskId(),
                    "OutlineGeneratorAgent",
                    prompt,
                    response,
                    costMs
            );

            String outlineJson = objectMapper.writeValueAsString(outline);

            int result = articleMapper.updateSelectedTitleAndOutline(
                    request.getTaskId(),
                    request.getSelectedTitle(),
                    outlineJson,
                    ArticlePhase.OUTLINE_EDITING.name(),
                    ArticleStatus.SUCCESS.name()
            );

            if (result != 1) {
                throw new RuntimeException("保存大纲失败");
            }

            return getByTaskId(request.getTaskId());
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - start;
            agentLogService.saveFailed(
                    request.getTaskId(),
                    "OutlineGeneratorAgent",
                    prompt,
                    e.getMessage(),
                    costMs
            );
            articleMapper.updateFailedStatus(
                    request.getTaskId(),
                    ArticlePhase.OUTLINE_EDITING.name(),
                    ArticleStatus.FAILED.name(),
                    e.getMessage()
            );
            throw new RuntimeException("生成大纲失败：" + e.getMessage());
        }
    }

    @Override
    public ArticleVO generateContent(String taskId) {
        String prompt = null;
        long start = System.currentTimeMillis();

        try {
            Article article = articleMapper.selectByTaskId(taskId);
            if (article == null) {
                throw new RuntimeException("文章任务不存在");
            }

            if (!StringUtils.hasText(article.getSelectedTitle())) {
                throw new RuntimeException("请先确认标题");
            }

            if (!StringUtils.hasText(article.getOutline())) {
                throw new RuntimeException("请先生成大纲");
            }

            prompt = contentGeneratorAgent.buildPrompt(
                    article.getTopic(),
                    article.getSelectedTitle(),
                    article.getOutline(),
                    article.getStyle()
            );

            String response = contentGeneratorAgent.callRaw(prompt);
            String content = contentGeneratorAgent.clean(response);

            long costMs = System.currentTimeMillis() - start;
            agentLogService.saveSuccess(
                    taskId,
                    "ContentGeneratorAgent",
                    prompt,
                    response,
                    costMs
            );

            int result = articleMapper.updateContent(
                    taskId,
                    content,
                    ArticlePhase.CONTENT_GENERATION.name(),
                    ArticleStatus.SUCCESS.name()
            );

            if (result != 1) {
                throw new RuntimeException("保存正文失败");
            }

            return getByTaskId(taskId);
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - start;
            agentLogService.saveFailed(
                    taskId,
                    "ContentGeneratorAgent",
                    prompt,
                    e.getMessage(),
                    costMs
            );
            articleMapper.updateFailedStatus(
                    taskId,
                    ArticlePhase.CONTENT_GENERATION.name(),
                    ArticleStatus.FAILED.name(),
                    e.getMessage()
            );
            throw new RuntimeException("生成正文失败：" + e.getMessage());
        }
    }

    @Override
    public ArticleVO saveContent(SaveContentRequest request) {
        try {
            Article article = articleMapper.selectByTaskId(request.getTaskId());
            if (article == null) {
                throw new RuntimeException("文章任务不存在");
            }

            if (!StringUtils.hasText(request.getContent())) {
                throw new RuntimeException("正文不能为空");
            }

            int result = articleMapper.updateContent(
                    request.getTaskId(),
                    request.getContent(),
                    ArticlePhase.CONTENT_GENERATION.name(),
                    ArticleStatus.SUCCESS.name()
            );

            if (result != 1) {
                throw new RuntimeException("保存正文失败");
            }

            return getByTaskId(request.getTaskId());
        } catch (Exception e) {
            throw new RuntimeException("保存正文失败：" + e.getMessage());
        }
    }

    @Override
    public SseEmitter streamGenerateContent(String taskId) {
        SseEmitter emitter = new SseEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                sendSse(emitter, "progress", new SseMessage(
                        "progress",
                        "开始检查文章任务",
                        null,
                        null,
                        null
                ));

                sleep(500);

                Article article = articleMapper.selectByTaskId(taskId);
                if (article == null) {
                    throw new RuntimeException("文章任务不存在");
                }

                sendSse(emitter, "progress", new SseMessage(
                        "progress",
                        "已找到文章任务：" + article.getTopic(),
                        null,
                        article.getPhase(),
                        article.getStatus()
                ));

                sleep(500);

                if (!StringUtils.hasText(article.getSelectedTitle())) {
                    throw new RuntimeException("请先确认标题");
                }

                if (!StringUtils.hasText(article.getOutline())) {
                    throw new RuntimeException("请先生成大纲");
                }

                sendSse(emitter, "progress", new SseMessage(
                        "progress",
                        "正在调用正文生成 Agent",
                        null,
                        article.getPhase(),
                        ArticleStatus.RUNNING.name()
                ));

                sleep(700);

                String prompt = contentGeneratorAgent.buildPrompt(
                        article.getTopic(),
                        article.getSelectedTitle(),
                        article.getOutline(),
                        article.getStyle()
                );

                long agentStart = System.currentTimeMillis();

                String response = contentGeneratorAgent.callRaw(prompt);
                String content = contentGeneratorAgent.clean(response);

                long costMs = System.currentTimeMillis() - agentStart;

                agentLogService.saveSuccess(
                        taskId,
                        "ContentGeneratorAgent",
                        prompt,
                        response,
                        costMs
                );

                sendSse(emitter, "progress", new SseMessage(
                        "progress",
                        "正文生成中，正在接收内容",
                        null,
                        ArticlePhase.CONTENT_GENERATION.name(),
                        ArticleStatus.RUNNING.name()
                ));

                String[] chunks = content.split("\\n\\s*\\n");
                StringBuilder streamedContent = new StringBuilder();

                for (String chunk : chunks) {
                    if (!StringUtils.hasText(chunk)) {
                        continue;
                    }

                    if (streamedContent.length() > 0) {
                        streamedContent.append("\n\n");
                    }

                    streamedContent.append(chunk);

                    sendSse(emitter, "content", new SseMessage(
                            "content",
                            "正文片段已生成",
                            streamedContent.toString(),
                            ArticlePhase.CONTENT_GENERATION.name(),
                            ArticleStatus.RUNNING.name()
                    ));

                    sleep(300);
                }

                int result = articleMapper.updateContent(
                        taskId,
                        content,
                        ArticlePhase.CONTENT_GENERATION.name(),
                        ArticleStatus.SUCCESS.name()
                );

                if (result != 1) {
                    throw new RuntimeException("保存正文失败");
                }

                sendSse(emitter, "done", new SseMessage(
                        "done",
                        "正文生成并保存成功",
                        content,
                        ArticlePhase.CONTENT_GENERATION.name(),
                        ArticleStatus.SUCCESS.name()
                ));

                emitter.complete();
            } catch (Exception e) {
                try {
                    sendSse(emitter, "fail", new SseMessage(
                            "fail",
                            e.getMessage(),
                            null,
                            ArticlePhase.FAILED.name(),
                            ArticleStatus.FAILED.name()
                    ));
                    agentLogService.saveFailed(
                            taskId,
                            "ContentGeneratorAgent",
                            null,
                            e.getMessage(),
                            0L
                    );
                    articleMapper.updateFailedStatus(
                            taskId,
                            ArticlePhase.CONTENT_GENERATION.name(),
                            ArticleStatus.FAILED.name(),
                            e.getMessage()
                    );
                } catch (IOException ignored) {
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @Override
    public SseEmitter realStreamGenerateContent(String taskId) {
        SseEmitter emitter = new SseEmitter(0L);

        CompletableFuture.runAsync(() -> {
            String prompt = null;
            long start = System.currentTimeMillis();

            try {
                sendSse(emitter, "progress", new SseMessage(
                        "progress",
                        "开始检查文章任务",
                        null,
                        null,
                        null
                ));

                Article article = articleMapper.selectByTaskId(taskId);
                if (article == null) {
                    throw new RuntimeException("文章任务不存在");
                }

                if (!StringUtils.hasText(article.getSelectedTitle())) {
                    throw new RuntimeException("请先确认标题");
                }

                if (!StringUtils.hasText(article.getOutline())) {
                    throw new RuntimeException("请先生成大纲");
                }

                sendSse(emitter, "progress", new SseMessage(
                        "progress",
                        "正在连接真实大模型流式接口",
                        null,
                        ArticlePhase.CONTENT_GENERATION.name(),
                        ArticleStatus.RUNNING.name()
                ));

                prompt = contentGeneratorAgent.buildPrompt(
                        article.getTopic(),
                        article.getSelectedTitle(),
                        article.getOutline(),
                        article.getStyle()
                );

                StringBuilder streamedContent = new StringBuilder();

                String rawContent = streamingLlmClient.chatStream(prompt, chunk -> {
                    try {
                        streamedContent.append(chunk);

                        String cleaned = contentGeneratorAgent.clean(streamedContent.toString());

                        sendSse(emitter, "content", new SseMessage(
                                "content",
                                "正文生成中",
                                cleaned,
                                ArticlePhase.CONTENT_GENERATION.name(),
                                ArticleStatus.RUNNING.name()
                        ));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

                String finalContent = contentGeneratorAgent.clean(rawContent);

                int result = articleMapper.updateContent(
                        taskId,
                        finalContent,
                        ArticlePhase.CONTENT_GENERATION.name(),
                        ArticleStatus.SUCCESS.name()
                );

                if (result != 1) {
                    throw new RuntimeException("保存正文失败");
                }

                long costMs = System.currentTimeMillis() - start;

                agentLogService.saveSuccess(
                        taskId,
                        "ContentGeneratorAgent-RealStream",
                        prompt,
                        rawContent,
                        costMs
                );

                sendSse(emitter, "done", new SseMessage(
                        "done",
                        "真实流式正文生成并保存成功",
                        finalContent,
                        ArticlePhase.CONTENT_GENERATION.name(),
                        ArticleStatus.SUCCESS.name()
                ));

                emitter.complete();
            } catch (Exception e) {
                long costMs = System.currentTimeMillis() - start;

                agentLogService.saveFailed(
                        taskId,
                        "ContentGeneratorAgent-RealStream",
                        prompt,
                        e.getMessage(),
                        costMs
                );
                articleMapper.updateFailedStatus(
                        taskId,
                        ArticlePhase.CONTENT_GENERATION.name(),
                        ArticleStatus.FAILED.name(),
                        e.getMessage()
                );

                try {
                    sendSse(emitter, "fail", new SseMessage(
                            "fail",
                            e.getMessage(),
                            null,
                            ArticlePhase.FAILED.name(),
                            ArticleStatus.FAILED.name()
                    ));
                } catch (Exception ignored) {
                }

                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @Override
    public ArticleVO generateImagePrompts(String taskId) {
        long start = System.currentTimeMillis();
        String prompt = null;
        String response = null;

        try {
            Article article = articleMapper.selectByTaskId(taskId);
            if (article == null) {
                throw new RuntimeException("文章任务不存在");
            }

            if (!StringUtils.hasText(article.getSelectedTitle())) {
                throw new RuntimeException("请先确认标题");
            }

            if (!StringUtils.hasText(article.getContent())) {
                throw new RuntimeException("请先生成正文");
            }

            prompt = imagePromptAgent.buildPrompt(
                    article.getTopic(),
                    article.getSelectedTitle(),
                    article.getOutline(),
                    article.getContent(),
                    article.getStyle()
            );

            response = imagePromptAgent.callRaw(prompt);

            List<ImagePromptOption> imagePrompts = imagePromptAgent.parse(response);
            String imagePromptsJson = objectMapper.writeValueAsString(imagePrompts);

            int result = articleMapper.updateImagePrompts(
                    taskId,
                    imagePromptsJson,
                    ArticlePhase.CONTENT_GENERATION.name(),
                    ArticleStatus.SUCCESS.name()
            );

            if (result != 1) {
                throw new RuntimeException("保存图片提示词失败");
            }

            long costMs = System.currentTimeMillis() - start;

            agentLogService.saveSuccess(
                    taskId,
                    "ImagePromptAgent",
                    prompt,
                    response,
                    costMs
            );

            return getByTaskId(taskId);
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - start;

            agentLogService.saveFailed(
                    taskId,
                    "ImagePromptAgent",
                    prompt,
                    e.getMessage(),
                    costMs
            );

            articleMapper.updateFailedStatus(
                    taskId,
                    ArticlePhase.CONTENT_GENERATION.name(),
                    ArticleStatus.FAILED.name(),
                    e.getMessage()
            );

            throw new RuntimeException("生成图片提示词失败：" + e.getMessage());
        }
    }

    @Override
    public ArticleVO saveOutline(SaveOutlineRequest request) {
        try {
            Article article = articleMapper.selectByTaskId(request.getTaskId());
            if (article == null) {
                throw new RuntimeException("文章任务不存在");
            }

            if (request.getOutline() == null || request.getOutline().isEmpty()) {
                throw new RuntimeException("大纲不能为空");
            }

            for (int i = 0; i < request.getOutline().size(); i++) {
                OutlineItem item = request.getOutline().get(i);

                if (item == null
                        || !StringUtils.hasText(item.getHeading())
                        || !StringUtils.hasText(item.getDescription())) {
                    throw new RuntimeException("第 " + (i + 1) + " 个大纲项不完整");
                }
            }

            String outlineJson = objectMapper.writeValueAsString(request.getOutline());

            int result = articleMapper.updateOutline(
                    request.getTaskId(),
                    outlineJson,
                    ArticlePhase.OUTLINE_EDITING.name(),
                    ArticleStatus.SUCCESS.name()
            );

            if (result != 1) {
                throw new RuntimeException("保存大纲失败");
            }

            return getByTaskId(request.getTaskId());
        } catch (Exception e) {
            throw new RuntimeException("保存大纲失败：" + e.getMessage());
        }
    }

    @Override
    public ArticleVO continueWorkflow(String taskId) {
        Article article = articleMapper.selectByTaskId(taskId);
        if (article == null) {
            throw new RuntimeException("文章任务不存在");
        }

        if (ArticleStatus.FAILED.name().equals(article.getStatus())) {
            throw new RuntimeException("当前任务处于失败状态，请先根据失败处理按钮重试");
        }

        String phase = article.getPhase();

        if (ArticlePhase.CREATED.name().equals(phase)) {
            return generateTitles(taskId);
        }

        if (ArticlePhase.TITLE_SELECTION.name().equals(phase)) {
            if (!StringUtils.hasText(article.getSelectedTitle())) {
                throw new RuntimeException("请先选择一个标题，再继续生成大纲");
            }

            ConfirmTitleRequest request = new ConfirmTitleRequest();
            request.setTaskId(taskId);
            request.setSelectedTitle(article.getSelectedTitle());

            return confirmTitleAndGenerateOutline(request);
        }

        if (ArticlePhase.OUTLINE_EDITING.name().equals(phase)) {
            if (!StringUtils.hasText(article.getOutline())) {
                throw new RuntimeException("请先生成或保存大纲，再继续生成正文");
            }

            return generateContent(taskId);
        }

        if (ArticlePhase.CONTENT_GENERATION.name().equals(phase)) {
            if (!StringUtils.hasText(article.getContent())) {
                return generateContent(taskId);
            }

            if (!StringUtils.hasText(article.getImagePrompts())) {
                return generateImagePrompts(taskId);
            }

            if (!StringUtils.hasText(article.getImageResults())) {
                return generateImageResults(taskId, "AUTO");
            }

            throw new RuntimeException("当前文章主线流程已完成");
        }

        throw new RuntimeException("当前阶段暂不支持继续下一步：" + phase);
    }

    @Override
    public ArticleVO generateImageResults(String taskId, String provider) {
        long start = System.currentTimeMillis();
        String inputText = null;
        String outputText = null;

        try {
            Article article = articleMapper.selectByTaskId(taskId);
            if (article == null) {
                throw new RuntimeException("文章任务不存在");
            }

            if (!StringUtils.hasText(article.getImagePrompts())) {
                throw new RuntimeException("请先生成配图提示词");
            }

            inputText = "provider=" + provider + "\n\n" + article.getImagePrompts();

            List<ImageResultOption> imageResults = imageResultAgent.generate(
                    article.getImagePrompts(),
                    provider
            );

            String imageResultsJson = objectMapper.writeValueAsString(imageResults);
            outputText = imageResultsJson;

            int result = articleMapper.updateImageResults(
                    taskId,
                    imageResultsJson,
                    ArticlePhase.CONTENT_GENERATION.name(),
                    ArticleStatus.SUCCESS.name()
            );

            if (result != 1) {
                throw new RuntimeException("保存图片结果失败");
            }

            long costMs = System.currentTimeMillis() - start;

            agentLogService.saveSuccess(
                    taskId,
                    "ImageResultAgent",
                    inputText,
                    outputText,
                    costMs
            );

            return getByTaskId(taskId);
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - start;

            agentLogService.saveFailed(
                    taskId,
                    "ImageResultAgent",
                    inputText,
                    e.getMessage(),
                    costMs
            );

            articleMapper.updateFailedStatus(
                    taskId,
                    ArticlePhase.CONTENT_GENERATION.name(),
                    ArticleStatus.FAILED.name(),
                    e.getMessage()
            );

            throw new RuntimeException("生成图片结果失败：" + e.getMessage());
        }
    }

    @Override
    public ArticleVO composeArticle(String taskId) {
        long start =
                System.currentTimeMillis();

        String inputText = null;
        String outputText = null;

        try {
            Article article =
                    articleMapper.selectByTaskId(
                            taskId
                    );

            if (article == null) {
                throw new RuntimeException(
                        "文章任务不存在"
                );
            }

            if (!StringUtils.hasText(
                    article.getContent()
            )) {
                throw new RuntimeException(
                        "请先生成文章正文"
                );
            }

            if (!StringUtils.hasText(
                    article.getImageResults()
            )) {
                throw new RuntimeException(
                        "请先生成图片结果"
                );
            }

            /*
             * 记录 Agent 输入。
             * 为了方便调试，保存标题、正文和图片结果。
             */
            inputText =
                    "title="
                            + article.getSelectedTitle()
                            + "\n\ncontent=\n"
                            + article.getContent()
                            + "\n\nimageResults=\n"
                            + article.getImageResults();

            outputText =
                    articleComposeAgent.compose(
                            article.getSelectedTitle(),
                            article.getContent(),
                            article.getImageResults()
                    );

            int updated =
                    articleMapper.updateFinalMarkdown(
                            taskId,
                            outputText,
                            ArticlePhase
                                    .ARTICLE_COMPOSITION
                                    .name(),
                            ArticleStatus
                                    .SUCCESS
                                    .name()
                    );

            if (updated != 1) {
                throw new RuntimeException(
                        "保存最终图文稿失败"
                );
            }

            long costMs =
                    System.currentTimeMillis()
                            - start;

            agentLogService.saveSuccess(
                    taskId,
                    "ArticleComposeAgent",
                    inputText,
                    outputText,
                    costMs
            );

            return getByTaskId(taskId);

        } catch (Exception e) {
            long costMs =
                    System.currentTimeMillis()
                            - start;

            agentLogService.saveFailed(
                    taskId,
                    "ArticleComposeAgent",
                    inputText,
                    e.getMessage(),
                    costMs
            );

            articleMapper.updateFailedStatus(
                    taskId,
                    ArticlePhase
                            .ARTICLE_COMPOSITION
                            .name(),
                    ArticleStatus
                            .FAILED
                            .name(),
                    e.getMessage()
            );

            throw new RuntimeException(
                    "图文合成失败："
                            + e.getMessage(),
                    e
            );
        }
    }

}