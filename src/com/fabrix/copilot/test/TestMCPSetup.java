package com.fabrix.copilot.test;

import com.fabrix.copilot.mcp.McpServerManager;
import com.fabrix.copilot.mcp.McpServerConfig;
import com.fabrix.copilot.utils.CopilotLogger;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP 테스트 설정 헬퍼
 * 로컬 MCP 서버를 테스트하기 위한 유틸리티
 */
public class TestMCPSetup {
    
    public static void setupTestMCPServers() {
        McpServerManager manager = McpServerManager.getInstance();
        
        // 1. 파일시스템 MCP 서버 (npx 사용)
        McpServerConfig fsConfig = new McpServerConfig(
            "test-filesystem",
            "stdio",
            "npx",
            Arrays.asList("-y", "@modelcontextprotocol/server-filesystem", System.getProperty("user.home")),
            new HashMap<>(),
            1
        );
        
        // 2. 로컬 HTTP MCP 서버 (테스트용)
        McpServerConfig httpConfig = new McpServerConfig(
            "test-http-server",
            "http",
            "",  // HTTP는 프로세스를 시작하지 않음
            Arrays.asList("http://localhost:3000"),  // 서버 URL
            new HashMap<>(),
            1
        );
        
        // 서버 추가
        CopilotLogger.info("Setting up test MCP servers...");
        
        if (manager.addServer(fsConfig)) {
            CopilotLogger.info("✅ Filesystem MCP server added");
        } else {
            CopilotLogger.error("❌ Failed to add filesystem MCP server", null);
        }
        
        if (manager.addServer(httpConfig)) {
            CopilotLogger.info("✅ HTTP MCP server added");
        } else {
            CopilotLogger.warn("❌ Failed to add HTTP MCP server (make sure server is running on port 3000)");
        }
        
        // 상태 확인
        McpServerManager.McpStatus status = manager.getStatus();
        CopilotLogger.info("MCP Status: " + status.toString());
        
        // 도구 목록 출력
        Map<String, java.util.List<McpServerManager.McpTool>> tools = manager.getConnectedTools();
        tools.forEach((server, toolList) -> {
            CopilotLogger.info("Server: " + server);
            toolList.forEach(tool -> {
                CopilotLogger.info("  - Tool: " + tool.getName() + " - " + tool.getDescription());
            });
        });
    }
    
    public static void testMCPTool() {
        McpServerManager manager = McpServerManager.getInstance();
        
        try {
            // list_directory 도구 테스트
            Map<String, Object> params = new HashMap<>();
            params.put("path", System.getProperty("user.home"));
            
            String result = manager.executeTool("list_directory", params, "test context");
            CopilotLogger.info("Tool execution result: " + result);
            
        } catch (Exception e) {
            CopilotLogger.error("Tool execution failed", e);
        }
    }
}