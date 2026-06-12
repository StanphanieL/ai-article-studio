package com.nana.aiarticlestudio.model.vo;

import com.nana.aiarticlestudio.model.entity.Article;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;

@Data
public class ArticleVO {

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

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String imagePrompts;

    private String imageResults;

    private String finalMarkdown;

    public static ArticleVO fromEntity(Article article) {
        if (article == null) {
            return null;
        }
        ArticleVO articleVO = new ArticleVO();
        BeanUtils.copyProperties(article, articleVO);
        return articleVO;
    }
}