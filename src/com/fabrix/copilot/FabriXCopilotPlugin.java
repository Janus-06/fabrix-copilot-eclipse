package com.fabrix.copilot;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import com.fabrix.copilot.core.ConversationManager;
import com.fabrix.copilot.core.LLMClient;
import com.fabrix.copilot.mcp.McpServerManager;
import com.fabrix.copilot.utils.CopilotLogger;
import com.fabrix.copilot.utils.UIResourceManager;

public class FabriXCopilotPlugin extends AbstractUIPlugin {

    public static final String PLUGIN_ID = "com.fabrix.copilot";
    private static FabriXCopilotPlugin plugin;

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
        
        System.out.println("🚀 FabriX Copilot 플러그인 시작...");
        
        // 비동기로 초기화
        Job initJob = new Job("Initializing FabriX Copilot") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    initializeServices();
                    return Status.OK_STATUS;
                } catch (Exception e) {
                    return new Status(IStatus.ERROR, PLUGIN_ID, 
                        "Failed to initialize services", e);
                }
            }
        };
        initJob.schedule();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        System.out.println("🛑 FabriX Copilot 플러그인 종료 중...");
        try {
            cleanupServices();
        } finally {
            plugin = null;
            super.stop(context);
        }
    }

    public static FabriXCopilotPlugin getDefault() {
        return plugin;
    }

    private void initializeServices() {
        try {
            System.out.println("🔧 핵심 서비스 초기화 중...");
            
            // 각 서비스를 개별적으로 초기화하고 에러 확인
            try {
                ConversationManager.getInstance();
                System.out.println("✅ ConversationManager 초기화 완료");
            } catch (Exception e) {
                System.err.println("❌ ConversationManager 초기화 실패: " + e.getMessage());
                e.printStackTrace();
            }
            
            // MCP는 나중에 초기화 (블로킹 방지)
            System.out.println("✅ 서비스 초기화 완료");
            
        } catch (Exception e) {
            System.err.println("❌ 서비스 초기화 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void cleanupServices() {
        try {
            System.out.println("🧹 서비스 리소스 정리 중...");
            
            // LLMClient 스레드 풀 종료
            try {
                LLMClient.getInstance().shutdown();
            } catch (Exception e) {
                System.err.println("LLMClient 종료 중 오류: " + e.getMessage());
            }
            
            // MCP 서버 매니저 종료
            try {
                McpServerManager.getInstance().shutdown();
            } catch (Exception e) {
                System.err.println("McpServerManager 종료 중 오류: " + e.getMessage());
            }

            // UI 리소스 해제
            UIResourceManager.dispose();
            
            // 로거 종료
            CopilotLogger.shutdown();

        } catch (Exception e) {
            System.err.println("서비스 정리 중 오류 발생: " + e.getMessage());
        }
    }
}