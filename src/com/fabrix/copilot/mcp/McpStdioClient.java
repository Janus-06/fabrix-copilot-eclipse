package com.fabrix.copilot.mcp;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import com.fabrix.copilot.utils.CopilotLogger;
import org.json.JSONObject;
import org.json.JSONArray;

/**
 * Claude Desktop 스타일의 MCP Stdio 클라이언트
 */
public class McpStdioClient {
    private final McpServerConfig config;
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private BufferedReader errorReader;
    
    private final AtomicLong requestId = new AtomicLong(1);
    private final Map<Long, CompletableFuture<JSONObject>> pendingRequests = new ConcurrentHashMap<>();
    private final Set<String> availableTools = new HashSet<>();
    
    private Thread readerThread;
    private volatile boolean running = false;
    
    public McpStdioClient(McpServerConfig config) {
        this.config = config;
    }
    
    /**
     * 서버 시작 및 연결
     */
    public boolean connect() {
        try {
            CopilotLogger.info("Starting MCP server: " + config.getName());
            
            // 프로세스 빌더 설정
            List<String> command = new ArrayList<>();
            command.add(config.getCommand());
            command.addAll(config.getArgs());
            
            ProcessBuilder pb = new ProcessBuilder(command);
            
            // 환경 변수 설정
            Map<String, String> env = pb.environment();
            env.putAll(config.getEnv());
            
            // 작업 디렉토리 설정 (args의 경로에서 추출)
            if (!config.getArgs().isEmpty()) {
                String firstArg = config.getArgs().get(0);
                File scriptFile = new File(firstArg);
                if (scriptFile.exists() && scriptFile.getParentFile() != null) {
                    pb.directory(scriptFile.getParentFile().getParentFile()); // dist의 부모 디렉토리
                    CopilotLogger.info("Working directory: " + pb.directory());
                }
            }
            
            // 프로세스 시작
            process = pb.start();
            
            // 스트림 설정
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            
            // 에러 스트림 리더 시작
            startErrorReader();
            
            // 응답 리더 시작
            startResponseReader();
            
            // 프로세스 상태 확인
            Thread.sleep(500); // 프로세스 시작 대기
            
            if (!process.isAlive()) {
                CopilotLogger.error("Process died immediately");
                return false;
            }
            
            // 초기화
            return initialize();
            
        } catch (Exception e) {
            CopilotLogger.error("Failed to start MCP server", e);
            return false;
        }
    }
    
    /**
     * 에러 스트림 리더
     */
    private void startErrorReader() {
        Thread errorThread = new Thread(() -> {
            try {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    CopilotLogger.warn("MCP stderr: " + line);
                }
            } catch (IOException e) {
                // 정상 종료
            }
        });
        errorThread.setDaemon(true);
        errorThread.start();
    }
    
    /**
     * 응답 리더 스레드
     */
    private void startResponseReader() {
        running = true;
        readerThread = new Thread(() -> {
            try {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    CopilotLogger.debug("MCP response: " + line);
                    handleResponse(line);
                }
            } catch (IOException e) {
                if (running) {
                    CopilotLogger.error("Reader thread error", e);
                }
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();
    }
    
    /**
     * 응답 처리
     */
    private void handleResponse(String response) {
        try {
            JSONObject json = new JSONObject(response);
            
            // JSON-RPC 응답인지 확인
            if (json.has("id")) {
                long id = json.getLong("id");
                CompletableFuture<JSONObject> future = pendingRequests.remove(id);
                if (future != null) {
                    future.complete(json);
                }
            }
            
            // 알림(notification) 처리
            if (!json.has("id") && json.has("method")) {
                handleNotification(json);
            }
            
        } catch (Exception e) {
            CopilotLogger.error("Failed to handle response: " + response, e);
        }
    }
    
    /**
     * 알림 처리
     */
    private void handleNotification(JSONObject notification) {
        String method = notification.getString("method");
        CopilotLogger.info("Received notification: " + method);
    }
    
    /**
     * JSON-RPC 요청 전송
     */
    private CompletableFuture<JSONObject> sendRequest(String method, Map<String, Object> params) {
        CompletableFuture<JSONObject> future = new CompletableFuture<>();
        
        try {
            long id = requestId.getAndIncrement();
            
            JSONObject request = new JSONObject();
            request.put("jsonrpc", "2.0");
            request.put("id", id);
            request.put("method", method);
            request.put("params", new JSONObject(params));
            
            pendingRequests.put(id, future);
            
            String jsonString = request.toString();
            CopilotLogger.debug("Sending: " + jsonString);
            
            writer.write(jsonString);
            writer.newLine();
            writer.flush();
            
            // 타임아웃 설정
            return future.orTimeout(10, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            future.completeExceptionally(e);
            return future;
        }
    }
    
    /**
     * 초기화
     */
    private boolean initialize() {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("protocolVersion", "2024-11-05");
            
            Map<String, Object> capabilities = new HashMap<>();
            capabilities.put("roots", new HashMap<>());
            params.put("capabilities", capabilities);
            
            Map<String, Object> clientInfo = new HashMap<>();
            clientInfo.put("name", "FabriX Copilot");
            clientInfo.put("version", "1.0.0");
            params.put("clientInfo", clientInfo);
            
            JSONObject response = sendRequest("initialize", params).get();
            
            if (response.has("result")) {
                CopilotLogger.info("✅ MCP server initialized: " + config.getName());
                
                // 도구 목록 가져오기
                return discoverTools();
            }
            
            return false;
            
        } catch (Exception e) {
            CopilotLogger.error("Initialization failed", e);
            return false;
        }
    }
    
    /**
     * 도구 검색
     */
    private boolean discoverTools() {
        try {
            JSONObject response = sendRequest("tools/list", new HashMap<>()).get();
            
            if (response.has("result")) {
                JSONObject result = response.getJSONObject("result");
                if (result.has("tools")) {
                    JSONArray tools = result.getJSONArray("tools");
                    
                    availableTools.clear();
                    for (int i = 0; i < tools.length(); i++) {
                        JSONObject tool = tools.getJSONObject(i);
                        String name = tool.getString("name");
                        availableTools.add(name);
                        CopilotLogger.info("Found tool: " + name);
                    }
                    
                    return true;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            CopilotLogger.error("Tool discovery failed", e);
            return false;
        }
    }
    
    /**
     * 도구 실행
     */
    public String executeTool(String toolName, Map<String, Object> parameters) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("name", toolName);
        params.put("arguments", parameters);
        
        try {
            JSONObject response = sendRequest("tools/call", params).get();
            
            if (response.has("result")) {
                JSONObject result = response.getJSONObject("result");
                if (result.has("content")) {
                    JSONArray content = result.getJSONArray("content");
                    if (content.length() > 0) {
                        JSONObject firstContent = content.getJSONObject(0);
                        return firstContent.getString("text");
                    }
                }
                return result.toString();
            }
            
            if (response.has("error")) {
                JSONObject error = response.getJSONObject("error");
                throw new Exception("Tool error: " + error.getString("message"));
            }
            
            return "No result";
            
        } catch (Exception e) {
            throw new Exception("Tool execution failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * 연결 해제
     */
    public void disconnect() {
        running = false;
        
        try {
            if (writer != null) {
                writer.close();
            }
            if (reader != null) {
                reader.close();
            }
            if (errorReader != null) {
                errorReader.close();
            }
            if (process != null && process.isAlive()) {
                process.destroy();
                process.waitFor(5, TimeUnit.SECONDS);
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        } catch (Exception e) {
            CopilotLogger.error("Error during disconnect", e);
        }
    }
    
    public boolean isConnected() {
        return process != null && process.isAlive() && running;
    }
    
    public Set<String> getAvailableTools() {
        return new HashSet<>(availableTools);
    }
}