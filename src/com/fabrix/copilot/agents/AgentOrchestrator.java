package com.fabrix.copilot.agents;

import com.fabrix.copilot.core.ConversationManager;
import com.fabrix.copilot.core.LLMClient;
import com.fabrix.copilot.utils.CopilotLogger; // 추가된 import

import java.util.function.Consumer;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

/**
 * 🎯 AgentOrchestrator - ReAct 패턴 통합 오케스트레이터 (수정됨)
 * - AgentProvider를 통해 ReactAgent의 싱글톤 인스턴스를 사용합니다.
 * - Eclipse Jobs API를 사용하여 모든 요청을 비동기적으로 처리합니다.
 */
public class AgentOrchestrator {
    private final ReactAgent reactAgent;
    private final ConversationManager conversationManager;
    private final LLMClient llmClient;

    public AgentOrchestrator() {
        this.reactAgent = new ReactAgent();
        this.conversationManager = ConversationManager.getInstance();
        this.llmClient = LLMClient.getInstance();
    }

    /**
     * [신규] 비동기 요청 처리 메서드
     * ChatView에서 호출할 기본 진입점입니다.
     */
    public void processComplexRequestAsync(String userRequest, String fileContext, String modelId, Consumer<String> onSuccess, Consumer<Exception> onError) {
        Job job = new Job("AI Assistant is thinking...") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    monitor.beginTask("에이전트 시스템 실행 중...", IProgressMonitor.UNKNOWN);
                    
                    CopilotLogger.info("Processing request with model: " + modelId);
                    CopilotLogger.info("User request: " + userRequest);
                    CopilotLogger.info("Context length: " + (fileContext != null ? fileContext.length() : 0));
                    
                    String enhancedContext = fileContext;
                    if (modelId != null && !modelId.isEmpty()) {
                        enhancedContext = "Model: " + modelId + "\n" + fileContext;
                    }
                    
                    // LLMClient를 직접 호출
                    llmClient.generateResponseAsync(userRequest, modelId, 
                        response -> {
                            CopilotLogger.info("Response received successfully");
                            onSuccess.accept(response);
                        },
                        error -> {
                            CopilotLogger.error("Response generation failed", error);
                            onError.accept(error);
                        });

                    return Status.OK_STATUS;
                    
                } catch (Exception e) {
                    CopilotLogger.error("Request processing failed", e);
                    onError.accept(e);
                    return Status.error("요청 처리 중 오류가 발생했습니다.", e);
                }
            }
        };
        job.setUser(true);
        job.schedule();
    }
    
    /**
     * [참고] 기존의 동기 처리 메서드 (내부적으로 사용되거나, 테스트용으로 유지)
     */
    public String processComplexRequest(String userRequest, String fileContext, String modelId) {
        String sessionId = conversationManager.startNewConversation();
        
        String enhancedContext = fileContext;
        if (modelId != null && !modelId.isEmpty()) {
            enhancedContext = "Model: " + modelId + "\n" + fileContext;
        }
        
        ReactAgent.ReactResponse response = reactAgent.process(
            userRequest, 
            enhancedContext, 
            sessionId
        );
        
        return response.getFinalAnswer();
    }
}