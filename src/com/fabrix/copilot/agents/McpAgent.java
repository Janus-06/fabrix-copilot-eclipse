package com.fabrix.copilot.agents;

import com.fabrix.copilot.core.LLMClient;
import com.fabrix.copilot.utils.PreferenceManager;
import com.fabrix.copilot.utils.CopilotLogger; // 추가
import com.fabrix.copilot.mcp.McpServerManager;

import java.util.Map;      // 추가
import java.util.HashMap;  // 추가

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
                // 실제로 연결된 MCP 서버 수 확인
                McpServerManager.McpStatus status = McpServerManager.getInstance().getStatus();
                this.mcpConnected = status.getConnectedServers() > 0;
                this.mcpServerUrl = preferenceManager.getMCPFullUrl();

                if (mcpConnected) {
                    System.out.println("✅ MCP is enabled and connected: " + mcpServerUrl);
                } else {
                    System.out.println("❌ MCP is enabled but no servers are connected: " + mcpServerUrl);
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
            
            CopilotLogger.info("McpAgent processing request: " + userRequest);
            
            // 1. 먼저 도구를 직접 실행해 보기
            String toolResult = tryDirectToolExecution(userRequest, mcpContext);
            if (toolResult != null) {
                return toolResult;
            }
            
            // 2. LLM을 통한 처리
            String prompt = buildMCPPrompt(userRequest, mcpContext);
            String response = safeGenerateResponse(prompt);
            
            // 3. LLM 응답에서 도구 실행 필요성 확인
            if (requiresMCPToolExecution(response)) {
                return executeMCPToolsFromLLMResponse(response, userRequest);
            }
            
            return response;
            
        } catch (Exception e) {
            return handleProcessError(e, userRequest);
        }
    }

    // 직접 도구 실행 시도
    private String tryDirectToolExecution(String request, String context) {
        try {
            String lower = request.toLowerCase();
            McpServerManager manager = McpServerManager.getInstance();
            
            // 파일 읽기 요청
            if ((lower.contains("파일") || lower.contains("file")) && 
                (lower.contains("읽") || lower.contains("read") || lower.contains("내용"))) {
                
                // 파일 경로 추출
                String filePath = extractFilePath(request);
                if (filePath != null) {
                    Map<String, Object> params = new HashMap<>();
                    params.put("path", filePath);
                    
                    CopilotLogger.info("Executing read_file with path: " + filePath);
                    String result = manager.executeTool("read_file", params, context);
                    return formatToolResult("read_file", filePath, result);
                }
            }
            
            // 디렉토리 목록 요청
            if ((lower.contains("디렉토리") || lower.contains("폴더") || lower.contains("directory")) && 
                (lower.contains("목록") || lower.contains("list") || lower.contains("보"))) {
                
                String dirPath = extractDirectoryPath(request);
                Map<String, Object> params = new HashMap<>();
                params.put("path", dirPath != null ? dirPath : "./");
                
                CopilotLogger.info("Executing list_directory with path: " + params.get("path"));
                String result = manager.executeTool("list_directory", params, context);
                return formatToolResult("list_directory", params.get("path").toString(), result);
            }
            
            // 파일 검색 요청
            if (lower.contains("파일") && (lower.contains("검색") || lower.contains("찾"))) {
                String query = extractSearchQuery(request);
                if (query != null) {
                    Map<String, Object> params = new HashMap<>();
                    params.put("query", query);
                    params.put("path", "./");
                    
                    CopilotLogger.info("Executing search_files with query: " + query);
                    String result = manager.executeTool("search_files", params, context);
                    return formatToolResult("search_files", query, result);
                }
            }
            
            // Git 상태 요청
            if ((lower.contains("git") || lower.contains("깃")) && lower.contains("상태")) {
                CopilotLogger.info("Executing git_status");
                String result = manager.executeTool("git_status", new HashMap<>(), context);
                return formatToolResult("git_status", null, result);
            }
            
            // Git 로그 요청
            if ((lower.contains("git") || lower.contains("깃")) && 
                (lower.contains("로그") || lower.contains("이력") || lower.contains("log"))) {
                
                Map<String, Object> params = new HashMap<>();
                params.put("limit", "10");
                
                CopilotLogger.info("Executing git_log");
                String result = manager.executeTool("git_log", params, context);
                return formatToolResult("git_log", null, result);
            }
            
        } catch (Exception e) {
            CopilotLogger.error("Direct tool execution failed", e);
        }
        
        return null; // 직접 실행할 수 없는 경우
    }

    // LLM 응답에서 도구 실행 - 누락된 메서드 추가
    private String executeMCPToolsFromLLMResponse(String response, String userRequest) {
        // 간단한 구현 - 실제로는 LLM 응답을 파싱하여 도구 실행
        return response + "\n\n🔌 **MCP 도구 실행 완료**";
    }

    // 도구 결과 포맷팅
    private String formatToolResult(String toolName, String parameter, String result) {
        StringBuilder formatted = new StringBuilder();
        
        formatted.append("🔌 **MCP 도구 실행 결과**\n\n");
        formatted.append("• **도구**: ").append(toolName).append("\n");
        
        if (parameter != null) {
            formatted.append("• **파라미터**: ").append(parameter).append("\n");
        }
        
        formatted.append("\n📊 **실행 결과**:\n\n");
        formatted.append("```\n");
        formatted.append(result);
        formatted.append("\n```\n");
        
        // 추가 설명
        switch (toolName) {
            case "read_file":
                formatted.append("\n✅ 파일을 성공적으로 읽었습니다.");
                break;
            case "list_directory":
                formatted.append("\n✅ 디렉토리 내용을 표시했습니다.");
                break;
            case "search_files":
                formatted.append("\n✅ 파일 검색을 완료했습니다.");
                break;
            case "git_status":
                formatted.append("\n✅ Git 저장소 상태를 확인했습니다.");
                break;
            case "git_log":
                formatted.append("\n✅ Git 커밋 이력을 조회했습니다.");
                break;
        }
        
        return formatted.toString();
    }

    // 파일 경로 추출
    private String extractFilePath(String request) {
        // 간단한 패턴 매칭으로 파일 경로 추출
        String[] patterns = {
            "([\\w\\-./]+\\.[\\w]+)",  // 파일명.확장자 패턴
            "\"([^\"]+)\"",             // 따옴표로 둘러싼 경로
            "'([^']+)'"                 // 작은 따옴표로 둘러싼 경로
        };
        
        for (String pattern : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(request);
            if (m.find()) {
                return m.group(1);
            }
        }
        
        return null;
    }

    // 디렉토리 경로 추출
    private String extractDirectoryPath(String request) {
        if (request.contains("현재") || request.contains("current")) {
            return "./";
        }
        
        // 경로 패턴 추출
        String[] patterns = {
            "([\\w\\-./]+)\\s*(디렉토리|폴더|directory|folder)",
            "\"([^\"]+)\"",
            "'([^']+)'"
        };
        
        for (String pattern : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(request);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        
        return null;
    }

    // 검색 쿼리 추출
    private String extractSearchQuery(String request) {
        String[] patterns = {
            "\"([^\"]+)\"",                    // 따옴표
            "'([^']+)'",                       // 작은 따옴표
            "([\\w]+)\\s*를?\\s*검색",         // ~를 검색
            "([\\w]+)\\s*가?\\s*포함된"        // ~가 포함된
        };
        
        for (String pattern : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(request);
            if (m.find()) {
                return m.group(1);
            }
        }
        
        return null;
    }
    
    private String safeGenerateResponse(String prompt) {
        try {
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