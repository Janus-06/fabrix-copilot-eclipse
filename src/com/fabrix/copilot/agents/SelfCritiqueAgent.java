package com.fabrix.copilot.agents;

import com.fabrix.copilot.core.LLMClient;
import com.fabrix.copilot.utils.CopilotLogger; // Logger import

/**
 * 🧠 Self-Critique Agent
 * LLM 응답의 정확성을 평가하고 개선 방향을 제시합니다.
 */
public class SelfCritiqueAgent {
    private final LLMClient llmClient;

    public SelfCritiqueAgent() {
        this.llmClient = LLMClient.getInstance();
    }

    public CritiqueResult evaluate(String originalInput, String response, String context) {
        String prompt = buildCritiquePrompt(originalInput, response, context);
        
        try {
            // [수정] generateResponse 호출 시 두 번째 인자로 null을 전달하여 기본 모델을 사용
            String critiqueJson = llmClient.generateResponse(prompt, null);
            return parseCritique(critiqueJson);
        } catch (Exception e) {
            CopilotLogger.error("Self-Critique 평가 중 오류 발생", e);
            return new CritiqueResult(false, "평가 생성에 실패했습니다: " + e.getMessage(), null);
        }
    }

    private String buildCritiquePrompt(String input, String response, String context) {
        return String.format("""
            당신은 AI 응답의 품질을 검수하는 전문가입니다.
            
            [원본 요청]
            %s
            
            [컨텍스트]
            %s
            
            [AI 응답]
            %s
            
            위 응답을 다음 기준으로 평가하고, 반드시 JSON 형식으로만 답변해주세요:
            1. 요청에 대한 정확한 답변인가? (정확성)
            2. 기술적인 오류가 있는가? (기술적 무결성)
            3. 더 나은 해결책이나 접근 방식이 있는가? (개선 가능성)
            
            JSON 형식:
            {
              "isValid": true 또는 false,
              "feedback": "응답이 유효하지 않다면, 구체적인 문제점을 한 문장으로 요약.",
              "improvedAction": "더 나은 답변을 얻기 위해 RouterAgent에게 전달할 새로운 지시사항. (예: '자바 코드로 스레드를 안전하게 중지하는 방법을 알려줘')"
            }
            """, input, context, response);
    }

    private CritiqueResult parseCritique(String critiqueJson) {
        try {
            // 간단한 JSON 파싱 (안정성을 위해 org.json 라이브러리 사용을 권장하지만, 기존 구조 유지)
            String json = critiqueJson.trim().replace("\\\"", "\"");
            if (json.startsWith("\"")) json = json.substring(1);
            if (json.endsWith("\"")) json = json.substring(0, json.length() - 1);

            boolean isValid = json.contains("\"isValid\": true");
            String feedback = extractJsonValue(json, "feedback");
            String improvedAction = extractJsonValue(json, "improvedAction");

            return new CritiqueResult(isValid, feedback, improvedAction);
        } catch (Exception e) {
            CopilotLogger.error("Self-Critique JSON 파싱 실패: " + critiqueJson, e);
            // 파싱 실패 시, 응답이 유효하지 않은 것으로 간주하고 원본 비평을 피드백으로 전달
            return new CritiqueResult(false, "AI의 평가 응답 형식이 올바르지 않습니다: " + critiqueJson, null);
        }
    }

    private String extractJsonValue(String json, String key) {
        try {
            String searchKey = "\"" + key + "\":\"";
            int startIndex = json.indexOf(searchKey);
            if (startIndex == -1) {
                return null;
            }
            startIndex += searchKey.length();
            int endIndex = json.indexOf("\"", startIndex);
            if (endIndex == -1) {
                return null;
            }
            return json.substring(startIndex, endIndex);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 자기 평가 결과를 담는 데이터 클래스
     */
    public static class CritiqueResult {
        public final boolean isValid;
        public final String feedback;
        public final String improvedAction;

        public CritiqueResult(boolean isValid, String feedback, String improvedAction) {
            this.isValid = isValid;
            this.feedback = feedback;
            this.improvedAction = improvedAction;
        }
    }
}