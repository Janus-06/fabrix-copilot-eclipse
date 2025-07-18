package com.fabrix.copilot.ui;

/**
 * 📡 MCP Server Info - MCP 서버 정보 클래스
 * 
 * MCP 서버의 설정 정보를 담는 데이터 클래스
 */
public class McpServerInfo {
    public String name;
    public String type;
    public String command;
    public String args;
    public boolean connected;
    public int toolCount;
    
    public McpServerInfo() {
        this.type = "stdio";
        this.connected = false;
        this.toolCount = 0;
    }
    
    public McpServerInfo(String name, String type, String command, String args) {
        this.name = name;
        this.type = type;
        this.command = command;
        this.args = args;
        this.connected = false;
        this.toolCount = 0;
    }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    
    public String getArgs() { return args; }
    public void setArgs(String args) { this.args = args; }
    
    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }
    
    public int getToolCount() { return toolCount; }
    public void setToolCount(int toolCount) { this.toolCount = toolCount; }
}