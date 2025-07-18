package com.fabrix.copilot.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 💾 Enhanced Conversation Manager - Multi-Agent 대화 기억
 */
public class ConversationManager {
    private static ConversationManager instance;
    private final Map<String, List<Message>> conversations;
    private final Map<String, String> conversationTitles;
    
    public static class Message {
        public final String content;
        public final boolean isUser;
        public final long timestamp;
        
        public Message(String content, boolean isUser, long timestamp) {
            this.content = content;
            this.isUser = isUser;
            this.timestamp = timestamp;
        }
    }
    
    private ConversationManager() {
        this.conversations = new ConcurrentHashMap<>();
        this.conversationTitles = new ConcurrentHashMap<>();
    }
    
    public static synchronized ConversationManager getInstance() {
        if (instance == null) {
            instance = new ConversationManager();
        }
        return instance;
    }
    
    public String startNewConversation() {
        String sessionId = UUID.randomUUID().toString();
        conversations.put(sessionId, new ArrayList<>());
        conversationTitles.put(sessionId, "New Conversation");
        return sessionId;
    }
    
    public void addMessage(String sessionId, String content, boolean isUser) {
        conversations.computeIfAbsent(sessionId, k -> new ArrayList<>())
                    .add(new Message(content, isUser, System.currentTimeMillis()));
        
        // 최근 50개만 유지
        List<Message> messages = conversations.get(sessionId);
        if (messages.size() > 50) {
            messages.subList(0, messages.size() - 50).clear();
        }
    }
    
    public String getConversationContext(String sessionId, int limit) {
        if (!conversations.containsKey(sessionId)) return "";
        
        List<Message> messages = conversations.get(sessionId);
        int start = Math.max(0, messages.size() - limit);
        
        StringBuilder context = new StringBuilder();
        context.append("=== 이전 대화 ===\n");
        for (int i = start; i < messages.size(); i++) {
            Message msg = messages.get(i);
            context.append(msg.isUser ? "사용자: " : "AI: ")
                  .append(msg.content)
                  .append("\n");
        }
        context.append("=== 현재 요청 ===\n");
        
        return context.toString();
    }
    
    public List<Message> getConversationHistory(String sessionId) {
        return conversations.getOrDefault(sessionId, new ArrayList<>());
    }
    
    public List<String> getConversationList() {
        return new ArrayList<>(conversationTitles.keySet());
    }
    
    public void clearConversation(String sessionId) {
        conversations.remove(sessionId);
        conversationTitles.remove(sessionId);
    }
    
    public Map<String, Integer> getConversationStats() {
        Map<String, Integer> stats = new HashMap<>();
        for (Map.Entry<String, List<Message>> entry : conversations.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().size());
        }
        return stats;
    }
}