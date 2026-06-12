package com.nana.aiarticlestudio.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nana.aiarticlestudio.model.vo.ImagePromptOption;
import com.nana.aiarticlestudio.model.vo.ImageResultOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ImageResultAgent {

    private final ObjectMapper objectMapper;

    private final PexelsImageClient pexelsImageClient;

    private final GoogleImagenClient googleImagenClient;

    private final SiliconFlowImageClient siliconFlowImageClient;

    public List<ImageResultOption> generate(String imagePromptsJson, String provider) throws Exception {
        if (!StringUtils.hasText(imagePromptsJson)) {
            throw new RuntimeException("配图提示词为空");
        }

        List<ImagePromptOption> prompts = objectMapper.readValue(
                imagePromptsJson,
                new TypeReference<List<ImagePromptOption>>() {}
        );

        List<ImageResultOption> results = new ArrayList<>();

        for (int i = 0; i < prompts.size(); i++) {
            ImagePromptOption prompt = prompts.get(i);
            ImageResultOption result = generateOne(prompt, provider, i);
            results.add(result);
        }

        return results;
    }

    private ImageResultOption generateOne(
            ImagePromptOption prompt,
            String provider,
            int index
    ) throws Exception {

        String normalizedProvider =
                StringUtils.hasText(provider)
                        ? provider.trim().toUpperCase()
                        : "AUTO";

        // 保留 Pexels。
        if ("PEXELS".equals(normalizedProvider)) {
            return pexelsImageClient.searchPhoto(prompt);
        }

        // 保留 Gemini。
        if ("GOOGLE_AI".equals(normalizedProvider)) {
            return googleImagenClient.generateImage(prompt);
        }

        // 新增 SiliconFlow。
        if ("SILICONFLOW".equals(normalizedProvider)) {
            return siliconFlowImageClient.generateImage(prompt);
        }

        // 自动模式暂时不调用 Gemini。
        if ("AUTO".equals(normalizedProvider)) {
            try {
                return pexelsImageClient.searchPhoto(prompt);
            } catch (Exception pexelsException) {

                System.out.println(
                        "Pexels 搜图失败，准备尝试 SiliconFlow："
                                + pexelsException.getMessage()
                );

                try {
                    return siliconFlowImageClient.generateImage(
                            prompt
                    );
                } catch (Exception siliconFlowException) {

                    System.out.println(
                            "SiliconFlow 生图失败，准备使用 fallback："
                                    + siliconFlowException.getMessage()
                    );

                    return fallback(prompt, index);
                }
            }
        }

        throw new RuntimeException(
                "不支持的图片来源：" + provider
        );
    }

    private ImageResultOption fallback(ImagePromptOption prompt, int index) {
        ImageResultOption result = new ImageResultOption();

        result.setImageTitle(prompt.getImageTitle());
        result.setUsageScene(prompt.getUsageScene());
        result.setPromptZh(prompt.getPromptZh());
        result.setPromptEn(prompt.getPromptEn());
        result.setImageUrl("https://picsum.photos/seed/ai-article-" + index + "/900/500");
        result.setSource("FALLBACK");
        result.setSourceUrl("");
        result.setAuthor("Picsum Placeholder");
        result.setAuthorUrl("");

        return result;
    }
}