package com.fabrix.copilot.ui;

import org.eclipse.swt.widgets.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.SWT;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;

/**
 * 📝 Input Dialog - 간단한 텍스트 입력 다이얼로그
 */
public class InputDialog extends Dialog {
    
    private String title;
    private String message;
    private String value = "";
    private Text inputText;
    
    public InputDialog(Shell parentShell, String title, String message) {
        super(parentShell);
        this.title = title;
        this.message = message;
    }
    
    /**
     * 정적 헬퍼 메서드
     */
    public static String open(Shell parentShell, String title, String message) {
        InputDialog dialog = new InputDialog(parentShell, title, message);
        if (dialog.open() == Dialog.OK) {
            return dialog.getValue();
        }
        return null;
    }
    
    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(title);
    }
    
    @Override
    protected Control createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        container.setLayout(new GridLayout(1, false));
        
        Label messageLabel = new Label(container, SWT.NONE);
        messageLabel.setText(message);
        messageLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        inputText = new Text(container, SWT.BORDER);
        GridData inputData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        inputData.widthHint = 300;
        inputText.setLayoutData(inputData);
        inputText.setText(value);
        inputText.selectAll();
        
        return container;
    }
    
    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, "확인", true);
        createButton(parent, IDialogConstants.CANCEL_ID, "취소", false);
    }
    
    @Override
    protected void okPressed() {
        value = inputText.getText();
        super.okPressed();
    }
    
    public String getValue() {
        return value;
    }
    
    public void setValue(String value) {
        this.value = value;
        if (inputText != null && !inputText.isDisposed()) {
            inputText.setText(value);
        }
    }
}