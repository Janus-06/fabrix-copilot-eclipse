package com.fabrix.copilot.agents;

import com.fabrix.copilot.core.LLMClient;
import com.fabrix.copilot.utils.PreferenceManager;

/**
 * 🔌 McpAgent - MCP(Model Context Protocol) 전문 에이전트
 * * MCP를 활용한 외부 도구 연동 및 컨텍스트 처리를 담당하는 AI 에이전트
 */
public class McpAgent {
    
    private final LLMClient llmClient;
    private final PreferenceManager preferenceManager;
    
    private final String SYSTEM_PROMPT =
        "당신은 MCP(Model Context Protocol)를 활용하는 전문 AI 어시스턴트입니다. " +
        "외부 도구와 서비스를 연동하여 파일 시스템, API 등에 접근할 수 있으며, " +
        "이를 활용하여 사용자에게 더 정확하고 실용적인 답변을 제공합니다.";
    
    private boolean mcpConnected = false;
    private String mcpServerUrl = "";
    
    public McpAgent() {
        this.llmClient = LLMClient.getInstance();
        this.preferenceManager = PreferenceManager.getInstance();
        initializeMCPConnection();
    }
    
    private void initializeMCPConnection() {
        try {
            if (preferenceManager.isMCPEnabled()) {
                this.mcpServerUrl = preferenceManager.getMCPFullUrl();
                // 실제 연결 테스트 로직은 McpServerManager 등으로 이전하는 것이 좋지만, 여기서는 구조를 유지합니다.
                this.mcpConnected = true; // 간단히 true로 설정
                
                if (mcpConnected) {
                    System.out.println("✅ MCP is enabled: " + mcpServerUrl);
                } else {
                    System.out.println("❌ MCP connection failed: " + mcpServerUrl);
                }
            } else {
                System.out.println("ℹ️ MCP is disabled in settings.");
            }
        } catch (Exception e) {
            System.err.println("MCP 초기화 실패: " + e.getMessage());
            this.mcpConnected = false;
        }
    }
    
    public String process(String userRequest, String mcpContext) {
        try {
            if (userRequest == null || userRequest.trim().isEmpty()) {
                return "❓ MCP를 통해 처리할 요청을 입력해주세요.";
            }
            
            if (!isMCPAvailable()) {
                return handleMCPUnavailable();
            }
            
            String prompt = buildMCPPrompt(userRequest, mcpContext);
            String response = safeGenerateResponse(prompt);
            
            if (requiresMCPToolExecution(response)) {
                return executeMCPTools(response);
            }
            
            return response;
            
        } catch (Exception e) {
            return handleProcessError(e, userRequest);
        }
    }
    
    private String safeGenerateResponse(String prompt) {
        try {
            // [수정] generateResponse 호출 시 두 번째 인자로 null을 전달하여 기본 모델을 사용하도록 함
            return llmClient.generateResponse(prompt, null);
            
        } catch (Exception e) {
            String errorMsg = "AI 응답 생성 실패: " + e.getMessage();
            System.err.println("McpAgent LLM 호출 실패: " + errorMsg);
            return generateFallbackResponse(prompt);
        }
    }
    
    private String buildMCPPrompt(String userRequest, String mcpContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(SYSTEM_PROMPT).append("\n\n");
        
        prompt.append("🔌 **MCP 상태 정보:**\n")
              .append("• 연결 상태: ").append(mcpConnected ? "✅ 연결됨" : "❌ 연결 안됨").append("\n\n");
        
        if (mcpContext != null && !mcpContext.trim().isEmpty()) {
            prompt.append("📋 **MCP 컨텍스트:**\n").append(mcpContext).append("\n\n");
        }
        
        prompt.append("🎯 **사용자 요청:** ").append(userRequest);
        return prompt.toString();
    }
    
    private boolean isMCPAvailable() {
        return preferenceManager.isMCPEnabled() && mcpConnected;
    }
    
    private String handleMCPUnavailable() {
        return "🔌 **MCP 서비스 사용 불가**\n\n" +
               "MCP 기능이 비활성화되어 있거나 서버에 연결할 수 없습니다.\n" +
               "Settings(설정)에서 MCP 설정을 확인하고 활성화해주세요.";
    }

    private boolean requiresMCPToolExecution(String response) {
        if (!mcpConnected || response == null) return false;
        String lower = response.toLowerCase();
        return lower.contains("파일을 검색") || lower.contains("데이터를 조회") || lower.contains("api 호출");
    }

    private String executeMCPTools(String response) {
        // 실제로는 McpServerManager를 통해 도구를 실행해야 합니다.
        // 여기서는 시뮬레이션 응답을 반환합니다.
        return response + "\n\n" +
               "🔌 **MCP 도구 실행 시뮬레이션**\n" +
               "이 응답은 실제 외부 도구 실행 결과로 대체되어야 합니다.";
    }
    
    private String handleProcessError(Exception e, String userRequest) {
        String errorMsg = "McpAgent 처리 중 오류 발생: " + e.getMessage();
        System.err.println(errorMsg);
        
        return "❌ **MCP 에이전트 오류**\n\n" +
               "MCP 요청 처리 중 문제가 발생했습니다.\n\n" +
               "**오류 정보:** " + e.getMessage();
    }
    
    private String generateFallbackResponse(String prompt) {
        return "🔌 **MCP 에이전트 임시 응답**\n\n" +
               "현재 AI 엔진 또는 MCP 서버에 접근할 수 없습니다.\n\n" +
               "🔄 시스템 복구 후 다시 시도해주세요.";
    }
    
    public boolean isReady() {
        return llmClient != null && preferenceManager != null;
    }
}