package com.fabrix.copilot.core;

import java.util.*;

/**
 * 💾 ReactConversationManager - 대화 기억
 */
public class ReactConversationManager {
    private static ReactConversationManager instance;
    private final Map<String, List<Message>> conversations;
    
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
    
    public ReactConversationManager() {
        this.conversations = new HashMap<>();
    }
    
    public static synchronized ReactConversationManager getInstance() {
        if (instance == null) {
            instance = new ReactConversationManager();
        }
        return instance;
    }
    
    public String startNewConversation() {
        String sessionId = UUID.randomUUID().toString();
        conversations.put(sessionId, new ArrayList<>());
        return sessionId;
    }
    
    public void addMessage(String sessionId, String content, boolean isUser) {
        conversations.computeIfAbsent(sessionId, k -> new ArrayList<>())
                    .add(new Message(content, isUser, System.currentTimeMillis()));
    }
    
    public String getConversationContext(String sessionId, int limit) {
        if (!conversations.containsKey(sessionId)) return "";
        
        List<Message> messages = conversations.get(sessionId);
        int start = Math.max(0, messages.size() - limit);
        
        StringBuilder context = new StringBuilder();
        for (int i = start; i < messages.size(); i++) {
            Message msg = messages.get(i);
            context.append(msg.isUser ? "사용자: " : "AI: ")
                  .append(msg.content)
                  .append("\n");
        }
        return context.toString();
    }
    
    public Map<String, Integer> getConversationStats() {
        Map<String, Integer> stats = new HashMap<>();
        for (Map.Entry<String, List<Message>> entry : conversations.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().size());
        }
        return stats;
    }
    
    public void clearConversation(String sessionId) {
        conversations.remove(sessionId);
    }
}