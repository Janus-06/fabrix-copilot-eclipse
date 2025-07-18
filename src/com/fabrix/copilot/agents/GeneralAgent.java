package com.fabrix.copilot.agents;

import com.fabrix.copilot.core.LLMClient;

/**
 * 🤖 GeneralAgent - 일반 목적 AI 에이전트
 * * 다양한 일반적인 질문과 요청을 처리하는 AI 에이전트
 */
public class GeneralAgent {
    
    private final LLMClient llmClient;
    
    private final String SYSTEM_PROMPT =
        "당신은 도움이 되는 AI 어시스턴트입니다. " +
        "사용자의 다양한 질문에 정확하고 유용한 답변을 제공합니다. " +
        "친근하고 전문적인 톤을 유지하며, 복잡한 개념은 이해하기 쉽게 설명합니다. " +
        "확실하지 않은 정보에 대해서는 솔직히 모른다고 말하고, 추가 정보를 찾는 방법을 제안합니다.";
    
    public GeneralAgent() {
        this.llmClient = LLMClient.getInstance();
    }
    
    public String process(String userMessage) {
        try {
            if (userMessage == null || userMessage.trim().isEmpty()) {
                return "❓ 무엇을 도와드릴까요? 질문이나 요청사항을 말씀해주세요.";
            }
            
            String prompt = buildGeneralPrompt(userMessage);
            String response = safeGenerateResponse(prompt);
            return postProcessResponse(response, userMessage);
            
        } catch (Exception e) {
            return handleProcessError(e, userMessage);
        }
    }
    
    public String processWithContext(String userMessage, String context) {
        try {
            if (userMessage == null || userMessage.trim().isEmpty()) {
                return "❓ 질문이나 요청사항을 입력해주세요.";
            }
            
            String prompt = buildContextualPrompt(userMessage, context);
            String response = safeGenerateResponse(prompt);
            return postProcessResponse(response, userMessage);
            
        } catch (Exception e) {
            return handleProcessError(e, userMessage);
        }
    }
    
    private String safeGenerateResponse(String prompt) {
        try {
            // [수정] generateResponse 호출 시 두 번째 인자로 null을 전달하여 기본 모델을 사용하도록 함
            return llmClient.generateResponse(prompt, null);
            
        } catch (Exception e) {
            String errorMsg = "AI 응답 생성 실패: " + e.getMessage();
            System.err.println("GeneralAgent LLM 호출 실패: " + errorMsg);
            return generateFallbackResponse(prompt);
        }
    }
    
    private String buildGeneralPrompt(String userMessage) {
        return SYSTEM_PROMPT + "\n\n사용자 질문: " + userMessage;
    }
    
    private String buildContextualPrompt(String userMessage, String context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(SYSTEM_PROMPT).append("\n\n");
        
        if (context != null && !context.trim().isEmpty()) {
            prompt.append("📋 관련 컨텍스트:\n").append(context).append("\n\n");
        }
        
        prompt.append("사용자 질문: ").append(userMessage);
        return prompt.toString();
    }
    
    private String postProcessResponse(String response, String userMessage) {
        if (response == null || response.trim().isEmpty()) {
            return "죄송합니다. 적절한 응답을 생성하지 못했습니다. 다른 방식으로 질문해주시겠어요?";
        }
        return response;
    }

    private String handleProcessError(Exception e, String userMessage) {
        String errorMsg = "GeneralAgent 처리 중 오류 발생: " + e.getMessage();
        System.err.println(errorMsg);
        
        return "❌ **일시적인 오류가 발생했습니다**\n\n" +
               "죄송합니다. 요청 처리 중 문제가 발생했습니다.\n\n" +
               "**오류 정보:** " + e.getMessage();
    }
    
    private String generateFallbackResponse(String prompt) {
        return "🤖 **AI 어시스턴트 임시 응답**\n\n" +
               "현재 주 AI 시스템에 접근할 수 없어 완전한 답변을 제공하지 못합니다.\n\n" +
               "💡 **일반적인 조언:**\n" +
               "• 신뢰할 수 있는 정보원을 확인하세요\n" +
               "• 여러 관점에서 문제를 바라보세요\n" +
               "• 단계적으로 접근해보세요\n\n" +
               "🔄 잠시 후 다시 시도해주시거나, 다른 방식으로 질문해주세요.";
    }
    
    public boolean isReady() {
        return llmClient != null;
    }

    public String getAgentInfo() {
        return "🤖 **GeneralAgent 정보**\n\n" +
               "• **전문 분야**: 일반적인 질문, 정보 제공, 학습 도움\n" +
               "• **상태**: " + (isReady() ? "✅ 준비됨" : "❌ 오류");
    }
}