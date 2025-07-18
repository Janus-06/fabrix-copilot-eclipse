package com.fabrix.copilot.ui;

import org.eclipse.swt.widgets.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.dnd.*;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;

import java.util.*;
import java.text.SimpleDateFormat;

/**
 * 👁️ Snippet Preview Dialog - 스니펫 미리보기 UI
 * 
 * 템플릿 변수가 치환된 스니펫을 미리보는 다이얼로그
 */
public class SnippetPreviewDialog extends Dialog {
    
    private final String originalCode;
    private final java.util.List<String> templateVariablesList;
    private final Map<String, Text> variableFields;
    private StyledText previewText;
    private Button applyButton;
    
    public SnippetPreviewDialog(Shell parentShell, String code, java.util.List<String> variables) {
        super(parentShell);
        this.originalCode = code;
        this.templateVariablesList = variables;
        this.variableFields = new HashMap<>();
    }
    
    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("👁️ Snippet Preview with Variables");
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
        
        createDescription(container);
        createVariableInputs(container);
        createPreviewArea(container);
        
        // 초기 미리보기
        updatePreview();
        
        return container;
    }
    
    /**
     * 설명 영역
     */
    private void createDescription(Composite parent) {
        Label descLabel = new Label(parent, SWT.WRAP);
        descLabel.setText("Enter values for template variables to see how the snippet will look when inserted:");
        descLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        Label separator = new Label(parent, SWT.HORIZONTAL | SWT.SEPARATOR);
        separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }
    
    /**
     * 변수 입력 영역
     */
    private void createVariableInputs(Composite parent) {
        if (templateVariablesList.isEmpty()) {
            Label noVarsLabel = new Label(parent, SWT.NONE);
            noVarsLabel.setText("No template variables found in this snippet.");
            return;
        }
        
        Group inputGroup = new Group(parent, SWT.NONE);
        inputGroup.setText("📝 Variable Values");
        inputGroup.setLayout(new GridLayout(3, false));
        inputGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        
        for (String variable : templateVariablesList) {
            Label varLabel = new Label(inputGroup, SWT.NONE);
            varLabel.setText(variable + ":");
            varLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
            
            Text varText = new Text(inputGroup, SWT.BORDER);
            varText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            varText.setMessage("Enter value for " + variable);
            
            // 기본값 설정
            varText.setText(getDefaultValue(variable));
            
            varText.addModifyListener(e -> updatePreview());
            variableFields.put(variable, varText);
            
            // 도움말 버튼
            Button helpButton = new Button(inputGroup, SWT.PUSH);
            helpButton.setText("?");
            helpButton.setToolTipText(getVariableHelp(variable));
            helpButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    MessageDialog.openInformation(getShell(), 
                        "Variable: " + variable, 
                        getVariableHelp(variable));
                }
            });
        }
        
        // 버튼 영역
        Composite buttonComposite = new Composite(inputGroup, SWT.NONE);
        buttonComposite.setLayout(new GridLayout(3, true));
        buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
        
        Button fillDefaultsButton = new Button(buttonComposite, SWT.PUSH);
        fillDefaultsButton.setText("📋 Fill Defaults");
        fillDefaultsButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        fillDefaultsButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                fillDefaultValues();
            }
        });
        
        Button clearButton = new Button(buttonComposite, SWT.PUSH);
        clearButton.setText("🗑️ Clear All");
        clearButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        clearButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                clearAllValues();
            }
        });
        
        applyButton = new Button(buttonComposite, SWT.PUSH);
        applyButton.setText("✅ Apply Values");
        applyButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        applyButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                updatePreview();
            }
        });
    }
    
    /**
     * 미리보기 영역
     */
    private void createPreviewArea(Composite parent) {
        Group previewGroup = new Group(parent, SWT.NONE);
        previewGroup.setText("👁️ Preview");
        previewGroup.setLayout(new GridLayout(1, false));
        previewGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        
        previewText = new StyledText(previewGroup, 
            SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
        previewText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        previewText.setFont(new Font(previewText.getDisplay(), "Consolas", 10, SWT.NORMAL));
        previewText.setBackground(previewText.getDisplay().getSystemColor(SWT.COLOR_INFO_BACKGROUND));
        
        // 복사 버튼
        Button copyButton = new Button(previewGroup, SWT.PUSH);
        copyButton.setText("📋 Copy Preview");
        copyButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        copyButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                copyPreview();
            }
        });
    }
    
    /**
     * 미리보기 업데이트
     */
    private void updatePreview() {
        String preview = originalCode;
        
        // 각 변수를 값으로 치환
        for (Map.Entry<String, Text> entry : variableFields.entrySet()) {
            String variable = entry.getKey();
            String value = entry.getValue().getText();
            
            if (!value.isEmpty()) {
                preview = preview.replace(variable, value);
            }
        }
        
        previewText.setText(preview);
        
        // 하이라이팅
        highlightUnreplacedVariables();
    }
    
    /**
     * 치환되지 않은 변수 하이라이팅
     */
    private void highlightUnreplacedVariables() {
        String text = previewText.getText();
        
        // 템플릿 변수 패턴
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$[A-Z_]+\\$");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        
        while (matcher.find()) {
            StyleRange style = new StyleRange();
            style.start = matcher.start();
            style.length = matcher.end() - matcher.start();
            style.foreground = previewText.getDisplay().getSystemColor(SWT.COLOR_RED);
            style.fontStyle = SWT.BOLD;
            previewText.setStyleRange(style);
        }
    }
    
    /**
     * 변수별 기본값 가져오기
     */
    private String getDefaultValue(String variable) {
        switch (variable) {
            case "$CLASS_NAME$":
                return "MyClass";
            case "$METHOD_NAME$":
                return "myMethod";
            case "$FUNCTION_NAME$":
                return "myFunction";
            case "$VARIABLE_NAME$":
                return "myVariable";
            case "$PARAMETER_NAME$":
                return "param";
            case "$FILE_NAME$":
                return "MyFile.java";
            case "$PACKAGE_NAME$":
                return "com.example";
            case "$PROJECT_NAME$":
                return "MyProject";
            case "$USER_NAME$":
                return System.getProperty("user.name", "User");
            case "$DATE$":
                return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            case "$TIME$":
                return new SimpleDateFormat("HH:mm:ss").format(new Date());
            case "$TODO$":
                return "TODO: Implement this";
            case "$SELECTION$":
                return "selected text";
            case "$CLIPBOARD$":
                return "clipboard content";
            case "$CURSOR$":
                return ""; // 커서 위치는 빈 문자열
            default:
                return variable.replace("$", "").toLowerCase();
        }
    }
    
    /**
     * 변수 도움말
     */
    private String getVariableHelp(String variable) {
        switch (variable) {
            case "$CLASS_NAME$":
                return "The name of the class where the snippet will be inserted";
            case "$METHOD_NAME$":
                return "The name of the method to be created or modified";
            case "$FUNCTION_NAME$":
                return "The name of the function to be created";
            case "$VARIABLE_NAME$":
                return "The name of a variable";
            case "$PARAMETER_NAME$":
                return "The name of a parameter";
            case "$FILE_NAME$":
                return "The name of the current file";
            case "$PACKAGE_NAME$":
                return "The package name (Java) or namespace";
            case "$PROJECT_NAME$":
                return "The name of the current project";
            case "$USER_NAME$":
                return "The current user's name";
            case "$DATE$":
                return "Current date in YYYY-MM-DD format";
            case "$TIME$":
                return "Current time in HH:MM:SS format";
            case "$TODO$":
                return "A TODO marker with placeholder text";
            case "$SELECTION$":
                return "The currently selected text in the editor";
            case "$CLIPBOARD$":
                return "The current clipboard content";
            case "$CURSOR$":
                return "Marks where the cursor should be placed after insertion";
            default:
                return "Custom variable: " + variable;
        }
    }
    
    /**
     * 기본값 채우기
     */
    private void fillDefaultValues() {
        for (Map.Entry<String, Text> entry : variableFields.entrySet()) {
            String variable = entry.getKey();
            Text field = entry.getValue();
            field.setText(getDefaultValue(variable));
        }
        updatePreview();
    }
    
    /**
     * 모든 값 지우기
     */
    private void clearAllValues() {
        for (Text field : variableFields.values()) {
            field.setText("");
        }
        updatePreview();
    }
    
    /**
     * 미리보기 복사
     */
    private void copyPreview() {
        String preview = previewText.getText();
        
        Clipboard clipboard = new Clipboard(getShell().getDisplay());
        clipboard.setContents(
            new String[]{preview}, 
            new Transfer[]{TextTransfer.getInstance()}
        );
        clipboard.dispose();
        
        // 상태 표시
        MessageDialog.openInformation(getShell(), "Copied", 
            "Preview has been copied to clipboard!");
    }
    
    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, "Close", true);
    }
}