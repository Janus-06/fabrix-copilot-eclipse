package com.fabrix.copilot.ui;

import org.eclipse.swt.widgets.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.custom.*;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;

import com.fabrix.copilot.core.ConversationManager;
import com.fabrix.copilot.utils.CopilotLogger;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * 📋 Conversation History Dialog - 대화 이력 관리
 * 
 * 저장된 대화들을 관리하고 불러오는 다이얼로그
 */
public class ConversationHistoryDialog extends Dialog {
    
    // UI 컴포넌트
    private org.eclipse.swt.widgets.List conversationList;
    private StyledText previewText;
    private Label statusLabel;
    private Button loadButton;
    private Button deleteButton;
    private Button exportButton;
    
    // 데이터
    private ConversationManager conversationManager;
    private String selectedSessionId;
    private Map<String, ConversationInfo> conversationInfoMap;
    
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    
    /**
     * 대화 정보 클래스
     */
    private static class ConversationInfo {
        String sessionId;
        Date startTime;
        int messageCount;
        String firstMessage;
        
        ConversationInfo(String sessionId, List<ConversationManager.Message> messages) {
            this.sessionId = sessionId;
            this.messageCount = messages.size();
            
            if (!messages.isEmpty()) {
                this.startTime = new Date(messages.get(0).timestamp);
                // 첫 사용자 메시지 찾기
                for (ConversationManager.Message msg : messages) {
                    if (msg.isUser) {
                        this.firstMessage = msg.content;
                        break;
                    }
                }
                if (this.firstMessage == null) {
                    this.firstMessage = messages.get(0).content;
                }
                // 너무 긴 메시지는 잘라내기
                if (this.firstMessage.length() > 50) {
                    this.firstMessage = this.firstMessage.substring(0, 47) + "...";
                }
            } else {
                this.startTime = new Date();
                this.firstMessage = "빈 대화";
            }
        }
        
        @Override
        public String toString() {
            return String.format("[%s] %s (%d messages)", 
                DATE_FORMAT.format(startTime), firstMessage, messageCount);
        }
    }
    
    public ConversationHistoryDialog(Shell parentShell, ConversationManager manager) {
        super(parentShell);
        this.conversationManager = manager;
        this.conversationInfoMap = new HashMap<>();
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }
    
    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("📋 대화 이력");
        shell.setSize(800, 600);
        
        // 중앙 위치
        Rectangle bounds = shell.getDisplay().getBounds();
        Rectangle rect = shell.getBounds();
        int x = bounds.x + (bounds.width - rect.width) / 2;
        int y = bounds.y + (bounds.height - rect.height) / 2;
        shell.setLocation(x, y);
    }
    
    @Override
    protected Control createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        container.setLayout(new GridLayout(1, false));
        
        createTitle(container);
        createMainContent(container);
        loadConversations();
        
        return container;
    }
    
    /**
     * 제목 영역 생성
     */
    private void createTitle(Composite parent) {
        Composite titleComposite = new Composite(parent, SWT.NONE);
        titleComposite.setLayout(new GridLayout(2, false));
        titleComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        Label titleLabel = new Label(titleComposite, SWT.NONE);
        titleLabel.setText("📋 저장된 대화 목록");
        titleLabel.setFont(new Font(titleLabel.getDisplay(), "Segoe UI", 12, SWT.BOLD));
        
        statusLabel = new Label(titleComposite, SWT.NONE);
        statusLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
        statusLabel.setText("Loading...");
        
        Label separator = new Label(parent, SWT.HORIZONTAL | SWT.SEPARATOR);
        separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }
    
    /**
     * 메인 컨텐츠 생성
     */
    private void createMainContent(Composite parent) {
        SashForm sashForm = new SashForm(parent, SWT.HORIZONTAL);
        sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        
        createConversationList(sashForm);
        createPreviewArea(sashForm);
        
        sashForm.setWeights(new int[]{40, 60});
    }
    
    /**
     * 대화 목록 생성
     */
    private void createConversationList(Composite parent) {
        Composite listComposite = new Composite(parent, SWT.NONE);
        listComposite.setLayout(new GridLayout(1, false));
        
        Label listLabel = new Label(listComposite, SWT.NONE);
        listLabel.setText("대화 목록:");
        
        conversationList = new org.eclipse.swt.widgets.List(listComposite, 
            SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
        conversationList.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        
        conversationList.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                updatePreview();
            }
        });
        
        conversationList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDoubleClick(MouseEvent e) {
                loadSelectedConversation();
            }
        });
        
        createListButtons(listComposite);
    }
    
    /**
     * 목록 버튼들 생성
     */
    private void createListButtons(Composite parent) {
        Composite buttonComposite = new Composite(parent, SWT.NONE);
        buttonComposite.setLayout(new GridLayout(3, true));
        buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        loadButton = new Button(buttonComposite, SWT.PUSH);
        loadButton.setText("📂 불러오기");
        loadButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        loadButton.setEnabled(false);
        loadButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                loadSelectedConversation();
            }
        });
        
        exportButton = new Button(buttonComposite, SWT.PUSH);
        exportButton.setText("💾 내보내기");
        exportButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        exportButton.setEnabled(false);
        exportButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                exportSelectedConversation();
            }
        });
        
        deleteButton = new Button(buttonComposite, SWT.PUSH);
        deleteButton.setText("🗑️ 삭제");
        deleteButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        deleteButton.setEnabled(false);
        deleteButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                deleteSelectedConversation();
            }
        });
    }
    
    /**
     * 미리보기 영역 생성
     */
    private void createPreviewArea(Composite parent) {
        Composite previewComposite = new Composite(parent, SWT.NONE);
        previewComposite.setLayout(new GridLayout(1, false));
        
        Label previewLabel = new Label(previewComposite, SWT.NONE);
        previewLabel.setText("대화 미리보기:");
        
        previewText = new StyledText(previewComposite, 
            SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
        previewText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        previewText.setFont(new Font(previewText.getDisplay(), "Consolas", 9, SWT.NORMAL));
        previewText.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW));
        
        // 검색 기능
        Composite searchComposite = new Composite(previewComposite, SWT.NONE);
        searchComposite.setLayout(new GridLayout(3, false));
        searchComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        Label searchLabel = new Label(searchComposite, SWT.NONE);
        searchLabel.setText("검색:");
        
        Text searchText = new Text(searchComposite, SWT.BORDER);
        searchText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        searchText.setMessage("검색어 입력...");
        
        Button searchButton = new Button(searchComposite, SWT.PUSH);
        searchButton.setText("🔍");
        searchButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                searchInPreview(searchText.getText());
            }
        });
        
        searchText.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.keyCode == SWT.CR) {
                    searchInPreview(searchText.getText());
                }
            }
        });
    }
    
    /**
     * 대화 목록 로드
     */
    private void loadConversations() {
        conversationList.removeAll();
        conversationInfoMap.clear();
        
        List<String> sessionIds = conversationManager.getConversationList();
        int validConversations = 0;
        
        for (String sessionId : sessionIds) {
            List<ConversationManager.Message> messages = 
                conversationManager.getConversationHistory(sessionId);
            
            if (!messages.isEmpty()) {
                ConversationInfo info = new ConversationInfo(sessionId, messages);
                conversationInfoMap.put(sessionId, info);
                conversationList.add(info.toString());
                validConversations++;
            }
        }
        
        statusLabel.setText(String.format("%d개의 대화 발견", validConversations));
        
        if (conversationList.getItemCount() > 0) {
            conversationList.select(0);
            updatePreview();
        }
    }
    
    /**
     * 미리보기 업데이트
     */
    private void updatePreview() {
        int selectionIndex = conversationList.getSelectionIndex();
        if (selectionIndex < 0) {
            previewText.setText("");
            loadButton.setEnabled(false);
            deleteButton.setEnabled(false);
            exportButton.setEnabled(false);
            return;
        }
        
        String selectedItem = conversationList.getItem(selectionIndex);
        ConversationInfo selectedInfo = null;
        
        // 선택된 대화 찾기
        for (ConversationInfo info : conversationInfoMap.values()) {
            if (info.toString().equals(selectedItem)) {
                selectedInfo = info;
                break;
            }
        }
        
        if (selectedInfo != null) {
            selectedSessionId = selectedInfo.sessionId;
            List<ConversationManager.Message> messages = 
                conversationManager.getConversationHistory(selectedSessionId);
            
            // 미리보기 텍스트 생성
            StringBuilder preview = new StringBuilder();
            preview.append("=== 대화 정보 ===\n");
            preview.append("시작 시간: ").append(DATE_FORMAT.format(selectedInfo.startTime)).append("\n");
            preview.append("메시지 수: ").append(selectedInfo.messageCount).append("\n");
            preview.append("세션 ID: ").append(selectedInfo.sessionId).append("\n");
            preview.append("\n=== 대화 내용 ===\n\n");
            
            for (ConversationManager.Message msg : messages) {
                String role = msg.isUser ? "👤 사용자" : "🤖 AI";
                String time = new SimpleDateFormat("HH:mm:ss").format(new Date(msg.timestamp));
                preview.append("[").append(time).append("] ").append(role).append(":\n");
                preview.append(msg.content).append("\n\n");
                preview.append("---\n\n");
            }
            
            previewText.setText(preview.toString());
            
            // 스타일 적용
            applyPreviewStyles();
            
            // 버튼 활성화
            loadButton.setEnabled(true);
            deleteButton.setEnabled(true);
            exportButton.setEnabled(true);
        }
    }
    
    /**
     * 미리보기 스타일 적용
     */
    private void applyPreviewStyles() {
        String text = previewText.getText();
        
        // 사용자 메시지 스타일
        applyStyle(text, "👤 사용자", SWT.COLOR_BLUE);
        
        // AI 메시지 스타일
        applyStyle(text, "🤖 AI", SWT.COLOR_DARK_GREEN);
        
        // 시간 스타일
        applyStyle(text, "\\[\\d{2}:\\d{2}:\\d{2}\\]", SWT.COLOR_DARK_GRAY);
    }
    
    /**
     * 스타일 적용 헬퍼
     */
    private void applyStyle(String text, String pattern, int colorId) {
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(text);
            
            while (m.find()) {
                StyleRange style = new StyleRange();
                style.start = m.start();
                style.length = m.end() - m.start();
                style.foreground = Display.getDefault().getSystemColor(colorId);
                style.fontStyle = SWT.BOLD;
                previewText.setStyleRange(style);
            }
        } catch (Exception e) {
            // 스타일 적용 실패 무시
        }
    }
    
    /**
     * 선택된 대화 불러오기
     */
    private void loadSelectedConversation() {
        if (selectedSessionId != null) {
            setReturnCode(Window.OK);
            close();
        }
    }
    
    /**
     * 선택된 대화 삭제
     */
    private void deleteSelectedConversation() {
        if (selectedSessionId == null) return;
        
        boolean confirm = MessageDialog.openConfirm(getShell(), 
            "대화 삭제", "선택한 대화를 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.");
        
        if (confirm) {
            conversationManager.clearConversation(selectedSessionId);
            loadConversations(); // 목록 새로고침
            CopilotLogger.info("Conversation deleted: " + selectedSessionId);
        }
    }
    
    /**
     * 선택된 대화 내보내기
     */
    private void exportSelectedConversation() {
        if (selectedSessionId == null) return;
        
        FileDialog dialog = new FileDialog(getShell(), SWT.SAVE);
        dialog.setFilterExtensions(new String[]{"*.md", "*.txt", "*.json", "*.html"});
        dialog.setFilterNames(new String[]{"Markdown", "Plain Text", "JSON", "HTML"});
        dialog.setFileName("conversation-" + 
            new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()));
        
        String path = dialog.open();
        if (path != null) {
            try {
                List<ConversationManager.Message> messages = 
                    conversationManager.getConversationHistory(selectedSessionId);
                
                ChatExporter.ExportFormat format = determineFormat(path);
                ChatExporter exporter = new ChatExporter();
                String content = exporter.export(messages, format);
                
                Files.write(Paths.get(path), content.getBytes());
                
                MessageDialog.openInformation(getShell(), 
                    "내보내기 완료", "대화가 성공적으로 내보내졌습니다: " + path);
                
                CopilotLogger.info("Conversation exported to: " + path);
                
            } catch (IOException e) {
                MessageDialog.openError(getShell(), 
                    "내보내기 실패", "파일 저장 중 오류가 발생했습니다: " + e.getMessage());
                CopilotLogger.error("Export failed", e);
            }
        }
    }
    
    /**
     * 미리보기에서 검색
     */
    private void searchInPreview(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return;
        }
        
        String content = previewText.getText().toLowerCase();
        String search = searchText.toLowerCase();
        
        int index = content.indexOf(search, previewText.getCaretOffset());
        if (index == -1 && previewText.getCaretOffset() > 0) {
            // 처음부터 다시 검색
            index = content.indexOf(search);
        }
        
        if (index != -1) {
            previewText.setSelection(index, index + searchText.length());
            previewText.showSelection();
        } else {
            MessageDialog.openInformation(getShell(), 
                "검색 결과", "'" + searchText + "'를 찾을 수 없습니다.");
        }
    }
    
    /**
     * 파일 형식 결정
     */
    private ChatExporter.ExportFormat determineFormat(String path) {
        if (path.endsWith(".md")) return ChatExporter.ExportFormat.MARKDOWN;
        if (path.endsWith(".json")) return ChatExporter.ExportFormat.JSON;
        if (path.endsWith(".html")) return ChatExporter.ExportFormat.HTML;
        return ChatExporter.ExportFormat.PLAIN_TEXT;
    }
    
    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, Window.OK, "불러오기", true);
        createButton(parent, Window.CANCEL, "닫기", false);
    }
    
    @Override
    protected void okPressed() {
        loadSelectedConversation();
    }
    
    /**
     * 선택된 세션 ID 반환
     */
    public String getSelectedSessionId() {
        return selectedSessionId;
    }
    
    @Override
    public boolean close() {
        // 리소스 정리
        Image[] images = new Image[]{};
        for (Control control : getShell().getChildren()) {
            if (control.getFont() != null && !control.getFont().isDisposed()) {
                // 폰트는 시스템 폰트가 아닌 경우만 dispose
            }
        }
        
        return super.close();
    }
}