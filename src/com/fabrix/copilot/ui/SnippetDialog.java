package com.fabrix.copilot.ui;

import org.eclipse.swt.widgets.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.*;

import com.fabrix.copilot.core.SnippetManager;
import com.fabrix.copilot.core.SnippetManager.CodeSnippet;
import com.fabrix.copilot.utils.CopilotLogger;

import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.swt.graphics.*;
import org.eclipse.jface.window.Window;
/**
 * 📌 Snippet Dialog - 코드 스니펫 관리 UI
 * 
 * 코드 스니펫을 관리하고 선택하는 다이얼로그
 */
public class SnippetDialog extends Dialog {
    
    private final SnippetManager snippetManager;
    private String selectedSnippet;
    
    // UI 컴포넌트
    private Text searchText;
    private TableViewer snippetTableViewer;
    private StyledText previewText;
    private Combo languageFilter;
    private Combo tagFilter;
    private Label statusLabel;
    
    // 버튼
    private Button insertButton;
    private Button newButton;
    private Button editButton;
    private Button deleteButton;
    private Button importButton;
    private Button exportButton;
    
    public SnippetDialog(Shell parentShell, SnippetManager snippetManager) {
        super(parentShell);
        this.snippetManager = snippetManager;
        setShellStyle(getShellStyle() | SWT.RESIZE | SWT.MAX);
    }
    
    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("📌 Code Snippets Manager");
        shell.setSize(900, 600);
        
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
        createSearchBar(container);
        createMainContent(container);
        createStatusBar(container);
        
        loadSnippets();
        updateButtonStates();
        
        return container;
    }
    
    /**
     * 헤더 생성
     */
    private void createHeader(Composite parent) {
        Composite headerComposite = new Composite(parent, SWT.NONE);
        headerComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        headerComposite.setLayout(new GridLayout(2, false));
        
        Label titleLabel = new Label(headerComposite, SWT.NONE);
        titleLabel.setText("📌 Code Snippets Library");
        titleLabel.setFont(new Font(titleLabel.getDisplay(), "Segoe UI", 12, SWT.BOLD));
        
        Label countLabel = new Label(headerComposite, SWT.NONE);
        countLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
        countLabel.setText(snippetManager.getAllSnippets().size() + " snippets");
        
        Label separator = new Label(parent, SWT.HORIZONTAL | SWT.SEPARATOR);
        separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }
    
    /**
     * 검색 바 생성
     */
    private void createSearchBar(Composite parent) {
        Composite searchComposite = new Composite(parent, SWT.NONE);
        searchComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        searchComposite.setLayout(new GridLayout(6, false));
        
        // 검색 필드
        Label searchLabel = new Label(searchComposite, SWT.NONE);
        searchLabel.setText("🔍 Search:");
        
        searchText = new Text(searchComposite, SWT.BORDER | SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL);
        searchText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        searchText.setMessage("Search snippets...");
        searchText.addModifyListener(e -> filterSnippets());
        
        // 언어 필터
        Label langLabel = new Label(searchComposite, SWT.NONE);
        langLabel.setText("Language:");
        
        languageFilter = new Combo(searchComposite, SWT.READ_ONLY);
        languageFilter.add("All Languages");
        Set<String> languages = snippetManager.getAllSnippets().stream()
            .map(CodeSnippet::getLanguage)
            .collect(Collectors.toSet());
        languages.forEach(languageFilter::add);
        languageFilter.select(0);
        languageFilter.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                filterSnippets();
            }
        });
        
        // 태그 필터
        Label tagLabel = new Label(searchComposite, SWT.NONE);
        tagLabel.setText("Tag:");
        
        tagFilter = new Combo(searchComposite, SWT.READ_ONLY);
        tagFilter.add("All Tags");
        snippetManager.getAllTags().forEach(tagFilter::add);
        tagFilter.select(0);
        tagFilter.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                filterSnippets();
            }
        });
    }
    
    /**
     * 메인 컨텐츠 생성
     */
    private void createMainContent(Composite parent) {
        SashForm sashForm = new SashForm(parent, SWT.HORIZONTAL);
        sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        
        createSnippetList(sashForm);
        createPreviewArea(sashForm);
        
        sashForm.setWeights(new int[]{1, 1});
    }
    
    /**
     * 스니펫 리스트 생성
     */
    private void createSnippetList(Composite parent) {
        Composite listComposite = new Composite(parent, SWT.NONE);
        listComposite.setLayout(new GridLayout(1, false));
        
        // 툴바
        ToolBar toolBar = new ToolBar(listComposite, SWT.FLAT);
        toolBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        ToolItem newItem = new ToolItem(toolBar, SWT.PUSH);
        newItem.setText("➕ New");
        newItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                createNewSnippet();
            }
        });
        
        ToolItem editItem = new ToolItem(toolBar, SWT.PUSH);
        editItem.setText("✏️ Edit");
        editItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                editSelectedSnippet();
            }
        });
        
        ToolItem deleteItem = new ToolItem(toolBar, SWT.PUSH);
        deleteItem.setText("🗑️ Delete");
        deleteItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                deleteSelectedSnippets();
            }
        });
        
        new ToolItem(toolBar, SWT.SEPARATOR);
        
        ToolItem importItem = new ToolItem(toolBar, SWT.PUSH);
        importItem.setText("📥 Import");
        importItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                importSnippets();
            }
        });
        
        ToolItem exportItem = new ToolItem(toolBar, SWT.PUSH);
        exportItem.setText("📤 Export");
        exportItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                exportSnippets();
            }
        });
        
        // 스니펫 테이블
        snippetTableViewer = new TableViewer(listComposite, 
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.V_SCROLL);
        
        Table table = snippetTableViewer.getTable();
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        
        createTableColumns();
        
        // 컨텐트 프로바이더와 라벨 프로바이더
        snippetTableViewer.setContentProvider(ArrayContentProvider.getInstance());
        snippetTableViewer.setLabelProvider(new SnippetLabelProvider());
        
        // 선택 리스너
        snippetTableViewer.addSelectionChangedListener(event -> {
            updatePreview();
            updateButtonStates();
        });
        
        // 더블클릭으로 삽입
        snippetTableViewer.addDoubleClickListener(event -> {
            insertSelectedSnippet();
        });
    }
    
    /**
     * 테이블 컬럼 생성
     */
    private void createTableColumns() {
        // 이름 컬럼
        TableViewerColumn nameColumn = new TableViewerColumn(snippetTableViewer, SWT.NONE);
        nameColumn.getColumn().setText("Name");
        nameColumn.getColumn().setWidth(200);
        
        // 언어 컬럼
        TableViewerColumn langColumn = new TableViewerColumn(snippetTableViewer, SWT.NONE);
        langColumn.getColumn().setText("Language");
        langColumn.getColumn().setWidth(100);
        
        // 태그 컬럼
        TableViewerColumn tagsColumn = new TableViewerColumn(snippetTableViewer, SWT.NONE);
        tagsColumn.getColumn().setText("Tags");
        tagsColumn.getColumn().setWidth(150);
        
        // 생성일 컬럼
        TableViewerColumn dateColumn = new TableViewerColumn(snippetTableViewer, SWT.NONE);
        dateColumn.getColumn().setText("Created");
        dateColumn.getColumn().setWidth(100);
    }
    
    /**
     * 미리보기 영역 생성
     */
    private void createPreviewArea(Composite parent) {
        Composite previewComposite = new Composite(parent, SWT.NONE);
        previewComposite.setLayout(new GridLayout(1, false));
        
        Label previewLabel = new Label(previewComposite, SWT.NONE);
        previewLabel.setText("👁️ Preview");
        previewLabel.setFont(new Font(previewLabel.getDisplay(), "Segoe UI", 10, SWT.BOLD));
        
        previewText = new StyledText(previewComposite, 
            SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
        previewText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        previewText.setFont(new Font(previewText.getDisplay(), "Consolas", 10, SWT.NORMAL));
        
        // 스니펫 정보 패널
        Group infoGroup = new Group(previewComposite, SWT.NONE);
        infoGroup.setText("📋 Snippet Information");
        infoGroup.setLayout(new GridLayout(2, false));
        infoGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        
        createInfoLabels(infoGroup);
    }
    
    /**
     * 정보 레이블 생성
     */
    private void createInfoLabels(Composite parent) {
        Label descLabel = new Label(parent, SWT.NONE);
        descLabel.setText("Description:");
        
        Label descValue = new Label(parent, SWT.WRAP);
        descValue.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        descValue.setData("description");
        
        Label tagsLabel = new Label(parent, SWT.NONE);
        tagsLabel.setText("Tags:");
        
        Label tagsValue = new Label(parent, SWT.NONE);
        tagsValue.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        tagsValue.setData("tags");
        
        Label sizeLabel = new Label(parent, SWT.NONE);
        sizeLabel.setText("Size:");
        
        Label sizeValue = new Label(parent, SWT.NONE);
        sizeValue.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        sizeValue.setData("size");
    }
    
    /**
     * 상태 바 생성
     */
    private void createStatusBar(Composite parent) {
        Composite statusComposite = new Composite(parent, SWT.NONE);
        statusComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        statusComposite.setLayout(new GridLayout(2, false));
        
        statusLabel = new Label(statusComposite, SWT.NONE);
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        updateStatus("Ready");
        
        Label helpLabel = new Label(statusComposite, SWT.NONE);
        helpLabel.setText("💡 Double-click to insert snippet");
        helpLabel.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
    }
    
    /**
     * 스니펫 로드
     */
    private void loadSnippets() {
        List<CodeSnippet> snippets = snippetManager.getAllSnippets();
        snippetTableViewer.setInput(snippets);
        updateStatus(snippets.size() + " snippets loaded");
    }
    
    /**
     * 스니펫 필터링
     */
    private void filterSnippets() {
        String searchQuery = searchText.getText().toLowerCase();
        String selectedLang = languageFilter.getText();
        String selectedTag = tagFilter.getText();
        
        List<CodeSnippet> allSnippets = snippetManager.getAllSnippets();
        List<CodeSnippet> filtered = allSnippets.stream()
            .filter(s -> {
                // 검색어 필터
                if (!searchQuery.isEmpty() && !s.matches(searchQuery)) {
                    return false;
                }
                
                // 언어 필터
                if (!"All Languages".equals(selectedLang) && 
                    !s.getLanguage().equals(selectedLang)) {
                    return false;
                }
                
                // 태그 필터
                if (!"All Tags".equals(selectedTag) && 
                    !s.getTags().contains(selectedTag)) {
                    return false;
                }
                
                return true;
            })
            .collect(Collectors.toList());
        
        snippetTableViewer.setInput(filtered);
        updateStatus(filtered.size() + " snippets found");
    }
    
    /**
     * 미리보기 업데이트
     */
    private void updatePreview() {
        IStructuredSelection selection = (IStructuredSelection) snippetTableViewer.getSelection();
        if (!selection.isEmpty()) {
            CodeSnippet snippet = (CodeSnippet) selection.getFirstElement();
            
            // 코드 표시
            previewText.setText(snippet.getCode());
            
            // 구문 하이라이팅 (간단한 구현)
            applySyntaxHighlighting(snippet.getLanguage());
            
            // 정보 업데이트
            updateInfoLabels(snippet);
        } else {
            previewText.setText("");
            clearInfoLabels();
        }
    }
    
    /**
     * 구문 하이라이팅
     */
    private void applySyntaxHighlighting(String language) {
        // 언어별 키워드
        Set<String> keywords = getKeywords(language);
        
        String text = previewText.getText();
        
        // 키워드 하이라이팅
        for (String keyword : keywords) {
            highlightWord(text, keyword, SWT.COLOR_DARK_BLUE);
        }
        
        // 문자열 하이라이팅
        highlightPattern(text, "\"[^\"]*\"", SWT.COLOR_DARK_GREEN);
        highlightPattern(text, "'[^']*'", SWT.COLOR_DARK_GREEN);
        
        // 주석 하이라이팅
        highlightPattern(text, "//[^\n]*", SWT.COLOR_DARK_GRAY);
        highlightPattern(text, "/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", SWT.COLOR_DARK_GRAY);
    }
    
    /**
     * 언어별 키워드 가져오기
     */
    private Set<String> getKeywords(String language) {
        Set<String> keywords = new HashSet<>();
        
        switch (language.toLowerCase()) {
            case "java":
                keywords.addAll(Arrays.asList(
                    "public", "private", "protected", "class", "interface", "extends", 
                    "implements", "static", "final", "void", "int", "String", "boolean",
                    "if", "else", "for", "while", "return", "new", "this", "super"
                ));
                break;
            case "javascript":
                keywords.addAll(Arrays.asList(
                    "function", "var", "let", "const", "if", "else", "for", "while",
                    "return", "class", "extends", "new", "this", "async", "await"
                ));
                break;
            case "python":
                keywords.addAll(Arrays.asList(
                    "def", "class", "if", "elif", "else", "for", "while", "in",
                    "return", "import", "from", "as", "self", "True", "False", "None"
                ));
                break;
        }
        
        return keywords;
    }
    
    /**
     * 단어 하이라이팅
     */
    private void highlightWord(String text, String word, int colorId) {
        String pattern = "\\b" + word + "\\b";
        highlightPattern(text, pattern, colorId);
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
                style.foreground = previewText.getDisplay().getSystemColor(colorId);
                previewText.setStyleRange(style);
            }
        } catch (Exception e) {
            // 하이라이팅 실패는 무시
        }
    }
    
    /**
     * 정보 레이블 업데이트
     */
    private void updateInfoLabels(CodeSnippet snippet) {
        Control[] children = ((Composite) previewText.getParent()).getChildren();
        for (Control child : children) {
            if (child instanceof Group) {
                Group group = (Group) child;
                for (Control label : group.getChildren()) {
                    if (label instanceof Label && label.getData() != null) {
                        Label valueLabel = (Label) label;
                        String key = (String) valueLabel.getData();
                        
                        switch (key) {
                            case "description":
                                valueLabel.setText(snippet.getDescription() != null ? 
                                    snippet.getDescription() : "No description");
                                break;
                            case "tags":
                                valueLabel.setText(String.join(", ", snippet.getTags()));
                                break;
                            case "size":
                                valueLabel.setText(snippet.getCode().length() + " characters");
                                break;
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 정보 레이블 클리어
     */
    private void clearInfoLabels() {
        updateInfoLabels(new CodeSnippet("", "", "", "", new Date(), new ArrayList<>(), ""));
    }
    
    /**
     * 새 스니펫 생성
     */
    private void createNewSnippet() {
        SnippetEditDialog dialog = new SnippetEditDialog(getShell(), null);
        if (dialog.open() == Window.OK) {
            CodeSnippet newSnippet = dialog.getSnippet(); // 오류 없도록 수정됨
            snippetManager.saveSnippet(
                newSnippet.getName(),
                newSnippet.getCode(),
                newSnippet.getLanguage()
            );
            
            // 설명과 태그 추가
            if (newSnippet.getDescription() != null) {
                CodeSnippet saved = snippetManager.getSnippetByName(newSnippet.getName());
                if (saved != null) {
                    saved.setDescription(newSnippet.getDescription());
                    newSnippet.getTags().forEach(tag -> snippetManager.addTag(saved.getId(), tag));
                }
            }
            
            loadSnippets();
            updateStatus("✅ Snippet created: " + newSnippet.getName());
        }
    }
    
    /**
     * 선택된 스니펫 편집
     */
    private void editSelectedSnippet() {
        IStructuredSelection selection = (IStructuredSelection) snippetTableViewer.getSelection();
        if (!selection.isEmpty()) {
            CodeSnippet snippet = (CodeSnippet) selection.getFirstElement();
            SnippetEditDialog dialog = new SnippetEditDialog(getShell(), snippet);
            
            if (dialog.open() == Window.OK) {
                CodeSnippet edited = dialog.getSnippet(); // 오류 없도록 수정됨
                snippetManager.updateSnippet(
                    snippet.getId(),
                    edited.getName(),
                    edited.getCode(),
                    edited.getLanguage()
                );
                
                loadSnippets();
                updateStatus("✅ Snippet updated: " + edited.getName());
            }
        }
    }
    
    /**
     * 선택된 스니펫 삭제
     */
    private void deleteSelectedSnippets() {
        IStructuredSelection selection = (IStructuredSelection) snippetTableViewer.getSelection();
        if (!selection.isEmpty()) {
            List<CodeSnippet> toDelete = selection.toList();
            
            String message = toDelete.size() == 1 ? 
                "Delete snippet '" + toDelete.get(0).getName() + "'?" :
                "Delete " + toDelete.size() + " snippets?";
            
            boolean confirm = MessageDialog.openConfirm(getShell(), "Delete Snippets", message);
            
            if (confirm) {
                for (CodeSnippet snippet : toDelete) {
                    snippetManager.deleteSnippet(snippet.getId());
                }
                
                loadSnippets();
                updateStatus("✅ " + toDelete.size() + " snippet(s) deleted");
            }
        }
    }
    
    /**
     * 스니펫 가져오기
     */
    private void importSnippets() {
        FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
        dialog.setFilterExtensions(new String[]{"*.json", "*.*"});
        dialog.setFilterNames(new String[]{"JSON Files", "All Files"});
        dialog.setText("Import Snippets");
        
        String path = dialog.open();
        if (path != null) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get(path)));
                snippetManager.importSnippets(content);
                
                loadSnippets();
                updateStatus("✅ Snippets imported successfully");
                
            } catch (Exception e) {
                MessageDialog.openError(getShell(), "Import Error", 
                    "Failed to import snippets: " + e.getMessage());
                CopilotLogger.error("Snippet import failed", e);
            }
        }
    }
    
    /**
     * 스니펫 내보내기
     */
    private void exportSnippets() {
        IStructuredSelection selection = (IStructuredSelection) snippetTableViewer.getSelection();
        List<String> ids = null;
        
        if (!selection.isEmpty()) {
            // [수정] 스트림을 사용하여 안전하게 ID 목록을 추출
            ids = (List<String>) selection.toList().stream()
                .filter(item -> item instanceof CodeSnippet)
                .map(item -> ((CodeSnippet) item).getId())
                .collect(Collectors.toList());
        }
        
        FileDialog dialog = new FileDialog(getShell(), SWT.SAVE);
        dialog.setFilterExtensions(new String[]{"*.json"});
        dialog.setFilterNames(new String[]{"JSON Files"});
        dialog.setFileName("code-snippets.json");
        dialog.setText("Export Snippets");
        
        String path = dialog.open();
        if (path != null) {
            try {
                String json = snippetManager.exportSnippets(ids);
                java.nio.file.Files.write(java.nio.file.Paths.get(path), json.getBytes());
                
                int count = ids != null ? ids.size() : snippetManager.getAllSnippets().size();
                updateStatus("✅ " + count + " snippet(s) exported");
                
            } catch (Exception e) {
                MessageDialog.openError(getShell(), "Export Error", 
                    "Failed to export snippets: " + e.getMessage());
                CopilotLogger.error("Snippet export failed", e);
            }
        }
    }
    
    /**
     * 선택된 스니펫 삽입
     */
    private void insertSelectedSnippet() {
        IStructuredSelection selection = (IStructuredSelection) snippetTableViewer.getSelection();
        if (!selection.isEmpty()) {
            CodeSnippet snippet = (CodeSnippet) selection.getFirstElement();
            selectedSnippet = snippet.getCode();
            okPressed();
        }
    }
    
    /**
     * 버튼 상태 업데이트
     */
    private void updateButtonStates() {
        IStructuredSelection selection = (IStructuredSelection) snippetTableViewer.getSelection();
        boolean hasSelection = !selection.isEmpty();
        
        if (insertButton != null && !insertButton.isDisposed()) {
            insertButton.setEnabled(hasSelection);
        }
    }
    
    /**
     * 상태 업데이트
     */
    private void updateStatus(String message) {
        if (statusLabel != null && !statusLabel.isDisposed()) {
            statusLabel.setText(message);
        }
    }
    
    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        insertButton = createButton(parent, IDialogConstants.OK_ID, "Insert", true);
        createButton(parent, IDialogConstants.CANCEL_ID, "Close", false);
        
        updateButtonStates();
    }
    
    @Override
    protected void okPressed() {
        IStructuredSelection selection = (IStructuredSelection) snippetTableViewer.getSelection();
        if (!selection.isEmpty()) {
            CodeSnippet snippet = (CodeSnippet) selection.getFirstElement();
            selectedSnippet = snippet.getCode();
        }
        super.okPressed();
    }
    
    /**
     * 선택된 스니펫 코드 반환
     */
    public String getSelectedSnippet() {
        return selectedSnippet;
    }
    
    /**
     * 스니펫 라벨 프로바이더
     */
    private class SnippetLabelProvider extends LabelProvider implements ITableLabelProvider {
        private final java.text.SimpleDateFormat dateFormat = 
            new java.text.SimpleDateFormat("yyyy-MM-dd");
        
        @Override
        public String getColumnText(Object element, int columnIndex) {
            if (element instanceof CodeSnippet) {
                CodeSnippet snippet = (CodeSnippet) element;
                switch (columnIndex) {
                    case 0: return snippet.getName();
                    case 1: return snippet.getLanguage();
                    case 2: return String.join(", ", snippet.getTags());
                    case 3: return dateFormat.format(snippet.getCreatedAt());
                }
            }
            return "";
        }
        
        @Override
        public Image getColumnImage(Object element, int columnIndex) {
            return null;
        }
    }
}