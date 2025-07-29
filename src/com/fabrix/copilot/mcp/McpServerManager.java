package com.fabrix.copilot.mcp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.fabrix.copilot.utils.CopilotLogger;
import com.fabrix.copilot.utils.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;

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
            CopilotLogger.info("🔄 Attempting to connect to MCP server: " + config.getName());
            McpClient client = new McpClient(config);
            if (client.connect()) {
                clients.put(config.getName(), client);
                configs.put(config.getName(), config);
                List<McpTool> tools = discoverTools(client, config.getName());
                availableTools.put(config.getName(), tools);
                CopilotLogger.info(String.format("✅ MCP server connected: %s (%d tools discovered)", 
                    config.getName(), tools.size()));
                return true;
            } else {
                CopilotLogger.warn("❌ MCP server connection failed: " + config.getName(), null);
                return false;
            }
        } catch (Exception e) {
            CopilotLogger.error("❌ Failed to add MCP server: " + config.getName(), e);
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
        List<String> disconnectedServers = clients.entrySet().stream()
            .filter(entry -> !entry.getValue().isConnected())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        disconnectedServers.forEach(this::removeServer);
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
                CopilotLogger.info("Parsing MCP configuration from JSON...");
                
                // JSON 파싱 추가
                JSONObject config = new JSONObject(configJson);
                JSONArray servers = config.optJSONArray("mcpServers");
                
                if (servers != null) {
                    for (int i = 0; i < servers.length(); i++) {
                        JSONObject serverConfig = servers.getJSONObject(i);
                        
                        McpServerConfig mcpConfig = new McpServerConfig(
                            serverConfig.getString("name"),
                            serverConfig.optString("type", "stdio"),
                            serverConfig.getString("command"),
                            parseArgs(serverConfig.optJSONArray("args")),
                            parseEnv(serverConfig.optJSONObject("env")),
                            serverConfig.optInt("priority", 1)
                        );
                        
                        addServer(mcpConfig);
                    }
                }
            } else {
                // 기본 설정 사용
                setupDefaultLocalMCP();
            }
        } catch (Exception e) {
            CopilotLogger.error("Failed to load MCP config from JSON", e);
            setupDefaultLocalMCP();
        }
    }

 // OS별 npx 실행 처리
    private String getNpxCommand() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "npx.cmd"; // Windows
        }
        return "npx"; // Mac/Linux
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
        } else if (serverName.toLowerCase().contains("web")) {
            return createWebTools();
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
            case "execute_query": return "query";
            default: return "";
        }
    }
    
    /**
     * 파일시스템 도구 생성
     */
    private List<McpTool> createFilesystemTools() { 
        List<McpTool> tools = new ArrayList<>();
        tools.add(new McpTool("read_file", "파일 내용 읽기", "path"));
        tools.add(new McpTool("write_file", "파일 쓰기", "path,content"));
        tools.add(new McpTool("list_directory", "디렉토리 목록", "path"));
        tools.add(new McpTool("search_files", "파일 검색", "query,path"));
        tools.add(new McpTool("create_directory", "디렉토리 생성", "path"));
        tools.add(new McpTool("delete_file", "파일 삭제", "path"));
        tools.add(new McpTool("move_file", "파일 이동", "source,destination"));
        tools.add(new McpTool("copy_file", "파일 복사", "source,destination"));
        return tools;
    }
    
    /**
     * Git 도구 생성
     */
    private List<McpTool> createGitTools() { 
        List<McpTool> tools = new ArrayList<>();
        tools.add(new McpTool("git_status", "Git 상태 확인", ""));
        tools.add(new McpTool("git_log", "Git 로그 보기", "limit"));
        tools.add(new McpTool("git_diff", "Git 변경사항 보기", ""));
        tools.add(new McpTool("git_branch", "Git 브랜치 목록", ""));
        tools.add(new McpTool("git_commit", "Git 커밋", "message"));
        tools.add(new McpTool("git_push", "Git 푸시", "branch"));
        tools.add(new McpTool("git_pull", "Git 풀", "branch"));
        return tools;
    }
    
    /**
     * SQLite 도구 생성
     */
    private List<McpTool> createSQLiteTools() {
        List<McpTool> tools = new ArrayList<>();
        tools.add(new McpTool("execute_query", "SQL 쿼리 실행", "query"));
        tools.add(new McpTool("list_tables", "테이블 목록", ""));
        tools.add(new McpTool("describe_table", "테이블 구조", "table_name"));
        tools.add(new McpTool("create_table", "테이블 생성", "table_name,columns"));
        return tools;
    }
    
    /**
     * 웹 도구 생성
     */
    private List<McpTool> createWebTools() {
        List<McpTool> tools = new ArrayList<>();
        tools.add(new McpTool("fetch_url", "URL 가져오기", "url"));
        tools.add(new McpTool("search_web", "웹 검색", "query"));
        tools.add(new McpTool("scrape_page", "페이지 스크래핑", "url,selector"));
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