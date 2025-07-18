package com.fabrix.copilot.agents;

import com.fabrix.copilot.core.ContextCollector;
import com.fabrix.copilot.core.LLMClient;

/**
 * 🤖 CodingAgent - 코딩 전문 에이전트
 * 코드 작성, 리뷰, 디버깅, 리팩토링을 전문으로 하는 AI 에이전트
 */
public class CodingAgent {
    
    private final LLMClient llmClient;
    private final ContextCollector contextCollector;
    
    private final String SYSTEM_PROMPT =
        "당신은 숙련된 프로그래머입니다. " +
        "코드 작성, 리뷰, 디버깅, 리팩토링을 전문으로 합니다. " +
        "간결하고 효율적인 코드를 작성하며, 베스트 프랙티스를 따릅니다. " +
        "코드에 적절한 주석을 포함하고, 설명이 필요한 경우 간단명료하게 제공합니다. " +
        "만약 요구사항이 불명확하다면, 구체적인 기술 사양을 물어보세요.";
    
    public CodingAgent() {
        this.llmClient = LLMClient.getInstance();
        this.contextCollector = new ContextCollector(); // ContextCollector 통합 버전을 사용
    }
    
    public String process(String userInput, String context) {
        try {
            if (userInput == null || userInput.trim().isEmpty()) {
                return "❌ 요청사항을 입력해주세요.";
            }
            
            String codeContext = safeGetCurrentCodeContext();
            String prompt = buildCodingPrompt(userInput, context, codeContext);
            
            String response = safeGenerateResponse(prompt);
            
            if (needsTechnicalClarification(userInput, response)) {
                return askTechnicalDetails(userInput);
            }
            
            return response;
            
        } catch (Exception e) {
            return handleProcessError(e, userInput);
        }
    }
    
    private String safeGenerateResponse(String prompt) {
        try {
            // [수정] generateResponse 호출 시 두 번째 인자로 null을 전달하여 기본 모델을 사용하도록 함
            return llmClient.generateResponse(prompt, null);
            
        } catch (Exception e) {
            String errorMsg = "AI 응답 생성 실패: " + e.getMessage();
            System.err.println("CodingAgent LLM 호출 실패: " + errorMsg);
            return generateFallbackResponse(prompt);
        }
    }
    
    private String safeGetCurrentCodeContext() {
        try {
            if (contextCollector == null) return "";
            return contextCollector.getCurrentCodeContext();
        } catch (Exception e) {
            System.err.println("코드 컨텍스트 수집 실패: " + e.getMessage());
            return "";
        }
    }
    
    private String buildCodingPrompt(String userInput, String context, String codeContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(SYSTEM_PROMPT).append("\n\n");
        
        if (codeContext != null && !codeContext.trim().isEmpty()) {
            prompt.append("📁 현재 코드 컨텍스트:\n```\n").append(codeContext).append("\n```\n\n");
        }
        
        if (context != null && !context.trim().isEmpty()) {
            prompt.append("📋 추가 컨텍스트: ").append(context).append("\n\n");
        }
        
        prompt.append("🎯 요청사항: ").append(userInput);
        return prompt.toString();
    }
    
    private boolean needsTechnicalClarification(String input, String response) {
        if (input == null || response == null) return false;
        String lowerInput = input.toLowerCase();
        String lowerResponse = response.toLowerCase();
        
        boolean hasVagueRequest = 
            (lowerInput.contains("최적화") && !lowerInput.contains("어떻게")) ||
            (lowerInput.contains("구현") && !lowerInput.contains("언어"));
        
        boolean responseNeedsClarification = 
            lowerResponse.contains("어떤 언어") ||
            lowerResponse.contains("더 구체적") ||
            lowerResponse.contains("자세한 정보");
            
        return hasVagueRequest || responseNeedsClarification;
    }
    
    private String askTechnicalDetails(String userInput) {
        StringBuilder details = new StringBuilder();
        details.append("🤖 코드 작성을 위해 몇 가지 확인이 필요합니다:\n\n");
        
        if (userInput.toLowerCase().contains("최적화")) {
            details.append("⚡ **성능 최적화 관련:**\n")
                   .append("• 어떤 부분의 성능을 개선하고 싶으신가요?\n")
                   .append("• 메모리 사용량과 실행 속도 중 어느 것이 우선인가요?\n\n");
        }
        
        if (userInput.toLowerCase().contains("구현")) {
            details.append("🛠️ **구현 세부사항:**\n")
                   .append("• 어떤 프로그래밍 언어를 사용하시나요?\n")
                   .append("• 사용 중인 프레임워크나 라이브러리가 있나요?\n\n");
        }
        
        details.append("💡 더 구체적인 정보를 제공해주시면 더 정확한 도움을 드릴 수 있습니다!");
        return details.toString();
    }
    
    private String handleProcessError(Exception e, String userInput) {
        String errorMsg = "CodingAgent 처리 중 오류 발생: " + e.getMessage();
        System.err.println(errorMsg);
        
        return "❌ **코딩 어시스턴트 오류**\n\n" +
               "요청 처리 중 문제가 발생했습니다.\n\n" +
               "**오류 정보:** " + e.getMessage();
    }
    
    private String generateFallbackResponse(String prompt) {
        return "🤖 **코딩 어시스턴트 임시 응답**\n\n" +
               "현재 AI 엔진에 접근할 수 없어 완전한 응답을 제공하지 못합니다.\n\n" +
               "💡 **기본 코딩 팁:**\n" +
               "• 코드는 명확하고 간결하게 작성하세요\n" +
               "• 적절한 주석을 추가하세요\n" +
               "• 에러 처리를 잊지 마세요\n\n" +
               "🔄 잠시 후 다시 시도해주시거나, 설정을 확인해주세요.";
    }
    
    public boolean isReady() {
        return llmClient != null;
    }
    
    public String getAgentInfo() {
        return "🤖 **CodingAgent 정보**\n\n" +
               "• **전문 분야**: 코드 작성, 리뷰, 디버깅, 리팩토링\n" +
               "• **상태**: " + (isReady() ? "✅ 준비됨" : "❌ 오류");
    }
}