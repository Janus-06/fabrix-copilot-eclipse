package com.fabrix.copilot.ui;

import org.eclipse.swt.widgets.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.texteditor.ITextEditor;

import com.fabrix.copilot.core.*;
import com.fabrix.copilot.agents.*;
import com.fabrix.copilot.utils.*;
import com.fabrix.copilot.mcp.McpServerManager;

import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.*;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.stream.Collectors;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

/**
 * 🤖 ChatView - 완전히 개선된 AI Assistant UI
 * 
 * 모든 기능이 구현되고 안정화된 채팅 인터페이스
 */
public class ChatView extends ViewPart {
    
    public static final String ID = "com.fabrix.copilot.views.chatView";
    
    // UI 컴포넌트들
    private Composite mainComposite;
    private Composite headerComposite;
    private Composite chatComposite;
    private Composite inputComposite;
    
    // 헤더
    private Label titleLabel;
    private Label statusLabel;
    private Combo modelCombo;
    private Button settingsButton;
    private Button mcpButton;
    
    // 채팅
    private ScrolledComposite chatScrolled;
    private Composite chatContent;
    private ToolBar chatToolBar;
    private ToolItem clearChatItem;
    private ToolItem exportChatItem;
    private ToolItem attachCodeItem;
    private ToolItem newConvItem;
    private ToolItem historyBtn;
    private ToolItem snippetBtn;
    
    // 입력
    private StyledText inputText;
    private Button sendButton;
    private Label characterCountLabel;
    private Combo codeAttachCombo;
    
    // 코드 첨부 관련 UI
    private Label attachStatusLabel;
    private Button clearAttachButton;
    
    // 비즈니스 로직
    private LLMClient llmClient;
    private PreferenceManager preferenceManager;
    private ConversationManager conversationManager;
    private AgentOrchestrator agentOrchestrator;
    private SnippetManager snippetManager;
    private EnhancedContextCollector contextCollector;
    private String currentSessionId;
    private boolean isProcessing = false;
    private Map<String, ModelInfo> modelMap = new HashMap<>();
    private String attachedCode = "";
    private String attachedFileName = "";
    
    // 내부 클래스 - MessageBubble
    private class MessageBubble extends Composite {
        private StyledText messageText;
        private Label timestampLabel;
        private boolean isUser;
        
        // 색상 상수
        private static final Color USER_BG = null; // 시스템 색상 사용
        private static final Color AI_BG = null;   // 시스템 색상 사용
        
        public MessageBubble(Composite parent, int style) {
            super(parent, style);
            createContents();
        }
        
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
        
        public void setMessage(String content, boolean isUser) {
            this.isUser = isUser;
            
            // 배경색 설정 - 시스템 색상 사용
            Color bgColor = isUser ? 
                new Color(getDisplay(), 220, 240, 255) : // 연한 파란색
                new Color(getDisplay(), 240, 240, 240);   // 연한 회색
            Color fgColor = getDisplay().getSystemColor(SWT.COLOR_BLACK);
            
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
            
            // 색상 리소스 정리
            bgColor.dispose();
        }
        
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
        
        public void setTimestamp(String timestamp) {
            if (timestampLabel != null && !timestampLabel.isDisposed()) {
                timestampLabel.setText(timestamp);
            }
        }
        
        @Override
        public void dispose() {
            // 동적으로 생성된 리소스만 정리
            super.dispose();
        }
    }

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(1, false));
        
        initializeComponents();
        createMainLayout(parent);
        createHeader();
        createChatArea();
        createInputArea();
        loadInitialData();
        
        CopilotLogger.info("ChatView initialized successfully");
    }
    
    private void initializeComponents() {
        this.preferenceManager = PreferenceManager.getInstance();
        this.llmClient = LLMClient.getInstance();
        this.conversationManager = ConversationManager.getInstance();
        this.agentOrchestrator = new AgentOrchestrator();
        this.snippetManager = new SnippetManager();
        this.contextCollector = new EnhancedContextCollector();
        this.currentSessionId = conversationManager.startNewConversation();
    }
    
    private void createMainLayout(Composite parent) {
        mainComposite = new Composite(parent, SWT.NONE);
        mainComposite.setLayout(new GridLayout(1, false));
        mainComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        
        GridLayout mainLayout = new GridLayout(1, false);
        mainLayout.marginWidth = 0;
        mainLayout.marginHeight = 0;
        mainLayout.verticalSpacing = 1;
        mainComposite.setLayout(mainLayout);
    }
    
    private void createHeader() {
        headerComposite = new Composite(mainComposite, SWT.NONE);
        headerComposite.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
        headerComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        
        GridLayout headerLayout = new GridLayout(5, false);
        headerLayout.marginHeight = 10;
        headerLayout.marginWidth = 15;
        headerLayout.horizontalSpacing = 10;
        headerComposite.setLayout(headerLayout);
        
        // 제목
        titleLabel = new Label(headerComposite, SWT.NONE);
        titleLabel.setText("🤖 Multi-Agent REACT");
        titleLabel.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
        titleLabel.setFont(new Font(Display.getDefault(), "Segoe UI", 12, SWT.BOLD));
        
        // 상태
        statusLabel = new Label(headerComposite, SWT.NONE);
        statusLabel.setText("Ready");
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        updateConnectionStatus();
        
        // 모델 선택
        modelCombo = new Combo(headerComposite, SWT.READ_ONLY);
        modelCombo.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        modelCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                handleModelSelection();
            }
        });
        loadAvailableModels();
        
        // MCP 버튼
        mcpButton = new Button(headerComposite, SWT.PUSH);
        mcpButton.setText("🔌 MCP");
        mcpButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                openMCPDialog();
            }
        });
        
        // 설정 버튼
        settingsButton = new Button(headerComposite, SWT.PUSH);
        settingsButton.setText("⚙️ Settings");
        settingsButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                openSettingsDialog();
            }
        });
    }
    
    private void createChatArea() {
        chatComposite = new Composite(mainComposite, SWT.NONE);
        chatComposite.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
        chatComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        
        GridLayout chatLayout = new GridLayout(1, false);
        chatLayout.marginWidth = 0;
        chatLayout.marginHeight = 0;
        chatComposite.setLayout(chatLayout);
        
        createChatToolbar();
        createScrolledChatArea();
        addWelcomeMessage();
    }
    
    private void createChatToolbar() {
        chatToolBar = new ToolBar(chatComposite, SWT.FLAT | SWT.RIGHT);
        chatToolBar.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
        chatToolBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        // 대화 관리 버튼들
        newConvItem = new ToolItem(chatToolBar, SWT.PUSH);
        newConvItem.setText("🆕");
        newConvItem.setToolTipText("새 대화 시작");
        newConvItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                startNewConversation();
            }
        });
        
        historyBtn = new ToolItem(chatToolBar, SWT.PUSH);
        historyBtn.setText("📋");
        historyBtn.setToolTipText("대화 이력");
        historyBtn.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                showConversationHistory();
            }
        });
        
        snippetBtn = new ToolItem(chatToolBar, SWT.PUSH);
        snippetBtn.setText("📌");
        snippetBtn.setToolTipText("코드 스니펫");
        snippetBtn.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                openSnippetDialog();
            }
        });
        
        new ToolItem(chatToolBar, SWT.SEPARATOR);
        
        attachCodeItem = new ToolItem(chatToolBar, SWT.PUSH);
        attachCodeItem.setText("📎 Code");
        attachCodeItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
            	attachCurrentSelection();
            }
        });
        
        exportChatItem = new ToolItem(chatToolBar, SWT.PUSH);
        exportChatItem.setText("💾 Export");
        exportChatItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                exportChatHistory();
            }
        });
        
        clearChatItem = new ToolItem(chatToolBar, SWT.PUSH);
        clearChatItem.setText("🗑️ Clear");
        clearChatItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                clearChatHistory();
            }
        });
    }
    
    private void createScrolledChatArea() {
        chatScrolled = new ScrolledComposite(chatComposite, SWT.V_SCROLL | SWT.H_SCROLL);
        chatScrolled.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
        chatScrolled.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        chatScrolled.setExpandHorizontal(true);
        chatScrolled.setExpandVertical(true);
        
        chatContent = new Composite(chatScrolled, SWT.NONE);
        chatContent.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
        
        GridLayout contentLayout = new GridLayout(1, false);
        contentLayout.marginWidth = 15;
        contentLayout.marginHeight = 15;
        contentLayout.verticalSpacing = 10;
        chatContent.setLayout(contentLayout);
        
        chatScrolled.setContent(chatContent);
        chatScrolled.setMinSize(chatContent.computeSize(SWT.DEFAULT, SWT.DEFAULT));
        
        chatScrolled.addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent e) {
                Rectangle r = chatScrolled.getClientArea();
                chatScrolled.setMinSize(chatContent.computeSize(r.width, SWT.DEFAULT));
            }
        });
    }
    
    private void createInputArea() {
        inputComposite = new Composite(mainComposite, SWT.NONE);
        inputComposite.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW));
        inputComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        
        GridLayout inputLayout = new GridLayout(1, false);
        inputLayout.marginHeight = 10;
        inputLayout.marginWidth = 15;
        inputLayout.verticalSpacing = 5;
        inputComposite.setLayout(inputLayout);
        
        createInputText();
        createCodeAttachCombo();
        createSendArea();
    }
    
    private void createInputText() {
        inputText = new StyledText(inputComposite, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
        inputText.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
        inputText.setFont(new Font(Display.getDefault(), "Segoe UI", 10, SWT.NORMAL));
        inputText.setWordWrap(true);
        
        GridData inputData = new GridData(SWT.FILL, SWT.FILL, true, true);
        inputData.heightHint = 60;
        inputText.setLayoutData(inputData);
        inputText.setMargins(10, 10, 10, 10);
        
        // 플레이스홀더 제거
        inputText.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (inputText.getText().equals("질문을 입력하세요...")) {
                    inputText.setText("");
                }
            }
        });
        
        inputText.addModifyListener(e -> updateCharacterCount());
        
        inputText.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if ((e.stateMask & SWT.CTRL) != 0 && e.keyCode == SWT.CR) {
                    e.doit = false;
                    sendMessage();
                }
            }
        });
    }
    
    private void createCodeAttachCombo() {
        Composite codeComposite = new Composite(inputComposite, SWT.NONE);
        codeComposite.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW));
        codeComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        GridLayout codeLayout = new GridLayout(2, false);
        codeLayout.marginWidth = 0;
        codeLayout.marginHeight = 5;
        codeLayout.horizontalSpacing = 5;
        codeComposite.setLayout(codeLayout);
        
        // 코드 첨부 상태 표시 영역
        Composite attachStatusComposite = new Composite(codeComposite, SWT.NONE);
        attachStatusComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        attachStatusComposite.setLayout(new GridLayout(3, false));
        
        Label attachIcon = new Label(attachStatusComposite, SWT.NONE);
        attachIcon.setText("📎");
        
        attachStatusLabel = new Label(attachStatusComposite, SWT.NONE);
        attachStatusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        attachStatusLabel.setText("코드 첨부 없음");
        attachStatusLabel.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_DARK_GRAY));
        
        // 첨부된 코드 제거 버튼 (초기에는 숨김)
        clearAttachButton = new Button(attachStatusComposite, SWT.PUSH);
        clearAttachButton.setText("✕");
        clearAttachButton.setToolTipText("첨부 제거");
        clearAttachButton.setVisible(false);
        clearAttachButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                clearAttachedCode();
            }
        });
        
        // 코드 첨부 버튼 그룹
        Composite buttonComposite = new Composite(codeComposite, SWT.NONE);
        buttonComposite.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        buttonComposite.setLayout(new GridLayout(4, false));
        
        // 빠른 첨부 버튼들
        Button attachSelectionBtn = new Button(buttonComposite, SWT.PUSH);
        attachSelectionBtn.setText("선택 영역");
        attachSelectionBtn.setToolTipText("현재 선택된 코드 첨부");
        attachSelectionBtn.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                attachCurrentSelection();
            }
        });
        
        Button attachFileBtn = new Button(buttonComposite, SWT.PUSH);
        attachFileBtn.setText("현재 파일");
        attachFileBtn.setToolTipText("현재 열린 파일 전체 첨부");
        attachFileBtn.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                attachCurrentFile();
            }
        });
        
        // 파일 선택 콤보박스
        codeAttachCombo = new Combo(buttonComposite, SWT.READ_ONLY);
        codeAttachCombo.setLayoutData(new GridData(150, SWT.DEFAULT));
        codeAttachCombo.setToolTipText("열린 파일 목록에서 선택");
        codeAttachCombo.add("파일 선택...");
        codeAttachCombo.select(0);
        codeAttachCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (codeAttachCombo.getSelectionIndex() > 0) {
                    String selected = codeAttachCombo.getText();
                    loadFileContent(selected);
                }
            }
        });
        
        Button refreshButton = new Button(buttonComposite, SWT.PUSH);
        refreshButton.setText("🔄");
        refreshButton.setToolTipText("파일 목록 새로고침");
        refreshButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                refreshCodeContexts();
            }
        });
        
        // 초기 파일 목록 로드
        Display.getDefault().asyncExec(() -> refreshCodeContexts());
    }
    
    // 첨부 상태 업데이트 메서드
    private void updateAttachmentStatus(String fileName, int length) {
        if (fileName != null && !fileName.isEmpty()) {
            attachStatusLabel.setText(String.format("%s (%s)", fileName, formatFileSize(length)));
            attachStatusLabel.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_DARK_GREEN));
            clearAttachButton.setVisible(true);
        } else {
            attachStatusLabel.setText("코드 첨부 없음");
            attachStatusLabel.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_DARK_GRAY));
            clearAttachButton.setVisible(false);
        }
        attachStatusLabel.getParent().layout();
    }
    
    private String formatFileSize(int length) {
        if (length < 1024) {
            return length + " 자";
        } else if (length < 1024 * 1024) {
            return String.format("%.1f KB", length / 1024.0);
        } else {
            return String.format("%.1f MB", length / (1024.0 * 1024));
        }
    }

    // 첨부된 코드 제거
    private void clearAttachedCode() {
        attachedCode = "";
        attachedFileName = "";
        updateAttachmentStatus(null, 0);
        addMessage("📎 첨부된 코드가 제거되었습니다.", false);
    }
    
    // 현재 선택 영역 첨부
    private void attachCurrentSelection() {
        String selection = contextCollector.getCurrentCodeContext();
        if (!selection.isEmpty()) {
            attachedCode = selection;
            attachedFileName = "선택된 코드";
            updateAttachmentStatus(attachedFileName, selection.length());
            
            // 코드 미리보기 표시
            String preview = selection.length() > 100 ? 
                selection.substring(0, 100) + "..." : selection;
            addMessage("📎 코드가 첨부되었습니다:\n```\n" + preview + "\n```", false);
        } else {
            MessageDialog.openInformation(getShell(), 
                "코드 선택 필요", 
                "에디터에서 첨부할 코드를 먼저 선택해주세요.");
        }
    }
    
    // 현재 파일 전체 첨부
    private void attachCurrentFile() {
        String fileName = contextCollector.getCurrentFileName();
        if (!fileName.isEmpty()) {
            String content = contextCollector.getCurrentFileContent();
            if (!content.isEmpty()) {
                // 파일 크기 제한 확인
                if (content.length() > 100000) { // 100KB 제한
                    boolean confirm = MessageDialog.openConfirm(getShell(),
                        "큰 파일 첨부",
                        String.format("파일 크기가 큽니다 (%s). 계속하시겠습니까?", 
                            formatFileSize(content.length())));
                    if (!confirm) return;
                }
                
                attachedCode = content;
                attachedFileName = fileName;
                updateAttachmentStatus(fileName, content.length());
                addMessage("📎 파일이 첨부되었습니다: " + fileName, false);
            } else {
                MessageDialog.openError(getShell(), 
                    "파일 읽기 실패", 
                    "파일 내용을 읽을 수 없습니다.");
            }
        } else {
            MessageDialog.openInformation(getShell(), 
                "파일 없음", 
                "현재 열려있는 파일이 없습니다.");
        }
    }

    // 특정 파일 내용 로드
    private void loadFileContent(String fileName) {
        Job job = new Job("Loading file: " + fileName) {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    String content = contextCollector.getFileContent(fileName);
                    
                    Display.getDefault().asyncExec(() -> {
                        if (!content.isEmpty()) {
                            attachedCode = content;
                            attachedFileName = fileName;
                            updateAttachmentStatus(fileName, content.length());
                            addMessage("📎 파일이 첨부되었습니다: " + fileName, false);
                        } else {
                            addMessage("❌ 파일 내용을 읽을 수 없습니다: " + fileName, false);
                        }
                    });
                    
                    return Status.OK_STATUS;
                    
                } catch (Exception e) {
                    Display.getDefault().asyncExec(() -> {
                        addMessage("❌ 파일 로드 실패: " + e.getMessage(), false);
                    });
                    return Status.error("Failed to load file", e);
                }
            }
        };
        
        job.setUser(false);
        job.schedule();
    }
    
    private void createSendArea() {
        Composite sendComposite = new Composite(inputComposite, SWT.NONE);
        sendComposite.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW));
        sendComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        GridLayout sendLayout = new GridLayout(3, false);
        sendLayout.marginWidth = 0;
        sendLayout.marginHeight = 0;
        sendLayout.horizontalSpacing = 10;
        sendComposite.setLayout(sendLayout);
        
        characterCountLabel = new Label(sendComposite, SWT.NONE);
        characterCountLabel.setText("0 characters");
        characterCountLabel.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW));
        characterCountLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        Button clearInputButton = new Button(sendComposite, SWT.PUSH);
        clearInputButton.setText("Clear");
        clearInputButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                inputText.setText("");
                inputText.setFocus();
            }
        });
        
        sendButton = new Button(sendComposite, SWT.PUSH);
        sendButton.setText("전송 (Ctrl+Enter)");
        sendButton.setEnabled(true);
        sendButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                sendMessage();
            }
        });
    }
    
 // ChatView.java의 sendMessage 메서드 수정

    private void sendMessage() {
        String message = inputText.getText().trim();
        if (message.isEmpty() || message.equals("질문을 입력하세요...") || isProcessing) {
            return;
        }

        if (!preferenceManager.hasValidAPIKey()) {
            addMessage("❌ API 키를 설정해주세요. Settings에서 설정할 수 있습니다.", false);
            openSettingsDialog();
            return;
        }

        setProcessingState(true);
        
        // 입력 텍스트를 즉시 저장하고 초기화
        final String userMessage = message;
        
        // UI 스레드에서 즉시 입력창 초기화
        Display.getDefault().syncExec(() -> {
            inputText.setText("");
            inputText.setFocus();
        });

        // 사용자 메시지 추가
        addMessage("👤 " + userMessage, true);
        conversationManager.addMessage(currentSessionId, userMessage, true);

        String selectedModel = getSelectedModelId();
        String context = getCurrentContext();
        
        // 비동기 처리
        if (shouldUseMCPTool(userMessage)) {
            executeMCPToolAsync(userMessage, context, selectedModel);
        } else {
            executeGeneralRequestAsync(userMessage, context, selectedModel);
        }
    }

    // MCP 도구 비동기 실행
    private void executeMCPToolAsync(String message, String context, String modelId) {
        Job job = new Job("MCP Tool Execution") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    monitor.beginTask("MCP 도구 실행 중...", IProgressMonitor.UNKNOWN);
                    
                    String mcpContext = "MCP Tool Request: " + message;
                    if (!attachedCode.isEmpty()) {
                        mcpContext += "\n\nAttached Code:\n" + attachedCode;
                    }
                    
                    // AgentOrchestrator를 통해 처리
                    String response = agentOrchestrator.processComplexRequest(
                        message, mcpContext, modelId);
                    
                    // UI 업데이트
                    Display.getDefault().asyncExec(() -> {
                        if (!chatContent.isDisposed()) {
                            addMessage("🔌 " + response, false);
                            conversationManager.addMessage(currentSessionId, response, false);
                            setProcessingState(false);
                            
                            // 스크롤
                            if (preferenceManager.isAutoScrollEnabled()) {
                                scrollToBottom();
                            }
                        }
                    });
                    
                    return Status.OK_STATUS;
                    
                } catch (Exception e) {
                    Display.getDefault().asyncExec(() -> {
                        if (!chatContent.isDisposed()) {
                            String errorMessage = "❌ MCP 도구 실행 실패: " + e.getMessage();
                            addMessage(errorMessage, false);
                            setProcessingState(false);
                        }
                    });
                    
                    CopilotLogger.error("MCP tool execution failed", e);
                    return Status.error("MCP 도구 실행 실패", e);
                }
            }
        };
        
        job.setUser(false);
        job.schedule();
    }

    // 일반 요청 비동기 실행
    private void executeGeneralRequestAsync(String message, String context, String modelId) {
        agentOrchestrator.processComplexRequestAsync(message, context, modelId,
            response -> {
                Display.getDefault().asyncExec(() -> {
                    if (chatContent.isDisposed()) return;
                    
                    addMessage("🤖 " + response, false);
                    conversationManager.addMessage(currentSessionId, response, false);
                    setProcessingState(false);
                    
                    // 스크롤
                    if (preferenceManager.isAutoScrollEnabled()) {
                        scrollToBottom();
                    }
                });
            },
            error -> {
                Display.getDefault().asyncExec(() -> {
                    if (chatContent.isDisposed()) return;
                    
                    String errorMessage = "❌ 오류: " + error.getMessage();
                    addMessage(errorMessage, false);
                    setProcessingState(false);
                    CopilotLogger.error("Message processing failed", error);
                });
            }
        );
    }

    // 처리 상태 설정 개선
    private void setProcessingState(boolean processing) {
        Display.getDefault().asyncExec(() -> {
            if (!isDisposed()) {
                isProcessing = processing;
                sendButton.setEnabled(!processing);
                inputText.setEditable(!processing);
                
                if (processing) {
                    statusLabel.setText("Processing...");
                    sendButton.setText("처리 중...");
                } else {
                    statusLabel.setText("Ready");
                    sendButton.setText("전송 (Ctrl+Enter)");
                    
                    // 포커스를 입력창으로 되돌리기
                    inputText.setFocus();
                }
            }
        });
    }

    // isDisposed 헬퍼 메서드 추가
    private boolean isDisposed() {
        return mainComposite == null || mainComposite.isDisposed() ||
               inputText == null || inputText.isDisposed() ||
               sendButton == null || sendButton.isDisposed() ||
               statusLabel == null || statusLabel.isDisposed();
    }
 // ChatView.java에 추가할 메서드

    /**
     * MCP 연결 테스트 메서드
     * 이 메서드를 ChatView 클래스에 추가하고, 
     * 적절한 위치(예: MCP 버튼 클릭 이벤트)에서 호출하세요
     */
    private void testMCPConnection() {
        try {
            McpServerManager manager = McpServerManager.getInstance();
            McpServerManager.McpStatus status = manager.getStatus();
            
            addMessage("🔍 MCP 연결 테스트 시작...", false);
            
            // 연결 상태 확인
            addMessage(String.format("📊 연결된 서버: %d개, 총 도구: %d개", 
                status.getConnectedServers(), status.getTotalTools()), false);
            
            if (status.getConnectedServers() > 0) {
                // 연결된 도구 목록 표시
                Map<String, List<McpServerManager.McpTool>> tools = manager.getConnectedTools();
                for (Map.Entry<String, List<McpServerManager.McpTool>> entry : tools.entrySet()) {
                    String serverName = entry.getKey();
                    List<McpServerManager.McpTool> serverTools = entry.getValue();
                    
                    addMessage(String.format("🔌 서버 [%s]: %d개 도구", serverName, serverTools.size()), false);
                    
                    // 첫 번째 도구로 테스트
                    if (!serverTools.isEmpty()) {
                        McpServerManager.McpTool firstTool = serverTools.get(0);
                        addMessage("🛠️ 도구 테스트: " + firstTool.getName(), false);
                        
                        try {
                            // 테스트 파라미터 준비
                            Map<String, Object> testParams = new HashMap<>();
                            
                            // 도구별 테스트 파라미터 설정
                            if (firstTool.getName().equalsIgnoreCase("GetProgram")) {
                                testParams.put("program_name", "RSABAPPROGRAM");
                            } else if (firstTool.getName().equalsIgnoreCase("SearchPrograms")) {
                                testParams.put("search_pattern", "*TEST*");
                                testParams.put("max_results", 5);
                            }
                            // 다른 도구들에 대한 테스트 파라미터 추가...
                            
                            // 도구 실행
                            String result = manager.executeTool(firstTool.getName(), 
                                testParams, "MCP Connection Test");
                                
                            addMessage("✅ 도구 실행 성공:\n" + result, false);
                            
                        } catch (Exception e) {
                            addMessage("❌ 도구 실행 실패: " + e.getMessage(), false);
                            CopilotLogger.error("MCP tool execution failed", e);
                        }
                    }
                }
            } else {
                addMessage("⚠️ 연결된 MCP 서버가 없습니다. MCP Manager에서 서버를 추가해주세요.", false);
            }
            
        } catch (Exception e) {
            addMessage("❌ MCP 테스트 중 오류 발생: " + e.getMessage(), false);
            CopilotLogger.error("MCP connection test failed", e);
        }
    }

    /**
     * MCP 버튼 클릭 이벤트 핸들러 수정
     * 기존의 openMCPDialog() 호출 부분을 다음과 같이 수정할 수 있습니다
     */
    private void createMCPButton() {
        mcpButton = new Button(headerComposite, SWT.PUSH);
        mcpButton.setText("🔌 MCP");
        mcpButton.setToolTipText("MCP Manager - Shift+클릭으로 연결 테스트");
        mcpButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                // Shift 키를 누르고 클릭하면 테스트 실행
                if ((e.stateMask & SWT.SHIFT) != 0) {
                    testMCPConnection();
                } else {
                    openMCPDialog();
                }
            }
        });
    }
    // MCP 도구 사용 여부 결정 - MCP가 설정되어 있고 명시적 요청인 경우만
    private boolean shouldUseMCPTool(String message) {
        // MCP 서버가 연결되어 있는지 확인
        McpServerManager.McpStatus status = McpServerManager.getInstance().getStatus();
        if (status.getConnectedServers() == 0) {
            return false;
        }
        
        String lower = message.toLowerCase();
        
        // 1. 연결된 도구들의 키워드 검사
        Map<String, List<McpServerManager.McpTool>> connectedTools = 
            McpServerManager.getInstance().getConnectedTools();
        
        for (List<McpServerManager.McpTool> tools : connectedTools.values()) {
            for (McpServerManager.McpTool tool : tools) {
                String toolName = tool.getName().toLowerCase();
                // 도구 이름이 메시지에 관련되어 있는지 확인
                if (isToolRelatedToMessage(toolName, lower)) {
                    CopilotLogger.info("MCP tool detected: " + tool.getName());
                    return true;
                }
            }
        }
        
        // 2. 일반적인 MCP 작업 패턴 감지
        // 파일 작업
        boolean fileOperation = 
            (lower.contains("파일") || lower.contains("file")) &&
            (lower.contains("읽") || lower.contains("read") ||
             lower.contains("쓰") || lower.contains("write") ||
             lower.contains("목록") || lower.contains("list") ||
             lower.contains("검색") || lower.contains("search") ||
             lower.contains("생성") || lower.contains("create") ||
             lower.contains("삭제") || lower.contains("delete"));
        
        // 디렉토리 작업
        boolean directoryOperation = 
            (lower.contains("디렉토리") || lower.contains("directory") || 
             lower.contains("폴더") || lower.contains("folder")) &&
            (lower.contains("보") || lower.contains("show") ||
             lower.contains("목록") || lower.contains("list") ||
             lower.contains("내용") || lower.contains("content"));
        
        // Git 작업
        boolean gitOperation = 
            lower.contains("git") || lower.contains("깃") ||
            lower.contains("커밋") || lower.contains("commit") ||
            lower.contains("브랜치") || lower.contains("branch") ||
            lower.contains("상태") || lower.contains("status");
        
        // 데이터베이스 작업
        boolean dbOperation = 
            (lower.contains("쿼리") || lower.contains("query") ||
             lower.contains("테이블") || lower.contains("table") ||
             lower.contains("데이터베이스") || lower.contains("database")) &&
            (lower.contains("실행") || lower.contains("execute") ||
             lower.contains("조회") || lower.contains("select") ||
             lower.contains("목록") || lower.contains("list"));
        
        // 명시적인 MCP/도구 언급
        boolean explicitMCP = 
            lower.contains("mcp") || 
            lower.contains("도구") && (lower.contains("사용") || lower.contains("실행"));
        
        boolean shouldUse = fileOperation || directoryOperation || gitOperation || 
                           dbOperation || explicitMCP;
        
        if (shouldUse) {
            CopilotLogger.info("MCP tool usage detected for message: " + message);
        }
        
        return shouldUse;
    }
    
 // 도구 이름과 메시지의 연관성 검사
    private boolean isToolRelatedToMessage(String toolName, String message) {
        // 도구 이름의 키워드 추출
        String[] toolKeywords = toolName.split("_");
        
        for (String keyword : toolKeywords) {
            if (keyword.length() > 2 && message.contains(keyword)) {
                return true;
            }
        }
        
        // 특정 도구별 키워드 매핑
        Map<String, String[]> toolKeywordMap = new HashMap<>();
        toolKeywordMap.put("read_file", new String[]{"읽", "read", "파일", "file", "내용", "content"});
        toolKeywordMap.put("write_file", new String[]{"쓰", "write", "저장", "save", "파일", "file"});
        toolKeywordMap.put("list_directory", new String[]{"목록", "list", "디렉토리", "directory", "폴더", "folder"});
        toolKeywordMap.put("search_files", new String[]{"검색", "search", "찾", "find", "파일", "file"});
        toolKeywordMap.put("git_status", new String[]{"git", "깃", "상태", "status"});
        toolKeywordMap.put("git_log", new String[]{"git", "깃", "로그", "log", "이력", "history"});
        toolKeywordMap.put("execute_query", new String[]{"쿼리", "query", "실행", "execute", "sql"});
        
        String[] keywords = toolKeywordMap.get(toolName);
        if (keywords != null) {
            int matchCount = 0;
            for (String keyword : keywords) {
                if (message.contains(keyword)) {
                    matchCount++;
                }
            }
            // 2개 이상의 키워드가 매칭되면 관련 있다고 판단
            return matchCount >= 2;
        }
        
        return false;
    }
    
    // MCP 도구 실행
    private void executeMCPTool(String message, String modelId) {
        String mcpContext = "MCP Tool Request: " + message;
        if (!attachedCode.isEmpty()) {
            mcpContext += "\n\nAttached Code:\n" + attachedCode;
        }
        
        agentOrchestrator.processComplexRequestAsync(message, mcpContext, modelId,
            response -> {
                Display.getDefault().asyncExec(() -> {
                    if (chatContent.isDisposed()) return;
                    
                    // MCP 도구 실행 결과 표시
                    addMessage("🔌 MCP 도구 실행 결과:\n" + response, false);
                    conversationManager.addMessage(currentSessionId, response, false);
                    setProcessingState(false);
                    inputText.setText("");
                    clearAttachedCode();
                });
            },
            error -> {
                Display.getDefault().asyncExec(() -> {
                    if (chatContent.isDisposed()) return;
                    String errorMessage = "❌ MCP 도구 실행 실패: " + error.getMessage();
                    addMessage(errorMessage, false);
                    setProcessingState(false);
                    CopilotLogger.error("MCP tool execution failed", error);
                });
            }
        );
    }
    
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.equals("선택된 코드")) return "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }
    
    private void addMessage(String content, boolean isUser) {
        MessageBubble bubble = new MessageBubble(chatContent, SWT.NONE);
        bubble.setMessage(content, isUser);
        bubble.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        
        // 타임스탬프 표시
        if (preferenceManager.isShowTimestamps()) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            bubble.setTimestamp(sdf.format(new Date()));
        }
        
        chatContent.layout();
        chatScrolled.setMinSize(chatContent.computeSize(
            chatScrolled.getClientArea().width, SWT.DEFAULT));
        
        if (preferenceManager.isAutoScrollEnabled()) {
            scrollToBottom();
        }
    }
    
    private void addWelcomeMessage() {
        String welcomeMsg = """
            🎯 **Multi-Agent REACT AI Assistant**
            
            환영합니다! 저는 여러 전문 에이전트가 협력하는 AI 어시스턴트입니다.
            
            **🤖 사용 가능한 에이전트:**
            • **CodingAgent** - 코드 작성, 리뷰, 디버깅
            • **McpAgent** - 외부 도구 연동 (파일, Git, DB 등)
            • **GeneralAgent** - 일반 질문 및 대화
            
            **🔌 MCP 도구 예시:**
            • "현재 디렉토리의 파일 목록을 보여줘"
            • "main.java 파일을 읽어줘"
            • "Git 상태를 확인해줘"
            
            **⌨️ 단축키:**
            • `Ctrl + Enter` - 메시지 전송
            • `Ctrl + Alt + F` - 채팅 창 열기
            
            **💡 팁:** 코드 선택 후 📎 버튼으로 첨부 가능
            """;
        
        addMessage(welcomeMsg, false);
        
        // API 키 상태 확인
        if (!preferenceManager.hasValidAPIKey()) {
            addMessage("⚠️ API 키가 설정되지 않았습니다. Settings에서 설정해주세요.", false);
        }
        
        // MCP 상태 표시
        McpServerManager.McpStatus mcpStatus = McpServerManager.getInstance().getStatus();
        if (mcpStatus.getTotalServers() > 0) {
            addMessage(String.format("🔌 MCP 서버 상태: %d/%d 연결됨", 
                mcpStatus.getConnectedServers(), mcpStatus.getTotalServers()), false);
        }
    }
    
    private void scrollToBottom() {
        Display.getDefault().asyncExec(() -> {
            if (!chatScrolled.isDisposed()) {
                Point origin = chatScrolled.getOrigin();
                Point size = chatContent.getSize();
                chatScrolled.setOrigin(origin.x, size.y);
            }
        });
    }
    
    private void updateCharacterCount() {
        if (!characterCountLabel.isDisposed() && !inputText.isDisposed()) {
            int count = inputText.getText().length();
            characterCountLabel.setText(count + " characters");
            
            // 글자 수 제한 경고
            if (count > 4000) {
                characterCountLabel.setForeground(
                    Display.getDefault().getSystemColor(SWT.COLOR_RED));
            } else {
                characterCountLabel.setForeground(
                    Display.getDefault().getSystemColor(SWT.COLOR_WIDGET_FOREGROUND));
            }
        }
    }
    
    private void openSettingsDialog() {
        SettingsDialog dialog = new SettingsDialog(getShell());
        if (dialog.open() == Window.OK) {
            loadInitialData();
            loadAvailableModels();
            addMessage("✅ 설정이 저장되었습니다.", false);
            CopilotLogger.info("Settings updated");
        }
    }
    
    private void openMCPDialog() {
        MCPManagerDialog dialog = new MCPManagerDialog(getShell());
        dialog.open();
        
        // MCP 다이얼로그 닫은 후 상태 업데이트
        updateConnectionStatus();
    }
    
    private void openSnippetDialog() {
        SnippetDialog dialog = new SnippetDialog(getShell(), snippetManager);
        if (dialog.open() == Window.OK) {
            String selectedSnippet = dialog.getSelectedSnippet();
            if (selectedSnippet != null) {
                inputText.insert(selectedSnippet);
            }
        }
    }
    
    private void loadAvailableModels() {
        modelCombo.setEnabled(false);
        modelCombo.removeAll();
        modelCombo.add("Loading models...");
        modelCombo.select(0);
        
        llmClient.getAvailableModelsAsync(
            models -> {
                Display.getDefault().asyncExec(() -> {
                    if (!modelCombo.isDisposed()) {
                        modelCombo.removeAll();
                        modelMap.clear();
                        
                        for (ModelInfo model : models) {
                            String displayName = model.isFabriX() ? 
                                "🏭 " + model.getModelLabel() : 
                                "🤖 " + model.getModelLabel();
                            modelCombo.add(displayName);
                            modelMap.put(displayName, model);
                        }
                        
                        if (modelCombo.getItemCount() > 0) {
                            // 기본 모델 선택
                            String defaultModel = preferenceManager.getSelectedModel();
                            int index = 0;
                            for (int i = 0; i < modelCombo.getItemCount(); i++) {
                                ModelInfo model = modelMap.get(modelCombo.getItem(i));
                                if (model != null && model.getModelId().equals(defaultModel)) {
                                    index = i;
                                    break;
                                }
                            }
                            modelCombo.select(index);
                            modelCombo.setEnabled(true);
                        } else {
                            modelCombo.add("No models available");
                            modelCombo.select(0);
                        }
                    }
                });
            },
            error -> {
                Display.getDefault().asyncExec(() -> {
                    if (!modelCombo.isDisposed()) {
                        modelCombo.removeAll();
                        modelCombo.add("Error loading models");
                        modelCombo.select(0);
                        CopilotLogger.error("Failed to load models", error);
                    }
                });
            }
        );
    }
    
    private void handleModelSelection() {
        String selected = modelCombo.getText();
        ModelInfo model = modelMap.get(selected);
        if (model != null) {
            preferenceManager.setSelectedModel(model.getModelId());
            addMessage("✅ 모델 변경: " + model.getModelLabel(), false);
            CopilotLogger.info("Model changed to: " + model.getModelId());
        }
    }
    
    private String getSelectedModelId() {
        String selected = modelCombo.getText();
        ModelInfo model = modelMap.get(selected);
        
        if (model != null) {
            return model.getModelId();
        }
        
        // 모델을 찾을 수 없으면 FabriX 키가 있는 경우 Gemma3 사용
        if (preferenceManager.hasFabriXKeys()) {
            CopilotLogger.info("모델 선택 실패, Gemma3(116) 사용");
            return "116";
        }
        
        return preferenceManager.getSelectedModel();
    }
    
    private void attachCurrentCode() {
           // 현재 선택 영역을 첨부합니다.
           attachCurrentSelection();
    }
    
    private void refreshCodeContexts() {
        codeAttachCombo.setEnabled(false);
        
        Job job = new Job("파일 목록 로드 중...") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    List<String> openFiles = contextCollector.getOpenFileNames();
                    
                    Display.getDefault().asyncExec(() -> {
                        if (codeAttachCombo.isDisposed()) return;
                        
                        codeAttachCombo.removeAll();
                        codeAttachCombo.add("파일 선택...");
                        
                        if (!openFiles.isEmpty()) {
                            for (String file : openFiles) {
                                codeAttachCombo.add(file);
                            }
                        }
                        
                        codeAttachCombo.select(0);
                        codeAttachCombo.setEnabled(true);
                    });
                    
                    return Status.OK_STATUS;
                } catch (Exception e) {
                    Display.getDefault().asyncExec(() -> {
                        codeAttachCombo.setEnabled(true);
                    });
                    return Status.error("파일 목록 로드 실패", e);
                }
            }
        };
        
        job.setUser(false);
        job.schedule();
    }
    
    private void exportChatHistory() {
        if (conversationManager.getConversationHistory(currentSessionId).isEmpty()) {
            addMessage("❌ 내보낼 대화가 없습니다.", false);
            return;
        }
        
        FileDialog dialog = new FileDialog(getShell(), SWT.SAVE);
        dialog.setFilterExtensions(new String[]{"*.md", "*.txt", "*.json", "*.html"});
        dialog.setFilterNames(new String[]{"Markdown", "Plain Text", "JSON", "HTML"});
        dialog.setFileName("chat-export-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()));
        
        String path = dialog.open();
        if (path != null) {
            try {
                List<ConversationManager.Message> messages = 
                    conversationManager.getConversationHistory(currentSessionId);
                
                ChatExporter.ExportFormat format = determineFormat(path);
                String content = formatMessages(messages, format);
                
                Files.write(Paths.get(path), content.getBytes());
                addMessage("✅ 대화가 내보내졌습니다: " + path, false);
                CopilotLogger.info("Chat exported to: " + path);
                
            } catch (IOException e) {
                addMessage("❌ 내보내기 실패: " + e.getMessage(), false);
                CopilotLogger.error("Export failed", e);
            }
        }
    }
    
    private ChatExporter.ExportFormat determineFormat(String path) {
        if (path.endsWith(".md")) return ChatExporter.ExportFormat.MARKDOWN;
        if (path.endsWith(".json")) return ChatExporter.ExportFormat.JSON;
        if (path.endsWith(".html")) return ChatExporter.ExportFormat.HTML;
        return ChatExporter.ExportFormat.PLAIN_TEXT;
    }
    
    private String formatMessages(List<ConversationManager.Message> messages, ChatExporter.ExportFormat format) {
        ChatExporter exporter = new ChatExporter();
        return exporter.export(messages, format);
    }
    
    private void startNewConversation() {
        if (!conversationManager.getConversationHistory(currentSessionId).isEmpty()) {
            boolean confirm = MessageDialog.openConfirm(getShell(), 
                "새 대화", "현재 대화를 저장하고 새 대화를 시작하시겠습니까?");
            if (!confirm) return;
        }
        
        currentSessionId = conversationManager.startNewConversation();
        clearChat();
        addWelcomeMessage();
        CopilotLogger.info("New conversation started: " + currentSessionId);
    }
    
    private void showConversationHistory() {
        ConversationHistoryDialog dialog = new ConversationHistoryDialog(
            getShell(), conversationManager);
        
        if (dialog.open() == Window.OK) {
            String selectedSessionId = dialog.getSelectedSessionId();
            if (selectedSessionId != null && !selectedSessionId.equals(currentSessionId)) {
                currentSessionId = selectedSessionId;
                loadConversation(selectedSessionId);
            }
        }
    }
    
    private void loadConversation(String sessionId) {
        clearChat();
        List<ConversationManager.Message> messages = 
            conversationManager.getConversationHistory(sessionId);
        
        for (ConversationManager.Message msg : messages) {
            addMessage(msg.content, msg.isUser);
        }
        
        addMessage("📂 이전 대화를 불러왔습니다.", false);
    }
    
    private void clearChat() {
        for (Control child : chatContent.getChildren()) {
            child.dispose();
        }
        chatContent.layout();
        chatScrolled.setMinSize(chatContent.computeSize(SWT.DEFAULT, SWT.DEFAULT));
    }
    
    private void clearChatHistory() {
        boolean confirm = MessageDialog.openConfirm(getShell(), 
            "대화 삭제", "현재 대화를 삭제하시겠습니까?");
        if (confirm) {
            conversationManager.clearConversation(currentSessionId);
            clearChat();
            addMessage("🗑️ 대화가 삭제되었습니다.", false);
            CopilotLogger.info("Conversation cleared: " + currentSessionId);
        }
    }
    
    private String getCurrentContext() {
        StringBuilder context = new StringBuilder();
        
        // 프로젝트 컨텍스트
        context.append("프로젝트: ").append(contextCollector.getProjectContext()).append("\n");
        
        // 현재 파일
        String currentFile = contextCollector.getCurrentFileName();
        if (!currentFile.isEmpty()) {
            context.append("현재 파일: ").append(currentFile).append("\n");
        }
        
        // 선택된 코드
        String selectedCode = contextCollector.getCurrentCodeContext();
        if (!selectedCode.isEmpty() && selectedCode.length() < 500) {
            context.append("선택된 코드:\n```\n").append(selectedCode).append("\n```\n");
        }
        
        // 첨부된 파일의 코드 (Copilot처럼 컨텍스트로 전달)
        if (!attachedCode.isEmpty()) {
            String language = getFileExtension(attachedFileName);
            context.append("\n첨부된 파일: ").append(attachedFileName).append("\n");
            context.append("```").append(language).append("\n");
            context.append(attachedCode);
            context.append("\n```\n");
        }
        
        // MCP 도구 가용성 (연결된 경우만)
        McpServerManager mcpManager = McpServerManager.getInstance();
        McpServerManager.McpStatus status = mcpManager.getStatus();
        if (status.getConnectedServers() > 0) {
            context.append("\nMCP 도구 사용 가능: ").append(status.getTotalTools()).append("개\n");
            
            // 사용 가능한 도구 목록
            Map<String, List<McpServerManager.McpTool>> tools = mcpManager.getConnectedTools();
            if (!tools.isEmpty()) {
                context.append("도구 목록: ");
                tools.values().stream()
                    .flatMap(List::stream)
                    .map(McpServerManager.McpTool::getName)
                    .distinct()
                    .forEach(toolName -> context.append(toolName).append(", "));
                context.append("\n");
            }
        }
        
        return context.toString();
    }
    
    private void updateConnectionStatus() {
        Display.getDefault().timerExec(5000, () -> {
            if (!statusLabel.isDisposed()) {
                boolean hasApi = preferenceManager.hasValidAPIKey();
                McpServerManager.McpStatus mcpStatus = McpServerManager.getInstance().getStatus();
                
                String status = String.format("API: %s | MCP: %d/%d servers, %d tools", 
                    hasApi ? "✅" : "❌",
                    mcpStatus.getConnectedServers(),
                    mcpStatus.getTotalServers(),
                    mcpStatus.getTotalTools());
                
                statusLabel.setText(status);
                updateConnectionStatus(); // 재귀 호출
            }
        });
    }
    
    private void loadInitialData() {
        if (!PreferenceManager.getInstance().hasValidAPIKey()) {
            addMessage("⚠️ API 키가 설정되지 않았습니다. Settings에서 설정해주세요.", false);
        } else {
            String apiType = preferenceManager.hasOpenAIKey() ? "OpenAI" : "FabriX";
            addMessage("✅ " + apiType + " API 키가 설정되어 있습니다.", false);
        }
        
        // MCP 서버 초기화
        initializeMCPServers();
        
        // 초기 파일 목록 로드
        Display.getDefault().asyncExec(() -> {
            refreshCodeContexts();
        });
    }
    
    private void initializeMCPServers() {
        McpServerManager manager = McpServerManager.getInstance();
        
        // 로컬 설정 로드
        manager.loadLocalMCPConfig();
        
        // NPX 확인 및 안내
        if (!checkNPXAvailability()) {
            addMessage("⚠️ npx를 찾을 수 없습니다. Node.js가 설치되어 있는지 확인하세요.", false);
            addMessage("💡 npx는 npm 5.2.0 이상에 포함되어 있습니다. 다음 명령으로 확인하세요:", false);
            addMessage("```\nnpm --version\nnpx --version\n```", false);
            return;
        }
        
        // 개발/테스트용 기본 MCP 서버 추가
        if (!preferenceManager.getBooleanValue("mcp.skip_default_servers", false)) {
            try {
                // 다양한 방법으로 MCP 서버 시작 시도
                boolean connected = false;
                
                // 방법 1: npx로 직접 실행
                if (!connected) {
                    connected = tryNPXConnection(manager);
                }
                
                // 방법 2: 글로벌 설치된 경우
                if (!connected) {
                    connected = tryGlobalInstallation(manager);
                }
                
                // 방법 3: 로컬 node_modules
                if (!connected) {
                    connected = tryLocalInstallation(manager);
                }
                
                if (!connected) {
                    addMessage("⚠️ MCP 서버 연결 실패. 다음 명령으로 설치해보세요:", false);
                    addMessage("```\nnpm install -g @modelcontextprotocol/server-filesystem\n```", false);
                }
                
            } catch (Exception e) {
                CopilotLogger.warn("Failed to setup default MCP servers: " + e.getMessage());
            }
        }
        
        // 상태 표시
        McpServerManager.McpStatus status = manager.getStatus();
        if (status.getTotalServers() > 0 && status.getConnectedServers() > 0) {
            addMessage(String.format("🔌 MCP: %d개 서버 중 %d개 연결됨 (%d개 도구 사용 가능)", 
                status.getTotalServers(), 
                status.getConnectedServers(),
                status.getTotalTools()), false);
            
            // 사용 가능한 도구 목록 표시
            if (status.getTotalTools() > 0) {
                Map<String, List<McpServerManager.McpTool>> tools = manager.getConnectedTools();
                StringBuilder toolsMsg = new StringBuilder("📋 사용 가능한 도구:\n");
                for (Map.Entry<String, List<McpServerManager.McpTool>> entry : tools.entrySet()) {
                    toolsMsg.append("• ").append(entry.getKey()).append(": ");
                    toolsMsg.append(entry.getValue().stream()
                        .map(McpServerManager.McpTool::getName)
                        .collect(Collectors.joining(", ")));
                    toolsMsg.append("\n");
                }
                addMessage(toolsMsg.toString(), false);
            }
        }
    }
    
    private boolean checkNPXAvailability() {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            String os = System.getProperty("os.name").toLowerCase();
            
            if (os.contains("win")) {
                pb.command("cmd", "/c", "npx", "--version");
            } else {
                pb.command("sh", "-c", "npx --version");
            }
            
            Process process = pb.start();
            boolean success = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            
            if (success && process.exitValue() == 0) {
                CopilotLogger.info("npx is available");
                return true;
            }
        } catch (Exception e) {
            CopilotLogger.warn("npx check failed: " + e.getMessage());
        }
        return false;
    }
    
    private boolean tryNPXConnection(McpServerManager manager) {
        try {
            com.fabrix.copilot.mcp.McpServerConfig fsConfig = new com.fabrix.copilot.mcp.McpServerConfig(
                "filesystem-mcp-npx",
                "stdio",
                "npx",
                Arrays.asList("--yes", "@modelcontextprotocol/server-filesystem", System.getProperty("user.home")),
                new HashMap<>(),
                1
            );
            
            if (manager.addServer(fsConfig)) {
                addMessage("🔌 파일시스템 MCP 서버가 npx로 연결되었습니다.", false);
                return true;
            }
        } catch (Exception e) {
            CopilotLogger.warn("NPX connection failed: " + e.getMessage());
        }
        return false;
    }
    
    private boolean tryGlobalInstallation(McpServerManager manager) {
        try {
            // npm 글로벌 경로 찾기
            String npmPrefix = getNPMPrefix();
            if (npmPrefix != null) {
                String serverPath = npmPrefix + "/lib/node_modules/@modelcontextprotocol/server-filesystem/dist/index.js";
                java.io.File file = new java.io.File(serverPath);
                
                if (file.exists()) {
                    com.fabrix.copilot.mcp.McpServerConfig fsConfig = new com.fabrix.copilot.mcp.McpServerConfig(
                        "filesystem-mcp-global",
                        "stdio",
                        "node",
                        Arrays.asList(serverPath, System.getProperty("user.home")),
                        new HashMap<>(),
                        1
                    );
                    
                    if (manager.addServer(fsConfig)) {
                        addMessage("🔌 파일시스템 MCP 서버가 글로벌 설치에서 연결되었습니다.", false);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            CopilotLogger.warn("Global installation check failed: " + e.getMessage());
        }
        return false;
    }
    
    private boolean tryLocalInstallation(McpServerManager manager) {
        try {
            String[] possiblePaths = {
                "./node_modules/@modelcontextprotocol/server-filesystem/dist/index.js",
                "../node_modules/@modelcontextprotocol/server-filesystem/dist/index.js",
                System.getProperty("user.home") + "/node_modules/@modelcontextprotocol/server-filesystem/dist/index.js"
            };
            
            for (String path : possiblePaths) {
                java.io.File file = new java.io.File(path);
                if (file.exists()) {
                    com.fabrix.copilot.mcp.McpServerConfig fsConfig = new com.fabrix.copilot.mcp.McpServerConfig(
                        "filesystem-mcp-local",
                        "stdio",
                        "node",
                        Arrays.asList(file.getAbsolutePath(), System.getProperty("user.home")),
                        new HashMap<>(),
                        1
                    );
                    
                    if (manager.addServer(fsConfig)) {
                        addMessage("🔌 파일시스템 MCP 서버가 로컬 설치에서 연결되었습니다.", false);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            CopilotLogger.warn("Local installation check failed: " + e.getMessage());
        }
        return false;
    }
    
    private String getNPMPrefix() {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            String os = System.getProperty("os.name").toLowerCase();
            
            if (os.contains("win")) {
                pb.command("cmd", "/c", "npm", "config", "get", "prefix");
            } else {
                pb.command("sh", "-c", "npm config get prefix");
            }
            
            Process process = pb.start();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String prefix = reader.readLine();
                if (prefix != null && !prefix.isEmpty()) {
                    return prefix.trim();
                }
            }
        } catch (Exception e) {
            CopilotLogger.warn("Failed to get npm prefix: " + e.getMessage());
        }
        return null;
    }
    
    // Shell 가져오기 헬퍼 메서드
    private Shell getShell() {
        return getSite().getShell();
    }
    
    @Override
    public void setFocus() {
        if (inputText != null && !inputText.isDisposed()) {
            inputText.setFocus();
        }
    }
    
    @Override
    public void dispose() {
        CopilotLogger.info("ChatView disposing");
        
        // 리소스 정리
        if (llmClient != null) {
            llmClient.shutdown();
        }
        
        // 폰트 정리
        for (Control control : chatContent.getChildren()) {
            if (control instanceof MessageBubble) {
                ((MessageBubble) control).dispose();
            }
        }
        
        super.dispose();
    }
}