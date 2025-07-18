package com.fabrix.copilot.ui;

import org.eclipse.swt.widgets.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;

import com.fabrix.copilot.mcp.McpServerConfig;
import com.fabrix.copilot.utils.CopilotLogger;

import java.util.*;
import java.io.File;
import org.eclipse.swt.graphics.Rectangle;
import java.util.List;
/**
 * 🔧 MCP Server Configuration Dialog
 * 
 * MCP 서버 설정을 입력/편집하는 다이얼로그
 */
public class McpServerConfigDialog extends Dialog {
    
    private Text nameText;
    private Combo typeCombo;
    private Text commandText;
    private Text argsText;
    private Text envText;
    private Spinner prioritySpinner;
    private Button browseButton;
    private Button testButton;
    private Label statusLabel;
    
    private McpServerConfig serverConfig;
    private McpServerInfo serverInfo;
    
    public McpServerConfigDialog(Shell parentShell, McpServerInfo serverInfo) {
        super(parentShell);
        this.serverInfo = serverInfo;
    }
    
    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(serverInfo == null ? "➕ Add MCP Server" : "✏️ Edit MCP Server");
        shell.setSize(600, 500);
        
        // 화면 중앙에 위치
        Rectangle displayBounds = shell.getDisplay().getPrimaryMonitor().getBounds();
        Rectangle shellBounds = shell.getBounds();
        int x = (displayBounds.width - shellBounds.width) / 2;
        int y = (displayBounds.height - shellBounds.height) / 2;
        shell.setLocation(x, y);
    }
    
    @Override
    protected Control createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        container.setLayout(new GridLayout(1, false));
        
        createHeader(container);
        createBasicSettings(container);
        createAdvancedSettings(container);
        createTemplates(container);
        
        if (serverInfo != null) {
            loadExistingConfig();
        }
        
        return container;
    }
    
    /**
     * 헤더 생성
     */
    private void createHeader(Composite parent) {
        Label descLabel = new Label(parent, SWT.WRAP);
        descLabel.setText("Configure a local MCP server. The server process will be managed by the plugin.");
        descLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        Label separator = new Label(parent, SWT.HORIZONTAL | SWT.SEPARATOR);
        separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }
    
    /**
     * 기본 설정
     */
    private void createBasicSettings(Composite parent) {
        Group basicGroup = new Group(parent, SWT.NONE);
        basicGroup.setText("🔧 Basic Settings");
        basicGroup.setLayout(new GridLayout(2, false));
        basicGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        
        // 서버 이름
        Label nameLabel = new Label(basicGroup, SWT.NONE);
        nameLabel.setText("Server Name:");
        
        nameText = new Text(basicGroup, SWT.BORDER);
        nameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        nameText.setMessage("e.g., mcp-filesystem");
        nameText.addModifyListener(e -> validateInput());
        
        // 서버 타입
        Label typeLabel = new Label(basicGroup, SWT.NONE);
        typeLabel.setText("Server Type:");
        
        typeCombo = new Combo(basicGroup, SWT.READ_ONLY);
        typeCombo.setItems(new String[]{"stdio", "http", "websocket"});
        typeCombo.select(0);
        typeCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        typeCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                updateUIForType();
            }
        });
        
        // 실행 명령
        Label commandLabel = new Label(basicGroup, SWT.NONE);
        commandLabel.setText("Command:");
        
        Composite commandComposite = new Composite(basicGroup, SWT.NONE);
        commandComposite.setLayout(new GridLayout(2, false));
        commandComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        commandText = new Text(commandComposite, SWT.BORDER);
        commandText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        commandText.setMessage("e.g., node, python, npx");
        commandText.addModifyListener(e -> validateInput());
        
        browseButton = new Button(commandComposite, SWT.PUSH);
        browseButton.setText("Browse...");
        browseButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                browseForCommand();
            }
        });
        
        // 인자
        Label argsLabel = new Label(basicGroup, SWT.NONE);
        argsLabel.setText("Arguments:");
        
        argsText = new Text(basicGroup, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL);
        GridData argsData = new GridData(SWT.FILL, SWT.FILL, true, true);
        argsData.heightHint = 60;
        argsText.setLayoutData(argsData);
        argsText.setMessage("One argument per line\ne.g.,\nC:/mcp-server/index.js\n--port\n8080");
    }
    
    /**
     * 고급 설정
     */
    private void createAdvancedSettings(Composite parent) {
        Group advancedGroup = new Group(parent, SWT.NONE);
        advancedGroup.setText("⚙️ Advanced Settings");
        advancedGroup.setLayout(new GridLayout(2, false));
        advancedGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        
        // 환경 변수
        Label envLabel = new Label(advancedGroup, SWT.NONE);
        envLabel.setText("Environment Variables:");
        envLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
        
        envText = new Text(advancedGroup, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL);
        GridData envData = new GridData(SWT.FILL, SWT.FILL, true, true);
        envData.heightHint = 60;
        envText.setLayoutData(envData);
        envText.setMessage("KEY=VALUE format, one per line\ne.g.,\nNODE_ENV=production\nPORT=8080");
        
        // 우선순위
        Label priorityLabel = new Label(advancedGroup, SWT.NONE);
        priorityLabel.setText("Priority:");
        
        prioritySpinner = new Spinner(advancedGroup, SWT.BORDER);
        prioritySpinner.setMinimum(1);
        prioritySpinner.setMaximum(10);
        prioritySpinner.setSelection(5);
        prioritySpinner.setToolTipText("Higher priority servers are preferred when multiple servers provide the same tool");
        
        // 테스트 버튼
        new Label(advancedGroup, SWT.NONE); // Spacer
        
        Composite testComposite = new Composite(advancedGroup, SWT.NONE);
        testComposite.setLayout(new GridLayout(2, false));
        testComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        testButton = new Button(testComposite, SWT.PUSH);
        testButton.setText("🧪 Test Configuration");
        testButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                testConfiguration();
            }
        });
        
        statusLabel = new Label(testComposite, SWT.NONE);
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }
    
    /**
     * 템플릿 섹션
     */
    private void createTemplates(Composite parent) {
        Group templateGroup = new Group(parent, SWT.NONE);
        templateGroup.setText("📋 Templates");
        templateGroup.setLayout(new GridLayout(4, true));
        templateGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        Button filesystemBtn = new Button(templateGroup, SWT.PUSH);
        filesystemBtn.setText("📁 Filesystem");
        filesystemBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        filesystemBtn.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                applyFilesystemTemplate();
            }
        });
        
        Button gitBtn = new Button(templateGroup, SWT.PUSH);
        gitBtn.setText("🔧 Git");
        gitBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        gitBtn.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                applyGitTemplate();
            }
        });
        
        Button sqliteBtn = new Button(templateGroup, SWT.PUSH);
        sqliteBtn.setText("🗃️ SQLite");
        sqliteBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        sqliteBtn.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                applySQLiteTemplate();
            }
        });
        
        Button customBtn = new Button(templateGroup, SWT.PUSH);
        customBtn.setText("🛠️ Custom");
        customBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        customBtn.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                applyCustomTemplate();
            }
        });
    }
    
    /**
     * 타입에 따른 UI 업데이트
     */
    private void updateUIForType() {
        String type = typeCombo.getText();
        
        switch (type) {
            case "http":
            case "websocket":
                // HTTP/WebSocket의 경우 추가 설정
                argsText.setMessage("URL and port configuration\ne.g.,\n--host\nlocalhost\n--port\n8080");
                break;
            case "stdio":
            default:
                argsText.setMessage("One argument per line");
                break;
        }
    }
    
    /**
     * 명령어 찾아보기
     */
    private void browseForCommand() {
        FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
        dialog.setFilterExtensions(new String[]{"*.exe", "*.bat", "*.sh", "*.*"});
        dialog.setText("Select Command");
        
        String selected = dialog.open();
        if (selected != null) {
            commandText.setText(selected);
        }
    }
    
    /**
     * 입력 검증
     */
    private void validateInput() {
        boolean valid = true;
        
        if (nameText.getText().trim().isEmpty()) {
            valid = false;
        }
        
        if (commandText.getText().trim().isEmpty()) {
            valid = false;
        }
        
        Button okButton = getButton(IDialogConstants.OK_ID);
        if (okButton != null) {
            okButton.setEnabled(valid);
        }
    }
    
    /**
     * 설정 테스트
     */
    private void testConfiguration() {
        statusLabel.setText("🔄 Testing...");
        statusLabel.setForeground(statusLabel.getDisplay().getSystemColor(SWT.COLOR_BLUE));
        
        // 비동기 테스트
        new Thread(() -> {
            try {
                // 간단한 프로세스 실행 테스트
                ProcessBuilder pb = new ProcessBuilder(commandText.getText().trim());
                
                // 인자 추가
                String[] args = argsText.getText().split("\n");
                for (String arg : args) {
                    if (!arg.trim().isEmpty()) {
                        pb.command().add(arg.trim());
                    }
                }
                
                // 환경 변수 설정
                Map<String, String> env = pb.environment();
                String[] envLines = envText.getText().split("\n");
                for (String envLine : envLines) {
                    if (envLine.contains("=")) {
                        String[] parts = envLine.split("=", 2);
                        env.put(parts[0].trim(), parts[1].trim());
                    }
                }
                
                // 테스트 실행
                Process process = pb.start();
                Thread.sleep(1000); // 1초 대기
                
                boolean alive = process.isAlive();
                if (alive) {
                    process.destroyForcibly();
                }
                
                Display.getDefault().asyncExec(() -> {
                    if (alive) {
                        statusLabel.setText("✅ Configuration valid");
                        statusLabel.setForeground(statusLabel.getDisplay().getSystemColor(SWT.COLOR_DARK_GREEN));
                    } else {
                        statusLabel.setText("⚠️ Process exited immediately");
                        statusLabel.setForeground(statusLabel.getDisplay().getSystemColor(SWT.COLOR_DARK_YELLOW));
                    }
                });
                
            } catch (Exception e) {
                Display.getDefault().asyncExec(() -> {
                    statusLabel.setText("❌ Test failed: " + e.getMessage());
                    statusLabel.setForeground(statusLabel.getDisplay().getSystemColor(SWT.COLOR_RED));
                });
            }
        }).start();
    }
    
    /**
     * 기존 설정 로드
     */
    private void loadExistingConfig() {
        if (serverInfo != null) {
            nameText.setText(serverInfo.name);
            typeCombo.setText(serverInfo.type);
            commandText.setText(serverInfo.command);
            if (serverInfo.args != null) {
                argsText.setText(serverInfo.args.replace(" ", "\n"));
            }
        }
    }
    
    /**
     * 파일시스템 템플릿 적용
     */
    private void applyFilesystemTemplate() {
        nameText.setText("mcp-filesystem");
        typeCombo.select(0); // stdio
        commandText.setText("npx");
        argsText.setText("-y\n@modelcontextprotocol/server-filesystem\n./");
        prioritySpinner.setSelection(5);
    }
    
    /**
     * Git 템플릿 적용
     */
    private void applyGitTemplate() {
        nameText.setText("mcp-git");
        typeCombo.select(0); // stdio
        commandText.setText("npx");
        argsText.setText("-y\n@modelcontextprotocol/server-git");
        prioritySpinner.setSelection(5);
    }
    
    /**
     * SQLite 템플릿 적용
     */
    private void applySQLiteTemplate() {
        nameText.setText("mcp-sqlite");
        typeCombo.select(0); // stdio
        commandText.setText("npx");
        argsText.setText("-y\n@modelcontextprotocol/server-sqlite\n--db\n./database.db");
        prioritySpinner.setSelection(5);
    }
    
    /**
     * 사용자 정의 템플릿
     */
    private void applyCustomTemplate() {
        nameText.setText("mcp-custom");
        typeCombo.select(0); // stdio
        commandText.setText("node");
        argsText.setText("path/to/your/mcp-server.js");
        prioritySpinner.setSelection(5);
        envText.setText("NODE_ENV=production");
    }
    
    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, serverInfo == null ? "Add" : "Save", true);
        createButton(parent, IDialogConstants.CANCEL_ID, "Cancel", false);
        
        validateInput();
    }
    
    @Override
    protected void okPressed() {
        // 서버 설정 생성
        List<String> args = new ArrayList<>();
        String[] argLines = argsText.getText().split("\n");
        for (String arg : argLines) {
            if (!arg.trim().isEmpty()) {
                args.add(arg.trim());
            }
        }
        
        Map<String, String> env = new HashMap<>();
        String[] envLines = envText.getText().split("\n");
        for (String envLine : envLines) {
            if (envLine.contains("=")) {
                String[] parts = envLine.split("=", 2);
                env.put(parts[0].trim(), parts[1].trim());
            }
        }
        
        serverConfig = new McpServerConfig(
            nameText.getText().trim(),
            typeCombo.getText(),
            commandText.getText().trim(),
            args,
            env,
            prioritySpinner.getSelection()
        );
        
        CopilotLogger.info("MCP server configured: " + serverConfig.getName());
        
        super.okPressed();
    }
    
    /**
     * 설정된 서버 설정 반환
     */
    public McpServerConfig getServerConfig() {
        return serverConfig;
    }
}