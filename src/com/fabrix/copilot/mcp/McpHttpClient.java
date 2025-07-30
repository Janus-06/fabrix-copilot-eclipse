package com.fabrix.copilot.mcp;

import java.io.*;
import java.net.*;
import java.util.*;

import com.fabrix.copilot.utils.CopilotLogger;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * HTTP 방식 MCP 클라이언트
 */
public class McpHttpClient extends McpClient {
    
    private final String baseUrl;
    private final Map<String, String> headers;
    private final Map<String, String> endpoints;
    
    public McpHttpClient(McpServerConfig config) {
        super(config);
        
        // HTTP 설정 파싱
        JSONObject httpConfig = parseHttpConfig(config);
        this.baseUrl = httpConfig.optString("url", "http://localhost:8080");
        this.headers = parseHeaders(httpConfig.optJSONObject("headers"));
        this.endpoints = parseEndpoints(httpConfig.optJSONObject("endpoints"));
    }
    
    @Override
    public boolean connect() {
        // connectHTTP가 아닌 connect를 오버라이드
        return connectHTTPInternal();
    }
    
    private boolean connectHTTPInternal() {
        try {
            // 헬스 체크
            String healthUrl = baseUrl + "/health";
            HttpURLConnection conn = (HttpURLConnection) new URL(healthUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            
            // 헤더 추가
            headers.forEach(conn::setRequestProperty);
            
            int responseCode = conn.getResponseCode();
            conn.disconnect();
            
            if (responseCode >= 200 && responseCode < 300) {
                CopilotLogger.info("HTTP MCP server connected: " + getServerName());
                
                // 도구 목록 조회
                discoverHttpTools();
                return true;
            }
            
        } catch (Exception e) {
            CopilotLogger.error("HTTP connection failed", e);
        }
        
        return false;
    }
    
    // sendHTTPRequest를 public 메서드로 변경
    public String sendRequest(String method, Map<String, Object> params) {
        try {
            JSONObject request = new JSONObject();
            request.put("jsonrpc", "2.0");
            request.put("id", System.currentTimeMillis());
            request.put("method", method);
            if (params != null) {
                request.put("params", new JSONObject(params));
            }
            
            return sendHTTPRequestInternal(request.toString());
        } catch (Exception e) {
            CopilotLogger.error("Failed to send request", e);
            return null;
        }
    }
    
    private String sendHTTPRequestInternal(String request) {
        try {
            // 요청 파싱
            JSONObject jsonRequest = new JSONObject(request);
            String method = jsonRequest.getString("method");
            
            // 엔드포인트 결정
            String endpoint = determineEndpoint(method);
            String url = baseUrl + endpoint;
            
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            
            // 헤더 추가
            headers.forEach(conn::setRequestProperty);
            
            // 요청 본문 전송
            try (OutputStream os = conn.getOutputStream()) {
                os.write(request.getBytes("UTF-8"));
            }
            
            // 응답 읽기
            int responseCode = conn.getResponseCode();
            InputStream inputStream = (responseCode >= 200 && responseCode < 300) ? 
                conn.getInputStream() : conn.getErrorStream();
            
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            
            return response.toString();
            
        } catch (Exception e) {
            CopilotLogger.error("HTTP request failed", e);
            return null;
        }
    }
    
    private void discoverHttpTools() {
        try {
            String toolsEndpoint = endpoints.getOrDefault("tools", "/tools");
            String url = baseUrl + toolsEndpoint;
            
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            headers.forEach(conn::setRequestProperty);
            
            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    
                    parseToolsFromHttpResponse(response.toString());
                }
            }
            
        } catch (Exception e) {
            CopilotLogger.error("Failed to discover HTTP tools", e);
        }
    }
    
    private void parseToolsFromHttpResponse(String response) {
        try {
            JSONArray tools = new JSONArray(response);
            
            // availableTools를 직접 접근하는 대신 부모 클래스의 메서드 사용
            Set<String> toolSet = getAvailableTools();
            toolSet.clear();
            
            for (int i = 0; i < tools.length(); i++) {
                JSONObject tool = tools.getJSONObject(i);
                String name = tool.getString("name");
                toolSet.add(name);
            }
            
            CopilotLogger.info("Discovered " + toolSet.size() + 
                             " tools from HTTP server");
            
        } catch (Exception e) {
            CopilotLogger.error("Failed to parse tools response", e);
        }
    }
    
    @Override
    public String callTool(String toolName, Map<String, Object> parameters) throws Exception {
        if (!isConnected()) {
            throw new Exception("Not connected to HTTP MCP server");
        }
        
        String executeEndpoint = endpoints.getOrDefault("execute", "/execute");
        String url = baseUrl + executeEndpoint;
        
        // 요청 생성
        JSONObject request = new JSONObject();
        request.put("tool", toolName);
        request.put("parameters", new JSONObject(parameters));
        
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        
        headers.forEach(conn::setRequestProperty);
        
        // 요청 전송
        try (OutputStream os = conn.getOutputStream()) {
            os.write(request.toString().getBytes("UTF-8"));
        }
        
        // 응답 처리
        int responseCode = conn.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                
                // 결과 파싱
                JSONObject result = new JSONObject(response.toString());
                if (result.has("result")) {
                    return result.getString("result");
                } else if (result.has("error")) {
                    throw new Exception("Tool error: " + result.getString("error"));
                }
                
                return response.toString();
            }
        } else {
            throw new Exception("HTTP error: " + responseCode);
        }
    }
    
    // 헬퍼 메서드들
    private String getServerName() {
        // McpServerConfig에서 이름 가져오기
        try {
            java.lang.reflect.Field configField = McpClient.class.getDeclaredField("config");
            configField.setAccessible(true);
            McpServerConfig config = (McpServerConfig) configField.get(this);
            return config.getName();
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    private JSONObject parseHttpConfig(McpServerConfig config) {
        // 실제 구현에서는 config에서 HTTP 설정을 파싱
        JSONObject httpConfig = new JSONObject();
        
        // config.getArgs()에서 URL 추출
        if (config.getArgs() != null && !config.getArgs().isEmpty()) {
            String lastArg = config.getArgs().get(config.getArgs().size() - 1);
            if (lastArg.startsWith("http")) {
                httpConfig.put("url", lastArg);
            }
        }
        
        return httpConfig;
    }
    
    private Map<String, String> parseHeaders(JSONObject headersObj) {
        Map<String, String> headers = new HashMap<>();
        if (headersObj != null) {
            for (String key : headersObj.keySet()) {
                headers.put(key, headersObj.getString(key));
            }
        }
        return headers;
    }
    
    private Map<String, String> parseEndpoints(JSONObject endpointsObj) {
        Map<String, String> endpoints = new HashMap<>();
        if (endpointsObj != null) {
            for (String key : endpointsObj.keySet()) {
                endpoints.put(key, endpointsObj.getString(key));
            }
        } else {
            // 기본 엔드포인트
            endpoints.put("tools", "/tools");
            endpoints.put("execute", "/execute");
        }
        return endpoints;
    }
    
    private String determineEndpoint(String method) {
        // 메서드에 따른 엔드포인트 결정
        if (method.contains("tools")) {
            return endpoints.getOrDefault("tools", "/tools");
        } else if (method.contains("execute") || method.contains("call")) {
            return endpoints.getOrDefault("execute", "/execute");
        }
        return "/api";
    }
}