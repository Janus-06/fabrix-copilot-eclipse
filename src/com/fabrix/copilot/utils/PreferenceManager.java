package com.fabrix.copilot.utils;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

/**
 * 💾 Preference Manager - Complete Implementation
 * 
 * FabriX API 지원 및 안전한 설정 저장
 */
public class PreferenceManager {
    
    // =================================================================
    // 🔑 상수 정의
    // =================================================================
    private static final String PLUGIN_ID = "com.fabrix.copilot";
    
    // API Keys - FabriX용으로 변경
    private static final String OPENAI_API_KEY = "api.openai.key";
    private static final String FABRIX_TOKEN_KEY = "api.fabrix.token";
    private static final String FABRIX_CLIENT_KEY = "api.fabrix.client";
    
    // MCP Settings
    private static final String MCP_ENABLED = "mcp.enabled";
    private static final String MCP_SERVER_URL = "mcp.server.url";
    private static final String MCP_SERVER_PORT = "mcp.server.port";
    private static final String MCP_PROTOCOL = "mcp.protocol";
    
    // Model Settings
    private static final String SELECTED_MODEL = "model.selected";
    private static final String DEFAULT_PROVIDER = "model.provider.default";
    private static final String TEMPERATURE = "model.temperature";
    private static final String MAX_TOKENS = "model.max.tokens";
    
    // UI Settings
    private static final String AUTO_SCROLL = "ui.auto.scroll";
    private static final String FONT_SIZE = "ui.font.size";
    private static final String SHOW_TIMESTAMPS = "ui.show.timestamps";
    
    // =================================================================
    // 🎯 인스턴스 변수
    // =================================================================
    private final IPreferenceStore preferenceStore;
    private static PreferenceManager instance;
    
    // =================================================================
    // 🏗️ 생성자 및 싱글톤
    // =================================================================
    
    /**
     * 생성자 (public으로 변경)
     */
    public PreferenceManager() {
        this.preferenceStore = new ScopedPreferenceStore(InstanceScope.INSTANCE, PLUGIN_ID);
        initializeDefaults();
    }
    
    /**
     * 싱글톤 인스턴스 반환
     */
    public static synchronized PreferenceManager getInstance() {
        if (instance == null) {
            instance = new PreferenceManager();
        }
        return instance;
    }
    
    /**
     * 🎯 기본값 설정
     */
    private void initializeDefaults() {
        // MCP Settings
        preferenceStore.setDefault(MCP_ENABLED, false);
        preferenceStore.setDefault(MCP_SERVER_URL, "http://localhost");
        preferenceStore.setDefault(MCP_SERVER_PORT, "8080");
        preferenceStore.setDefault(MCP_PROTOCOL, "http");
        
        // Model Settings
        preferenceStore.setDefault(SELECTED_MODEL, "gpt-3.5-turbo");
        preferenceStore.setDefault(DEFAULT_PROVIDER, "openai");
        preferenceStore.setDefault(TEMPERATURE, "0.7");
        preferenceStore.setDefault(MAX_TOKENS, "2048");
        preferenceStore.setDefault(SELECTED_MODEL, "116"); // Gemma3를 기본으로
        preferenceStore.setDefault(DEFAULT_PROVIDER, "fabrix"); // FabriX를 기본으로
        
        // UI Settings
        preferenceStore.setDefault(AUTO_SCROLL, true);
        preferenceStore.setDefault(FONT_SIZE, 12);
        preferenceStore.setDefault(SHOW_TIMESTAMPS, true);
    }
    
    // =================================================================
    // 🔑 API Keys 관리 - FabriX 지원
    // =================================================================
    
    /**
     * 🤖 OpenAI API Key 설정
     */
    public void setOpenAIKey(String apiKey) {
        try {
            setEncryptedValue(OPENAI_API_KEY, apiKey);
            updateEnvironmentVariable("OPENAI_API_KEY", apiKey);
        } catch (Exception e) {
            System.err.println("OpenAI API Key 저장 실패: " + e.getMessage());
        }
    }
    
    /**
     * 🤖 OpenAI API Key 조회
     */
    public String getOpenAIKey() {
        try {
            return getDecryptedValue(OPENAI_API_KEY);
        } catch (Exception e) {
            System.err.println("OpenAI API Key 조회 실패: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * 🏭 FabriX Token 설정
     */
    public void setFabriXToken(String token) {
        try {
            setEncryptedValue(FABRIX_TOKEN_KEY, token);
            updateEnvironmentVariable("FABRIX_TOKEN", token);
        } catch (Exception e) {
            System.err.println("FabriX Token 저장 실패: " + e.getMessage());
        }
    }
    
    /**
     * 🏭 FabriX Token 조회
     */
    public String getFabriXToken() {
        try {
            return getDecryptedValue(FABRIX_TOKEN_KEY);
        } catch (Exception e) {
            System.err.println("FabriX Token 조회 실패: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * 🏭 FabriX Client Key 설정
     */
    public void setFabriXClientKey(String clientKey) {
        try {
            setEncryptedValue(FABRIX_CLIENT_KEY, clientKey);
            updateEnvironmentVariable("FABRIX_CLIENT", clientKey);
        } catch (Exception e) {
            System.err.println("FabriX Client Key 저장 실패: " + e.getMessage());
        }
    }
    
    /**
     * 🏭 FabriX Client Key 조회
     */
    public String getFabriXClientKey() {
        try {
            return getDecryptedValue(FABRIX_CLIENT_KEY);
        } catch (Exception e) {
            System.err.println("FabriX Client Key 조회 실패: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * 🔍 API Key 존재 여부 확인
     */
    public boolean hasValidAPIKey() {
        return hasOpenAIKey() || hasFabriXKeys();
    }
    
    public boolean hasOpenAIKey() {
        String key = getOpenAIKey();
        return key != null && !key.trim().isEmpty();
    }
    
    public boolean hasFabriXKeys() {
        String token = getFabriXToken();
        String client = getFabriXClientKey();
        return (token != null && !token.trim().isEmpty()) && 
               (client != null && !client.trim().isEmpty());
    }
    
    // =================================================================
    // 🔌 MCP 설정 관리
    // =================================================================
    
    public void setMCPEnabled(boolean enabled) {
        try {
            preferenceStore.setValue(MCP_ENABLED, enabled);
        } catch (Exception e) {
            System.err.println("MCP 활성화 설정 실패: " + e.getMessage());
        }
    }
    
    public boolean isMCPEnabled() {
        try {
            return preferenceStore.getBoolean(MCP_ENABLED);
        } catch (Exception e) {
            return false;
        }
    }
    
    public void setMCPServerUrl(String url) {
        setValue(MCP_SERVER_URL, url);
    }
    
    public String getMCPServerUrl() {
        return preferenceStore.getString(MCP_SERVER_URL);
    }
    
    public void setMCPPort(String port) {
        setValue(MCP_SERVER_PORT, port);
    }
    
    public String getMCPPort() {
        return preferenceStore.getString(MCP_SERVER_PORT);
    }
    
    public void setMCPProtocol(String protocol) {
        setValue(MCP_PROTOCOL, protocol);
    }
    
    public String getMCPProtocol() {
        return preferenceStore.getString(MCP_PROTOCOL);
    }
    
    public String getMCPFullUrl() {
        return String.format("%s://%s:%s", 
            getMCPProtocol(), 
            getMCPServerUrl().replace("http://", "").replace("https://", ""), 
            getMCPPort());
    }
    
    // =================================================================
    // 🤖 모델 설정 관리
    // =================================================================
    
    public void setSelectedModel(String model) {
        setValue(SELECTED_MODEL, model);
    }
    
    public String getSelectedModel() {
        return preferenceStore.getString(SELECTED_MODEL);
    }
    
    public void setDefaultProvider(String provider) {
        setValue(DEFAULT_PROVIDER, provider);
    }
    
    public String getDefaultProvider() {
        return preferenceStore.getString(DEFAULT_PROVIDER);
    }
    
    public void setTemperature(double temperature) {
        try {
            preferenceStore.setValue(TEMPERATURE, String.valueOf(temperature));
        } catch (Exception e) {
            System.err.println("Temperature 설정 실패: " + e.getMessage());
        }
    }
    
    public double getTemperature() {
        try {
            return Double.parseDouble(preferenceStore.getString(TEMPERATURE));
        } catch (Exception e) {
            return 0.7;
        }
    }
    
    public void setMaxTokens(int maxTokens) {
        try {
            preferenceStore.setValue(MAX_TOKENS, String.valueOf(maxTokens));
        } catch (Exception e) {
            System.err.println("Max Tokens 설정 실패: " + e.getMessage());
        }
    }
    
    public int getMaxTokens() {
        try {
            return Integer.parseInt(preferenceStore.getString(MAX_TOKENS));
        } catch (Exception e) {
            return 2048;
        }
    }
    
    // =================================================================
    // 🎨 UI 설정 관리
    // =================================================================
    
    public void setAutoScroll(boolean autoScroll) {
        try {
            preferenceStore.setValue(AUTO_SCROLL, autoScroll);
        } catch (Exception e) {
            System.err.println("Auto Scroll 설정 실패: " + e.getMessage());
        }
    }
    
    public boolean isAutoScrollEnabled() {
        try {
            return preferenceStore.getBoolean(AUTO_SCROLL);
        } catch (Exception e) {
            return true;
        }
    }
    
    public void setFontSize(int fontSize) {
        try {
            preferenceStore.setValue(FONT_SIZE, fontSize);
        } catch (Exception e) {
            System.err.println("Font Size 설정 실패: " + e.getMessage());
        }
    }
    
    public int getFontSize() {
        try {
            return preferenceStore.getInt(FONT_SIZE);
        } catch (Exception e) {
            return 12;
        }
    }
    
    public void setShowTimestamps(boolean show) {
        try {
            preferenceStore.setValue(SHOW_TIMESTAMPS, show);
        } catch (Exception e) {
            System.err.println("Timestamps 설정 실패: " + e.getMessage());
        }
    }
    
    public boolean isShowTimestamps() {
        try {
            return preferenceStore.getBoolean(SHOW_TIMESTAMPS);
        } catch (Exception e) {
            return true;
        }
    }
    
    // =================================================================
    // 🔧 누락된 Public 메서드들 추가
    // =================================================================
    
    /**
     * 🔧 일반적인 문자열 값 설정 (public 접근자)
     */
    public void setValue(String key, String value) {
        try {
            preferenceStore.setValue(key, value != null ? value.trim() : "");
        } catch (Exception e) {
            System.err.println("값 설정 실패 (" + key + "): " + e.getMessage());
        }
    }
    
    /**
     * 🔧 일반적인 문자열 값 조회 (public 접근자)
     */
    public String getValue(String key, String defaultValue) {
        try {
            String value = preferenceStore.getString(key);
            return (value != null && !value.trim().isEmpty()) ? value : defaultValue;
        } catch (Exception e) {
            System.err.println("값 조회 실패 (" + key + "): " + e.getMessage());
            return defaultValue;
        }
    }
    
    /**
     * 🔧 일반적인 문자열 값 조회 (기본값 없음)
     */
    public String getValue(String key) {
        return getValue(key, "");
    }
    
    /**
     * 🔧 Boolean 값 설정 (public 접근자)
     */
    public void setBooleanValue(String key, boolean value) {
        try {
            preferenceStore.setValue(key, value);
        } catch (Exception e) {
            System.err.println("Boolean 값 설정 실패 (" + key + "): " + e.getMessage());
        }
    }
    
    /**
     * 🔧 Boolean 값 조회 (public 접근자)
     */
    public boolean getBooleanValue(String key, boolean defaultValue) {
        try {
            return preferenceStore.getBoolean(key);
        } catch (Exception e) {
            System.err.println("Boolean 값 조회 실패 (" + key + "): " + e.getMessage());
            return defaultValue;
        }
    }
    
    /**
     * 🔧 Integer 값 설정 (public 접근자)
     */
    public void setIntValue(String key, int value) {
        try {
            preferenceStore.setValue(key, value);
        } catch (Exception e) {
            System.err.println("Integer 값 설정 실패 (" + key + "): " + e.getMessage());
        }
    }
    
    /**
     * 🔧 Integer 값 조회 (public 접근자)
     */
    public int getIntValue(String key, int defaultValue) {
        try {
            return preferenceStore.getInt(key);
        } catch (Exception e) {
            System.err.println("Integer 값 조회 실패 (" + key + "): " + e.getMessage());
            return defaultValue;
        }
    }
    
    // =================================================================
    // 🔐 암호화/복호화 유틸리티
    // =================================================================
    
    private void setEncryptedValue(String key, String value) {
        try {
            if (value != null && !value.trim().isEmpty()) {
                String encrypted = encrypt(value.trim());
                preferenceStore.setValue(key, encrypted);
            } else {
                preferenceStore.setValue(key, "");
            }
        } catch (Exception e) {
            System.err.println("암호화 저장 실패: " + e.getMessage());
        }
    }
    
    private String getDecryptedValue(String key) {
        try {
            String encrypted = preferenceStore.getString(key);
            return encrypted.isEmpty() ? "" : decrypt(encrypted);
        } catch (Exception e) {
            System.err.println("복호화 조회 실패: " + e.getMessage());
            return "";
        }
    }
    
    private String encrypt(String data) {
        try {
            return java.util.Base64.getEncoder().encodeToString(data.getBytes("UTF-8"));
        } catch (Exception e) {
            System.err.println("❌ Encryption failed: " + e.getMessage());
            return data;
        }
    }
    
    private String decrypt(String encryptedData) {
        try {
            byte[] decodedBytes = java.util.Base64.getDecoder().decode(encryptedData);
            return new String(decodedBytes, "UTF-8");
        } catch (Exception e) {
            System.err.println("❌ Decryption failed: " + e.getMessage());
            return encryptedData;
        }
    }
    
    // =================================================================
    // 🌍 환경변수 관리
    // =================================================================
    
    private void updateEnvironmentVariable(String key, String value) {
        try {
            if (value != null && !value.trim().isEmpty()) {
                System.setProperty(key, value.trim());
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to set environment variable " + key + ": " + e.getMessage());
        }
    }
    
    public void setAllEnvironmentVariables() {
        try {
            updateEnvironmentVariable("OPENAI_API_KEY", getOpenAIKey());
            updateEnvironmentVariable("FABRIX_TOKEN", getFabriXToken());
            updateEnvironmentVariable("FABRIX_CLIENT", getFabriXClientKey());
        } catch (Exception e) {
            System.err.println("환경변수 설정 실패: " + e.getMessage());
        }
    }
    
    public void loadFromEnvironmentVariables() {
        try {
            loadAPIKeyFromEnv("OPENAI_API_KEY", this::setOpenAIKey);
            loadAPIKeyFromEnv("FABRIX_TOKEN", this::setFabriXToken);
            loadAPIKeyFromEnv("FABRIX_CLIENT", this::setFabriXClientKey);
        } catch (Exception e) {
            System.err.println("환경변수 로드 실패: " + e.getMessage());
        }
    }
    
    private void loadAPIKeyFromEnv(String envKey, java.util.function.Consumer<String> setter) {
        try {
            String envValue = System.getenv(envKey);
            if (envValue != null && !envValue.trim().isEmpty()) {
                setter.accept(envValue.trim());
                System.out.println("✅ " + envKey + " loaded from environment");
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to load " + envKey + " from environment: " + e.getMessage());
        }
    }
    
    // =================================================================
    // 🛠️ 유틸리티 메서드
    // =================================================================
    
    public boolean validateSettings() {
        boolean isValid = true;
        
        try {
            if (!hasValidAPIKey()) {
                System.out.println("⚠️ No API keys configured");
                isValid = false;
            }
            
            if (isMCPEnabled()) {
                if (getMCPServerUrl().isEmpty() || getMCPPort().isEmpty()) {
                    System.out.println("⚠️ MCP enabled but incomplete configuration");
                    isValid = false;
                }
                
                try {
                    Integer.parseInt(getMCPPort());
                } catch (NumberFormatException e) {
                    System.out.println("⚠️ Invalid MCP port number");
                    isValid = false;
                }
            }
        } catch (Exception e) {
            System.err.println("설정 검증 실패: " + e.getMessage());
            isValid = false;
        }
        
        return isValid;
    }
    
    public void resetAllSettings() {
        try {
            preferenceStore.setValue(OPENAI_API_KEY, "");
            preferenceStore.setValue(FABRIX_TOKEN_KEY, "");
            preferenceStore.setValue(FABRIX_CLIENT_KEY, "");
            initializeDefaults();
            System.out.println("✅ All settings reset to defaults");
        } catch (Exception e) {
            System.err.println("설정 리셋 실패: " + e.getMessage());
        }
    }
    
    public void printSettings() {
        try {
            System.out.println("\n=== 🛠️ Copilot Settings ===");
            System.out.println("OpenAI Key: " + (hasOpenAIKey() ? "✅ Set" : "❌ Not set"));
            System.out.println("FabriX Keys: " + (hasFabriXKeys() ? "✅ Set" : "❌ Not set"));
            System.out.println("MCP: " + (isMCPEnabled() ? "✅ Enabled" : "❌ Disabled"));
            if (isMCPEnabled()) {
                System.out.println("MCP URL: " + getMCPFullUrl());
            }
            System.out.println("Model: " + getSelectedModel());
            System.out.println("Provider: " + getDefaultProvider());
            System.out.println("Auto Scroll: " + (isAutoScrollEnabled() ? "✅" : "❌"));
            System.out.println("=====================================\n");
        } catch (Exception e) {
            System.err.println("설정 출력 실패: " + e.getMessage());
        }
    }
    
    public void saveSettings() {
        try {
            setAllEnvironmentVariables();
            System.out.println("✅ Settings saved and environment variables updated");
        } catch (Exception e) {
            System.err.println("❌ Failed to save settings: " + e.getMessage());
        }
    }
}