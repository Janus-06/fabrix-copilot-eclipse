package com.fabrix.copilot.agents;

import com.fabrix.copilot.core.ConversationManager;
import com.fabrix.copilot.core.LLMClient;
import com.fabrix.copilot.utils.CopilotLogger;

import java.util.function.Consumer;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

/**
 * 🎯 AgentOrchestrator - ReAct 패턴 통합 오케스트레이터 (개선된 버전)
 * - MCP 도구 요청을 감지하고 적절한 에이전트로 라우팅
 * - Eclipse Jobs API를 사용하여 모든 요청을 비동기적으로 처리
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
     * 비동기 요청 처리 메서드 - MCP 도구 감지 기능 추가
     * ChatView에서 호출할 기본 진입점입니다.
     */
    public void processComplexRequestAsync(String userRequest, String fileContext, String modelId,
            Consumer<String> onSuccess, Consumer<Throwable> onError) {
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

// 모든 요청을 ReactAgent 기반으로 처리
String response = processComplexRequest(userRequest, enhancedContext, modelId);

org.eclipse.swt.widgets.Display.getDefault().asyncExec(() -> {
onSuccess.accept(response);
});

return Status.OK_STATUS;
} catch (Exception e) {
CopilotLogger.error("Request processing failed", e);
org.eclipse.swt.widgets.Display.getDefault().asyncExec(() -> {
onError.accept(e);
});
return Status.error("요청 처리 중 오류가 발생했습니다.", e);
}
}
};
job.setUser(true);
job.schedule();
}

    
    /**
     * MCP 도구 요청인지 감지
     */
    private boolean isMCPToolRequest(String request) {
        if (request == null || request.isEmpty()) {
            return false;
        }
        
        String lower = request.toLowerCase();
        
        // 파일 관련 키워드
        boolean fileRelated = lower.contains("파일") || lower.contains("file") || 
                            lower.contains("디렉토리") || lower.contains("directory") ||
                            lower.contains("폴더") || lower.contains("folder");
        
        // 동작 관련 키워드
        boolean actionRelated = lower.contains("읽") || lower.contains("read") ||
                              lower.contains("쓰") || lower.contains("write") ||
                              lower.contains("저장") || lower.contains("save") ||
                              lower.contains("목록") || lower.contains("list") ||
                              lower.contains("검색") || lower.contains("search") ||
                              lower.contains("찾") || lower.contains("find");
        
        // Git 관련 키워드
        boolean gitRelated = lower.contains("git") || lower.contains("깃") ||
                           lower.contains("커밋") || lower.contains("commit") ||
                           lower.contains("브랜치") || lower.contains("branch");
        
        // 데이터베이스 관련 키워드
        boolean dbRelated = lower.contains("쿼리") || lower.contains("query") ||
                          lower.contains("테이블") || lower.contains("table") ||
                          lower.contains("데이터베이스") || lower.contains("database");
        
        // MCP 명시적 언급
        boolean mcpExplicit = lower.contains("mcp") || lower.contains("도구") || lower.contains("tool");
        
        return (fileRelated && actionRelated) || gitRelated || dbRelated || mcpExplicit;
    }
    
    /**
     * 복잡한 요청 처리 (ReactAgent 사용)
     */
    public String processComplexRequest(String userRequest, String fileContext, String modelId) {
        try {
            // 새 대화 세션 시작
            String sessionId = conversationManager.startNewConversation();
            
            // 컨텍스트 강화
            String enhancedContext = buildEnhancedContext(userRequest, fileContext, modelId);
            
            CopilotLogger.info("Processing complex request through ReactAgent");
            CopilotLogger.info("Session ID: " + sessionId);
            CopilotLogger.info("Enhanced context: " + enhancedContext);
            
            // ReactAgent를 통해 처리
            ReactAgent.ReactResponse response = reactAgent.process(
                userRequest, 
                enhancedContext, 
                sessionId
            );
            
            // 대화 기록에 추가
            conversationManager.addMessage(sessionId, userRequest, true);
            conversationManager.addMessage(sessionId, response.getFinalAnswer(), false);
            
            // 처리 과정 로그
            logProcessingSteps(response);
            
            return response.getFinalAnswer();
            
        } catch (Exception e) {
            CopilotLogger.error("Complex request processing failed", e);
            return "❌ 요청 처리 중 오류가 발생했습니다: " + e.getMessage();
        }
    }
    
    /**
     * 강화된 컨텍스트 생성
     */
    private String buildEnhancedContext(String userRequest, String fileContext, String modelId) {
        StringBuilder context = new StringBuilder();
        
        // 모델 정보
        if (modelId != null && !modelId.isEmpty()) {
            context.append("Selected Model: ").append(modelId).append("\n");
        }
        
        // 파일 컨텍스트
        if (fileContext != null && !fileContext.isEmpty()) {
            context.append("\n=== File Context ===\n");
            context.append(fileContext).append("\n");
        }
        
        // MCP 도구 가용성 힌트
        if (isMCPToolRequest(userRequest)) {
            context.append("\n=== Available MCP Tools ===\n");
            context.append("- File operations: read_file, write_file, list_directory, search_files\n");
            context.append("- Git operations: git_status, git_log, git_diff\n");
            context.append("- Database operations: execute_query, list_tables\n");
        }
        
        // 요청 타입 힌트
        context.append("\n=== Request Type ===\n");
        if (userRequest.toLowerCase().contains("코드") || userRequest.toLowerCase().contains("code")) {
            context.append("This appears to be a coding-related request.\n");
        } else if (isMCPToolRequest(userRequest)) {
            context.append("This appears to be an MCP tool request.\n");
        } else {
            context.append("This appears to be a general knowledge request.\n");
        }
        
        return context.toString();
    }
    
    /**
     * 처리 과정 로깅
     */
    private void logProcessingSteps(ReactAgent.ReactResponse response) {
        CopilotLogger.info("=== ReactAgent Processing Steps ===");
        CopilotLogger.info("Overall Status: " + response.getOverallStatus());
        CopilotLogger.info("Total Steps: " + response.getSteps().size());
        
        for (ReactAgent.ReactStep step : response.getSteps()) {
            CopilotLogger.info(String.format("Step [%s] - Action: %s, Status: %s",
                new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(step.getTimestamp())),
                step.getActionType(),
                step.getStatus()
            ));
            
            if (step.getResult() != null && step.getResult().length() > 100) {
                CopilotLogger.debug("Result preview: " + step.getResult().substring(0, 100) + "...");
            } else {
                CopilotLogger.debug("Result: " + step.getResult());
            }
        }
        CopilotLogger.info("================================");
    }
    
    /**
     * 에이전트 시스템 상태 확인
     */
    public boolean isReady() {
        boolean llmReady = llmClient != null;
        boolean reactReady = reactAgent != null;
        boolean conversationReady = conversationManager != null;
        
        boolean allReady = llmReady && reactReady && conversationReady;
        
        if (!allReady) {
            CopilotLogger.warn("Agent system not fully ready - LLM: " + llmReady + 
                             ", React: " + reactReady + ", Conversation: " + conversationReady);
        }
        
        return allReady;
    }
    
    /**
     * 시스템 정보 반환
     */
    public String getSystemInfo() {
        StringBuilder info = new StringBuilder();
        info.append("🎯 **Agent Orchestrator Status**\n\n");
        info.append("• LLM Client: ").append(llmClient != null ? "✅ Ready" : "❌ Not initialized").append("\n");
        info.append("• React Agent: ").append(reactAgent != null ? "✅ Ready" : "❌ Not initialized").append("\n");
        info.append("• Conversation Manager: ").append(conversationManager != null ? "✅ Ready" : "❌ Not initialized").append("\n");
        
        // 대화 통계
        if (conversationManager != null) {
            info.append("\n**Conversation Statistics:**\n");
            java.util.Map<String, Integer> stats = conversationManager.getConversationStats();
            info.append("• Active sessions: ").append(stats.size()).append("\n");
            int totalMessages = stats.values().stream().mapToInt(Integer::intValue).sum();
            info.append("• Total messages: ").append(totalMessages).append("\n");
        }
        
        return info.toString();
    }
}