package com.nana.aiarticlestudio.model.vo;

import com.nana.aiarticlestudio.model.entity.AgentLog;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;

@Data
public class AgentLogVO {

    private Long id;

    private String taskId;

    private String agentName;

    private String inputText;

    private String outputText;

    private String status;

    private Long costMs;

    private String errorMessage;

    private LocalDateTime createTime;

    public static AgentLogVO fromEntity(AgentLog agentLog) {
        if (agentLog == null) {
            return null;
        }

        AgentLogVO vo = new AgentLogVO();
        BeanUtils.copyProperties(agentLog, vo);
        return vo;
    }
}