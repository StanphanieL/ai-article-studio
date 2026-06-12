package com.nana.aiarticlestudio.service.impl;

import com.nana.aiarticlestudio.mapper.AgentLogMapper;
import com.nana.aiarticlestudio.model.entity.AgentLog;
import com.nana.aiarticlestudio.model.vo.AgentLogVO;
import com.nana.aiarticlestudio.service.AgentLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentLogServiceImpl implements AgentLogService {

    private static final int DEFAULT_LIMIT = 50;

    private final AgentLogMapper agentLogMapper;

    @Override
    public void saveSuccess(String taskId,
                            String agentName,
                            String inputText,
                            String outputText,
                            long costMs) {
        AgentLog log = new AgentLog();
        log.setTaskId(taskId);
        log.setAgentName(agentName);
        log.setInputText(inputText);
        log.setOutputText(outputText);
        log.setStatus("SUCCESS");
        log.setCostMs(costMs);
        log.setErrorMessage(null);

        agentLogMapper.insert(log);
    }

    @Override
    public void saveFailed(String taskId,
                           String agentName,
                           String inputText,
                           String errorMessage,
                           long costMs) {
        AgentLog log = new AgentLog();
        log.setTaskId(taskId);
        log.setAgentName(agentName);
        log.setInputText(inputText);
        log.setOutputText(null);
        log.setStatus("FAILED");
        log.setCostMs(costMs);
        log.setErrorMessage(errorMessage);

        agentLogMapper.insert(log);
    }

    @Override
    public List<AgentLogVO> listByTaskId(String taskId) {
        return agentLogMapper.listByTaskId(taskId, DEFAULT_LIMIT)
                .stream()
                .map(AgentLogVO::fromEntity)
                .toList();
    }
}