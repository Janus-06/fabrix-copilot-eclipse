package com.fabrix.copilot.mcp;

import java.util.*;

/**
 * McpClient 인터페이스에 맞춰 McpStdioClient를 래핑
 */
public class McpClientAdapter extends McpClient {
    private final McpStdioClient stdioClient;
    
    public McpClientAdapter(McpStdioClient stdioClient) {
        super(null); // 더미 config
        this.stdioClient = stdioClient;
    }
    
    @Override
    public boolean connect() {
        return stdioClient.isConnected();
    }
    
    @Override
    public void disconnect() {
        stdioClient.disconnect();
    }
    
    @Override
    public boolean isConnected() {
        return stdioClient.isConnected();
    }
    
    @Override
    public Set<String> getAvailableTools() {
        return stdioClient.getAvailableTools();
    }
    
    @Override
    public String callTool(String toolName, Map<String, Object> parameters) throws Exception {
        return stdioClient.executeTool(toolName, parameters);
    }
}