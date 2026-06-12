package com.nana.aiarticlestudio.agent;

import com.nana.aiarticlestudio.model.enums.JsonRepairType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonRepairAgent {

    private final LlmClient llmClient;

    public String repair(JsonRepairType type, String rawOutput) {
        String prompt = buildPrompt(type, rawOutput);
        return llmClient.chat(prompt);
    }

    public String buildPrompt(JsonRepairType type, String rawOutput) {
        if (type == JsonRepairType.TITLE_OPTIONS) {
            return buildTitleRepairPrompt(rawOutput);
        }

        if (type == JsonRepairType.OUTLINE) {
            return buildOutlineRepairPrompt(rawOutput);
        }

        throw new RuntimeException("不支持的 JSON 修复类型：" + type);
    }

    private String buildTitleRepairPrompt(String rawOutput) {
        return """
                你是一个严格的 JSON 修复器。
                下面是一段模型生成的标题候选内容，但它可能不是合法 JSON，可能包含思考过程、Markdown、解释文字、残缺 JSON 或多余内容。

                你的任务：
                只把它修复为合法 JSON 数组。

                修复要求：
                1. 必须只输出 JSON 数组
                2. 不要输出 Markdown
                3. 不要输出 ```json
                4. 不要输出任何解释性文字
                5. 不要输出 <think>、</think> 或任何思考过程
                6. JSON 数组必须完整闭合，最后一个字符必须是 ]
                7. 数组中必须正好有 3 个对象
                8. 每个对象必须包含 title 和 reason 两个字段
                9. 如果原始内容超过 3 个标题，只保留最好的 3 个
                10. 如果原始内容少于 3 个标题，请根据上下文补足到 3 个
                11. 不要改变字段名

                合法格式示例：
                [
                  {
                    "title": "标题1",
                    "reason": "推荐理由1"
                  },
                  {
                    "title": "标题2",
                    "reason": "推荐理由2"
                  },
                  {
                    "title": "标题3",
                    "reason": "推荐理由3"
                  }
                ]

                原始内容如下：
                %s
                """.formatted(rawOutput);
    }

    private String buildOutlineRepairPrompt(String rawOutput) {
        return """
                你是一个严格的 JSON 修复器。
                下面是一段模型生成的文章大纲内容，但它可能不是合法 JSON，可能包含思考过程、Markdown、解释文字、残缺 JSON、章节数量不对或多余内容。

                你的任务：
                只把它修复为合法 JSON 数组。

                修复要求：
                1. 必须只输出 JSON 数组
                2. 不要输出 Markdown
                3. 不要输出 ```json
                4. 不要输出任何解释性文字
                5. 不要输出 <think>、</think> 或任何思考过程
                6. JSON 数组必须完整闭合，最后一个字符必须是 ]
                7. 数组中必须正好有 5 个对象
                8. 每个对象必须包含 heading 和 description 两个字段
                9. 如果原始内容超过 5 个章节，请合并或保留最关键的 5 个
                10. 如果原始内容少于 5 个章节，请根据上下文补足到 5 个
                11. 不要改变字段名

                合法格式示例：
                [
                  {
                    "heading": "一、章节标题",
                    "description": "章节说明"
                  },
                  {
                    "heading": "二、章节标题",
                    "description": "章节说明"
                  }
                ]

                原始内容如下：
                %s
                """.formatted(rawOutput);
    }
}