package com.fabrix.copilot.ui;

import org.eclipse.swt.widgets.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;

import com.fabrix.copilot.core.SnippetManager;
import com.fabrix.copilot.core.SnippetManager.CodeSnippet;

import java.util.*;
import java.util.List;

/**
 * ✏️ Snippet Edit Dialog - 스니펫 편집
 * 
 * 코드 스니펫을 생성하거나 편집하는 다이얼로그
 */
public class SnippetEditDialog extends Dialog {
    
    private Text nameText;
    private Combo languageCombo;
    private StyledText codeText;
    private Text descriptionText;
    private Text tagsText;
    
    private SnippetManager.CodeSnippet snippet;
    private String name;
    private String code;
    private String language;
    private String description;
    private List<String> tags;

    public CodeSnippet getSnippet() {
        // 이 메서드는 okPressed() 이후에 호출되어야 유효한 값을 가짐
        return new CodeSnippet(
            (snippet != null) ? snippet.getId() : "", // 기존 스니펫 ID 또는 임시 ID
            name,
            code,
            language,
            new Date(),
            tags,
            description
        );
    }
    
    public SnippetEditDialog(Shell parentShell, SnippetManager.CodeSnippet snippet) {
        super(parentShell);
        this.snippet = snippet;
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }
    
    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(snippet == null ? "✏️ 새 스니펫" : "✏️ 스니펫 편집");
        shell.setSize(600, 500);
        
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
        
        createNameSection(container);
        createLanguageSection(container);
        createCodeSection(container);
        createMetadataSection(container);
        
        if (snippet != null) {
            loadSnippetData();
        }
        
        return container;
    }
    
    /**
     * 이름 섹션 생성
     */
    private void createNameSection(Composite parent) {
        Composite nameComposite = new Composite(parent, SWT.NONE);
        nameComposite.setLayout(new GridLayout(2, false));
        nameComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        Label nameLabel = new Label(nameComposite, SWT.NONE);
        nameLabel.setText("이름:");
        
        nameText = new Text(nameComposite, SWT.BORDER);
        nameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        nameText.setMessage("스니펫 이름 입력...");
        nameText.addModifyListener(e -> validateInput());
    }
    
    /**
     * 언어 섹션 생성
     */
    private void createLanguageSection(Composite parent) {
        Composite langComposite = new Composite(parent, SWT.NONE);
        langComposite.setLayout(new GridLayout(2, false));
        langComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        Label langLabel = new Label(langComposite, SWT.NONE);
        langLabel.setText("언어:");
        
        languageCombo = new Combo(langComposite, SWT.READ_ONLY);
        languageCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        // 지원 언어 추가
        String[] languages = {
            "java", "javascript", "python", "c", "cpp", "csharp",
            "php", "ruby", "go", "rust", "kotlin", "swift",
            "sql", "xml", "html", "css", "json", "yaml", "markdown",
            "shell", "powershell", "dockerfile", "text"
        };
        
        for (String lang : languages) {
            languageCombo.add(lang);
        }
        
        languageCombo.select(0); // 기본값 java
    }
    
    /**
     * 코드 섹션 생성
     */
    private void createCodeSection(Composite parent) {
        Label codeLabel = new Label(parent, SWT.NONE);
        codeLabel.setText("코드:");
        
        codeText = new StyledText(parent, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
        GridData codeData = new GridData(SWT.FILL, SWT.FILL, true, true);
        codeData.heightHint = 200;
        codeText.setLayoutData(codeData);
        codeText.setFont(new Font(codeText.getDisplay(), "Consolas", 10, SWT.NORMAL));
        codeText.addModifyListener(e -> validateInput());
        
        // 탭 키 처리
        codeText.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.keyCode == SWT.TAB) {
                    e.doit = false;
                    codeText.insert("    ");
                }
            }
        });
    }
    
    /**
     * 메타데이터 섹션 생성
     */
    private void createMetadataSection(Composite parent) {
        Group metaGroup = new Group(parent, SWT.NONE);
        metaGroup.setText("추가 정보");
        metaGroup.setLayout(new GridLayout(2, false));
        metaGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        
        // 설명
        Label descLabel = new Label(metaGroup, SWT.NONE);
        descLabel.setText("설명:");
        
        descriptionText = new Text(metaGroup, SWT.BORDER);
        descriptionText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        descriptionText.setMessage("스니펫 설명 (선택사항)");
        
        // 태그
        Label tagsLabel = new Label(metaGroup, SWT.NONE);
        tagsLabel.setText("태그:");
        
        tagsText = new Text(metaGroup, SWT.BORDER);
        tagsText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        tagsText.setMessage("쉼표로 구분 (예: util, helper, validation)");
    }
    
    /**
     * 기존 스니펫 데이터 로드
     */
    private void loadSnippetData() {
        if (snippet == null) return;
        
        nameText.setText(snippet.getName());
        codeText.setText(snippet.getCode());
        
        // 언어 선택
        int langIndex = languageCombo.indexOf(snippet.getLanguage());
        if (langIndex >= 0) {
            languageCombo.select(langIndex);
        } else {
            languageCombo.setText(snippet.getLanguage());
        }
        
        // 설명
        if (snippet.getDescription() != null) {
            descriptionText.setText(snippet.getDescription());
        }
        
        // 태그
        if (!snippet.getTags().isEmpty()) {
            tagsText.setText(String.join(", ", snippet.getTags()));
        }
    }
    
    /**
     * 입력 검증
     */
    private void validateInput() {
        Button okButton = getButton(IDialogConstants.OK_ID);
        if (okButton != null) {
            boolean valid = !nameText.getText().trim().isEmpty() && 
                           !codeText.getText().trim().isEmpty();
            okButton.setEnabled(valid);
        }
    }
    
    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, "저장", true);
        createButton(parent, IDialogConstants.CANCEL_ID, "취소", false);
        
        validateInput();
    }
    
    @Override
    protected void okPressed() {
        // 데이터 수집
        name = nameText.getText().trim();
        code = codeText.getText();
        language = languageCombo.getText();
        description = descriptionText.getText().trim();
        
        // 태그 파싱
        tags = new ArrayList<>();
        String tagsInput = tagsText.getText().trim();
        if (!tagsInput.isEmpty()) {
            String[] tagArray = tagsInput.split(",");
            for (String tag : tagArray) {
                String trimmed = tag.trim();
                if (!trimmed.isEmpty()) {
                    tags.add(trimmed);
                }
            }
        }
        
        super.okPressed();
    }
    
    // Getter 메서드들
    public String getName() { return name; }
    public String getCode() { return code; }
    public String getLanguage() { return language; }
    public String getDescription() { return description; }
    public List<String> getTags() { return tags; }
}