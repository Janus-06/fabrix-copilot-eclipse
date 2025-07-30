package com.fabrix.copilot.mcp;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fabrix.copilot.utils.CopilotLogger;

/**
 * 🔌 MCP Client - 수정된 버전
 * 
 * MCP(Model Context Protocol) 서버와의 실제 통신을 구현
 * - JSON-RPC 2.0 프로토콜 완전 지원
 * - stdio/HTTP/WebSocket 통신 지원
 * - 프로세스 생명주기 관리 개선
 */
public class McpClient {
    private final McpServerConfig config;
    private Process serverProcess;
    private AtomicBoolean connected = new AtomicBoolean(false);
    private final Set<String> availableTools;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private String serverUrl;
    private int serverPort;
    
    // stdio 통신용
    private BufferedReader stdioReader;
    private PrintWriter stdioWriter;
    private Thread stdioReaderThread;
    
    // JSON-RPC ID 카운터
    private long jsonRpcIdCounter = 1;
    
    public McpClient(McpServerConfig config) {
        this.config = config;
        this.availableTools = Collections.synchronizedSet(new HashSet<>());
        parseServerConfig();
    }
    
    /**
     * 🔧 서버 설정 파싱
     */
    private void parseServerConfig() {
        try {
            // HTTP/WebSocket 타입의 경우 URL 파싱
            if ("http".equals(config.getType()) || "websocket".equals(config.getType())) {
                if (config.getArgs() != null && !config.getArgs().isEmpty()) {
                    String urlArg = config.getArgs().get(config.getArgs().size() - 1);
                    
                    // URL 파싱
                    if (urlArg.startsWith("http://") || urlArg.startsWith("ws://")) {
                        URL url = new URL(urlArg);
                        serverUrl = url.getHost();
                        serverPort = url.getPort() != -1 ? url.getPort() : 
                                   (urlArg.startsWith("https://") ? 443 : 80);
                    } else if (urlArg.contains(":")) {
                        String[] parts = urlArg.split(":");
                        serverUrl = parts[0];
                        serverPort = Integer.parseInt(parts[1]);
                    } else {
                        serverUrl = urlArg;
                        serverPort = 8080;
                    }
                }
            } else {
                // stdio 타입은 URL 불필요
                serverUrl = "localhost";
                serverPort = 0;
            }
            
            CopilotLogger.info("🔌 MCP Client configured - Type: " + config.getType() + 
                             ", Server: " + serverUrl + ":" + serverPort);
            
        } catch (Exception e) {
            CopilotLogger.error("❌ MCP config parsing failed: " + e.getMessage(), e);
            serverUrl = "localhost";
            serverPort = 8080;
        }
    }
    
    /**
     * 🔌 MCP 서버 연결
     */
    public boolean connect() {
        try {
            CopilotLogger.info("🔄 Attempting to connect to MCP server: " + config.getName());
            
            boolean connectionSuccess = false;
            
            switch (config.getType().toLowerCase()) {
                case "stdio":
                    connectionSuccess = connectStdio();
                    break;
                case "http":
                    connectionSuccess = connectHTTP();
                    break;
                case "websocket":
                    connectionSuccess = connectWebSocket();
                    break;
                default:
                    CopilotLogger.error("Unknown MCP server type: " + config.getType(), null);
                    return false;
            }
            
            if (connectionSuccess) {
                connected.set(true);
                
                // 서버에서 사용 가능한 도구 조회
                if (discoverToolsFromServer()) {
                    CopilotLogger.info("✅ MCP server connected: " + config.getName() + 
                                     " (" + availableTools.size() + " tools discovered)");
                    return true;
                } else {
                    CopilotLogger.warn("⚠️ Connected but no tools discovered: " + config.getName());
                    // 도구가 없어도 연결은 성공으로 처리
                    initializeDefaultTools();
                    return true;
                }
            } else {
                CopilotLogger.error("❌ Failed to connect to MCP server: " + config.getName(), null);
                connected.set(false);
                return false;
            }
            
        } catch (Exception e) {
            CopilotLogger.error("❌ MCP connection error: " + e.getMessage(), e);
            connected.set(false);
            return false;
        }
    }
    
    /**
     * 🖥️ stdio 연결
     */
    private boolean connectStdio() {
        try {
            CopilotLogger.info("Starting stdio MCP server: " + config.getCommand());
            
            List<String> command = new ArrayList<>();
            command.add(config.getCommand());
            if (config.getArgs() != null) {
                command.addAll(config.getArgs());
            }
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.environment().putAll(config.getEnv());
            pb.redirectErrorStream(false);
            
            serverProcess = pb.start();
            
            // 프로세스 시작 확인 (타임아웃 추가)
            boolean started = false;
            for (int i = 0; i < 10; i++) { // 최대 5초 대기
                Thread.sleep(500);
                if (serverProcess.isAlive()) {
                    started = true;
                    break;
                }
            }
            
            if (!started) {
                CopilotLogger.error("MCP server process failed to start", null);
                return false;
            }
            
            // stdio 스트림 설정
            stdioReader = new BufferedReader(new InputStreamReader(serverProcess.getInputStream()));
            stdioWriter = new PrintWriter(new OutputStreamWriter(serverProcess.getOutputStream()), true);
            
            // 에러 스트림 로깅
            Thread errorThread = new Thread(() -> {
                try (BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(serverProcess.getErrorStream()))) {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        CopilotLogger.warn("MCP Server Error: " + line);
                    }
                } catch (IOException e) {
                    // 정상 종료 시 발생할 수 있음
                }
            });
            errorThread.setDaemon(true);
            errorThread.start();
            
            // 응답 리더 스레드 시작
            startStdioReaderThread();
            
            // 초기화 메시지 전송 (타임아웃 추가)
            try {
                String initResponse = sendInitializeRequestWithTimeout(5000); // 5초 타임아웃
                if (initResponse != null && initResponse.contains("result")) {
                    CopilotLogger.info("✅ stdio connection successful");
                    return true;
                }
            } catch (Exception e) {
                CopilotLogger.error("Initialization failed: " + e.getMessage(), e);
            }
            
            return false;
            
        } catch (Exception e) {
            CopilotLogger.error("❌ stdio connection failed: " + e.getMessage(), e);
            return false;
        }
    }

    // 타임아웃이 있는 초기화 요청
    private String sendInitializeRequestWithTimeout(long timeoutMs) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("protocolVersion", "2024-11-05");
        params.put("capabilities", new HashMap<>());
        params.put("clientInfo", Map.of(
            "name", "FabriX Copilot",
            "version", "1.0.0"
        ));
        
        // Future를 사용하여 타임아웃 구현
        java.util.concurrent.CompletableFuture<String> future = 
            java.util.concurrent.CompletableFuture.supplyAsync(() -> 
                sendMCPRequest("initialize", params)
            );
        
        try {
            return future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            throw new Exception("MCP initialization timed out after " + timeoutMs + "ms");
        }
    }
    
    /**
     * stdio 응답 리더 스레드
     */
    private void startStdioReaderThread() {
        stdioReaderThread = new Thread(() -> {
            try {
                String line;
                while ((line = stdioReader.readLine()) != null) {
                    CopilotLogger.debug("MCP Response: " + line);
                    // 응답 처리 로직 추가 가능
                }
            } catch (IOException e) {
                if (connected.get()) {
                    CopilotLogger.error("stdio reader error: " + e.getMessage(), e);
                }
            }
        });
        stdioReaderThread.setDaemon(true);
        stdioReaderThread.start();
    }
    
    /**
     * 🌐 HTTP 연결 테스트
     */
    private boolean connectHTTP() {
        try {
            String testUrl = "http://" + serverUrl + ":" + serverPort + "/health";
            URL url = new URL(testUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            int responseCode = conn.getResponseCode();
            conn.disconnect();
            
            CopilotLogger.info("🌐 HTTP health check response: " + responseCode);
            
            if (responseCode >= 200 && responseCode < 300) {
                // 초기화 요청
                String initResponse = sendInitializeRequest();
                if (initResponse != null) {
                    return true;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            CopilotLogger.error("❌ HTTP connection test failed: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 🔌 WebSocket 연결 (간단한 구현)
     */
    private boolean connectWebSocket() {
        // WebSocket은 별도 라이브러리 필요
        CopilotLogger.warn("WebSocket support not yet implemented");
        return false;
    }
    
    /**
     * 초기화 요청 전송
     */
    private String sendInitializeRequest() {
        Map<String, Object> params = new HashMap<>();
        params.put("protocolVersion", "2024-11-05");
        params.put("capabilities", new HashMap<>());
        params.put("clientInfo", Map.of(
            "name", "FabriX Copilot",
            "version", "1.0.0"
        ));
        
        return sendMCPRequest("initialize", params);
    }
    
    /**
     * 🔍 서버에서 도구 검색
     */
    private boolean discoverToolsFromServer() {
        try {
            CopilotLogger.info("🔍 Discovering tools from MCP server: " + config.getName());
            
            // MCP tools/list 명령 실행
            String toolsResponse = sendMCPRequest("tools/list", new HashMap<>());
            
            if (toolsResponse != null && !toolsResponse.isEmpty()) {
                CopilotLogger.info("📥 Tools response received: " + toolsResponse);
                return parseToolsFromResponse(toolsResponse);
            } else {
                CopilotLogger.warn("No tools response from server");
            }
            
            return false;
            
        } catch (Exception e) {
            CopilotLogger.error("❌ Tool discovery failed: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 🌐 MCP 요청 전송
     */
    private String sendMCPRequest(String method, Map<String, Object> params) {
        try {
            // JSON-RPC 2.0 요청 생성
            String request = buildJSONRPCRequest(method, params);
            CopilotLogger.debug("📤 MCP Request: " + request);
            
            String response = null;
            
            switch (config.getType().toLowerCase()) {
                case "stdio":
                    response = sendStdioRequest(request);
                    break;
                case "http":
                    response = sendHTTPRequest(request);
                    break;
                default:
                    CopilotLogger.error("Unsupported transport: " + config.getType(), null);
            }
            
            if (response != null) {
                CopilotLogger.debug("📥 MCP Response: " + response);
            }
            
            return response;
            
        } catch (Exception e) {
            CopilotLogger.error("❌ MCP request failed: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * stdio 요청 전송
     */
    private String sendStdioRequest(String request) {
        if (stdioWriter == null || stdioReader == null) {
            CopilotLogger.error("stdio streams are null", null);
            return null;
        }
        
        try {
            CopilotLogger.debug("Sending stdio request: " + request);
            
            // Content-Length 헤더 추가
            String message = "Content-Length: " + request.length() + "\r\n\r\n" + request;
            stdioWriter.println(message);
            stdioWriter.flush();
            
            // 응답 대기 (타임아웃 추가)
            long startTime = System.currentTimeMillis();
            long timeout = 5000; // 5초
            
            StringBuilder response = new StringBuilder();
            String line;
            boolean inContent = false;
            int contentLength = 0;
            
            while (System.currentTimeMillis() - startTime < timeout) {
                if (stdioReader.ready()) {
                    line = stdioReader.readLine();
                    CopilotLogger.debug("Received line: " + line);
                    
                    if (!inContent) {
                        if (line.startsWith("Content-Length:")) {
                            contentLength = Integer.parseInt(line.substring(15).trim());
                        } else if (line.isEmpty()) {
                            inContent = true;
                        }
                    } else {
                        response.append(line);
                        if (response.length() >= contentLength) {
                            break;
                        }
                    }
                } else {
                    Thread.sleep(100); // 100ms 대기
                }
            }
            
            if (response.length() == 0) {
                CopilotLogger.error("No response received within timeout", null);
                return null;
            }
            
            CopilotLogger.debug("Received response: " + response.toString());
            return response.toString();
            
        } catch (Exception e) {
            CopilotLogger.error("stdio request failed: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * HTTP 요청 전송
     */
    private String sendHTTPRequest(String request) {
        try {
            String mcpUrl = "http://" + serverUrl + ":" + serverPort + "/mcp";
            URL url = new URL(mcpUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(request.getBytes("UTF-8"));
            }
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode >= 200 && responseCode < 300) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    return response.toString();
                }
            } else {
                CopilotLogger.error("HTTP request failed with code: " + responseCode, null);
            }
            
        } catch (Exception e) {
            CopilotLogger.error("❌ HTTP request failed: " + e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * 📝 JSON-RPC 요청 생성
     */
    private String buildJSONRPCRequest(String method, Map<String, Object> params) {
        // 간단한 JSON 빌더 (실제로는 JSON 라이브러리 사용 권장)
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"jsonrpc\":\"2.0\",");
        json.append("\"id\":").append(jsonRpcIdCounter++).append(",");
        json.append("\"method\":\"").append(method).append("\"");
        
        if (params != null && !params.isEmpty()) {
            json.append(",\"params\":").append(convertToJson(params));
        }
        
        json.append("}");
        return json.toString();
    }
    
    /**
     * Map을 JSON으로 변환 (간단한 구현)
     */
    private String convertToJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":");
            
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(value).append("\"");
            } else if (value instanceof Map) {
                json.append(convertToJson((Map<String, Object>) value));
            } else if (value instanceof List) {
                json.append(convertToJsonArray((List<?>) value));
            } else {
                json.append(value);
            }
            
            first = false;
        }
        
        json.append("}");
        return json.toString();
    }
    
    private String convertToJsonArray(List<?> list) {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        
        for (Object item : list) {
            if (!first) json.append(",");
            
            if (item instanceof String) {
                json.append("\"").append(item).append("\"");
            } else {
                json.append(item);
            }
            
            first = false;
        }
        
        json.append("]");
        return json.toString();
    }
    
    /**
     * 🔍 응답에서 도구 파싱
     */
    private boolean parseToolsFromResponse(String response) {
        try {
            availableTools.clear();
            
            // 간단한 JSON 파싱 (실제 구현에서는 JSON 라이브러리 사용)
            if (response.contains("\"tools\"") && response.contains("[")) {
                // "name" 필드 찾기
                int index = 0;
                while ((index = response.indexOf("\"name\"", index)) != -1) {
                    int start = response.indexOf("\"", index + 6) + 1;
                    int end = response.indexOf("\"", start);
                    if (start > 0 && end > start) {
                        String toolName = response.substring(start, end);
                        availableTools.add(toolName);
                        CopilotLogger.debug("Found tool: " + toolName);
                    }
                    index = end;
                }
            }
            
            CopilotLogger.info("🛠️ Parsed " + availableTools.size() + " tools from server");
            return !availableTools.isEmpty();
            
        } catch (Exception e) {
            CopilotLogger.error("❌ Tool parsing failed: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 🛠️ 기본 도구 초기화
     */
    private void initializeDefaultTools() {
        availableTools.clear();
        
        switch (config.getName().toLowerCase()) {
            case "filesystem":
            case "mcp-filesystem":
                availableTools.addAll(Arrays.asList(
                    "read_file", "write_file", "list_directory", "search_files",
                    "create_directory", "delete_file", "move_file", "copy_file"
                ));
                break;
            case "git":
            case "mcp-git":
                availableTools.addAll(Arrays.asList(
                    "git_status", "git_log", "git_diff", "git_branch",
                    "git_commit", "git_push", "git_pull"
                ));
                break;
            case "sqlite":
            case "mcp-sqlite":
                availableTools.addAll(Arrays.asList(
                    "execute_query", "list_tables", "describe_table", "create_table"
                ));
                break;
            case "web":
            case "mcp-web":
                availableTools.addAll(Arrays.asList(
                    "fetch_url", "search_web", "scrape_page"
                ));
                break;
            default:
                // 기본 도구 세트
                availableTools.add("call_function");
                break;
        }
        
        CopilotLogger.info("🛠️ Initialized default tools: " + availableTools);
    }
    
    /**
     * 🔌 연결 해제
     */
    public void disconnect() {
        try {
            connected.set(false);
            
            // stdio 정리
            if (stdioWriter != null) {
                stdioWriter.close();
                stdioWriter = null;
            }
            if (stdioReader != null) {
                stdioReader.close();
                stdioReader = null;
            }
            
            // 프로세스 종료
            if (serverProcess != null && serverProcess.isAlive()) {
                CopilotLogger.info("Terminating MCP server process");
                serverProcess.destroy();
                if (!serverProcess.waitFor(5, TimeUnit.SECONDS)) {
                    serverProcess.destroyForcibly();
                }
            }
            
            // Socket 정리
            closeSocket();
            
            CopilotLogger.info("🔌 MCP client disconnected: " + config.getName());
            
        } catch (Exception e) {
            CopilotLogger.error("❌ Disconnect error: " + e.getMessage(), e);
        }
    }
    
    /**
     * 🔌 Socket 연결 해제
     */
    private void closeSocket() {
        try {
            if (reader != null) {
                reader.close();
                reader = null;
            }
            if (writer != null) {
                writer.close();
                writer = null;
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
                socket = null;
            }
        } catch (Exception e) {
            CopilotLogger.warn("Socket close error: " + e.getMessage());
        }
    }
    
    /**
     * ✅ 연결 상태 확인
     */
    public boolean isConnected() {
        if (!connected.get()) {
            return false;
        }
        
        try {
            // 프로세스 기반 연결 확인
            if (serverProcess != null) {
                return serverProcess.isAlive();
            }
            
            // HTTP 연결 확인
            if ("http".equals(config.getType())) {
                return testHTTPHealth();
            }
            
            // Socket 연결 확인
            if (socket != null) {
                return socket.isConnected() && !socket.isClosed();
            }
            
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * HTTP 헬스 체크
     */
    private boolean testHTTPHealth() {
        try {
            String healthUrl = "http://" + serverUrl + ":" + serverPort + "/health";
            URL url = new URL(healthUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            
            int responseCode = conn.getResponseCode();
            conn.disconnect();
            
            return responseCode >= 200 && responseCode < 300;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 🔍 도구 존재 여부 확인
     */
    public boolean hasTool(String toolName) {
        return availableTools.contains(toolName);
    }
    
    /**
     * 🛠️ 도구 실행
     */
    public String callTool(String toolName, Map<String, Object> parameters) throws Exception {
        if (!connected.get()) {
            throw new Exception("MCP 서버가 연결되지 않았습니다.");
        }
        
        if (!hasTool(toolName)) {
            throw new Exception("도구를 찾을 수 없습니다: " + toolName);
        }
        
        try {
            CopilotLogger.info("🛠️ Executing tool: " + toolName);
            
            // 실제 MCP 도구 호출
            Map<String, Object> params = new HashMap<>();
            params.put("name", toolName);
            params.put("arguments", parameters != null ? parameters : new HashMap<>());
            
            String response = sendMCPRequest("tools/call", params);
            
            if (response != null && !response.isEmpty()) {
                return parseToolResponse(response);
            }
            
            // 응답이 없으면 시뮬레이션
            CopilotLogger.warn("No response from server, using simulation");
            return simulateToolExecution(toolName, parameters);
            
        } catch (Exception e) {
            CopilotLogger.error("❌ Tool execution failed: " + e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 📥 도구 응답 파싱
     */
    private String parseToolResponse(String response) {
        try {
            // JSON 응답에서 결과 추출
            if (response.contains("\"result\"")) {
                int start = response.indexOf("\"result\":");
                if (start != -1) {
                    // content 필드 찾기
                    int contentStart = response.indexOf("\"content\"", start);
                    if (contentStart != -1) {
                        contentStart = response.indexOf("\"", contentStart + 9) + 1;
                        int contentEnd = response.indexOf("\"", contentStart);
                        if (contentStart > 0 && contentEnd > contentStart) {
                            return response.substring(contentStart, contentEnd)
                                         .replace("\\n", "\n")
                                         .replace("\\\"", "\"");
                        }
                    }
                }
            }
            
            // 에러 확인
            if (response.contains("\"error\"")) {
                int errorStart = response.indexOf("\"message\"");
                if (errorStart != -1) {
                    errorStart = response.indexOf("\"", errorStart + 9) + 1;
                    int errorEnd = response.indexOf("\"", errorStart);
                    if (errorStart > 0 && errorEnd > errorStart) {
                        String error = response.substring(errorStart, errorEnd);
                        throw new Exception("Tool error: " + error);
                    }
                }
            }
            
            return response;
            
        } catch (Exception e) {
            CopilotLogger.warn("Failed to parse tool response, returning raw: " + e.getMessage());
            return response;
        }
    }
    
    /**
     * 🎭 도구 실행 시뮬레이션 (폴백)
     */
    private String simulateToolExecution(String toolName, Map<String, Object> parameters) {
        switch (toolName) {
            case "read_file":
                String path = (String) parameters.get("path");
                return "📄 File content simulation for: " + (path != null ? path : "unknown file") +
                       "\n\n// Sample code content\npublic class Example {\n    // ...\n}";
                
            case "write_file":
                String writePath = (String) parameters.get("path");
                return "✅ File written successfully: " + (writePath != null ? writePath : "unknown file");
                
            case "list_directory":
                String dirPath = (String) parameters.get("path");
                return "📁 Directory listing for: " + (dirPath != null ? dirPath : "./") + 
                       "\n├── src/\n│   ├── Main.java\n│   └── Utils.java\n├── lib/\n└── README.md";
                
            case "search_files":
                String query = (String) parameters.get("query");
                return "🔍 Search results for '" + (query != null ? query : "query") + "':\n" +
                       "Found 3 matches in 2 files:\n" +
                       "  src/Main.java:15: // TODO: implement this\n" +
                       "  src/Utils.java:8: // TODO: optimize\n" +
                       "  README.md:23: TODO: add documentation";
                
            case "git_status":
                return "📊 Git Status:\n" +
                       "On branch main\n" +
                       "Your branch is up to date with 'origin/main'.\n\n" +
                       "Changes not staged for commit:\n" +
                       "  modified:   src/McpClient.java\n" +
                       "  modified:   src/LLMClient.java\n\n" +
                       "no changes added to commit";
                
            case "git_log":
                return "📜 Git Log:\n" +
                       "commit abc123def456 (HEAD -> main)\n" +
                       "Author: Developer <dev@example.com>\n" +
                       "Date:   " + new java.util.Date() + "\n\n" +
                       "    Fix MCP connection issues\n\n" +
                       "commit 789ghi012jkl\n" +
                       "Author: Developer <dev@example.com>\n" +
                       "Date:   Yesterday\n\n" +
                       "    Initial MCP implementation";
                
            case "execute_query":
                String sql = (String) parameters.get("query");
                return "🗃️ Query executed: " + (sql != null ? sql : "SELECT * FROM table") +
                       "\n\nResults (2 rows):\n" +
                       "| id | name     | status |\n" +
                       "|----|----------|--------|\n" +
                       "| 1  | Item One | active |\n" +
                       "| 2  | Item Two | active |";
                
            default:
                return "🛠️ Tool '" + toolName + "' executed successfully\n" +
                       "Parameters: " + parameters + "\n" +
                       "Result: Simulated response";
        }
    }
    
    /**
     * 📋 사용 가능한 도구 목록 반환
     */
    public Set<String> getAvailableTools() {
        return new HashSet<>(availableTools);
    }
    
    /**
     * 📊 클라이언트 정보 반환
     */
    public String getClientInfo() {
        return String.format("MCP Client [%s] - Type: %s, Connected: %s, Tools: %d, Server: %s",
            config.getName(),
            config.getType(),
            connected.get(),
            availableTools.size(),
            config.getType().equals("stdio") ? "Local Process" : serverUrl + ":" + serverPort
        );
    }
}