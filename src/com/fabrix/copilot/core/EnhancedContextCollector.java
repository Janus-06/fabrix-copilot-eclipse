package com.fabrix.copilot.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.swt.widgets.Display;
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
 * 🔍 Enhanced Context Collector - 수정된 버전
 * UI 스레드 동기화 문제 해결 및 안전한 파일 접근
 */
public class EnhancedContextCollector extends ContextCollector {
    
    private static final int MAX_CONTEXT_LENGTH = 10000;
    private static final int DEFAULT_CONTEXT_LINES = 50;
    private static final long TIMEOUT_SECONDS = 5;
    
    /**
     * 열려있는 모든 파일의 이름 목록을 가져옵니다.
     * asyncExec을 사용하여 데드락 방지
     */
    public List<String> getOpenFileNames() {
        List<String> fileNames = new ArrayList<>();
        
        try {
            // CompletableFuture를 사용하여 비동기 처리
            CompletableFuture<List<String>> future = new CompletableFuture<>();
            
            Display display = Display.getDefault();
            if (display == null || display.isDisposed()) {
                CopilotLogger.warn("Display is not available");
                return fileNames;
            }
            
            display.asyncExec(() -> {
                List<String> result = new ArrayList<>();
                try {
                    // Workbench가 준비되었는지 확인
                    if (!PlatformUI.isWorkbenchRunning()) {
                        CopilotLogger.warn("Workbench is not running");
                        future.complete(result);
                        return;
                    }
                    
                    IWorkbenchWindow[] windows = PlatformUI.getWorkbench().getWorkbenchWindows();
                    if (windows == null || windows.length == 0) {
                        CopilotLogger.info("No workbench windows found");
                        future.complete(result);
                        return;
                    }
                    
                    CopilotLogger.info("Found " + windows.length + " workbench windows");
                    
                    for (IWorkbenchWindow window : windows) {
                        if (window == null) continue;
                        
                        IWorkbenchPage[] pages = window.getPages();
                        if (pages == null) continue;
                        
                        for (IWorkbenchPage page : pages) {
                            if (page == null) continue;
                            
                            IEditorReference[] editors = page.getEditorReferences();
                            if (editors == null) continue;
                            
                            CopilotLogger.info("Page has " + editors.length + " open editors");
                            
                            for (IEditorReference ref : editors) {
                                try {
                                    // 에디터가 이미 초기화되었는지 확인
                                    IEditorPart editor = ref.getEditor(false);
                                    if (editor != null) {
                                        IEditorInput input = editor.getEditorInput();
                                        if (input != null) {
                                            String fileName = input.getName();
                                            if (fileName != null && !fileName.isEmpty()) {
                                                result.add(fileName);
                                                CopilotLogger.debug("Found open file: " + fileName);
                                            }
                                        }
                                    } else {
                                        // 에디터가 초기화되지 않은 경우 에디터 입력에서 이름 가져오기
                                        String name = ref.getName();
                                        if (name != null && !name.isEmpty()) {
                                            result.add(name);
                                            CopilotLogger.debug("Found editor by name: " + name);
                                        }
                                    }
                                } catch (Exception e) {
                                    CopilotLogger.warn("Failed to access editor: " + e.getMessage());
                                }
                            }
                        }
                    }
                    
                    // 중복 제거
                    result = result.stream().distinct().collect(Collectors.toList());
                    
                } catch (Exception e) {
                    CopilotLogger.error("Error in UI thread while getting open files", e);
                } finally {
                    future.complete(result);
                }
            });
            
            // 타임아웃을 두고 결과 대기
            fileNames = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            CopilotLogger.error("Failed to get open file names", e);
        }
        
        CopilotLogger.info("Total open files found: " + fileNames.size());
        return fileNames;
    }
    
    /**
     * 특정 파일의 내용을 가져옵니다.
     * 안전한 비동기 처리
     */
    public String getFileContent(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        
        try {
            CompletableFuture<String> future = new CompletableFuture<>();
            
            Display display = Display.getDefault();
            if (display == null || display.isDisposed()) {
                CopilotLogger.warn("Display is not available");
                return "";
            }
            
            display.asyncExec(() -> {
                String content = "";
                try {
                    if (!PlatformUI.isWorkbenchRunning()) {
                        future.complete("");
                        return;
                    }
                    
                    IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                    if (window != null) {
                        IWorkbenchPage page = window.getActivePage();
                        if (page != null) {
                            IEditorReference[] editors = page.getEditorReferences();
                            
                            for (IEditorReference ref : editors) {
                                try {
                                    // 파일 이름 매칭
                                    String editorName = ref.getName();
                                    if (fileName.equals(editorName)) {
                                        IEditorPart editor = ref.getEditor(false);
                                        if (editor instanceof ITextEditor) {
                                            ITextEditor textEditor = (ITextEditor) editor;
                                            IEditorInput input = editor.getEditorInput();
                                            if (input != null) {
                                                IDocument document = textEditor.getDocumentProvider().getDocument(input);
                                                if (document != null) {
                                                    content = document.get();
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    CopilotLogger.warn("Error accessing editor content: " + e.getMessage());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    CopilotLogger.error("Error getting file content for: " + fileName, e);
                } finally {
                    future.complete(content);
                }
            });
            
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            CopilotLogger.error("Failed to get file content: " + fileName, e);
            return "";
        }
    }
    
    /**
     * 현재 메서드의 시그니처를 가져옵니다.
     */
    public String getCurrentMethodSignature() {
        try {
            CompletableFuture<String> future = new CompletableFuture<>();
            
            Display display = Display.getDefault();
            if (display == null || display.isDisposed()) {
                return "";
            }
            
            display.asyncExec(() -> {
                String signature = "";
                try {
                    IEditorPart editor = getActiveEditor();
                    if (!(editor instanceof ITextEditor)) {
                        future.complete("");
                        return;
                    }
                    
                    ITextEditor textEditor = (ITextEditor) editor;
                    IEditorInput input = editor.getEditorInput();
                    
                    if (input instanceof IFileEditorInput) {
                        IFile file = ((IFileEditorInput) input).getFile();
                        if (isJavaFile(file)) {
                            signature = getJavaMethodSignature(file, textEditor);
                        }
                    }
                } catch (Exception e) {
                    CopilotLogger.error("Failed to get method signature", e);
                } finally {
                    future.complete(signature);
                }
            });
            
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            CopilotLogger.error("Failed to get current method signature", e);
            return "";
        }
    }
    
    /**
     * 현재 클래스의 구조를 가져옵니다.
     */
    public ClassStructure getCurrentClassStructure() {
        try {
            CompletableFuture<ClassStructure> future = new CompletableFuture<>();
            
            Display display = Display.getDefault();
            if (display == null || display.isDisposed()) {
                return null;
            }
            
            display.asyncExec(() -> {
                ClassStructure structure = null;
                try {
                    IEditorPart editor = getActiveEditor();
                    if (!(editor instanceof ITextEditor)) {
                        future.complete(null);
                        return;
                    }
                    
                    IEditorInput input = editor.getEditorInput();
                    if (input instanceof IFileEditorInput) {
                        IFile file = ((IFileEditorInput) input).getFile();
                        if (isJavaFile(file)) {
                            structure = analyzeJavaClass(file);
                        }
                    }
                } catch (Exception e) {
                    CopilotLogger.error("Failed to get class structure", e);
                } finally {
                    future.complete(structure);
                }
            });
            
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            CopilotLogger.error("Failed to get current class structure", e);
            return null;
        }
    }
    
    /**
     * 프로젝트의 의존성 정보를 가져옵니다.
     */
    public ProjectDependencies getProjectDependencies() {
        ProjectDependencies deps = new ProjectDependencies();
        
        try {
            IProject project = getCurrentProject();
            if (project != null && project.exists() && project.isOpen()) {
                if (project.hasNature(JavaCore.NATURE_ID)) {
                    IJavaProject javaProject = JavaCore.create(project);
                    
                    // 클래스패스 엔트리 분석
                    deps.setClasspathEntries(analyzeClasspath(javaProject));
                    
                    // 참조 프로젝트
                    deps.setReferencedProjects(getReferencedProjects(project));
                }
            }
        } catch (Exception e) {
            CopilotLogger.error("Failed to get project dependencies", e);
        }
        
        return deps;
    }
    
    /**
     * 확장된 코드 컨텍스트를 가져옵니다.
     */
    public String getExtendedCodeContext() {
        StringBuilder context = new StringBuilder();
        
        try {
            // 현재 선택된 코드
            String selectedCode = getCurrentCodeContext();
            if (!selectedCode.isEmpty()) {
                context.append("=== Selected Code ===\n");
                context.append(selectedCode).append("\n\n");
            }
            
            // 현재 메서드
            String methodSig = getCurrentMethodSignature();
            if (!methodSig.isEmpty()) {
                context.append("=== Current Method ===\n");
                context.append(methodSig).append("\n\n");
            }
            
            // 클래스 구조
            ClassStructure classStruct = getCurrentClassStructure();
            if (classStruct != null) {
                context.append("=== Class Structure ===\n");
                context.append(classStruct.toString()).append("\n\n");
            }
            
            // 주변 코드
            String surroundingCode = getSurroundingCode();
            if (!surroundingCode.isEmpty()) {
                context.append("=== Surrounding Code ===\n");
                context.append(surroundingCode).append("\n");
            }
            
        } catch (Exception e) {
            CopilotLogger.error("Failed to get extended context", e);
        }
        
        return truncateContext(context.toString());
    }
    
    /**
     * 현재 커서 주변의 코드를 가져옵니다.
     */
    private String getSurroundingCode() {
        try {
            CompletableFuture<String> future = new CompletableFuture<>();
            
            Display display = Display.getDefault();
            if (display == null || display.isDisposed()) {
                return "";
            }
            
            display.asyncExec(() -> {
                String code = "";
                try {
                    ITextEditor editor = getActiveTextEditor();
                    if (editor == null) {
                        future.complete("");
                        return;
                    }
                    
                    IDocument document = editor.getDocumentProvider().getDocument(editor.getEditorInput());
                    ITextSelection selection = (ITextSelection) editor.getSelectionProvider().getSelection();
                    
                    int offset = selection.getOffset();
                    int startLine = Math.max(0, selection.getStartLine() - DEFAULT_CONTEXT_LINES / 2);
                    int endLine = Math.min(document.getNumberOfLines() - 1, 
                                          selection.getEndLine() + DEFAULT_CONTEXT_LINES / 2);
                    
                    StringBuilder codeBuilder = new StringBuilder();
                    for (int i = startLine; i <= endLine; i++) {
                        String line = document.get(document.getLineOffset(i), document.getLineLength(i));
                        codeBuilder.append(line);
                    }
                    
                    code = codeBuilder.toString();
                    
                } catch (Exception e) {
                    CopilotLogger.warn("Failed to get surrounding code: " + e.getMessage());
                } finally {
                    future.complete(code);
                }
            });
            
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            return "";
        }
    }
    
    // Helper methods
    
    private IEditorPart getActiveEditor() {
        try {
            if (!PlatformUI.isWorkbenchRunning()) {
                return null;
            }
            
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window != null) {
                IWorkbenchPage page = window.getActivePage();
                if (page != null) {
                    return page.getActiveEditor();
                }
            }
        } catch (Exception e) {
            CopilotLogger.warn("Failed to get active editor: " + e.getMessage());
        }
        return null;
    }
    
    private ITextEditor getActiveTextEditor() {
        IEditorPart editor = getActiveEditor();
        if (editor instanceof ITextEditor) {
            return (ITextEditor) editor;
        }
        return null;
    }
    
    private IProject getCurrentProject() {
        try {
            IEditorPart editor = getActiveEditor();
            if (editor != null) {
                IEditorInput input = editor.getEditorInput();
                if (input instanceof IFileEditorInput) {
                    IFile file = ((IFileEditorInput) input).getFile();
                    if (file != null && file.exists()) {
                        return file.getProject();
                    }
                }
            }
        } catch (Exception e) {
            CopilotLogger.warn("Failed to get current project: " + e.getMessage());
        }
        return null;
    }
    
    private boolean isJavaFile(IFile file) {
        return file != null && file.exists() && "java".equalsIgnoreCase(file.getFileExtension());
    }
    
    private String getJavaMethodSignature(IFile file, ITextEditor editor) {
        try {
            ICompilationUnit compilationUnit = JavaCore.createCompilationUnitFrom(file);
            if (compilationUnit == null || !compilationUnit.exists()) return "";
            
            ITextSelection selection = (ITextSelection) editor.getSelectionProvider().getSelection();
            IJavaElement element = compilationUnit.getElementAt(selection.getOffset());
            
            if (element instanceof IMethod) {
                IMethod method = (IMethod) element;
                return method.getElementName() + method.getSignature();
            }
        } catch (JavaModelException e) {
            CopilotLogger.warn("Failed to get Java method signature: " + e.getMessage());
        }
        
        return "";
    }
    
    private ClassStructure analyzeJavaClass(IFile file) {
        ClassStructure structure = new ClassStructure();
        
        try {
            ICompilationUnit compilationUnit = JavaCore.createCompilationUnitFrom(file);
            if (compilationUnit == null || !compilationUnit.exists()) return structure;
            
            IType[] types = compilationUnit.getTypes();
            for (IType type : types) {
                structure.setClassName(type.getElementName());
                structure.setPackageName(type.getPackageFragment().getElementName());
                
                // Methods
                IMethod[] methods = type.getMethods();
                for (IMethod method : methods) {
                    structure.addMethod(method.getElementName());
                }
                
                // Fields
                structure.setFieldCount(type.getFields().length);
            }
            
        } catch (JavaModelException e) {
            CopilotLogger.warn("Failed to analyze Java class: " + e.getMessage());
        }
        
        return structure;
    }
    
    private List<String> analyzeClasspath(IJavaProject javaProject) {
        List<String> entries = new ArrayList<>();
        // Simplified implementation
        entries.add("JRE System Library");
        entries.add("Plugin Dependencies");
        return entries;
    }
    
    private List<String> getReferencedProjects(IProject project) {
        List<String> referenced = new ArrayList<>();
        try {
            IProject[] refs = project.getReferencedProjects();
            for (IProject ref : refs) {
                if (ref.exists() && ref.isOpen()) {
                    referenced.add(ref.getName());
                }
            }
        } catch (Exception e) {
            CopilotLogger.warn("Failed to get referenced projects: " + e.getMessage());
        }
        return referenced;
    }
    
    private String truncateContext(String context) {
        if (context.length() > MAX_CONTEXT_LENGTH) {
            return context.substring(0, MAX_CONTEXT_LENGTH) + "\n... (truncated)";
        }
        return context;
    }
    
    // Inner classes - ContextCollector에서 상속
    public static class ClassStructure {
        private String packageName = "";
        private String className = "";
        private List<String> methods = new ArrayList<>();
        private int fieldCount = 0;
        
        public String getPackageName() { return packageName; }
        public void setPackageName(String packageName) { this.packageName = packageName; }
        
        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        
        public List<String> getMethods() { return methods; }
        public void addMethod(String method) { this.methods.add(method); }
        
        public int getFieldCount() { return fieldCount; }
        public void setFieldCount(int count) { this.fieldCount = count; }
        
        @Override
        public String toString() {
            return String.format("Class: %s.%s\nMethods: %d\nFields: %d", 
                packageName, className, methods.size(), fieldCount);
        }
    }
    
    public static class ProjectDependencies {
        private List<String> classpathEntries = new ArrayList<>();
        private List<String> referencedProjects = new ArrayList<>();
        
        public List<String> getClasspathEntries() { return classpathEntries; }
        public void setClasspathEntries(List<String> entries) { this.classpathEntries = entries; }
        
        public List<String> getReferencedProjects() { return referencedProjects; }
        public void setReferencedProjects(List<String> projects) { this.referencedProjects = projects; }
    }
}