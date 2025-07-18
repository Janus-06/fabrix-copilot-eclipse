package com.fabrix.copilot.ui;

import org.eclipse.swt.widgets.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;

import com.fabrix.copilot.mcp.McpServerManager;
import com.fabrix.copilot.utils.CopilotLogger;

import java.util.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.dnd.*;

/**
 * 🧪 Tool Test Dialog - MCP 도구 테스트 UI
 * 
 * 선택된 MCP 도구를 테스트하는 다이얼로그
 */
public class ToolTestDialog extends Dialog {
    
    private final McpServerManager.McpTool tool;
    private final McpServerManager mcpManager;
    
    private Text contextText;
    private Composite parametersComposite;
    private Map<String, Text> parameterFields;
    private StyledText resultText;
    private Button executeButton;
    private Label statusLabel;
    
    public ToolTestDialog(Shell parentShell, McpServerManager.McpTool tool) {
        super(parentShell);
        this.tool = tool;
        this.mcpManager = McpServerManager.getInstance();
        this.parameterFields = new HashMap<>();
    }
    
    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("🧪 Test Tool: " + tool.getName());
        shell.setSize(700, 600);
        
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
        
        createToolInfo(container);
        createContextSection(container);
        createParametersSection(container);
        createResultSection(container);
        createStatusBar(container);
        
        return container;
    }
    
    /**
     * 도구 정보 표시
     */
    private void createToolInfo(Composite parent) {
        Group infoGroup = new Group(parent, SWT.NONE);
        infoGroup.setText("🛠️ Tool Information");
        infoGroup.setLayout(new GridLayout(2, false));
        infoGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        
        Label nameLabel = new Label(infoGroup, SWT.NONE);
        nameLabel.setText("Name:");
        nameLabel.setFont(new Font(nameLabel.getDisplay(), "Segoe UI", 9, SWT.BOLD));
        
        Label nameValue = new Label(infoGroup, SWT.NONE);
        nameValue.setText(tool.getName());
        nameValue.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        Label descLabel = new Label(infoGroup, SWT.NONE);
        descLabel.setText("Description:");
        descLabel.setFont(new Font(descLabel.getDisplay(), "Segoe UI", 9, SWT.BOLD));
        
        Label descValue = new Label(infoGroup, SWT.WRAP);
        descValue.setText(tool.getDescription());
        descValue.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        if (!tool.getParameters().isEmpty()) {
            Label paramsLabel = new Label(infoGroup, SWT.NONE);
            paramsLabel.setText("Parameters:");
            paramsLabel.setFont(new Font(paramsLabel.getDisplay(), "Segoe UI", 9, SWT.BOLD));
            
            Label paramsValue = new Label(infoGroup, SWT.NONE);
            paramsValue.setText(tool.getParameters());
            paramsValue.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        }
    }
    
    /**
     * 컨텍스트 섹션
     */
    private void createContextSection(Composite parent) {
        Group contextGroup = new Group(parent, SWT.NONE);
        contextGroup.setText("📋 Context (Optional)");
        contextGroup.setLayout(new GridLayout(1, false));
        contextGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        
        contextText = new Text(contextGroup, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL);
        GridData contextData = new GridData(SWT.FILL, SWT.FILL, true, true);
        contextData.heightHint = 60;
        contextText.setLayoutData(contextData);
        contextText.setMessage("Enter any context information for the tool execution...");
    }
    
    /**
     * 파라미터 섹션
     */
    private void createParametersSection(Composite parent) {
        Group paramsGroup = new Group(parent, SWT.NONE);
        paramsGroup.setText("🔧 Parameters");
        paramsGroup.setLayout(new GridLayout(1, false));
        paramsGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        
        parametersComposite = new Composite(paramsGroup, SWT.NONE);
        parametersComposite.setLayout(new GridLayout(2, false));
        parametersComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        
        createParameterFields();
    }
    
    /**
     * 파라미터 필드 생성
     */
    private void createParameterFields() {
        String params = tool.getParameters();
        if (params == null || params.isEmpty()) {
            Label noParamsLabel = new Label(parametersComposite, SWT.NONE);
            noParamsLabel.setText("No parameters required");
            noParamsLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
            return;
        }
        
        // 파라미터 파싱 (쉼표로 구분)
        String[] paramArray = params.split(",");
        for (String param : paramArray) {
            param = param.trim();
            if (!param.isEmpty()) {
                createParameterField(param);
            }
        }
    }
    
    /**
     * 개별 파라미터 필드 생성
     */
    private void createParameterField(String paramName) {
        Label label = new Label(parametersComposite, SWT.NONE);
        label.setText(paramName + ":");
        
        Text field = new Text(parametersComposite, SWT.BORDER);
        field.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        field.setMessage("Enter " + paramName);
        
        // 특정 파라미터에 대한 기본값 제공
        setDefaultValue(paramName, field);
        
        parameterFields.put(paramName, field);
    }
    
    /**
     * 파라미터 기본값 설정
     */
    private void setDefaultValue(String paramName, Text field) {
        switch (paramName.toLowerCase()) {
            case "path":
                field.setText("./");
                break;
            case "query":
                field.setText("TODO");
                break;
            case "limit":
                field.setText("10");
                break;
            case "file":
                field.setText("example.txt");
                break;
            case "content":
                field.setText("// Sample content");
                break;
        }
    }
    
    /**
     * 결과 섹션
     */
    private void createResultSection(Composite parent) {
        Group resultGroup = new Group(parent, SWT.NONE);
        resultGroup.setText("📊 Execution Result");
        resultGroup.setLayout(new GridLayout(1, false));
        resultGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        
        resultText = new StyledText(resultGroup, 
            SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
        resultText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        resultText.setFont(new Font(resultText.getDisplay(), "Consolas", 10, SWT.NORMAL));
        resultText.setBackground(resultText.getDisplay().getSystemColor(SWT.COLOR_INFO_BACKGROUND));
        
        // 결과 복사 버튼
        ToolBar resultToolBar = new ToolBar(resultGroup, SWT.FLAT | SWT.RIGHT);
        resultToolBar.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
        
        ToolItem copyItem = new ToolItem(resultToolBar, SWT.PUSH);
        copyItem.setText("📋 Copy Result");
        copyItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                copyResult();
            }
        });
        
        ToolItem clearItem = new ToolItem(resultToolBar, SWT.PUSH);
        clearItem.setText("🗑️ Clear");
        clearItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                resultText.setText("");
            }
        });
    }
    
    /**
     * 상태 바
     */
    private void createStatusBar(Composite parent) {
        Composite statusComposite = new Composite(parent, SWT.NONE);
        statusComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        statusComposite.setLayout(new GridLayout(2, false));
        
        statusLabel = new Label(statusComposite, SWT.NONE);
        statusLabel.setText("Ready to execute tool");
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        Label helpLabel = new Label(statusComposite, SWT.NONE);
        helpLabel.setText("💡 Fill in parameters and click Execute");
        helpLabel.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
    }
    
    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        executeButton = createButton(parent, IDialogConstants.OK_ID, "▶️ Execute", true);
        createButton(parent, IDialogConstants.CANCEL_ID, "Close", false);
    }
    
    @Override
    protected void buttonPressed(int buttonId) {
        if (buttonId == IDialogConstants.OK_ID) {
            executeTool();
        } else {
            super.buttonPressed(buttonId);
        }
    }
    
    /**
     * 도구 실행
     */
    private void executeTool() {
        // 파라미터 수집
        Map<String, Object> parameters = new HashMap<>();
        for (Map.Entry<String, Text> entry : parameterFields.entrySet()) {
            String value = entry.getValue().getText().trim();
            if (!value.isEmpty()) {
                parameters.put(entry.getKey(), value);
            }
        }
        
        String context = contextText.getText().trim();
        
        // UI 업데이트
        executeButton.setEnabled(false);
        statusLabel.setText("🔄 Executing tool...");
        statusLabel.setForeground(statusLabel.getDisplay().getSystemColor(SWT.COLOR_BLUE));
        resultText.setText("Executing " + tool.getName() + "...\n\n");
        
        // 비동기 실행
        new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();
                
                // 도구 실행
                String result = mcpManager.executeTool(tool.getName(), parameters, context);
                
                long duration = System.currentTimeMillis() - startTime;
                
                Display.getDefault().asyncExec(() -> {
                    if (!resultText.isDisposed()) {
                        resultText.append("=== Execution Result ===\n");
                        resultText.append("Duration: " + duration + "ms\n\n");
                        resultText.append(result);
                        
                        statusLabel.setText("✅ Execution completed in " + duration + "ms");
                        statusLabel.setForeground(statusLabel.getDisplay().getSystemColor(SWT.COLOR_DARK_GREEN));
                        
                        // 결과 하이라이팅
                        highlightResult();
                    }
                    
                    if (!executeButton.isDisposed()) {
                        executeButton.setEnabled(true);
                    }
                });
                
                CopilotLogger.info("Tool executed successfully: " + tool.getName());
                
            } catch (Exception e) {
                Display.getDefault().asyncExec(() -> {
                    if (!resultText.isDisposed()) {
                        resultText.append("\n❌ Execution Error:\n");
                        resultText.append(e.getMessage());
                        resultText.append("\n\nStack Trace:\n");
                        resultText.append(getStackTraceString(e));
                    }
                    
                    if (!statusLabel.isDisposed()) {
                        statusLabel.setText("❌ Execution failed: " + e.getMessage());
                        statusLabel.setForeground(statusLabel.getDisplay().getSystemColor(SWT.COLOR_RED));
                    }
                    
                    if (!executeButton.isDisposed()) {
                        executeButton.setEnabled(true);
                    }
                });
                
                CopilotLogger.error("Tool execution failed: " + tool.getName(), e);
            }
        }).start();
    }
    
    /**
     * 결과 하이라이팅
     */
    private void highlightResult() {
        String text = resultText.getText();
        
        // 성공 메시지 하이라이팅
        highlightPattern(text, "✅[^\\n]+", SWT.COLOR_DARK_GREEN);
        
        // 에러 메시지 하이라이팅
        highlightPattern(text, "❌[^\\n]+", SWT.COLOR_RED);
        
        // 경고 메시지 하이라이팅
        highlightPattern(text, "⚠️[^\\n]+", SWT.COLOR_DARK_YELLOW);
        
        // JSON 형식 하이라이팅
        highlightPattern(text, "\\{[^}]*\\}", SWT.COLOR_DARK_BLUE);
        
        // 파일 경로 하이라이팅
        highlightPattern(text, "([A-Za-z]:)?[\\\\/][^\\s]+", SWT.COLOR_DARK_CYAN);
    }
    
    /**
     * 패턴 하이라이팅
     */
    private void highlightPattern(String text, String pattern, int colorId) {
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(text);
            
            while (m.find()) {
                StyleRange style = new StyleRange();
                style.start = m.start();
                style.length = m.end() - m.start();
                style.foreground = resultText.getDisplay().getSystemColor(colorId);
                resultText.setStyleRange(style);
            }
        } catch (Exception e) {
            // 하이라이팅 실패는 무시
        }
    }
    
    /**
     * 결과 복사
     */
    private void copyResult() {
        String result = resultText.getText();
        if (!result.isEmpty()) {
        	Clipboard clipboard = new Clipboard(getShell().getDisplay());
            clipboard.setContents(
                new String[]{result}, 
                new Transfer[]{TextTransfer.getInstance()}
            );
            clipboard.dispose();
            
            statusLabel.setText("📋 Result copied to clipboard");
        }
    }
    
    /**
     * 스택 트레이스 문자열 변환
     */
    private String getStackTraceString(Throwable throwable) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}