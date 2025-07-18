package com.fabrix.copilot;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.fabrix.copilot.core.ConversationManager;
import com.fabrix.copilot.core.LLMClient;
import com.fabrix.copilot.mcp.McpServerManager;
import com.fabrix.copilot.utils.CopilotLogger;
import com.fabrix.copilot.utils.UIResourceManager;

/**
 * 플러그인의 생명주기를 관리하는 메인 클래스입니다.
 */
public class FabriXCopilotPlugin extends AbstractUIPlugin {

    public static final String PLUGIN_ID = "com.fabrix.copilot";
    private static FabriXCopilotPlugin plugin;

    /**
     * 플러그인이 시작될 때 호출됩니다.
     */
    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
        
        System.out.println("🚀 FabriX Copilot 플러그인 시작...");
        
        try {
            // 핵심 서비스 초기화
            initializeServices();
            
            System.out.println("✅ FabriX Copilot 플러그인이 성공적으로 시작되었습니다!");
            
        } catch (Exception e) {
            // Logger가 초기화되기 전일 수 있으므로 System.err 사용
            System.err.println("❌ FabriX Copilot 초기화 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 플러그인이 종료될 때 호출됩니다.
     */
    @Override
    public void stop(BundleContext context) throws Exception {
        System.out.println("🛑 FabriX Copilot 플러그인 종료 중...");
        try {
            // 서비스 리소스 정리
            cleanupServices();
        } finally {
            plugin = null;
            super.stop(context);
        }
    }

    /**
     * 플러그인의 싱글톤 인스턴스를 반환합니다.
     */
    public static FabriXCopilotPlugin getDefault() {
        return plugin;
    }

    /**
     * 플러그인에서 사용하는 핵심 서비스들을 초기화합니다.
     */
    private void initializeServices() {
        try {
            CopilotLogger.info("🔧 핵심 서비스 초기화 중...");
            ConversationManager.getInstance();
            McpServerManager.getInstance().loadLocalMCPConfig(); // 로컬 MCP 설정 로드
            CopilotLogger.info("✅ 서비스 초기화 완료");
        } catch (Exception e) {
            CopilotLogger.error("서비스 초기화 실패", e);
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 사용된 모든 서비스의 리소스를 정리합니다.
     */
    private void cleanupServices() {
        try {
            CopilotLogger.info("🧹 서비스 리소스 정리 중...");
            
            // LLMClient 스레드 풀 종료
            LLMClient.getInstance().shutdown();
            
            // MCP 서버 매니저 종료
            McpServerManager.getInstance().shutdown();

            // UI 리소스(폰트 등) 해제
            UIResourceManager.dispose();
            
            // 로거 종료 (가장 마지막에)
            CopilotLogger.shutdown();

        } catch (Exception e) {
            System.err.println("서비스 정리 중 오류 발생: " + e.getMessage());
        }
    }
}