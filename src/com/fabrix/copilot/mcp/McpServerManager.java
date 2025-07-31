package com.fabrix.copilot.mcp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fabrix.copilot.utils.CopilotLogger;
import com.fabrix.copilot.utils.PreferenceManager;

/**
 * 🔌 MCP Server Manager - MCP 서버 관리
 * 
 * Model Context Protocol 서버들의 연결과 도구를 중앙에서 관리합니다.
 */
public class McpServerManager {
    private static McpServerManager instance;
    private final Map<String, McpClient> clients;
    private final Map<String, McpServerConfig> configs;
    private final Map<String, List<McpTool>> availableTools;

    private McpServerManager() {
        this.clients = new ConcurrentHashMap<>();
        this.configs = new ConcurrentHashMap<>();
        this.availableTools = new ConcurrentHashMap<>();
        CopilotLogger.info("🔌 MCP Server Manager initialized.");
    }

    public static synchronized McpServerManager getInstance() {
        if (instance == null) {
            instance = new McpServerManager();
        }
        return instance;
    }

    /**
     * MCP 서버 추가
     */
    public boolean addServer(McpServerConfig config) {
        try {
            CopilotLogger.info("🔄 Adding MCP server: " + config.getName());
            
            // Claude Desktop 스타일의 stdio 클라이언트 사용
            McpStdioClient client = new McpStdioClient(config);
            
            if (client.connect()) {
                // 기존 clients Map의 타입을 변경하거나, adapter 패턴 사용
                clients.put(config.getName(), new McpClientAdapter(client));
                configs.put(config.getName(), config);
                
                // 도구 목록 생성
                Set<String> tools = client.getAvailableTools();
                List<McpTool> mcpTools = tools.stream()
                    .map(name -> new McpTool(name, getToolDescription(name), getToolParameters(name)))
                    .collect(Collectors.toList());
                
                availableTools.put(config.getName(), mcpTools);
                
                CopilotLogger.info(String.format("✅ MCP server connected: %s (%d tools)", 
                    config.getName(), mcpTools.size()));
                
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            CopilotLogger.error("Failed to add MCP server", e);
            return false;
        }
    }

    private boolean verifyNpxCommand(McpServerConfig config) {
        try {
            // npx 사용 가능 여부 확인
            ProcessBuilder pb = new ProcessBuilder();
            String os = System.getProperty("os.name").toLowerCase();
            
            if (os.contains("win")) {
                pb.command("cmd", "/c", "npx", "--version");
            } else {
                pb.command("sh", "-c", "npx --version");
            }
            
            Process process = pb.start();
            boolean completed = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            
            if (!completed) {
                process.destroyForcibly();
                CopilotLogger.error("npx command timed out", null);
                return false;
            }
            
            if (process.exitValue() != 0) {
                CopilotLogger.error("npx command failed with exit code: " + process.exitValue(), null);
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            CopilotLogger.error("Failed to verify npx command: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * MCP 서버 제거
     */
    public void removeServer(String serverName) {
        McpClient client = clients.remove(serverName);
        if (client != null) {
            client.disconnect();
            CopilotLogger.info("🔌 MCP server disconnected: " + serverName);
        }
        configs.remove(serverName);
        availableTools.remove(serverName);
    }
    
    /**
     * 모든 서버 새로고침
     */
    public void refreshServers() {
        CopilotLogger.info("🔄 Refreshing MCP server connections...");
        
        // 각 서버를 개별적으로 처리
        for (Map.Entry<String, McpClient> entry : new HashMap<>(clients).entrySet()) {
            String serverName = entry.getKey();
            McpClient client = entry.getValue();
            
            try {
                if (!client.isConnected()) {
                    CopilotLogger.info("Reconnecting server: " + serverName);
                    removeServer(serverName);
                    
                    // 설정이 있으면 재연결 시도
                    McpServerConfig config = configs.get(serverName);
                    if (config != null) {
                        addServer(config);
                    }
                }
            } catch (Exception e) {
                CopilotLogger.error("Error refreshing server " + serverName + ": " + e.getMessage(), e);
            }
        }
        
        CopilotLogger.info("✅ MCP refresh complete. Connected servers: " + clients.size());
    }

    /**
     * 로컬 MCP 설정 로드
     */
    public void loadLocalMCPConfig() {
        try {
            PreferenceManager prefs = PreferenceManager.getInstance();
            String configJson = prefs.getValue("mcp.config.json", "");
            
            if (!configJson.isEmpty() && !configJson.equals("{}")) {
                CopilotLogger.info("Parsing local MCP configuration...");
                parseAndLoadMCPConfig(configJson);
            } else {
                CopilotLogger.info("No local MCP configuration found, setting up defaults.");
                setupDefaultLocalMCP();
            }
        } catch (Exception e) {
            CopilotLogger.error("❌ Failed to load local MCP config", e);
        }
    }
    
    /**
     * MCP 설정 JSON 파싱 및 로드
     */
    private void parseAndLoadMCPConfig(String configJson) {
        try {
            JSONObject config = new JSONObject(configJson);
            
            // mcpServers 섹션 파싱
            if (config.has("mcpServers")) {
                JSONObject servers = config.getJSONObject("mcpServers");
                
                for (String serverName : servers.keySet()) {
                    JSONObject serverConfig = servers.getJSONObject(serverName);
                    
                    String command = serverConfig.getString("command");
                    JSONArray argsArray = serverConfig.optJSONArray("args");
                    JSONObject envObject = serverConfig.optJSONObject("env");
                    
                    List<String> args = parseArgs(argsArray);
                    Map<String, String> env = parseEnv(envObject);
                    
                    McpServerConfig mcpConfig = new McpServerConfig(
                        serverName,
                        "stdio", // 기본 타입
                        command,
                        args,
                        env,
                        1
                    );
                    
                    addServer(mcpConfig);
                }
            }
        } catch (Exception e) {
            CopilotLogger.error("Failed to parse MCP config JSON", e);
        }
    }
    
    /**
     * JSON 배열을 List<String>으로 변환
     */
    private List<String> parseArgs(JSONArray argsArray) {
        List<String> args = new ArrayList<>();
        if (argsArray != null) {
            for (int i = 0; i < argsArray.length(); i++) {
                args.add(argsArray.getString(i));
            }
        }
        return args;
    }
    
    /**
     * JSON 객체를 Map<String, String>으로 변환
     */
    private Map<String, String> parseEnv(JSONObject envObject) {
        Map<String, String> env = new HashMap<>();
        if (envObject != null) {
            for (String key : envObject.keySet()) {
                env.put(key, envObject.getString(key));
            }
        }
        return env;
    }

    /**
     * 기본 로컬 MCP 설정
     */
    private void setupDefaultLocalMCP() {
        // 파일시스템 MCP 서버
        McpServerConfig fsConfig = new McpServerConfig(
            "mcp-filesystem", 
            "stdio", 
            "npx",
            Arrays.asList("-y", "@modelcontextprotocol/server-filesystem", "./"),
            new HashMap<>(), 
            1
        );
        
        // Git MCP 서버
        McpServerConfig gitConfig = new McpServerConfig(
            "mcp-git", 
            "stdio", 
            "npx",
            Arrays.asList("-y", "@modelcontextprotocol/server-git"),
            new HashMap<>(), 
            1
        );
        
        if (!clients.containsKey(fsConfig.getName())) {
            addServer(fsConfig);
        }
        
        if (!clients.containsKey(gitConfig.getName())) {
            addServer(gitConfig);
        }
    }
    
    /**
     * 모든 서버 종료
     */
    public void shutdown() {
        CopilotLogger.info("🔌 Shutting down all MCP servers...");
        new ArrayList<>(clients.keySet()).forEach(this::removeServer);
        CopilotLogger.info("✅ All MCP servers shut down.");
    }

    /**
     * 도구 실행
     */
    public String executeTool(String toolName, Map<String, Object> parameters, String context) throws Exception {
        // 연결된 서버 중에서 해당 도구를 지원하는 서버 찾기
        for (Map.Entry<String, McpClient> entry : clients.entrySet()) {
            McpClient client = entry.getValue();
            if (client.isConnected() && client.hasTool(toolName)) {
                CopilotLogger.info("🛠️ Executing tool: " + toolName + " on server: " + entry.getKey());
                return client.callTool(toolName, parameters);
            }
        }
        
        throw new Exception("Tool not found or no connected server supports it: " + toolName);
    }

    /**
     * 도구 검색
     */
    private List<McpTool> discoverTools(McpClient client, String serverName) {
        if (!client.isConnected()) {
            CopilotLogger.warn("⚠️ Cannot discover tools, server is not connected: " + serverName);
            return new ArrayList<>();
        }
        
        CopilotLogger.info("🔍 Discovering tools for server: " + serverName);
        
        // 먼저 서버에서 실제 도구 목록을 가져오려고 시도
        Set<String> availableTools = client.getAvailableTools();
        if (!availableTools.isEmpty()) {
            CopilotLogger.info("✅ Found " + availableTools.size() + " tools from server");
            List<McpTool> tools = new ArrayList<>();
            for (String toolName : availableTools) {
                tools.add(new McpTool(toolName, getToolDescription(toolName), getToolParameters(toolName)));
            }
            return tools;
        }
        
        // 서버 이름에 따라 기본 도구 세트 반환
        CopilotLogger.info("Using default tools for server type: " + serverName);
        if (serverName.toLowerCase().contains("filesystem")) {
            return createFilesystemTools();
        } else if (serverName.toLowerCase().contains("git")) {
            return createGitTools();
        } else if (serverName.toLowerCase().contains("sqlite")) {
            return createSQLiteTools();
        }
        
        return new ArrayList<>();
    }
    
    private String getToolDescription(String toolName) {
        switch (toolName) {
            case "read_file": return "파일 내용 읽기";
            case "write_file": return "파일 쓰기";
            case "list_directory": return "디렉토리 목록";
            case "search_files": return "파일 검색";
            case "git_status": return "Git 상태 확인";
            case "git_log": return "Git 로그 보기";
            case "git_diff": return "Git 변경사항 보기";
            case "execute_query": return "SQL 쿼리 실행";
            default: return toolName + " 도구";
        }
    }
    
    private String getToolParameters(String toolName) {
        switch (toolName) {
            case "read_file": return "path";
            case "write_file": return "path,content";
            case "list_directory": return "path";
            case "search_files": return "query,path";
            case "git_status": return "";
            case "git_log": return "limit";
            case "git_diff": return "";
            case "execute_query": return "query";
            default: return "";
        }
    }
    
    /**
     * 파일시스템 도구 생성 (실제 MCP filesystem 서버가 제공하는 도구만)
     */
    private List<McpTool> createFilesystemTools() { 
        List<McpTool> tools = new ArrayList<>();
        tools.add(new McpTool("read_file", "파일 내용 읽기", "path"));
        tools.add(new McpTool("write_file", "파일 쓰기", "path,content"));
        tools.add(new McpTool("list_directory", "디렉토리 목록", "path"));
        tools.add(new McpTool("search_files", "파일 검색", "query,path"));
        // 기본 제공되지 않는 도구들 제거
        return tools;
    }
    
    /**
     * Git 도구 생성 (실제 MCP git 서버가 제공하는 도구만)
     */
    private List<McpTool> createGitTools() { 
        List<McpTool> tools = new ArrayList<>();
        tools.add(new McpTool("git_status", "Git 상태 확인", ""));
        tools.add(new McpTool("git_log", "Git 로그 보기", "limit"));
        tools.add(new McpTool("git_diff", "Git 변경사항 보기", ""));
        // 기본 제공되지 않는 도구들 제거
        return tools;
    }
    
    /**
     * SQLite 도구 생성 (실제 MCP sqlite 서버가 제공하는 도구만)
     */
    private List<McpTool> createSQLiteTools() {
        List<McpTool> tools = new ArrayList<>();
        tools.add(new McpTool("execute_query", "SQL 쿼리 실행", "query"));
        tools.add(new McpTool("list_tables", "테이블 목록", ""));
        tools.add(new McpTool("describe_table", "테이블 구조", "table_name"));
        return tools;
    }
    
    /**
     * MCP 상태 조회
     */
    public McpStatus getStatus() {
        long connectedCount = clients.values().stream().filter(McpClient::isConnected).count();
        int totalTools = getConnectedTools().values().stream().mapToInt(List::size).sum();
        return new McpStatus(configs.size(), (int) connectedCount, totalTools);
    }
    
    /**
     * 연결된 도구 목록 조회
     */
    public Map<String, List<McpTool>> getConnectedTools() {
        return clients.entrySet().stream()
                .filter(entry -> entry.getValue().isConnected())
                .collect(Collectors.toMap(
                    Map.Entry::getKey, 
                    entry -> availableTools.getOrDefault(entry.getKey(), new ArrayList<>())
                ));
    }
    
    /**
     * 특정 서버의 도구 목록 조회
     */
    public List<McpTool> getServerTools(String serverName) {
        return availableTools.getOrDefault(serverName, new ArrayList<>());
    }
    
    /**
     * 서버 설정 조회
     */
    public McpServerConfig getServerConfig(String serverName) {
        return configs.get(serverName);
    }
    
    /**
     * 모든 서버 이름 조회
     */
    public List<String> getServerNames() {
        return new ArrayList<>(configs.keySet());
    }
    
    /**
     * 🛠️ MCP Tool - MCP 도구 정보
     */
    public static class McpTool {
        private final String name;
        private final String description;
        private final String parameters;
        
        public McpTool(String name, String description, String parameters) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
        }
        
        public String getName() { 
            return name; 
        }
        
        public String getDescription() { 
            return description; 
        }
        
        public String getParameters() { 
            return parameters; 
        }
        
        @Override
        public String toString() {
            return String.format("%s - %s", name, description);
        }
    }
    
    /**
     * 📊 MCP Status - MCP 상태 정보
     */
    public static class McpStatus {
        private final int totalConfiguredServers;
        private final int connectedServers;
        private final int totalTools;
        
        public McpStatus(int totalConfiguredServers, int connectedServers, int totalTools) {
            this.totalConfiguredServers = totalConfiguredServers;
            this.connectedServers = connectedServers;
            this.totalTools = totalTools;
        }

        public int getTotalServers() { 
            return totalConfiguredServers; 
        }
        
        public int getConnectedServers() { 
            return connectedServers; 
        }
        
        public int getTotalTools() { 
            return totalTools; 
        }
        
        @Override
        public String toString() {
            return String.format("Servers: %d/%d connected, Tools: %d", 
                connectedServers, totalConfiguredServers, totalTools);
        }
    }
}