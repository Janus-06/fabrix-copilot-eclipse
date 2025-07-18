package com.fabrix.copilot.ui;

import org.eclipse.swt.widgets.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;

/**
 * 💬 Message Bubble - 채팅 메시지 버블
 * 
 * 사용자와 AI의 메시지를 표시하는 커스텀 위젯
 */
public class MessageBubble extends Composite {
    
    private StyledText messageText;
    private Label timestampLabel;
    private boolean isUser;
    
    // 색상
    private static final Color USER_BG = new Color(null, 220, 240, 255); // 연한 파란색
    private static final Color AI_BG = new Color(null, 240, 240, 240);   // 연한 회색
    private static final Color USER_FG = new Color(null, 0, 0, 0);       // 검정
    private static final Color AI_FG = new Color(null, 0, 0, 0);         // 검정
    
    public MessageBubble(Composite parent, int style) {
        super(parent, style);
        createContents();
    }
    
    /**
     * 컨텐츠 생성
     */
    private void createContents() {
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 10;
        layout.marginHeight = 10;
        layout.verticalSpacing = 5;
        setLayout(layout);
        
        // 메시지 텍스트
        messageText = new StyledText(this, SWT.WRAP | SWT.READ_ONLY);
        messageText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        messageText.setCaret(null);
        messageText.setEditable(false);
        
        // 타임스탬프
        timestampLabel = new Label(this, SWT.NONE);
        timestampLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
        timestampLabel.setFont(new Font(getDisplay(), "Segoe UI", 8, SWT.NORMAL));
        timestampLabel.setForeground(getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
    }
    
    /**
     * 메시지 설정
     */
    public void setMessage(String content, boolean isUser) {
        this.isUser = isUser;
        
        // 배경색 설정
        Color bgColor = isUser ? USER_BG : AI_BG;
        Color fgColor = isUser ? USER_FG : AI_FG;
        
        setBackground(bgColor);
        messageText.setBackground(bgColor);
        messageText.setForeground(fgColor);
        timestampLabel.setBackground(bgColor);
        
        // 메시지 처리
        String processedContent = processMessage(content);
        messageText.setText(processedContent);
        
        // 마크다운 스타일 적용
        applyMarkdownStyles(processedContent);
        
        // 레이아웃 업데이트
        layout();
    }
    
    /**
     * 메시지 전처리
     */
    private String processMessage(String content) {
        if (content == null) return "";
        
        // 이모지 제거 (사용자/AI 표시는 버블로 구분)
        if (content.startsWith("👤 ")) {
            content = content.substring(3);
        } else if (content.startsWith("🤖 ")) {
            content = content.substring(3);
        }
        
        return content.trim();
    }
    
    /**
     * 마크다운 스타일 적용
     */
    private void applyMarkdownStyles(String text) {
        // 굵은 글씨 (**text**)
        applyStyle(text, "\\*\\*([^*]+)\\*\\*", SWT.BOLD);
        
        // 이탤릭 (*text*)
        applyStyle(text, "(?<!\\*)\\*([^*]+)\\*(?!\\*)", SWT.ITALIC);
        
        // 코드 블록 (```code```)
        applyCodeBlockStyle(text);
        
        // 인라인 코드 (`code`)
        applyInlineCodeStyle(text);
        
        // 헤더 (### Header)
        applyHeaderStyle(text);
    }
    
    /**
     * 스타일 적용 헬퍼
     */
    private void applyStyle(String text, String pattern, int style) {
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(text);
            
            while (m.find()) {
                StyleRange range = new StyleRange();
                range.start = m.start();
                range.length = m.end() - m.start();
                range.fontStyle = style;
                messageText.setStyleRange(range);
            }
        } catch (Exception e) {
            // 스타일 적용 실패 무시
        }
    }
    
    /**
     * 코드 블록 스타일
     */
    private void applyCodeBlockStyle(String text) {
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("```([^`]+)```", java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher m = p.matcher(text);
            
            while (m.find()) {
                StyleRange range = new StyleRange();
                range.start = m.start();
                range.length = m.end() - m.start();
                range.background = new Color(getDisplay(), 245, 245, 245);
                range.font = new Font(getDisplay(), "Consolas", 9, SWT.NORMAL);
                messageText.setStyleRange(range);
            }
        } catch (Exception e) {
            // 스타일 적용 실패 무시
        }
    }
    
    /**
     * 인라인 코드 스타일
     */
    private void applyInlineCodeStyle(String text) {
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("`([^`]+)`");
            java.util.regex.Matcher m = p.matcher(text);
            
            while (m.find()) {
                StyleRange range = new StyleRange();
                range.start = m.start();
                range.length = m.end() - m.start();
                range.background = new Color(getDisplay(), 245, 245, 245);
                range.font = new Font(getDisplay(), "Consolas", 9, SWT.NORMAL);
                messageText.setStyleRange(range);
            }
        } catch (Exception e) {
            // 스타일 적용 실패 무시
        }
    }
    
    /**
     * 헤더 스타일
     */
    private void applyHeaderStyle(String text) {
        try {
            String[] lines = text.split("\n");
            int offset = 0;
            
            for (String line : lines) {
                if (line.startsWith("###")) {
                    StyleRange range = new StyleRange();
                    range.start = offset;
                    range.length = line.length();
                    range.fontStyle = SWT.BOLD;
                    range.font = new Font(getDisplay(), "Segoe UI", 11, SWT.BOLD);
                    messageText.setStyleRange(range);
                } else if (line.startsWith("##")) {
                    StyleRange range = new StyleRange();
                    range.start = offset;
                    range.length = line.length();
                    range.fontStyle = SWT.BOLD;
                    range.font = new Font(getDisplay(), "Segoe UI", 12, SWT.BOLD);
                    messageText.setStyleRange(range);
                } else if (line.startsWith("#")) {
                    StyleRange range = new StyleRange();
                    range.start = offset;
                    range.length = line.length();
                    range.fontStyle = SWT.BOLD;
                    range.font = new Font(getDisplay(), "Segoe UI", 14, SWT.BOLD);
                    messageText.setStyleRange(range);
                }
                
                offset += line.length() + 1; // +1 for newline
            }
        } catch (Exception e) {
            // 스타일 적용 실패 무시
        }
    }
    
    /**
     * 타임스탬프 설정
     */
    public void setTimestamp(String timestamp) {
        if (timestampLabel != null && !timestampLabel.isDisposed()) {
            timestampLabel.setText(timestamp);
        }
    }
    
    /**
     * 리소스 정리
     */
    @Override
    public void dispose() {
        // 정적 색상은 dispose하지 않음 (시스템 전체에서 공유)
        
        // 동적으로 생성된 폰트나 색상이 있다면 여기서 정리
        super.dispose();
    }
}