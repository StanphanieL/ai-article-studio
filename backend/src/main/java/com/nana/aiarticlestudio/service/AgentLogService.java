package com.nana.aiarticlestudio.service;

import com.nana.aiarticlestudio.model.vo.AgentLogVO;

import java.util.List;

public interface AgentLogService {

    void saveSuccess(String taskId,
                     String agentName,
                     String inputText,
                     String outputText,
                     long costMs);

    void saveFailed(String taskId,
                    String agentName,
                    String inputText,
                    String errorMessage,
                    long costMs);

    List<AgentLogVO> listByTaskId(String taskId);
}