package com.fabrix.copilot.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;

public class OpenChatHandler extends AbstractHandler {
    
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        try {
            IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
            IWorkbenchPage page = window.getActivePage();
            
            // 뷰 ID 변경됨
            page.showView("com.fabrix.copilot.views.chatView");
            
            System.out.println("✅ FabriX Chat 뷰가 열렸습니다!");
            
        } catch (PartInitException e) {
            System.err.println("❌ 채팅 뷰를 열 수 없습니다: " + e.getMessage());
            throw new ExecutionException("채팅 뷰를 열 수 없습니다", e);
        }
        
        return null;
    }
}