package com.fabrix.copilot.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.json.JSONArray;
import org.json.JSONObject;

import com.fabrix.copilot.utils.CopilotLogger;
import com.fabrix.copilot.utils.PreferenceManager;

/**
 * 🤖 LLM Client - 수정된 버전
 * SSL 처리, 에러 핸들링, 재시도 로직 개선
 */
public class LLMClient {

    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String FABRIX_API_URL = "https://sds-api.fabrix-in.samsungsds.com/dev/samsungsds/sds_dev_chat_v1/1/openapi/chat/v1/messages";
    private static final String FABRIX_MODELS_URL = "https://sds-api.fabrix-in.samsungsds.com/dev/samsungsds/sds_dev_chat_v1/1/openapi/chat/v1/models";
    
    private static final int CONNECTION_TIMEOUT = 30000; // 30초로 증가
    private static final int READ_TIMEOUT = 60000; // 60초로 증가
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY = 1000; // 1초
    
    private static LLMClient instance;

    private final PreferenceManager preferenceManager;
    private final ExecutorService executorService;
    private final Map<String, FabriXModel> fabriXModelsCache;
    private long modelsCacheTime = 0;
    private static final long CACHE_DURATION = 300000;
    
    // SSL 컨텍스트를 클래스 레벨에서 초기화
    private static SSLContext sslContext;
    
    static {
        initializeSSLContext();
    }

    public static synchronized LLMClient getInstance() {
        if (instance == null) {
            instance = new LLMClient();
        }
        return instance;
    }

    private LLMClient() {
        this.preferenceManager = PreferenceManager.getInstance();
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "LLMClient-Worker");
            t.setDaemon(true);
            return t;
        });
        this.fabriXModelsCache = new ConcurrentHashMap<>();
        preferenceManager.setAllEnvironmentVariables();
        
        CopilotLogger.info("LLMClient initialized with SSL bypass for development");
        
        // SSL 우회 설정이 필요한 경우에만 활성화 (설정에서 제어 가능)
        if (preferenceManager.getBooleanValue("ssl.bypass.enabled", true)) {
            CopilotLogger.warn("SSL bypass is enabled. Configure 'ssl.bypass.enabled=false' for production use.");
        }
    }
    
    /**
     * SSL 인증서 검증 우회 - 개발/테스트 환경용
     * 주의: 프로덕션 환경에서는 사용하지 마세요!
     */
    private static void initializeSSLContext() {
        try {
            // 모든 인증서를 신뢰하는 TrustManager 생성
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { 
                        return new X509Certificate[0]; 
                    }
                    
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // 클라이언트 인증서 검증 스킵
                        CopilotLogger.debug("Skipping client certificate validation");
                    }
                    
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // 서버 인증서 검증 스킵
                        CopilotLogger.debug("Skipping server certificate validation for: " + 
                            (certs.length > 0 ? certs[0].getSubjectDN() : "unknown"));
                    }
                }
            };
            
            // SSL 컨텍스트 생성 및 설정
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            
            // 기본 SSL 소켓 팩토리 설정
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            
            // 호스트명 검증도 우회 (모든 호스트명 허용)
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> {
                CopilotLogger.debug("Skipping hostname verification for: " + hostname);
                return true;
            });
            
            CopilotLogger.warn("⚠️ SSL certificate validation is DISABLED. This should only be used in development/test environments!");
            
        } catch (Exception e) {
            CopilotLogger.error("Failed to initialize SSL bypass context", e);
        }
    }
    
    public void generateResponseAsync(String message, String modelId, Consumer<String> onSuccess, Consumer<Exception> onError) {
        Job job = new Job("LLM 응답 생성 중...") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    monitor.beginTask("AI 모델과 통신 중...", IProgressMonitor.UNKNOWN);
                    
                    CopilotLogger.info("Starting LLM request - Model: " + (modelId != null ? modelId : "default"));
                    
                    if (monitor.isCanceled()) return Status.CANCEL_STATUS;

                    String response = generateResponseWithRetry(message, modelId);
                    
                    CopilotLogger.info("LLM request successful");
                    onSuccess.accept(response);
                    
                    monitor.done();
                    return Status.OK_STATUS;
                } catch (Exception e) {
                    CopilotLogger.error("LLM request failed", e);
                    onError.accept(e);
                    return Status.error("AI 응답 생성에 실패했습니다.", e);
                }
            }
        };
        job.setUser(true);
        job.schedule();
    }
    
    /**
     * 재시도 로직이 포함된 응답 생성
     */
    private String generateResponseWithRetry(String message, String modelId) throws Exception {
        Exception lastException = null;
        
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                if (i > 0) {
                    CopilotLogger.info("Retry attempt " + i + " for LLM request");
                    Thread.sleep(RETRY_DELAY * i); // 점진적 대기
                }
                
                return generateResponse(message, modelId);
                
            } catch (Exception e) {
                lastException = e;
                CopilotLogger.warn("LLM request attempt " + (i + 1) + " failed: " + e.getMessage());
            }
        }
        
        throw new Exception("Failed after " + MAX_RETRIES + " attempts: " + 
            (lastException != null ? lastException.getMessage() : "Unknown error"));
    }
    
    public String generateResponse(String message, String modelId) throws Exception {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Message cannot be empty");
        }
        
        // API 키 검증
        if (!preferenceManager.hasValidAPIKey()) {
            throw new IllegalStateException("No valid API key configured. Please set OpenAI or FabriX API keys in settings.");
        }
        
        String effectiveModelId = (modelId == null || modelId.isEmpty()) ? preferenceManager.getSelectedModel() : modelId;
        
        CopilotLogger.info("Generating response with model: " + effectiveModelId);

        if (isFabriXModel(effectiveModelId)) {
            return sendFabriXMessage(message, effectiveModelId);
        } else {
            return sendOpenAIMessage(message, effectiveModelId);
        }
    }

    private String sendOpenAIMessage(String message, String model) throws Exception {
        String apiKey = preferenceManager.getOpenAIKey();
        if (apiKey.isEmpty()) {
            throw new IllegalStateException("OpenAI API key not configured");
        }
        
        // API 키 형식 검증
        if (!apiKey.startsWith("sk-")) {
            throw new IllegalStateException("Invalid OpenAI API key format");
        }
        
        String requestBody = buildOpenAIRequest(message, model);
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Content-Type", "application/json");
        
        CopilotLogger.info("Sending request to OpenAI API");
        String response = makeHTTPRequest(OPENAI_BASE_URL + "/chat/completions", "POST", headers, requestBody);
        return parseOpenAIResponse(response);
    }
    
    private String sendFabriXMessage(String message, String modelId) throws Exception {
        String token = preferenceManager.getFabriXToken();
        String client = preferenceManager.getFabriXClientKey();
        
        if (token.isEmpty() || client.isEmpty()) {
            throw new IllegalStateException("FabriX API keys not configured");
        }
        
        String requestBody = buildFabriXRequest(message, modelId);
        Map<String, String> headers = new HashMap<>();
        headers.put("x-openapi-token", token);
        headers.put("x-generative-ai-client", client);
        headers.put("Content-Type", "application/json");
        
        CopilotLogger.info("Sending request to FabriX API");
        String response = makeHTTPRequest(FABRIX_API_URL, "POST", headers, requestBody);
        return parseFabriXResponse(response);
    }

    private String buildOpenAIRequest(String message, String model) {
        JSONObject payload = new JSONObject();
        payload.put("model", model == null || model.isEmpty() ? "gpt-3.5-turbo" : model);
        payload.put("temperature", preferenceManager.getTemperature());
        payload.put("max_tokens", preferenceManager.getMaxTokens());
        
        JSONArray messages = new JSONArray();
        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", message);
        messages.put(userMessage);
        
        payload.put("messages", messages);
        
        CopilotLogger.debug("Request payload: " + payload.toString());
        return payload.toString();
    }
    
    private String buildFabriXRequest(String message, String modelId) {
        JSONObject payload = new JSONObject();
        if (modelId != null && !modelId.isEmpty()) {
            payload.put("llmId", modelId);
        }
        payload.put("temperature", preferenceManager.getTemperature());
        payload.put("max_tokens", preferenceManager.getMaxTokens());
        
        JSONArray messages = new JSONArray();
        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", message);
        messages.put(userMessage);
        
        payload.put("messages", messages);
        
        CopilotLogger.debug("FabriX request payload: " + payload.toString());
        return payload.toString();
    }

    private String parseOpenAIResponse(String jsonResponse) throws Exception {
        try {
            CopilotLogger.debug("OpenAI response: " + jsonResponse);
            
            JSONObject json = new JSONObject(jsonResponse);
            
            // 에러 체크
            if (json.has("error")) {
                JSONObject error = json.getJSONObject("error");
                String errorMessage = error.optString("message", "Unknown error");
                String errorType = error.optString("type", "unknown");
                throw new Exception("OpenAI API Error [" + errorType + "]: " + errorMessage);
            }
            
            JSONArray choices = json.getJSONArray("choices");
            if (choices.length() > 0) {
                JSONObject message = choices.getJSONObject(0).optJSONObject("message");
                if (message != null && message.has("content") && !message.isNull("content")) {
                    return message.getString("content");
                }
            }
            throw new Exception("No valid content found in OpenAI response.");
        } catch (Exception e) {
            CopilotLogger.error("OpenAI response parsing error: " + jsonResponse, e);
            throw new Exception("Failed to parse OpenAI response: " + e.getMessage(), e);
        }
    }
    
    private String parseFabriXResponse(String jsonResponse) throws Exception {
        try {
            CopilotLogger.debug("FabriX response: " + jsonResponse);
            
            JSONObject json = new JSONObject(jsonResponse);
            
            // 에러 체크
            if (json.has("error")) {
                String errorMessage = json.optString("error", "Unknown error");
                throw new Exception("FabriX API Error: " + errorMessage);
            }
            
            JSONObject result = json.optJSONObject("result");
            if (result != null && result.has("message")) {
                return result.getJSONObject("message").getString("content");
            }
            
            JSONArray choices = json.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                return choices.getJSONObject(0).getJSONObject("message").getString("content");
            }
            
            throw new Exception("No content found in FabriX response");
        } catch (Exception e) {
            CopilotLogger.error("FabriX response parsing error: " + jsonResponse, e);
            throw new Exception("Failed to parse FabriX response: " + e.getMessage(), e);
        }
    }
    
    public void getAvailableModelsAsync(Consumer<List<ModelInfo>> onSuccess, Consumer<Exception> onError) {
        executorService.submit(() -> {
            try {
                List<ModelInfo> allModels = new ArrayList<>();
                
                // OpenAI 모델 추가
                if (preferenceManager.hasOpenAIKey()) {
                    allModels.add(new ModelInfo("gpt-4", "GPT-4", "GPT-4", "Most capable GPT-4 model", false));
                    allModels.add(new ModelInfo("gpt-4-turbo-preview", "GPT-4 Turbo", "GPT-4 Turbo", "GPT-4 Turbo with 128k context", false));
                    allModels.add(new ModelInfo("gpt-3.5-turbo", "GPT-3.5 Turbo", "GPT-3.5 Turbo", "Fast and efficient model", false));
                }
                
                // FabriX 모델 추가
                if (preferenceManager.hasFabriXKeys()) {
                    try {
                        List<FabriXModel> fabrixModels = getFabriXModels();
                        for (FabriXModel fabrixModel : fabrixModels) {
                            allModels.add(new ModelInfo(
                                fabrixModel.getModelId(), 
                                fabrixModel.getModelName(),
                                fabrixModel.getModelLabel(), 
                                fabrixModel.getModelDescription(), 
                                true
                            ));
                        }
                    } catch (Exception e) {
                        CopilotLogger.warn("Failed to load FabriX models, continuing with OpenAI models only", e);
                    }
                }
                
                if (allModels.isEmpty()) {
                    throw new Exception("No models available. Please configure API keys in settings.");
                }
                
                onSuccess.accept(allModels);
            } catch (Exception e) {
                onError.accept(e);
            }
        });
    }

    private List<FabriXModel> getFabriXModels() throws Exception {
        if (System.currentTimeMillis() - modelsCacheTime < CACHE_DURATION && !fabriXModelsCache.isEmpty()) {
            return new ArrayList<>(fabriXModelsCache.values());
        }
        
        String token = preferenceManager.getFabriXToken();
        String client = preferenceManager.getFabriXClientKey();
        if (token.isEmpty() || client.isEmpty()) {
            throw new IllegalStateException("FabriX API keys not configured");
        }
        
        Map<String, String> headers = new HashMap<>();
        headers.put("x-openapi-token", token);
        headers.put("x-generative-ai-client", client);
        
        String response = makeHTTPRequest(FABRIX_MODELS_URL, "GET", headers, null);
        List<FabriXModel> models = parseFabriXModels(response);
        
        fabriXModelsCache.clear();
        models.forEach(model -> fabriXModelsCache.put(model.getModelId(), model));
        modelsCacheTime = System.currentTimeMillis();
        
        return models;
    }
    
    private List<FabriXModel> parseFabriXModels(String jsonResponse) throws Exception {
        List<FabriXModel> models = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(jsonResponse);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                String modelId = obj.optString("modelId", null);
                String modelName = obj.optString("modelName", null);
                if (modelId != null && modelName != null) {
                    models.add(new FabriXModel(
                        modelId, 
                        modelName, 
                        obj.optString("modelLabel", modelName), 
                        obj.optString("modelDescription", "")
                    ));
                }
            }
        } catch (Exception e) {
            CopilotLogger.error("Failed to parse FabriX models response", e);
            throw new Exception("Failed to parse FabriX models response", e);
        }
        return models;
    }

    private boolean isFabriXModel(String modelId) {
        if (fabriXModelsCache.containsKey(modelId)) return true;
        return modelId != null && (modelId.contains("fabrix") || modelId.contains("sds") || !modelId.startsWith("gpt"));
    }

    private String makeHTTPRequest(String urlString, String method, Map<String, String> headers, String body) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            
            // SSL 설정 적용
            if (connection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) connection).setSSLSocketFactory(sslContext.getSocketFactory());
            }
            
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setUseCaches(false);
            connection.setDoInput(true);
            
            // 헤더 설정
            if (headers != null) {
                headers.forEach(connection::setRequestProperty);
            }
            
            // User-Agent 추가
            connection.setRequestProperty("User-Agent", "FabriX-Copilot/1.0");
            
            // 프록시 설정 확인 및 적용
            String proxyHost = System.getProperty("http.proxyHost");
            String proxyPort = System.getProperty("http.proxyPort");
            if (proxyHost != null && proxyPort != null) {
                CopilotLogger.info("Using proxy: " + proxyHost + ":" + proxyPort);
                
                // HTTPS 프록시 설정도 확인
                String httpsProxyHost = System.getProperty("https.proxyHost");
                String httpsProxyPort = System.getProperty("https.proxyPort");
                if (httpsProxyHost != null && httpsProxyPort != null && connection instanceof HttpsURLConnection) {
                    CopilotLogger.info("Using HTTPS proxy: " + httpsProxyHost + ":" + httpsProxyPort);
                }
            }
            
            // 요청 본문 전송
            if (body != null && !body.isEmpty() && !method.equals("GET")) {
                connection.setDoOutput(true);
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = body.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
            }
            
            // 응답 처리
            int responseCode = connection.getResponseCode();
            CopilotLogger.info("HTTP Response Code: " + responseCode);
            
            InputStream stream;
            if (responseCode >= 200 && responseCode < 300) {
                stream = connection.getInputStream();
            } else {
                stream = connection.getErrorStream();
                if (stream == null) {
                    throw new IOException("HTTP " + responseCode + ": No error details available");
                }
            }
            
            String response = readInputStream(stream);
            
            if (responseCode >= 200 && responseCode < 300) {
                return response;
            } else {
                CopilotLogger.error("HTTP Error Response: " + response, null);
                throw new IOException("HTTP " + responseCode + ": " + response);
            }
            
        } catch (Exception e) {
            CopilotLogger.error("HTTP request failed: " + urlString, e);
            throw e;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    private String readInputStream(InputStream inputStream) throws IOException {
        if (inputStream == null) return "";
        
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append('\n');
            }
        }
        return response.toString().trim();
    }
    
    public void shutdown() {
        try {
            CopilotLogger.info("Shutting down LLMClient");
            executorService.shutdown();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}