package com.fabrix.copilot.ui;

import org.eclipse.swt.widgets.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;

import com.fabrix.copilot.mcp.McpServerManager;
import com.fabrix.copilot.mcp.McpServerConfig;
import com.fabrix.copilot.utils.PreferenceManager;
import com.fabrix.copilot.utils.CopilotLogger;

import java.util.*;
import java.util.List;

/**
 * 🔌 MCP Manager Dialog - MCP 서버 관리
 * 
 * MCP 서버 상태 확인 및 도구 목록 표시
 */
public class MCPManagerDialog extends Dialog {
    
    private Tree serverTree;
    private Text detailsText;
    private Label statusLabel;
    private Button refreshButton;
    private Button settingsButton;
    
    private McpServerManager mcpManager;
    private PreferenceManager preferenceManager;
    
    public MCPManagerDialog(Shell parentShell) {
        super(parentShell);
        this.mcpManager = McpServerManager.getInstance();
        this.preferenceManager = PreferenceManager.getInstance();
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }
    
    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("🔌 MCP Server Manager");
        shell.setSize(700, 500);
        
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
        
        createHeader(container);
        createMainContent(container);
        loadMCPStatus();
        
        return container;
    }
    
    /**
     * 헤더 생성
     */
    private void createHeader(Composite parent) {
        Composite headerComposite = new Composite(parent, SWT.NONE);
        headerComposite.setLayout(new GridLayout(3, false));
        headerComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        Label titleLabel = new Label(headerComposite, SWT.NONE);
        titleLabel.setText("🔌 MCP (Model Context Protocol) Servers");
        titleLabel.setFont(new Font(titleLabel.getDisplay(), "Segoe UI", 12, SWT.BOLD));
        
        statusLabel = new Label(headerComposite, SWT.NONE);
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        statusLabel.setText("Loading...");
        
        refreshButton = new Button(headerComposite, SWT.PUSH);
        refreshButton.setText("🔄 Refresh");
        refreshButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                refreshMCPStatus();
            }
        });
        
        Label separator = new Label(parent, SWT.HORIZONTAL | SWT.SEPARATOR);
        separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }
    
    /**
     * 메인 컨텐츠 생성
     */
    private void createMainContent(Composite parent) {
        SashForm sashForm = new SashForm(parent, SWT.HORIZONTAL);
        sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        
        createServerTree(sashForm);
        createDetailsArea(sashForm);
        
        sashForm.setWeights(new int[]{50, 50});
    }
    
    /**
     * 서버 트리 생성
     */
    private void createServerTree(Composite parent) {
        Composite treeComposite = new Composite(parent, SWT.NONE);
        treeComposite.setLayout(new GridLayout(1, false));
        
        Label treeLabel = new Label(treeComposite, SWT.NONE);
        treeLabel.setText("📋 서버 및 도구:");
        
        serverTree = new Tree(treeComposite, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        serverTree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        
        serverTree.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                updateDetails();
            }
        });
        
        // 트리 컨텍스트 메뉴
        Menu contextMenu = new Menu(serverTree);
        serverTree.setMenu(contextMenu);
        
        MenuItem testItem = new MenuItem(contextMenu, SWT.PUSH);
        testItem.setText("🧪 Test Tool");
        testItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                testSelectedTool();
            }
        });
    }
    
    /**
     * 상세 정보 영역 생성
     */
    private void createDetailsArea(Composite parent) {
        Composite detailsComposite = new Composite(parent, SWT.NONE);
        detailsComposite.setLayout(new GridLayout(1, false));
        
        Label detailsLabel = new Label(detailsComposite, SWT.NONE);
        detailsLabel.setText("📝 상세 정보:");
        
        detailsText = new Text(detailsComposite, 
            SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
        detailsText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        detailsText.setFont(new Font(detailsText.getDisplay(), "Consolas", 9, SWT.NORMAL));
        
        settingsButton = new Button(detailsComposite, SWT.PUSH);
        settingsButton.setText("⚙️ MCP Settings");
        settingsButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        settingsButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                openMCPSettings();
            }
        });
    }
    
    /**
     * MCP 상태 로드
     */
    private void loadMCPStatus() {
        serverTree.removeAll();
        
        McpServerManager.McpStatus status = mcpManager.getStatus();
        statusLabel.setText(String.format("서버: %d개 연결됨 / %d개 설정됨 | 도구: %d개", 
            status.getConnectedServers(), status.getTotalServers(), status.getTotalTools()));
        
        // 연결된 도구들만 표시
        Map<String, List<McpServerManager.McpTool>> connectedTools = mcpManager.getConnectedTools();
        
        if (connectedTools.isEmpty()) {
            TreeItem noServerItem = new TreeItem(serverTree, SWT.NONE);
            noServerItem.setText("❌ 연결된 MCP 서버가 없습니다");
            noServerItem.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_RED));
            
            detailsText.setText("MCP 서버가 연결되지 않았습니다.\n\n" +
                               "설정 방법:\n" +
                               "1. Settings → MCP Config에서 서버 설정\n" +
                               "2. 외부 MCP 서버 실행\n" +
                               "3. Refresh 버튼으로 연결 확인");
        } else {
            // 연결된 서버별로 도구 표시
            for (Map.Entry<String, List<McpServerManager.McpTool>> entry : connectedTools.entrySet()) {
                String serverName = entry.getKey();
                List<McpServerManager.McpTool> tools = entry.getValue();
                
                TreeItem serverItem = new TreeItem(serverTree, SWT.NONE);
                serverItem.setText("🟢 " + serverName + " (" + tools.size() + " tools)");
                serverItem.setData("type", "server");
                serverItem.setData("name", serverName);
                
                // 도구들 추가
                for (McpServerManager.McpTool tool : tools) {
                    TreeItem toolItem = new TreeItem(serverItem, SWT.NONE);
                    toolItem.setText("🛠️ " + tool.getName());
                    toolItem.setData("type", "tool");
                    toolItem.setData("tool", tool);
                    toolItem.setData("server", serverName);
                }
                
                serverItem.setExpanded(true);
            }
        }
    }
    
    /**
     * 상세 정보 업데이트
     */
    private void updateDetails() {
        TreeItem[] selection = serverTree.getSelection();
        if (selection.length == 0) {
            detailsText.setText("");
            return;
        }
        
        TreeItem selected = selection[0];
        String type = (String) selected.getData("type");
        
        if ("server".equals(type)) {
            String serverName = (String) selected.getData("name");
            showServerDetails(serverName);
        } else if ("tool".equals(type)) {
            McpServerManager.McpTool tool = (McpServerManager.McpTool) selected.getData("tool");
            String serverName = (String) selected.getData("server");
            showToolDetails(tool, serverName);
        }
    }
    
    /**
     * 서버 상세 정보 표시
     */
    private void showServerDetails(String serverName) {
        StringBuilder details = new StringBuilder();
        details.append("🔌 MCP Server: ").append(serverName).append("\n\n");
        details.append("상태: 🟢 연결됨\n");
        details.append("타입: stdio/http\n");
        
        List<McpServerManager.McpTool> tools = mcpManager.getConnectedTools().get(serverName);
        if (tools != null) {
            details.append("도구 수: ").append(tools.size()).append("\n\n");
            details.append("제공하는 도구들:\n");
            for (McpServerManager.McpTool tool : tools) {
                details.append("• ").append(tool.getName()).append("\n");
            }
        }
        
        detailsText.setText(details.toString());
    }
    
    /**
     * 도구 상세 정보 표시
     */
    private void showToolDetails(McpServerManager.McpTool tool, String serverName) {
        StringBuilder details = new StringBuilder();
        details.append("🛠️ Tool: ").append(tool.getName()).append("\n\n");
        details.append("서버: ").append(serverName).append("\n");
        details.append("설명: ").append(tool.getDescription()).append("\n");
        
        if (tool.getParameters() != null && !tool.getParameters().isEmpty()) {
            details.append("\n파라미터:\n");
            String[] params = tool.getParameters().split(",");
            for (String param : params) {
                details.append("• ").append(param.trim()).append("\n");
            }
        }
        
        details.append("\n사용 예시:\n");
        details.append(getToolExample(tool.getName()));
        
        detailsText.setText(details.toString());
    }
    
    /**
     * 도구 사용 예시 생성
     */
    private String getToolExample(String toolName) {
        switch (toolName) {
            case "read_file":
                return "\"main.java 파일을 읽어줘\"";
            case "write_file":
                return "\"result.txt 파일에 결과를 저장해줘\"";
            case "list_directory":
                return "\"현재 디렉토리의 파일 목록을 보여줘\"";
            case "search_files":
                return "\"TODO라는 단어가 포함된 파일을 찾아줘\"";
            case "git_status":
                return "\"Git 상태를 확인해줘\"";
            case "git_log":
                return "\"최근 커밋 이력을 보여줘\"";
            case "execute_query":
                return "\"users 테이블의 모든 데이터를 조회해줘\"";
            case "fetch_url":
                return "\"https://example.com 페이지를 가져와줘\"";
            default:
                return "이 도구를 사용하려면 자연어로 요청하세요.";
        }
    }
    
    /**
     * MCP 상태 새로고침
     */
    private void refreshMCPStatus() {
        statusLabel.setText("Refreshing...");
        refreshButton.setEnabled(false);
        
        new Thread(() -> {
            mcpManager.refreshServers();
            
            Display.getDefault().asyncExec(() -> {
                if (!serverTree.isDisposed()) {
                    loadMCPStatus();
                    refreshButton.setEnabled(true);
                    CopilotLogger.info("MCP status refreshed");
                }
            });
        }).start();
    }
    
    /**
     * 선택된 도구 테스트
     */
    private void testSelectedTool() {
        TreeItem[] selection = serverTree.getSelection();
        if (selection.length == 0) return;
        
        TreeItem selected = selection[0];
        if (!"tool".equals(selected.getData("type"))) {
            MessageDialog.openInformation(getShell(), "테스트", "도구를 선택해주세요.");
            return;
        }
        
        McpServerManager.McpTool tool = (McpServerManager.McpTool) selected.getData("tool");
        String serverName = (String) selected.getData("server");
        
        // 간단한 테스트 파라미터
        Map<String, Object> testParams = new HashMap<>();
        
        switch (tool.getName()) {
            case "read_file":
                testParams.put("path", "test.txt");
                break;
            case "list_directory":
                testParams.put("path", "./");
                break;
            case "git_status":
                // 파라미터 없음
                break;
            default:
                testParams.put("test", "true");
        }
        
        try {
            String result = mcpManager.executeTool(tool.getName(), testParams, "test");
            MessageDialog.openInformation(getShell(), 
                "도구 테스트 결과", 
                "도구: " + tool.getName() + "\n\n결과:\n" + result);
        } catch (Exception e) {
            MessageDialog.openError(getShell(), 
                "테스트 실패", 
                "도구 실행 중 오류 발생: " + e.getMessage());
        }
    }
    
    /**
     * MCP 설정 열기
     */
    private void openMCPSettings() {
        SettingsDialog dialog = new SettingsDialog(getShell());
        dialog.open();
        
        // 설정 변경 후 새로고침
        refreshMCPStatus();
    }
    
    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, Dialog.OK, "닫기", true);
    }
}