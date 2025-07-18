package com.fabrix.copilot.ui;

import com.fabrix.copilot.core.ConversationManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 💾 Chat Exporter - 대화 내보내기 유틸리티
 * 
 * 다양한 형식으로 대화 내보내기 지원
 */
public class ChatExporter {
    
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 내보내기 형식
     */
    public enum ExportFormat {
        PLAIN_TEXT, MARKDOWN, JSON, HTML
    }
    
    /**
     * 대화 내보내기
     */
    public String export(List<ConversationManager.Message> messages, ExportFormat format) {
        switch (format) {
            case MARKDOWN:
                return exportAsMarkdown(messages);
            case JSON:
                return exportAsJson(messages);
            case HTML:
                return exportAsHtml(messages);
            default:
                return exportAsPlainText(messages);
        }
    }
    
    /**
     * 일반 텍스트로 내보내기
     */
    private String exportAsPlainText(List<ConversationManager.Message> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== FabriX Copilot Chat Export ===\n");
        sb.append("Exported: ").append(DATE_FORMAT.format(new Date())).append("\n");
        sb.append("Total Messages: ").append(messages.size()).append("\n");
        sb.append("=====================================\n\n");
        
        for (ConversationManager.Message msg : messages) {
            String role = msg.isUser ? "USER" : "ASSISTANT";
            String timestamp = DATE_FORMAT.format(new Date(msg.timestamp));
            
            sb.append("[").append(timestamp).append("] ");
            sb.append(role).append(": ");
            sb.append(msg.content).append("\n\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Markdown으로 내보내기
     */
    private String exportAsMarkdown(List<ConversationManager.Message> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("# FabriX Copilot Chat Export\n\n");
        sb.append("**Exported:** ").append(DATE_FORMAT.format(new Date())).append("\n");
        sb.append("**Total Messages:** ").append(messages.size()).append("\n\n");
        sb.append("---\n\n");
        
        for (ConversationManager.Message msg : messages) {
            String timestamp = DATE_FORMAT.format(new Date(msg.timestamp));
            
            if (msg.isUser) {
                sb.append("### 👤 User\n");
                sb.append("*").append(timestamp).append("*\n\n");
                sb.append(msg.content).append("\n\n");
            } else {
                sb.append("### 🤖 Assistant\n");
                sb.append("*").append(timestamp).append("*\n\n");
                sb.append(msg.content).append("\n\n");
            }
            
            sb.append("---\n\n");
        }
        
        return sb.toString();
    }
    
    /**
     * JSON으로 내보내기
     */
    private String exportAsJson(List<ConversationManager.Message> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"export_info\": {\n");
        sb.append("    \"exported_at\": \"").append(DATE_FORMAT.format(new Date())).append("\",\n");
        sb.append("    \"total_messages\": ").append(messages.size()).append("\n");
        sb.append("  },\n");
        sb.append("  \"messages\": [\n");
        
        for (int i = 0; i < messages.size(); i++) {
            ConversationManager.Message msg = messages.get(i);
            String timestamp = DATE_FORMAT.format(new Date(msg.timestamp));
            
            sb.append("    {\n");
            sb.append("      \"timestamp\": \"").append(timestamp).append("\",\n");
            sb.append("      \"role\": \"").append(msg.isUser ? "user" : "assistant").append("\",\n");
            sb.append("      \"content\": \"").append(escapeJson(msg.content)).append("\"\n");
            sb.append("    }");
            
            if (i < messages.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        
        sb.append("  ]\n");
        sb.append("}");
        
        return sb.toString();
    }
    
    /**
     * HTML로 내보내기
     */
    private String exportAsHtml(List<ConversationManager.Message> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html>\n");
        sb.append("<head>\n");
        sb.append("  <meta charset=\"UTF-8\">\n");
        sb.append("  <title>FabriX Copilot Chat Export</title>\n");
        sb.append("  <style>\n");
        sb.append("    body { font-family: 'Segoe UI', Arial, sans-serif; max-width: 800px; margin: 0 auto; padding: 20px; }\n");
        sb.append("    .header { background: #f0f0f0; padding: 20px; border-radius: 8px; margin-bottom: 20px; }\n");
        sb.append("    .message { margin: 20px 0; padding: 15px; border-radius: 8px; }\n");
        sb.append("    .user { background: #e3f2fd; border-left: 4px solid #2196f3; }\n");
        sb.append("    .assistant { background: #f5f5f5; border-left: 4px solid #757575; }\n");
        sb.append("    .timestamp { font-size: 0.9em; color: #666; margin-bottom: 5px; }\n");
        sb.append("    .role { font-weight: bold; margin-bottom: 10px; }\n");
        sb.append("    .content { white-space: pre-wrap; }\n");
        sb.append("    code { background: #f0f0f0; padding: 2px 4px; border-radius: 3px; }\n");
        sb.append("    pre { background: #f0f0f0; padding: 10px; border-radius: 5px; overflow-x: auto; }\n");
        sb.append("  </style>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");
        
        sb.append("  <div class=\"header\">\n");
        sb.append("    <h1>🤖 FabriX Copilot Chat Export</h1>\n");
        sb.append("    <p><strong>Exported:</strong> ").append(DATE_FORMAT.format(new Date())).append("</p>\n");
        sb.append("    <p><strong>Total Messages:</strong> ").append(messages.size()).append("</p>\n");
        sb.append("  </div>\n\n");
        
        for (ConversationManager.Message msg : messages) {
            String timestamp = DATE_FORMAT.format(new Date(msg.timestamp));
            String cssClass = msg.isUser ? "user" : "assistant";
            String role = msg.isUser ? "👤 User" : "🤖 Assistant";
            
            sb.append("  <div class=\"message ").append(cssClass).append("\">\n");
            sb.append("    <div class=\"timestamp\">").append(timestamp).append("</div>\n");
            sb.append("    <div class=\"role\">").append(role).append("</div>\n");
            sb.append("    <div class=\"content\">").append(escapeHtml(msg.content)).append("</div>\n");
            sb.append("  </div>\n\n");
        }
        
        sb.append("</body>\n");
        sb.append("</html>");
        
        return sb.toString();
    }
    
    /**
     * JSON 이스케이프
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
    
    /**
     * HTML 이스케이프
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}