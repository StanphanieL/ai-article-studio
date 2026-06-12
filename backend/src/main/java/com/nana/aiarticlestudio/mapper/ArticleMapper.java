package com.nana.aiarticlestudio.mapper;

import com.nana.aiarticlestudio.model.entity.Article;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ArticleMapper {

    @Insert("""
            INSERT INTO article (
                task_id,
                topic,
                style,
                model_config,
                phase,
                status
            )
            VALUES (
                #{taskId},
                #{topic},
                #{style},
                #{modelConfig},
                #{phase},
                #{status}
            )
            """)
    int insert(Article article);

    @Select("""
            SELECT *
            FROM article
            WHERE task_id = #{taskId}
              AND is_deleted = 0
            LIMIT 1
            """)
    Article selectByTaskId(
            String taskId
    );

    @Select("""
            SELECT *
            FROM article
            WHERE is_deleted = 0
            ORDER BY create_time DESC
            LIMIT #{offset}, #{pageSize}
            """)
    List<Article> list(
            @Param("offset") int offset,
            @Param("pageSize") int pageSize
    );

    @Select("""
            SELECT COUNT(*)
            FROM article
            WHERE is_deleted = 0
            """)
    long count();

    @Update("""
            UPDATE article
            SET is_deleted = 1
            WHERE task_id = #{taskId}
              AND is_deleted = 0
            """)
    int deleteByTaskId(
            String taskId
    );

    @Update("""
            UPDATE article
            SET title_options = #{titleOptions},
                phase = #{phase},
                status = #{status},
                error_message = NULL
            WHERE task_id = #{taskId}
              AND is_deleted = 0
            """)
    int updateTitleOptions(
            @Param("taskId") String taskId,
            @Param("titleOptions") String titleOptions,
            @Param("phase") String phase,
            @Param("status") String status
    );

    @Update("""
            UPDATE article
            SET selected_title = #{selectedTitle},
                outline = #{outline},
                phase = #{phase},
                status = #{status},
                error_message = NULL
            WHERE task_id = #{taskId}
              AND is_deleted = 0
            """)
    int updateSelectedTitleAndOutline(
            @Param("taskId") String taskId,
            @Param("selectedTitle") String selectedTitle,
            @Param("outline") String outline,
            @Param("phase") String phase,
            @Param("status") String status
    );

    @Update("""
            UPDATE article
            SET content = #{content},
                phase = #{phase},
                status = #{status},
                error_message = NULL
            WHERE task_id = #{taskId}
              AND is_deleted = 0
            """)
    int updateContent(
            @Param("taskId") String taskId,
            @Param("content") String content,
            @Param("phase") String phase,
            @Param("status") String status
    );

    @Update("""
            UPDATE article
            SET outline = #{outline},
                phase = #{phase},
                status = #{status},
                error_message = NULL
            WHERE task_id = #{taskId}
              AND is_deleted = 0
            """)
    int updateOutline(
            @Param("taskId") String taskId,
            @Param("outline") String outline,
            @Param("phase") String phase,
            @Param("status") String status
    );

    @Update("""
            UPDATE article
            SET selected_title = #{selectedTitle},
                phase = #{phase},
                status = #{status},
                error_message = NULL
            WHERE task_id = #{taskId}
              AND is_deleted = 0
            """)
    int updateSelectedTitle(
            @Param("taskId") String taskId,
            @Param("selectedTitle") String selectedTitle,
            @Param("phase") String phase,
            @Param("status") String status
    );

    @Update("""
            UPDATE article
            SET phase = #{phase},
                status = #{status},
                error_message = #{errorMessage}
            WHERE task_id = #{taskId}
              AND is_deleted = 0
            """)
    int updateFailedStatus(
            @Param("taskId") String taskId,
            @Param("phase") String phase,
            @Param("status") String status,
            @Param("errorMessage") String errorMessage
    );

    @Update("""
            UPDATE article
            SET image_prompts = #{imagePrompts},
                image_results = NULL,
                final_markdown = NULL,
                phase = #{phase},
                status = #{status},
                error_message = NULL
            WHERE task_id = #{taskId}
              AND is_deleted = 0
            """)
    int updateImagePrompts(
            @Param("taskId") String taskId,
            @Param("imagePrompts") String imagePrompts,
            @Param("phase") String phase,
            @Param("status") String status
    );

    @Update("""
            UPDATE article
            SET image_results = #{imageResults},
                final_markdown = NULL,
                phase = #{phase},
                status = #{status},
                error_message = NULL
            WHERE task_id = #{taskId}
              AND is_deleted = 0
            """)
    int updateImageResults(
            @Param("taskId") String taskId,
            @Param("imageResults") String imageResults,
            @Param("phase") String phase,
            @Param("status") String status
    );

    @Update("""
            UPDATE article
            SET final_markdown = #{finalMarkdown},
                phase = #{phase},
                status = #{status},
                error_message = NULL
            WHERE task_id = #{taskId}
              AND is_deleted = 0
            """)
    int updateFinalMarkdown(
            @Param("taskId") String taskId,
            @Param("finalMarkdown") String finalMarkdown,
            @Param("phase") String phase,
            @Param("status") String status
    );

    @Update("""
            UPDATE article
            SET style = #{style},
                model_config = #{modelConfig},
                title_options = NULL,
                selected_title = NULL,
                outline = NULL,
                content = NULL,
                full_content = NULL,
                image_prompts = NULL,
                image_results = NULL,
                final_markdown = NULL,
                phase = #{phase},
                status = #{status},
                error_message = NULL
            WHERE task_id = #{taskId}
              AND is_deleted = 0
            """)
    int updateModelConfig(
            @Param("taskId") String taskId,
            @Param("style") String style,
            @Param("modelConfig") String modelConfig,
            @Param("phase") String phase,
            @Param("status") String status
    );
}