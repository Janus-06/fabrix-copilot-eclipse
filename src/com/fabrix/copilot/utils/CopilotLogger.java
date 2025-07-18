package com.fabrix.copilot.utils;

// [수정] 누락된 Eclipse Console 관련 클래스들을 모두 import
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.graphics.Color; // SWT Color import 추가
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.osgi.framework.FrameworkUtil;

/**
 * 📝 Copilot Logger - 중앙화된 로깅 시스템
 * * Eclipse 콘솔과 파일 로깅을 통합 관리합니다.
 */
public class CopilotLogger {
    
    private static final String CONSOLE_NAME = "FabriX Copilot";
    private static final String LOG_FILE_NAME = "fabrix-copilot.log";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    
    private static MessageConsole console;
    private static MessageConsoleStream infoStream;
    private static MessageConsoleStream errorStream;
    private static MessageConsoleStream debugStream;
    
    private static File logFile;
    private static PrintWriter fileWriter;
    private static final ExecutorService logExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CopilotLogger-Thread");
        t.setDaemon(true);
        return t;
    });
    
    // 로그 레벨
    public enum LogLevel {
        DEBUG(0, "DEBUG"),
        INFO(1, "INFO"),
        WARN(2, "WARN"),
        ERROR(3, "ERROR");
        
        private final int level;
        private final String label;
        
        LogLevel(int level, String label) {
            this.level = level;
            this.label = label;
        }
        
        public String getLabel() { return label; }
    }
    
    private static LogLevel currentLevel = LogLevel.INFO;
    
    static {
        // UI 스레드에서 초기화하도록 보장
        if (Platform.isRunning()) {
            org.eclipse.swt.widgets.Display.getDefault().asyncExec(CopilotLogger::initialize);
        }
    }
    
    /**
     * 로거 초기화
     */
    private static void initialize() {
        try {
            // Eclipse 콘솔 초기화
            initializeConsole();
            
            // 파일 로거 초기화
            initializeFileLogger();
            
            // 시작 메시지
            info("=== FabriX Copilot Logger Initialized ===");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Eclipse 콘솔 초기화
     */
    private static void initializeConsole() {
        ConsolePlugin plugin = ConsolePlugin.getDefault();
        if (plugin == null) return;
        
        IConsoleManager conMan = plugin.getConsoleManager();
        
        // 기존 콘솔 찾기
        IConsole[] existing = conMan.getConsoles();
        for (IConsole con : existing) {
            if (CONSOLE_NAME.equals(con.getName()) && con instanceof MessageConsole) {
                console = (MessageConsole) con;
                break;
            }
        }
        
        // 없으면 새로 생성
        if (console == null) {
            console = new MessageConsole(CONSOLE_NAME, null);
            conMan.addConsoles(new IConsole[]{console});
        }
        
        // 스트림 생성
        infoStream = console.newMessageStream();
        errorStream = console.newMessageStream();
        debugStream = console.newMessageStream();
        
        // [수정] UIResourceManager 대신 Display에서 직접 색상 가져오기
        org.eclipse.swt.widgets.Display display = org.eclipse.swt.widgets.Display.getCurrent();
        if (display != null) {
            infoStream.setColor(display.getSystemColor(org.eclipse.swt.SWT.COLOR_BLACK));
            errorStream.setColor(display.getSystemColor(org.eclipse.swt.SWT.COLOR_RED));
            debugStream.setColor(display.getSystemColor(org.eclipse.swt.SWT.COLOR_DARK_GRAY));
        }
    }
    
    /**
     * 파일 로거 초기화
     */
    private static void initializeFileLogger() {
        try {
            // 로그 디렉토리 생성
            IPath stateLocation = Platform.getStateLocation(
                FrameworkUtil.getBundle(CopilotLogger.class));
            if (stateLocation == null) return;
            
            File logDir = stateLocation.toFile();
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            // 로그 파일 생성
            logFile = new File(logDir, LOG_FILE_NAME);
            
            // 파일 크기 체크 (10MB 이상이면 백업)
            if (logFile.exists() && logFile.length() > 10 * 1024 * 1024) {
                File backupFile = new File(logDir, LOG_FILE_NAME + "." + 
                    System.currentTimeMillis() + ".bak");
                logFile.renameTo(backupFile);
            }
            
            // Writer 생성
            fileWriter = new PrintWriter(new FileWriter(logFile, true), true);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // --- 나머지 메서드(log, info, error, debug 등)는 기존 코드와 동일하게 유지 ---
    public static void setLogLevel(LogLevel level) {
        currentLevel = level;
        info("Log level changed to: " + level.getLabel());
    }

    public static void debug(String message) {
        log(LogLevel.DEBUG, message, null);
    }

    public static void info(String message) {
        log(LogLevel.INFO, message, null);
    }


    public static void warn(String message) {
        log(LogLevel.WARN, message, null);
    }
    
    public static void warn(String message, Throwable throwable) {
        log(LogLevel.WARN, message, throwable);
    }
    
    public static void error(String message, Throwable throwable) {
        log(LogLevel.ERROR, message, throwable);
    }
    
    private static void log(LogLevel level, String message, Throwable throwable) {
        if (level.level < currentLevel.level) {
            return;
        }
        
        logExecutor.execute(() -> {
            try {
                String timestamp = DATE_FORMAT.format(new Date());
                String threadName = Thread.currentThread().getName();
                String logMessage = String.format("[%s] [%s] [%s] %s",
                    timestamp, level.getLabel(), threadName, message);
                
                // UI 관련 코드는 UI 스레드에서 실행
                org.eclipse.swt.widgets.Display.getDefault().asyncExec(() -> {
                    writeToConsole(level, logMessage);
                    if (throwable != null) {
                        writeToConsole(level, getStackTraceString(throwable));
                    }
                });
                
                // 파일 출력
                writeToFile(logMessage);
                if (throwable != null) {
                    writeToFile(getStackTraceString(throwable));
                }
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    private static void writeToConsole(LogLevel level, String message) {
        if (console == null) return;
        try {
            MessageConsoleStream stream = getStreamForLevel(level);
            if (stream != null && !stream.isClosed()) {
                stream.println(message);
            }
        } catch (Exception e) {
            // 콘솔 쓰기 실패는 무시
        }
    }
    
    private static void writeToFile(String message) {
        if (fileWriter == null) return;
        try {
            fileWriter.println(message);
            fileWriter.flush();
        } catch (Exception e) {
            // 파일 쓰기 실패는 무시
        }
    }
    
    private static MessageConsoleStream getStreamForLevel(LogLevel level) {
        switch (level) {
            case DEBUG: return debugStream;
            case ERROR: case WARN: return errorStream;
            default: return infoStream;
        }
    }

    private static String getStackTraceString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
    
    public static void showConsole() {
        if (console == null) return;
        org.eclipse.swt.widgets.Display.getDefault().asyncExec(() -> {
            try {
                ConsolePlugin.getDefault().getConsoleManager().showConsoleView(console);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void shutdown() {
        try {
            info("=== FabriX Copilot Logger Shutting Down ===");
            logExecutor.shutdown();
            if (!logExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                logExecutor.shutdownNow();
            }
            if (fileWriter != null) {
                fileWriter.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}