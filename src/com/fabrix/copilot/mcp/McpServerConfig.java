package com.fabrix.copilot.mcp;

import java.util.List;
import java.util.Map;

public class McpServerConfig {
    private final String name;
    private final String type;
    private final String command;
    private final List<String> args;
    private final Map<String, String> env;
    private final int priority;
    
    public McpServerConfig(String name, String type, String command, 
                          List<String> args, Map<String, String> env, int priority) {
        this.name = name;
        this.type = type;
        this.command = command;
        this.args = args;
        this.env = env;
        this.priority = priority;
    }
    
    // Getters
    public String getName() { return name; }
    public String getType() { return type; }
    public String getCommand() { return command; }
    public List<String> getArgs() { return args; }
    public Map<String, String> getEnv() { return env; }
    public int getPriority() { return priority; }
}