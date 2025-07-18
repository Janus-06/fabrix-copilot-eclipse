package com.fabrix.copilot.ui;

import org.eclipse.swt.widgets.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;

/**
 * 🔤 Template Variable Dialog - 템플릿 변수 삽입 UI
 * 
 * 코드 스니펫에 사용할 템플릿 변수를 선택하는 다이얼로그
 */
public class TemplateVariableDialog extends Dialog {
    
    private String selectedVariable;
    private List variableList;
    private Text customText;
    private Text previewText;
    
    // 미리 정의된 변수들
    private static final String[][] PREDEFINED_VARIABLES = {
        {"$CLASS_NAME$", "Class name"},
        {"$METHOD_NAME$", "Method name"},
        {"$FUNCTION_NAME$", "Function name"},
        {"$VARIABLE_NAME$", "Variable name"},
        {"$PARAMETER_NAME$", "Parameter name"},
        {"$FILE_NAME$", "File name"},
        {"$PACKAGE_NAME$", "Package name"},
        {"$PROJECT_NAME$", "Project name"},
        {"$USER_NAME$", "User name"},
        {"$DATE$", "Current date"},
        {"$TIME$", "Current time"},
        {"$TODO$", "TODO marker"},
        {"$SELECTION$", "Selected text"},
        {"$CLIPBOARD$", "Clipboard content"},
        {"$CURSOR$", "Cursor position"}
    };
    
    public TemplateVariableDialog(Shell parentShell) {
        super(parentShell);
    }
    
    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("🔤 Insert Template Variable");
        shell.setSize(500, 400);
    }
    
    @Override
    protected Control createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        container.setLayout(new GridLayout(1, false));
        
        createDescription(container);
        createVariableSelection(container);
        createCustomVariable(container);
        createPreview(container);
        
        return container;
    }
    
    /**
     * 설명 텍스트
     */
    private void createDescription(Composite parent) {
        Label descLabel = new Label(parent, SWT.WRAP);
        descLabel.setText("Template variables are placeholders that will be replaced when the snippet is inserted. " +
                         "Select a predefined variable or create a custom one.");
        descLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        Label separator = new Label(parent, SWT.HORIZONTAL | SWT.SEPARATOR);
        separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }
    
    /**
     * 변수 선택 영역
     */
    private void createVariableSelection(Composite parent) {
        Group selectionGroup = new Group(parent, SWT.NONE);
        selectionGroup.setText("📋 Predefined Variables");
        selectionGroup.setLayout(new GridLayout(1, false));
        selectionGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        
        variableList = new List(selectionGroup, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
        variableList.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        
        // 변수 목록 추가
        for (String[] var : PREDEFINED_VARIABLES) {
            variableList.add(String.format("%-20s - %s", var[0], var[1]));
        }
        
        variableList.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int index = variableList.getSelectionIndex();
                if (index >= 0) {
                    selectedVariable = PREDEFINED_VARIABLES[index][0];
                    updatePreview();
                    customText.setText("");
                }
            }
        });
        
        variableList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDoubleClick(MouseEvent e) {
                if (variableList.getSelectionIndex() >= 0) {
                    okPressed();
                }
            }
        });
    }
    
    /**
     * 사용자 정의 변수
     */
    private void createCustomVariable(Composite parent) {
        Group customGroup = new Group(parent, SWT.NONE);
        customGroup.setText("✏️ Custom Variable");
        customGroup.setLayout(new GridLayout(2, false));
        customGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        Label customLabel = new Label(customGroup, SWT.NONE);
        customLabel.setText("Name:");
        
        customText = new Text(customGroup, SWT.BORDER);
        customText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        customText.setMessage("Enter custom variable name (e.g., MY_VARIABLE)");
        customText.addModifyListener(e -> {
            String text = customText.getText().trim();
            if (!text.isEmpty()) {
                // 자동으로 $ 추가
                if (!text.startsWith("$")) text = "$" + text;
                if (!text.endsWith("$")) text = text + "$";
                selectedVariable = text.toUpperCase();
                updatePreview();
                variableList.deselectAll();
            }
        });
    }
    
    /**
     * 미리보기
     */
    private void createPreview(Composite parent) {
        Group previewGroup = new Group(parent, SWT.NONE);
        previewGroup.setText("👁️ Preview");
        previewGroup.setLayout(new GridLayout(1, false));
        previewGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        previewText = new Text(previewGroup, SWT.BORDER | SWT.READ_ONLY);
        previewText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        previewText.setBackground(previewText.getDisplay().getSystemColor(SWT.COLOR_INFO_BACKGROUND));
    }
    
    /**
     * 미리보기 업데이트
     */
    private void updatePreview() {
        if (selectedVariable != null) {
            previewText.setText("Variable: " + selectedVariable);
        } else {
            previewText.setText("");
        }
        
        // OK 버튼 활성화
        Button okButton = getButton(IDialogConstants.OK_ID);
        if (okButton != null) {
            okButton.setEnabled(selectedVariable != null && !selectedVariable.isEmpty());
        }
    }
    
    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, "Insert", true);
        createButton(parent, IDialogConstants.CANCEL_ID, "Cancel", false);
        
        // 초기 상태에서 OK 버튼 비활성화
        getButton(IDialogConstants.OK_ID).setEnabled(false);
    }
    
    /**
     * 선택된 변수 반환
     */
    public String getVariable() {
        return selectedVariable;
    }
}