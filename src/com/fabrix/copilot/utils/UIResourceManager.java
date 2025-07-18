package com.fabrix.copilot.utils;

import java.util.HashMap;
import java.util.Map;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Display;

/**
 * 🎨 UIResourceManager (신규)
 * SWT 리소스(Color, Font)를 중앙에서 관리하여 리소스 누수를 방지합니다.
 * 플러그인 종료 시 모든 리소스를 한 번에 해제합니다.
 */
public final class UIResourceManager {
    private static final Map<String, Font> fontRegistry = new HashMap<>();

    /**
     * 시스템 색상을 반환합니다. 이 색상들은 해제할 필요가 없습니다.
     */
    public static Color getSystemColor(int swtColorId) {
        return Display.getDefault().getSystemColor(swtColorId);
    }

    /**
     * 캐시된 폰트를 반환합니다. 없으면 새로 생성하고 캐시에 저장합니다.
     */
    public static Font getFont(String name, int size, int style) {
        String key = name + "|" + size + "|" + style;
        // computeIfAbsent를 사용하여 동시성 문제 없이 폰트 생성
        return fontRegistry.computeIfAbsent(key, k -> {
            FontData fontData = new FontData(name, size, style);
            return new Font(Display.getDefault(), fontData);
        });
    }

    /**
     * 플러그인 종료 시 호출되어 모든 캐시된 폰트를 해제합니다.
     */
    public static void dispose() {
        for (Font font : fontRegistry.values()) {
            if (font != null && !font.isDisposed()) {
                font.dispose();
            }
        }
        fontRegistry.clear();
        CopilotLogger.info("UI Fonts disposed.");
    }
}