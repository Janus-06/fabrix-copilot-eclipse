package com.fabrix.copilot.agents;

import com.fabrix.copilot.core.ConversationManager;
import com.fabrix.copilot.core.LLMClient;
import com.fabrix.copilot.utils.CopilotLogger; // Logger import

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 🎯 Complete REACT Agent - 리팩토링 버전
 * - 누락된 생성자 문제를 해결하고 AgentProvider를 통해 에이전트를 참조합니다.
 */
public class ReactAgent {

    private final LLMClient llmClient;
    private final CodingAgent codingAgent;
    private final McpAgent mcpAgent;
    private final GeneralAgent generalAgent;
    private final SelfCritiqueAgent critiqueAgent;

    public enum ActionType {
        CODE, MCP, GENERAL, CLARIFY
    }

    public enum ReactStatus {
        COMPLETED, ERROR, NEEDS_CLARIFICATION
    }

    /**
     * 📊 ReactStep - REACT 프로세스 단계
     */
    public static class ReactStep {
        private final long timestamp;
        private ActionType actionType;
        private String result;
        private ReactStatus status;

        public ReactStep() {
            this.timestamp = System.currentTimeMillis();
        }

        /**
         * [수정] 오류 해결을 위해 누락된 생성자 추가
         */
        public ReactStep(ActionType actionType, String result, ReactStatus status) {
            this.timestamp = System.currentTimeMillis();
            this.actionType = actionType;
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
    
    public ReactAgent() {
        // [수정] AgentProvider를 통해 싱글톤 에이전트 인스턴스 사용
        this.llmClient = LLMClient.getInstance();
        this.codingAgent = AgentProvider.getCodingAgent();
        this.mcpAgent = AgentProvider.getMcpAgent();
        this.generalAgent = AgentProvider.getGeneralAgent();
        this.critiqueAgent = AgentProvider.getSelfCritiqueAgent();
    }
    
    /**
     * 🎯 메인 REACT 프로세스
     */
    public ReactResponse process(String userRequest, String context, String sessionId) {
        List<ReactStep> steps = new ArrayList<>();
        try {
            String conversationContext = ConversationManager.getInstance().getConversationContext(sessionId, 3);
            
            // OBSERVE
            steps.add(new ReactStep(ActionType.GENERAL, "사용자 요청 분석: " + userRequest, ReactStatus.COMPLETED));
            
            // THINK
            ReactAction action = decideAction(userRequest, context, conversationContext);
            
            // ACT (Self-Critique 포함)
            ReactResult result = executeAndCritique(action, userRequest, context);
            
            steps.add(new ReactStep(action.getType(), result.getContent(), result.getStatus()));
            
            return new ReactResponse(steps, result.getContent(), result.getStatus());
        } catch (Exception e) {
            CopilotLogger.error("ReactAgent process failed", e);
            String errorMessage = "ReactAgent 처리 중 심각한 오류가 발생했습니다: " + e.getMessage();
            steps.add(new ReactStep(ActionType.GENERAL, errorMessage, ReactStatus.ERROR));
            return new ReactResponse(steps, errorMessage, ReactStatus.ERROR);
        }
    }
    
    /**
     * ⚡️ 실행 및 자기 평가 (Self-Critique)
     */
    private ReactResult executeAndCritique(ReactAction action, String input, String context) {
        ReactResult result = new ReactResult();
        try {
            String initialContent = executeAgent(action, input, context);
            
            SelfCritiqueAgent.CritiqueResult critique = critiqueAgent.evaluate(input, initialContent, context);
            
            if (critique.isValid) {
                result.setContent(initialContent);
                result.setStatus(ReactStatus.COMPLETED);
            } else {
                ReactAction improvedAction = decideImprovedAction(critique.improvedAction);
                String improvedContent = executeAgent(improvedAction, input, context);
                
                result.setContent(improvedContent + "\n\n💡 (자체 피드백을 통해 답변을 개선했습니다)");
                result.setStatus(ReactStatus.COMPLETED);
                result.setMetadata("Self-critique applied: " + critique.feedback);
            }
        } catch (Exception e) {
            result.setContent("처리 중 오류가 발생했습니다: " + e.getMessage());
            result.setStatus(ReactStatus.ERROR);
            CopilotLogger.error("Agent execution or critique failed", e);
        }
        return result;
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
     * 🤔 행동 결정 로직
     */
    private ReactAction decideAction(String request, String context, String conversationContext) {
        String lower = request.toLowerCase();
        
        if (lower.contains("코드") || lower.contains("함수") || lower.contains("java") || 
            lower.contains("python") || lower.contains("수정") || lower.contains("리팩토링") ||
            lower.contains("버그") || lower.contains("디버그")) {
            return new ReactAction(ActionType.CODE, "코드 분석 및 수정", request);
        }
        
        if (lower.contains("파일") || lower.contains("검색") || lower.contains("git") || 
            lower.contains("mcp") || lower.contains("조회") || lower.contains("목록") ||
            lower.contains("폴더") || lower.contains("디렉토리")) {
            return new ReactAction(ActionType.MCP, "외부 도구 사용", request);
        }
        
        return new ReactAction(ActionType.GENERAL, "일반 질문 응답", request);
    }
    
    private ReactAction decideImprovedAction(String improvedActionHint) {
        if (improvedActionHint == null || improvedActionHint.trim().isEmpty()) {
            return new ReactAction(ActionType.GENERAL, "Self-critique fallback");
        }

        String hint = improvedActionHint.toLowerCase();
        
        if (hint.contains("코드") || hint.contains("java") || hint.contains("python")) {
            return new ReactAction(ActionType.CODE, "Improved code analysis", hint);
        } else if (hint.contains("파일") || hint.contains("검색") || hint.contains("mcp")) {
            return new ReactAction(ActionType.MCP, "Improved MCP action", hint);
        } else {
            return new ReactAction(ActionType.GENERAL, "Improved general response", hint);
        }
    }
    
    /**
     * ⚡ Multi-Agent 실행
     */
    private ReactResult executeMultiAgent(ReactAction action, String input, String context) {
        ReactResult result = new ReactResult();
        
        try {
            // 1차 실행
            String content = executeAgent(action, input, context);
            
            // Self-Critique 평가
            SelfCritiqueAgent critiqueAgent = new SelfCritiqueAgent();
            SelfCritiqueAgent.CritiqueResult critique = 
                critiqueAgent.evaluate(input, content, context);
            
            if (critique.isValid) {
                result.setContent(content);
                result.setStatus(ReactStatus.COMPLETED);
            } else {
                // 개선된 행동으로 재시도
                ReactAction improvedAction = decideImprovedAction(critique.improvedAction);
                String improvedContent = executeAgent(improvedAction, input, context);
                
                result.setContent(improvedContent + "\n\n💡 개선된 답변입니다.");
                result.setStatus(ReactStatus.COMPLETED);
                result.setMetadata("Self-critique applied: " + critique.feedback);
            }
            
        } catch (Exception e) {
            result.setContent("처리 중 오류: " + e.getMessage());
            result.setStatus(ReactStatus.ERROR);
        }
        
        return result;
    }

   
    /**
     * 🎨 인터페이스 메서드들
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
        String context = ConversationManager.getInstance()
            .getConversationContext(sessionId, 5);
        return context;
    }
}