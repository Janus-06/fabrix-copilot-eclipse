package com.fabrix.copilot.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// [수정] 필요한 모든 클래스를 import
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

import com.fabrix.copilot.utils.CopilotLogger;

/**
 * 🔍 Context Collector - 통합 버전
 * Eclipse 에디터에서 코드, 프로젝트, 파일 등 다양한 컨텍스트를 수집하는 모든 기능을 담당합니다.
 */
public class ContextCollector {
    
    /**
     * 현재 에디터에서 선택된 코드 또는 현재 커서가 위치한 라인의 코드를 가져옵니다.
     * @return 선택된 코드 또는 현재 라인 텍스트
     */
    public String getCurrentCodeContext() {
        try {
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window == null) return "";
            
            IWorkbenchPage page = window.getActivePage();
            if (page == null) return "";
            
            IEditorPart editor = page.getActiveEditor();
            if (editor instanceof ITextEditor) {
                ITextEditor textEditor = (ITextEditor) editor;
                ISelectionProvider provider = textEditor.getSelectionProvider();
                if (provider == null) return "";
                
                ISelection selection = provider.getSelection();
                if (selection instanceof ITextSelection) {
                    ITextSelection textSelection = (ITextSelection) selection;
                    String selectedText = textSelection.getText();
                    
                    // 선택된 텍스트가 없으면 현재 줄의 내용을 반환
                    if (selectedText == null || selectedText.isEmpty()) {
                        try {
                            IDocument document = textEditor.getDocumentProvider().getDocument(editor.getEditorInput());
                            int line = textSelection.getStartLine();
                            IRegion lineInfo = document.getLineInformation(line);
                            return document.get(lineInfo.getOffset(), lineInfo.getLength()).trim();
                        } catch (Exception e) {
                            CopilotLogger.error("현재 라인 정보를 가져오는데 실패했습니다.", e);
                        }
                    }
                    return selectedText;
                }
            }
        } catch (Exception e) {
            CopilotLogger.error("코드 컨텍스트를 가져오는데 실패했습니다.", e);
        }
        return "";
    }
    
    /**
     * 현재 활성화된 파일이 속한 프로젝트의 이름을 가져옵니다.
     * @return 프로젝트 이름
     */
    public String getProjectContext() {
        try {
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window == null) return "Unknown Project";
            
            IWorkbenchPage page = window.getActivePage();
            if (page == null) return "Unknown Project";
            
            IEditorPart editor = page.getActiveEditor();
            if (editor != null) {
                IEditorInput input = editor.getEditorInput();
                if (input instanceof IFileEditorInput) {
                    IFile file = ((IFileEditorInput) input).getFile();
                    IProject project = file.getProject();
                    return project.getName();
                }
            }
        } catch (Exception e) {
            CopilotLogger.error("프로젝트 컨텍스트를 가져오는데 실패했습니다.", e);
        }
        return "Unknown Project";
    }
    
    /**
     * 현재 활성화된 에디터의 파일 이름을 가져옵니다.
     * @return 파일 이름
     */
    public String getCurrentFileName() {
        try {
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window == null) return "";
            
            IWorkbenchPage page = window.getActivePage();
            if (page == null) return "";
            
            IEditorPart editor = page.getActiveEditor();
            if (editor != null) {
                return editor.getEditorInput().getName();
            }
        } catch (Exception e) {
            CopilotLogger.error("현재 파일 이름을 가져오는데 실패했습니다.", e);
        }
        return "";
    }
    
    /**
     * 현재 파일의 확장자를 기반으로 프로그래밍 언어를 추측합니다.
     * @return 언어 이름 (e.g., "java", "python")
     */
    public String getCurrentFileLanguage() {
        String fileName = getCurrentFileName();
        if (fileName.isEmpty()) return "text";
        
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1) return "text";
        
        String extension = fileName.substring(lastDot + 1).toLowerCase();
        
        // 간단한 확장자-언어 매핑
        Map<String, String> languageMap = new HashMap<>();
        languageMap.put("java", "java");
        languageMap.put("js", "javascript");
        languageMap.put("py", "python");
        languageMap.put("sql", "sql");
        languageMap.put("xml", "xml");
        languageMap.put("html", "html");
        languageMap.put("css", "css");
        languageMap.put("md", "markdown");
        
        return languageMap.getOrDefault(extension, "text");
    }

    /**
     * 현재 파일의 전체 내용을 가져옵니다.
     * @return 파일 전체 내용 문자열
     */
    public String getCurrentFileContent() {
        try {
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window == null) return "";
            
            IWorkbenchPage page = window.getActivePage();
            if (page == null) return "";
            
            IEditorPart editor = page.getActiveEditor();
            if (editor instanceof ITextEditor) {
                ITextEditor textEditor = (ITextEditor) editor;
                IDocument document = textEditor.getDocumentProvider().getDocument(editor.getEditorInput());
                return document.get();
            }
        } catch (Exception e) {
            CopilotLogger.error("파일 내용을 가져오는데 실패했습니다.", e);
        }
        return "";
    }

    /**
     * 현재 파일의 구조(클래스, 메서드, 임포트) 정보를 분석하여 반환합니다.
     * @return FileStructure 객체
     */
    public FileStructure getFileStructure() {
        FileStructure structure = new FileStructure();
        try {
            String fileName = getCurrentFileName();
            String content = getCurrentFileContent();
            String language = getCurrentFileLanguage();
            
            structure.setFileName(fileName);
            structure.setLanguage(language);
            
            if ("java".equals(language)) {
                structure.setClasses(extractJavaClasses(content));
                structure.setMethods(extractJavaMethods(content));
                structure.setImports(extractJavaImports(content));
            }
        } catch (Exception e) {
            CopilotLogger.error("파일 구조를 분석하는데 실패했습니다.", e);
        }
        return structure;
    }

    // --- Private Helper Methods ---

    private List<String> extractJavaClasses(String content) {
        List<String> classes = new ArrayList<>();
        Pattern pattern = Pattern.compile("(?:public|private|protected)?\\s*(?:static|final|abstract)?\\s*class\\s+(\\w+)");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            classes.add(matcher.group(1));
        }
        return classes;
    }

    private List<String> extractJavaMethods(String content) {
        List<String> methods = new ArrayList<>();
        Pattern pattern = Pattern.compile("(?:public|private|protected)\\s+(?:static\\s+)?(?:final\\s+)?(?:\\w+(?:<[^>]+>)?\\s+)(\\w+)\\s*\\(");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String methodName = matcher.group(1);
            if (!methodName.equals("if") && !methodName.equals("for") && !methodName.equals("while") && !methodName.equals("switch")) {
                methods.add(methodName);
            }
        }
        return methods;
    }
    
    private List<String> extractJavaImports(String content) {
        List<String> imports = new ArrayList<>();
        Pattern pattern = Pattern.compile("import\\s+(?:static\\s+)?([\\w\\.\\*]+);");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            imports.add(matcher.group(1));
        }
        return imports;
    }
    
    /**
     * 파일 구조 정보를 담는 내부 클래스
     */
    public static class FileStructure {
        private String fileName;
        private String language;
        private List<String> classes = new ArrayList<>();
        private List<String> methods = new ArrayList<>();
        private List<String> imports = new ArrayList<>();
        
        // Getters and Setters
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public List<String> getClasses() { return classes; }
        public void setClasses(List<String> classes) { this.classes = classes; }
        public List<String> getMethods() { return methods; }
        public void setMethods(List<String> methods) { this.methods = methods; }
        public List<String> getImports() { return imports; }
        public void setImports(List<String> imports) { this.imports = imports; }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("File: ").append(fileName).append(" (").append(language).append(")\n");
            if (!classes.isEmpty()) sb.append("Classes: ").append(String.join(", ", classes)).append("\n");
            if (!methods.isEmpty()) sb.append("Methods: ").append(String.join(", ", methods)).append("\n");
            if (!imports.isEmpty()) sb.append("Imports: ").append(imports.size()).append(" imports\n");
            return sb.toString();
        }
    }
}