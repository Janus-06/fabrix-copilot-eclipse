package com.fabrix.copilot.core;

import java.io.*;
import java.nio.file.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.runtime.*;
import org.osgi.framework.FrameworkUtil;

import com.fabrix.copilot.utils.CopilotLogger;

/**
 * 📌 Snippet Manager - 코드 스니펫 관리
 * 
 * 자주 사용하는 코드 조각을 저장하고 관리
 */
public class SnippetManager {
    
    private static final String SNIPPETS_FILE = "snippets.json";
    private final Map<String, CodeSnippet> snippets;
    private Path snippetsPath;
    
    public SnippetManager() {
        this.snippets = new ConcurrentHashMap<>();
        this.snippetsPath = initializeSnippetsPath();
        loadSnippets();
    }
    
    /**
     * 스니펫 경로 초기화
     */
    private Path initializeSnippetsPath() {
        try {
            IPath stateLocation = Platform.getStateLocation(
                FrameworkUtil.getBundle(SnippetManager.class));
            Path dir = stateLocation.toFile().toPath();
            Files.createDirectories(dir);
            return dir.resolve(SNIPPETS_FILE);
        } catch (Exception e) {
            CopilotLogger.error("Failed to initialize snippets path", e);
            return null;
        }
    }
    
    /**
     * 스니펫 저장
     */
    public void saveSnippet(String name, String code, String language) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Snippet name cannot be empty");
        }
        
        CodeSnippet snippet = new CodeSnippet(
            UUID.randomUUID().toString(),
            name.trim(),
            code,
            language,
            new Date(),
            new ArrayList<>(),
            ""
        );
        
        snippets.put(snippet.getId(), snippet);
        persistSnippets();
        
        CopilotLogger.info("Snippet saved: " + name);
    }
    
    /**
     * 스니펫 업데이트
     */
    public void updateSnippet(String id, String name, String code, String language) {
        CodeSnippet existing = snippets.get(id);
        if (existing == null) {
            throw new IllegalArgumentException("Snippet not found: " + id);
        }
        
        CodeSnippet updated = new CodeSnippet(
            id,
            name,
            code,
            language,
            existing.getCreatedAt(),
            existing.getTags(),
            existing.getDescription()
        );
        
        snippets.put(id, updated);
        persistSnippets();
        
        CopilotLogger.info("Snippet updated: " + name);
    }
    
    /**
     * 스니펫 삭제
     */
    public void deleteSnippet(String id) {
        CodeSnippet removed = snippets.remove(id);
        if (removed != null) {
            persistSnippets();
            CopilotLogger.info("Snippet deleted: " + removed.getName());
        }
    }
    
    /**
     * 스니펫 조회
     */
    public CodeSnippet getSnippet(String id) {
        return snippets.get(id);
    }
    
    /**
     * 이름으로 스니펫 조회
     */
    public CodeSnippet getSnippetByName(String name) {
        return snippets.values().stream()
            .filter(s -> s.getName().equals(name))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 모든 스니펫 조회
     */
    public List<CodeSnippet> getAllSnippets() {
        return new ArrayList<>(snippets.values());
    }
    
    /**
     * 스니펫 검색
     */
    public List<CodeSnippet> searchSnippets(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllSnippets();
        }
        
        String lowerQuery = query.toLowerCase();
        
        return snippets.values().stream()
            .filter(s -> s.matches(lowerQuery))
            .sorted(Comparator.comparing(CodeSnippet::getName))
            .collect(Collectors.toList());
    }
    
    /**
     * 언어별 스니펫 조회
     */
    public List<CodeSnippet> getSnippetsByLanguage(String language) {
        return snippets.values().stream()
            .filter(s -> s.getLanguage().equalsIgnoreCase(language))
            .sorted(Comparator.comparing(CodeSnippet::getName))
            .collect(Collectors.toList());
    }
    
    /**
     * 태그별 스니펫 조회
     */
    public List<CodeSnippet> getSnippetsByTag(String tag) {
        return snippets.values().stream()
            .filter(s -> s.getTags().contains(tag))
            .sorted(Comparator.comparing(CodeSnippet::getName))
            .collect(Collectors.toList());
    }
    
    /**
     * 태그 추가
     */
    public void addTag(String snippetId, String tag) {
        CodeSnippet snippet = snippets.get(snippetId);
        if (snippet != null && !snippet.getTags().contains(tag)) {
            snippet.getTags().add(tag);
            persistSnippets();
        }
    }
    
    /**
     * 태그 제거
     */
    public void removeTag(String snippetId, String tag) {
        CodeSnippet snippet = snippets.get(snippetId);
        if (snippet != null) {
            snippet.getTags().remove(tag);
            persistSnippets();
        }
    }
    
    /**
     * 모든 태그 조회
     */
    public Set<String> getAllTags() {
        return snippets.values().stream()
            .flatMap(s -> s.getTags().stream())
            .collect(Collectors.toSet());
    }
    
    /**
     * 스니펫 import (JSON)
     */
    public void importSnippets(String json) {
        try {
            // 간단한 JSON 파싱
            List<CodeSnippet> imported = parseSnippetsJson(json);
            
            for (CodeSnippet snippet : imported) {
                // ID 충돌 방지
                if (snippets.containsKey(snippet.getId())) {
                    snippet = new CodeSnippet(
                        UUID.randomUUID().toString(),
                        snippet.getName() + " (imported)",
                        snippet.getCode(),
                        snippet.getLanguage(),
                        new Date(),
                        snippet.getTags(),
                        snippet.getDescription()
                    );
                }
                snippets.put(snippet.getId(), snippet);
            }
            
            persistSnippets();
            CopilotLogger.info("Imported " + imported.size() + " snippets");
            
        } catch (Exception e) {
            CopilotLogger.error("Failed to import snippets", e);
            throw new RuntimeException("Import failed: " + e.getMessage());
        }
    }
    
    /**
     * 스니펫 export (JSON)
     */
    public String exportSnippets(List<String> ids) {
        List<CodeSnippet> toExport = ids == null || ids.isEmpty() ? 
            getAllSnippets() : 
            ids.stream()
                .map(snippets::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        return toJson(toExport);
    }
    
    /**
     * 스니펫 저장
     */
    private void persistSnippets() {
        if (snippetsPath == null) return;
        
        try {
            String json = toJson(getAllSnippets());
            Files.write(snippetsPath, json.getBytes());
            CopilotLogger.debug("Snippets persisted to: " + snippetsPath);
        } catch (Exception e) {
            CopilotLogger.error("Failed to persist snippets", e);
        }
    }
    
    /**
     * 스니펫 로드
     */
    private void loadSnippets() {
        if (snippetsPath == null || !Files.exists(snippetsPath)) {
            loadDefaultSnippets();
            return;
        }
        
        try {
            String json = new String(Files.readAllBytes(snippetsPath));
            List<CodeSnippet> loaded = parseSnippetsJson(json);
            
            snippets.clear();
            for (CodeSnippet snippet : loaded) {
                snippets.put(snippet.getId(), snippet);
            }
            
            CopilotLogger.info("Loaded " + snippets.size() + " snippets");
            
        } catch (Exception e) {
            CopilotLogger.error("Failed to load snippets", e);
            loadDefaultSnippets();
        }
    }
    
    /**
     * 기본 스니펫 로드
     */
    private void loadDefaultSnippets() {
        // Java 스니펫
        saveSnippet("Singleton Pattern", """
            private static $CLASS_NAME$ instance;
            
            private $CLASS_NAME$() {
                // Private constructor
            }
            
            public static synchronized $CLASS_NAME$ getInstance() {
                if (instance == null) {
                    instance = new $CLASS_NAME$();
                }
                return instance;
            }
            """, "java");
        
        saveSnippet("Try-With-Resources", """
            try (InputStream is = new FileInputStream("$FILE_PATH$");
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                
                String line;
                while ((line = reader.readLine()) != null) {
                    // Process line
                }
                
            } catch (IOException e) {
                e.printStackTrace();
            }
            """, "java");
        
        // JavaScript 스니펫
        saveSnippet("Async Function", """
            async function $FUNCTION_NAME$($PARAMS$) {
                try {
                    const result = await $ASYNC_CALL$;
                    return result;
                } catch (error) {
                    console.error('Error:', error);
                    throw error;
                }
            }
            """, "javascript");
        
        // Python 스니펫
        saveSnippet("Class Template", """
            class $CLASS_NAME$:
                def __init__(self, $PARAMS$):
                    self.$ATTRIBUTE$ = $VALUE$
                
                def $METHOD_NAME$(self):
                    pass
            """, "python");
    }
    
    /**
     * JSON 변환
     */
    private String toJson(List<CodeSnippet> snippets) {
        StringBuilder json = new StringBuilder();
        json.append("[\n");
        
        for (int i = 0; i < snippets.size(); i++) {
            if (i > 0) json.append(",\n");
            json.append(snippets.get(i).toJson());
        }
        
        json.append("\n]");
        return json.toString();
    }
    
    /**
     * JSON 파싱
     */
    private List<CodeSnippet> parseSnippetsJson(String json) {
        List<CodeSnippet> result = new ArrayList<>();
        
        // 간단한 JSON 배열 파싱
        json = json.trim();
        if (json.startsWith("[") && json.endsWith("]")) {
            json = json.substring(1, json.length() - 1);
            
            // 각 객체 파싱
            String[] objects = json.split("\\},\\s*\\{");
            for (String obj : objects) {
                if (!obj.startsWith("{")) obj = "{" + obj;
                if (!obj.endsWith("}")) obj = obj + "}";
                
                try {
                    CodeSnippet snippet = CodeSnippet.fromJson(obj);
                    if (snippet != null) {
                        result.add(snippet);
                    }
                } catch (Exception e) {
                    CopilotLogger.warn("Failed to parse snippet: " + e.getMessage());
                }
            }
        }
        
        return result;
    }
    
    /**
     * 코드 스니펫 클래스
     */
    public static class CodeSnippet {
        private final String id;
        private final String name;
        private final String code;
        private final String language;
        private final Date createdAt;
        private final List<String> tags;
        private String description;
        
        public CodeSnippet(String id, String name, String code, String language,
                          Date createdAt, List<String> tags, String description) {
            this.id = id;
            this.name = name;
            this.code = code;
            this.language = language;
            this.createdAt = createdAt;
            this.tags = new ArrayList<>(tags);
            this.description = description;
        }
        
        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public String getCode() { return code; }
        public String getLanguage() { return language; }
        public Date getCreatedAt() { return createdAt; }
        public List<String> getTags() { return tags; }
        public String getDescription() { return description; }
        
        public void setDescription(String description) {
            this.description = description;
        }
        
        /**
         * 검색 매칭
         */
        public boolean matches(String query) {
            return name.toLowerCase().contains(query) ||
                   code.toLowerCase().contains(query) ||
                   language.toLowerCase().contains(query) ||
                   (description != null && description.toLowerCase().contains(query)) ||
                   tags.stream().anyMatch(tag -> tag.toLowerCase().contains(query));
        }
        
        /**
         * JSON 변환
         */
        public String toJson() {
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"id\": \"").append(escapeJson(id)).append("\",\n");
            json.append("  \"name\": \"").append(escapeJson(name)).append("\",\n");
            json.append("  \"code\": \"").append(escapeJson(code)).append("\",\n");
            json.append("  \"language\": \"").append(escapeJson(language)).append("\",\n");
            json.append("  \"createdAt\": ").append(createdAt.getTime()).append(",\n");
            json.append("  \"tags\": [");
            
            for (int i = 0; i < tags.size(); i++) {
                if (i > 0) json.append(", ");
                json.append("\"").append(escapeJson(tags.get(i))).append("\"");
            }
            
            json.append("],\n");
            json.append("  \"description\": \"").append(escapeJson(description)).append("\"\n");
            json.append("}");
            
            return json.toString();
        }
        
        /**
         * JSON 파싱
         */
        public static CodeSnippet fromJson(String json) {
            try {
                String id = extractJsonValue(json, "id");
                String name = extractJsonValue(json, "name");
                String code = extractJsonValue(json, "code");
                String language = extractJsonValue(json, "language");
                String description = extractJsonValue(json, "description");
                
                String createdAtStr = extractJsonValue(json, "createdAt");
                Date createdAt = new Date(Long.parseLong(createdAtStr));
                
                List<String> tags = extractJsonArray(json, "tags");
                
                return new CodeSnippet(id, name, code, language, createdAt, tags, description);
                
            } catch (Exception e) {
                CopilotLogger.error("Failed to parse snippet JSON", e);
                return null;
            }
        }
        
        private static String extractJsonValue(String json, String key) {
            String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            
            if (m.find()) {
                return unescapeJson(m.group(1));
            }
            
            // 숫자 값
            pattern = "\"" + key + "\"\\s*:\\s*(\\d+)";
            p = java.util.regex.Pattern.compile(pattern);
            m = p.matcher(json);
            
            if (m.find()) {
                return m.group(1);
            }
            
            return "";
        }
        
        private static List<String> extractJsonArray(String json, String key) {
            List<String> result = new ArrayList<>();
            
            String pattern = "\"" + key + "\"\\s*:\\s*\\[([^\\]]+)\\]";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            
            if (m.find()) {
                String arrayContent = m.group(1);
                String[] items = arrayContent.split(",");
                
                for (String item : items) {
                    item = item.trim();
                    if (item.startsWith("\"") && item.endsWith("\"")) {
                        result.add(unescapeJson(item.substring(1, item.length() - 1)));
                    }
                }
            }
            
            return result;
        }
        
        private static String escapeJson(String str) {
            if (str == null) return "";
            return str.replace("\\", "\\\\")
                      .replace("\"", "\\\"")
                      .replace("\n", "\\n")
                      .replace("\r", "\\r")
                      .replace("\t", "\\t");
        }
        
        private static String unescapeJson(String str) {
            if (str == null) return "";
            return str.replace("\\\\", "\\")
                      .replace("\\\"", "\"")
                      .replace("\\n", "\n")
                      .replace("\\r", "\r")
                      .replace("\\t", "\t");
        }
        
        @Override
        public String toString() {
            return name + " (" + language + ")";
        }
    }
}