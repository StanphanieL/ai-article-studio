package com.nana.aiarticlestudio.mapper;

import com.nana.aiarticlestudio.model.entity.AgentLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgentLogMapper {

    @Insert("""
            INSERT INTO agent_log (
                task_id,
                agent_name,
                input_text,
                output_text,
                status,
                cost_ms,
                error_message
            )
            VALUES (
                #{taskId},
                #{agentName},
                #{inputText},
                #{outputText},
                #{status},
                #{costMs},
                #{errorMessage}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AgentLog agentLog);

    @Select("""
            SELECT *
            FROM agent_log
            WHERE task_id = #{taskId}
            ORDER BY create_time DESC
            LIMIT #{limit}
            """)
    List<AgentLog> listByTaskId(@Param("taskId") String taskId,
                                @Param("limit") int limit);
}