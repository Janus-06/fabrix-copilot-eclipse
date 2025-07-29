package com.fabrix.copilot.agents;

import com.fabrix.copilot.core.ConversationManager;
import com.fabrix.copilot.core.LLMClient;
import com.fabrix.copilot.utils.CopilotLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * 🎯 Complete REACT Agent - 개선된 버전
 * - ReAct 패턴을 구현하고 UI 피드백을 제공합니다.
 */
public class ReactAgent {

    private final LLMClient llmClient;
    private final CodingAgent codingAgent;
    private final McpAgent mcpAgent;
    private final GeneralAgent generalAgent;
    private final SelfCritiqueAgent critiqueAgent;

    public enum ActionType {
        CODE, MCP, GENERAL, CLARIFY, OBSERVE, THINK, REFLECT
    }

    public enum ReactStatus {
        COMPLETED, ERROR, NEEDS_CLARIFICATION, IN_PROGRESS
    }

    /**
     * 📊 ReactStep - REACT 프로세스 단계
     */
    public static class ReactStep {
        private final long timestamp;
        private ActionType actionType;
        private String result;
        private ReactStatus status;
        private String description;

        public ReactStep() {
            this.timestamp = System.currentTimeMillis();
        }

        public ReactStep(ActionType actionType, String result, ReactStatus status) {
            this.timestamp = System.currentTimeMillis();
            this.actionType = actionType;
            this.result = result;
            this.status = status;
            this.description = "";
        }
        
        public ReactStep(ActionType actionType, String description, String result, ReactStatus status) {
            this.timestamp = System.currentTimeMillis();
            this.actionType = actionType;
            this.description = description;
            this.result = result;
            this.status = status;
        }

        // Getters and Setters
        public ActionType getActionType() { return actionType; }
        public void setActionType(ActionType actionType) { this.actionType = actionType; }
        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
        public ReactStatus getStatus() { return status; }
        public void setStatus(ReactStatus status) { this.status = status; }
        public long getTimestamp() { return timestamp; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
    
    /**
     * 🎯 ReactAction - REACT 행동 정의
     */
    public static class ReactAction {
        private final ActionType type;
        private final String description;
        private final String parameters;
        
        public ReactAction(ActionType type, String description) {
            this.type = type;
            this.description = description;
            this.parameters = "";
        }
        
        public ReactAction(ActionType type, String description, String parameters) {
            this.type = type;
            this.description = description;
            this.parameters = parameters;
        }
        
        public ActionType getType() { return type; }
        public String getDescription() { return description; }
        public String getParameters() { return parameters; }
    }
    
    /**
     * 📈 ReactResult - REACT 실행 결과
     */
    public static class ReactResult {
        private String content;
        private ReactStatus status;
        private String metadata;
        
        public ReactResult() {
            this.status = ReactStatus.COMPLETED;
            this.metadata = "";
        }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public ReactStatus getStatus() { return status; }
        public void setStatus(ReactStatus status) { this.status = status; }
        
        public String getMetadata() { return metadata; }
        public void setMetadata(String metadata) { this.metadata = metadata; }
    }
    
    /**
     * 📋 ReactResponse - 최종 응답
     */
    public static class ReactResponse {
        private final List<ReactStep> steps;
        private final String finalAnswer;
        private final ReactStatus overallStatus;
        
        public ReactResponse(List<ReactStep> steps, String finalAnswer, ReactStatus status) {
            this.steps = steps;
            this.finalAnswer = finalAnswer;
            this.overallStatus = status;
        }
        
        public List<ReactStep> getSteps() { return steps; }
        public String getFinalAnswer() { return finalAnswer; }
        public ReactStatus getOverallStatus() { return overallStatus; }
    }
    
    /**
     * 🔄 ReactCallback - UI 피드백을 위한 콜백 인터페이스
     */
    public interface ReactCallback {
        void onThought(String thought);
        void onAction(String action, String tool);
        void onObservation(String observation);
        void onReflection(String reflection);
    }
    
    public ReactAgent() {
        this.llmClient = LLMClient.getInstance();
        this.codingAgent = AgentProvider.getCodingAgent();
        this.mcpAgent = AgentProvider.getMcpAgent();
        this.generalAgent = AgentProvider.getGeneralAgent();
        this.critiqueAgent = AgentProvider.getSelfCritiqueAgent();
    }
    
    /**
     * 🎯 메인 REACT 프로세스 - 기본 버전 (콜백 없음)
     */
    public ReactResponse process(String userRequest, String context, String sessionId) {
        return process(userRequest, context, sessionId, null);
    }
    
    /**
     * 🎯 메인 REACT 프로세스 - 콜백 지원 버전
     */
    public ReactResponse process(String userRequest, String context, String sessionId, ReactCallback callback) {
        List<ReactStep> steps = new ArrayList<>();
        
        try {
            String conversationContext = ConversationManager.getInstance().getConversationContext(sessionId, 3);
            
            // 1. OBSERVE - 요청 분석
            String observation = analyzeRequest(userRequest, context);
            steps.add(new ReactStep(ActionType.OBSERVE, "요청 분석", observation, ReactStatus.COMPLETED));
            if (callback != null) {
                callback.onObservation("요청을 분석하고 있습니다: " + userRequest);
            }
            
            // 2. THINK - 작업 계획
            ReactAction action = decideAction(userRequest, context, conversationContext);
            String thought = "선택된 작업: " + action.getDescription();
            steps.add(new ReactStep(ActionType.THINK, "계획 수립", thought, ReactStatus.COMPLETED));
            if (callback != null) {
                callback.onThought("필요한 작업을 계획하고 있습니다...");
            }
            
            // 3. ACT - 실행
            if (callback != null) {
                callback.onAction("실행 중", action.getType().toString());
            }
            ReactResult result = executeAndCritique(action, userRequest, context);
            steps.add(new ReactStep(action.getType(), "작업 실행", result.getContent(), result.getStatus()));
            
            // 4. REFLECT - 결과 평가
            String reflection = evaluateResult(result, userRequest);
            steps.add(new ReactStep(ActionType.REFLECT, "결과 평가", reflection, ReactStatus.COMPLETED));
            if (callback != null) {
                callback.onReflection("결과를 평가하고 있습니다...");
            }
            
            return new ReactResponse(steps, result.getContent(), result.getStatus());
            
        } catch (Exception e) {
            CopilotLogger.error("ReactAgent process failed", e);
            String errorMessage = "처리 중 오류가 발생했습니다: " + e.getMessage();
            steps.add(new ReactStep(ActionType.GENERAL, errorMessage, ReactStatus.ERROR));
            return new ReactResponse(steps, errorMessage, ReactStatus.ERROR);
        }
    }
    
    /**
     * 요청 분석 - LLM을 사용한 의도 파악
     */
    private String analyzeRequest(String request, String context) {
        try {
            String prompt = String.format("""
                다음 사용자 요청을 분석하세요:
                
                요청: %s
                컨텍스트: %s
                
                다음을 파악하세요:
                1. 요청의 주요 목적
                2. 필요한 작업 유형 (코딩, 파일 작업, 일반 질문 등)
                3. 예상되는 결과
                
                간단히 한 문장으로 요약하세요.
                """, request, context);
            
            String analysis = llmClient.generateResponse(prompt, null);
            return analysis != null ? analysis : "요청 분석 완료";
            
        } catch (Exception e) {
            CopilotLogger.warn("Request analysis failed, using fallback", e);
            return "사용자 요청: " + request;
        }
    }
    
    /**
     * 결과 평가
     */
    private String evaluateResult(ReactResult result, String originalRequest) {
        if (result.getStatus() == ReactStatus.COMPLETED) {
            return "요청이 성공적으로 처리되었습니다.";
        } else if (result.getStatus() == ReactStatus.ERROR) {
            return "처리 중 오류가 발생했습니다. 대체 방법을 고려해야 합니다.";
        } else {
            return "추가 정보가 필요합니다.";
        }
    }
    
    /**
     * ⚡️ 실행 및 자기 평가 (Self-Critique)
     */
    private ReactResult executeAndCritique(ReactAction action, String input, String context) {
        ReactResult result = new ReactResult();
        try {
            String initialContent = executeAgent(action, input, context);
            
            // Self-Critique는 선택적으로 적용
            if (shouldUseSelfCritique(action.getType())) {
                SelfCritiqueAgent.CritiqueResult critique = critiqueAgent.evaluate(input, initialContent, context);
                
                if (critique.isValid) {
                    result.setContent(initialContent);
                    result.setStatus(ReactStatus.COMPLETED);
                } else {
                    // 간단한 개선 시도
                    ReactAction improvedAction = decideImprovedAction(critique.improvedAction);
                    String improvedContent = executeAgent(improvedAction, input, context);
                    
                    result.setContent(improvedContent + "\n\n💡 (피드백을 통해 답변을 개선했습니다)");
                    result.setStatus(ReactStatus.COMPLETED);
                    result.setMetadata("Self-critique applied: " + critique.feedback);
                }
            } else {
                result.setContent(initialContent);
                result.setStatus(ReactStatus.COMPLETED);
            }
            
        } catch (Exception e) {
            result.setContent("처리 중 오류가 발생했습니다: " + e.getMessage());
            result.setStatus(ReactStatus.ERROR);
            CopilotLogger.error("Agent execution failed", e);
        }
        return result;
    }
    
    /**
     * Self-Critique 사용 여부 결정
     */
    private boolean shouldUseSelfCritique(ActionType actionType) {
        // MCP나 코드 작업은 즉각적인 결과가 중요하므로 Self-Critique 생략
        return actionType == ActionType.GENERAL;
    }
    
    private String executeAgent(ReactAction action, String input, String context) {
        switch (action.getType()) {
            case CODE:
                return codingAgent.process(input, context);
            case MCP:
                return mcpAgent.process(input, context);
            case GENERAL:
            default:
                return generalAgent.processWithContext(input, context);
        }
    }
    
    /**
     * 🤔 행동 결정 로직 - 개선된 버전
     */
    private ReactAction decideAction(String request, String context, String conversationContext) {
        String lower = request.toLowerCase();
        
        // 코드 관련 키워드 확인
        if (lower.contains("코드") || lower.contains("함수") || lower.contains("class") ||
            lower.contains("메소드") || lower.contains("method") || lower.contains("변수") ||
            lower.contains("java") || lower.contains("python") || lower.contains("javascript") ||
            lower.contains("수정") || lower.contains("리팩토링") || lower.contains("refactor") ||
            lower.contains("버그") || lower.contains("디버그") || lower.contains("에러") || 
            lower.contains("error") || lower.contains("구현") || lower.contains("implement")) {
            return new ReactAction(ActionType.CODE, "코드 작성 및 분석", request);
        }
        
        // MCP 도구 관련 - 명시적인 파일/디렉토리 작업
        if ((lower.contains("파일") && (lower.contains("읽") || lower.contains("쓰") || 
             lower.contains("목록") || lower.contains("생성") || lower.contains("삭제"))) ||
            (lower.contains("디렉토리") || lower.contains("폴더")) ||
            lower.contains("git") || lower.contains("깃") ||
            lower.contains("mcp") || lower.contains("도구")) {
            return new ReactAction(ActionType.MCP, "MCP 도구 사용", request);
        }
        
        // 일반 질문
        return new ReactAction(ActionType.GENERAL, "일반 질문 응답", request);
    }
    
    private ReactAction decideImprovedAction(String improvedActionHint) {
        if (improvedActionHint == null || improvedActionHint.trim().isEmpty()) {
            return new ReactAction(ActionType.GENERAL, "개선된 응답");
        }

        String hint = improvedActionHint.toLowerCase();
        
        if (hint.contains("코드") || hint.contains("java") || hint.contains("python")) {
            return new ReactAction(ActionType.CODE, "개선된 코드 분석", hint);
        } else if (hint.contains("파일") || hint.contains("검색") || hint.contains("mcp")) {
            return new ReactAction(ActionType.MCP, "개선된 MCP 작업", hint);
        } else {
            return new ReactAction(ActionType.GENERAL, "개선된 일반 응답", hint);
        }
    }
    
    /**
     * 🎨 대화 기록 관련 메서드들
     */
    public List<ReactStep> getConversationHistory(String sessionId) {
        List<ConversationManager.Message> messages = 
            ConversationManager.getInstance().getConversationHistory(sessionId);
        
        List<ReactStep> history = new ArrayList<>();
        for (ConversationManager.Message msg : messages) {
            ReactStep step = new ReactStep();
            step.setActionType(msg.isUser ? ActionType.GENERAL : ActionType.GENERAL);
            step.setResult(msg.content);
            step.setStatus(ReactStatus.COMPLETED);
            history.add(step);
        }
        
        return history;
    }
    
    public String getConversationSummary(String sessionId) {
        return ConversationManager.getInstance()
            .getConversationContext(sessionId, 5);
    }
}