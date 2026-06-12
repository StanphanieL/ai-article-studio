package com.nana.aiarticlestudio.model.vo;

import lombok.Data;

@Data
public class ImageResultOption {

    private String imageTitle;

    private String usageScene;

    private String imageUrl;

    private String promptZh;

    private String promptEn;

    /**
     * 图片来源：PEXELS / GOOGLE_AI / FALLBACK
     */
    private String source;

    /**
     * 来源页面链接，例如 Pexels 图片页
     */
    private String sourceUrl;

    /**
     * 作者，例如 Pexels 摄影师
     */
    private String author;

    /**
     * 作者主页
     */
    private String authorUrl;
}