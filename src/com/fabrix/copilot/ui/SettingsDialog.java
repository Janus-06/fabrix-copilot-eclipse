package com.fabrix.copilot.ui;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.fabrix.copilot.mcp.McpServerManager;
import com.fabrix.copilot.utils.PreferenceManager;

/**
 * 🛠️ Settings Dialog - 최종 리팩토링 버전
 * - 모든 메서드 구현 및 오류 해결
 */
public class SettingsDialog extends Dialog {

    private PreferenceManager preferenceManager;
    private McpServerManager mcpServerManager;

    // UI Components
    private Text openaiKeyText;
    private Text fabrixTokenText;
    private Text fabrixClientText;
    private Label apiStatusLabel;
    private Text mcpConfigText;
    private Label mcpStatusLabel;
    private Button autoScrollButton;
    private Scale fontSizeScale;
    private Label fontSizeValueLabel;
    private Button showTimestampsButton;
    private Scale temperatureScale;
    private Label temperatureValueLabel;
    private Text maxTokensText;

    public SettingsDialog(Shell parentShell) {
        super(parentShell);
        this.preferenceManager = PreferenceManager.getInstance();
        this.mcpServerManager = McpServerManager.getInstance();
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("🛠️ Copilot Settings");
        shell.setSize(650, 550);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        container.setLayout(new GridLayout(1, false));

        CTabFolder tabFolder = new CTabFolder(container, SWT.BORDER);
        tabFolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        tabFolder.setSimple(false);

        createAPIKeyTab(tabFolder);
        createMCPTab(tabFolder);
        createUISettingsTab(tabFolder);

        tabFolder.setSelection(0);
        loadSettings();
        return container;
    }

    private void createAPIKeyTab(CTabFolder tabFolder) {
        CTabItem apiKeyTab = new CTabItem(tabFolder, SWT.NONE);
        apiKeyTab.setText("🔑 API Keys");

        Composite apiKeyComposite = new Composite(tabFolder, SWT.NONE);
        apiKeyComposite.setLayout(new GridLayout(1, false));
        apiKeyTab.setControl(apiKeyComposite);

        // OpenAI Section
        Group openaiGroup = new Group(apiKeyComposite, SWT.NONE);
        openaiGroup.setText("🤖 OpenAI API");
        openaiGroup.setLayout(new GridLayout(3, false));
        openaiGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        new Label(openaiGroup, SWT.NONE).setText("API Key:");
        openaiKeyText = new Text(openaiGroup, SWT.BORDER | SWT.PASSWORD);
        openaiKeyText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Button testOpenAIButton = new Button(openaiGroup, SWT.PUSH);
        testOpenAIButton.setText("Test");
        testOpenAIButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                // This is a mock test. A real implementation would make an API call.
                updateAPIStatus(openaiKeyText.getText().startsWith("sk-") ? "OpenAI key format is valid." : "Invalid OpenAI key format.", SWT.COLOR_DARK_GREEN);
            }
        });

        // FabriX Section
        Group fabrixGroup = new Group(apiKeyComposite, SWT.NONE);
        fabrixGroup.setText("🏭 FabriX API");
        fabrixGroup.setLayout(new GridLayout(3, false));
        fabrixGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        new Label(fabrixGroup, SWT.NONE).setText("Token:");
        fabrixTokenText = new Text(fabrixGroup, SWT.BORDER | SWT.PASSWORD);
        fabrixTokenText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

        new Label(fabrixGroup, SWT.NONE).setText("Client Key:");
        fabrixClientText = new Text(fabrixGroup, SWT.BORDER | SWT.PASSWORD);
        fabrixClientText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Button testFabriXButton = new Button(fabrixGroup, SWT.PUSH);
        testFabriXButton.setText("Test");
        testFabriXButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                // Mock test
                updateAPIStatus("FabriX connection test simulated.", SWT.COLOR_DARK_GREEN);
            }
        });

        // Status Section
        apiStatusLabel = new Label(apiKeyComposite, SWT.NONE);
        apiStatusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        apiStatusLabel.setText("Ready.");
    }

    private void createMCPTab(CTabFolder tabFolder) {
        CTabItem mcpTab = new CTabItem(tabFolder, SWT.NONE);
        mcpTab.setText("🔌 MCP Config");
        
        Composite mcpComposite = new Composite(tabFolder, SWT.NONE);
        mcpComposite.setLayout(new GridLayout(1, false));
        mcpTab.setControl(mcpComposite);
        
        // Editor Group
        Group configGroup = new Group(mcpComposite, SWT.NONE);
        configGroup.setText("📝 MCP Configuration (JSON)");
        configGroup.setLayout(new GridLayout(1, false));
        configGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        
        mcpConfigText = new Text(configGroup, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
        mcpConfigText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        mcpConfigText.setFont(new Font(mcpConfigText.getDisplay(), "Consolas", 10, SWT.NORMAL));
        
        // Button Group
        Composite buttonComposite = new Composite(mcpComposite, SWT.NONE);
        buttonComposite.setLayout(new GridLayout(3, false));
        buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        Button loadButton = new Button(buttonComposite, SWT.PUSH);
        loadButton.setText("📂 Load Config");
        loadButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                loadLocalMCPConfig();
            }
        });
        
        Button testButton = new Button(buttonComposite, SWT.PUSH);
        testButton.setText("🧪 Test & Reload");
        testButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                testLocalMCP();
            }
        });
        
        Button applyButton = new Button(buttonComposite, SWT.PUSH);
        applyButton.setText("✅ Apply & Save");
        applyButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
        applyButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                applyMCPConfig();
            }
        });

        mcpStatusLabel = new Label(mcpComposite, SWT.NONE);
        mcpStatusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    private void createUISettingsTab(CTabFolder tabFolder) {
        CTabItem uiTab = new CTabItem(tabFolder, SWT.NONE);
        uiTab.setText("🎨 UI & AI");
        
        Composite uiComposite = new Composite(tabFolder, SWT.NONE);
        uiComposite.setLayout(new GridLayout(1, false));
        uiTab.setControl(uiComposite);
        
        Group uiGroup = new Group(uiComposite, SWT.NONE);
        uiGroup.setText("Display Settings");
        uiGroup.setLayout(new GridLayout(3, false));
        uiGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        
        autoScrollButton = new Button(uiGroup, SWT.CHECK);
        autoScrollButton.setText("Auto-scroll to bottom");
        autoScrollButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
        
        showTimestampsButton = new Button(uiGroup, SWT.CHECK);
        showTimestampsButton.setText("Show timestamps");
        showTimestampsButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
        
        new Label(uiGroup, SWT.NONE).setText("Font Size:");
        fontSizeScale = new Scale(uiGroup, SWT.HORIZONTAL);
        fontSizeScale.setMinimum(8);
        fontSizeScale.setMaximum(16);
        fontSizeScale.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                updateFontSizeLabel();
            }
        });
        fontSizeValueLabel = new Label(uiGroup, SWT.NONE);
        
        Group aiGroup = new Group(uiComposite, SWT.NONE);
        aiGroup.setText("AI Model Settings");
        aiGroup.setLayout(new GridLayout(3, false));
        aiGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        
        new Label(aiGroup, SWT.NONE).setText("Temperature:");
        temperatureScale = new Scale(aiGroup, SWT.HORIZONTAL);
        temperatureScale.setMinimum(0);
        temperatureScale.setMaximum(100);
        temperatureScale.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                updateTemperatureLabel();
            }
        });
        temperatureValueLabel = new Label(aiGroup, SWT.NONE);
        
        new Label(aiGroup, SWT.NONE).setText("Max Tokens:");
        maxTokensText = new Text(aiGroup, SWT.BORDER);
        maxTokensText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    }
    
    private void loadSettings() {
        openaiKeyText.setText(preferenceManager.getOpenAIKey());
        fabrixTokenText.setText(preferenceManager.getFabriXToken());
        fabrixClientText.setText(preferenceManager.getFabriXClientKey());
        
        mcpConfigText.setText(preferenceManager.getValue("mcp.config.json", "{}"));

        autoScrollButton.setSelection(preferenceManager.isAutoScrollEnabled());
        showTimestampsButton.setSelection(preferenceManager.isShowTimestamps());
        
        fontSizeScale.setSelection(preferenceManager.getFontSize());
        updateFontSizeLabel();
        
        temperatureScale.setSelection((int)(preferenceManager.getTemperature() * 100));
        updateTemperatureLabel();
        
        maxTokensText.setText(String.valueOf(preferenceManager.getMaxTokens()));
    }

    private void saveSettings() {
        preferenceManager.setOpenAIKey(openaiKeyText.getText().trim());
        preferenceManager.setFabriXToken(fabrixTokenText.getText().trim());
        preferenceManager.setFabriXClientKey(fabrixClientText.getText().trim());
        
        preferenceManager.setValue("mcp.config.json", mcpConfigText.getText().trim());
        
        preferenceManager.setAutoScroll(autoScrollButton.getSelection());
        preferenceManager.setShowTimestamps(showTimestampsButton.getSelection());
        preferenceManager.setFontSize(fontSizeScale.getSelection());
        preferenceManager.setTemperature(temperatureScale.getSelection() / 100.0);
        try {
            preferenceManager.setMaxTokens(Integer.parseInt(maxTokensText.getText().trim()));
        } catch (NumberFormatException e) {
            preferenceManager.setMaxTokens(2048); // 기본값
        }

        preferenceManager.saveSettings();
        MessageDialog.openInformation(getShell(), "Settings Saved", "Your settings have been saved successfully.");
    }
    
    // --- 누락되었던 메서드 구현 ---

    private void loadLocalMCPConfig() {
        mcpServerManager.loadLocalMCPConfig(); // Manager에게 위임
        mcpConfigText.setText(preferenceManager.getValue("mcp.config.json", "{}"));
        updateMCPStatus("Local MCP configuration loaded.", SWT.COLOR_DARK_GREEN);
    }

    private void testLocalMCP() {
        updateMCPStatus("Reloading and testing MCP servers...", SWT.COLOR_BLUE);
        applyMCPConfig(); // 먼저 저장 및 적용
        mcpServerManager.refreshServers();
        McpServerManager.McpStatus status = mcpServerManager.getStatus();
        String statusText = String.format("Test complete: %d out of %d servers connected.", 
            status.getConnectedServers(), status.getTotalServers());
        updateMCPStatus(statusText, SWT.COLOR_DARK_GREEN);
    }
    
    private void applyMCPConfig() {
        String config = mcpConfigText.getText();
        preferenceManager.setValue("mcp.config.json", config);
        mcpServerManager.loadLocalMCPConfig();
        updateMCPStatus("MCP configuration applied.", SWT.COLOR_DARK_GREEN);
    }

    // --- UI 헬퍼 메서드 ---

    private void updateFontSizeLabel() {
        fontSizeValueLabel.setText(fontSizeScale.getSelection() + "px");
    }

    private void updateTemperatureLabel() {
        temperatureValueLabel.setText(String.format("%.2f", temperatureScale.getSelection() / 100.0));
    }
    
    private void updateAPIStatus(String message, int color) {
        if (apiStatusLabel != null && !apiStatusLabel.isDisposed()) {
            apiStatusLabel.setText(message);
            apiStatusLabel.setForeground(apiStatusLabel.getDisplay().getSystemColor(color));
        }
    }

    private void updateMCPStatus(String message, int color) {
        if (mcpStatusLabel != null && !mcpStatusLabel.isDisposed()) {
            mcpStatusLabel.setText(message);
            mcpStatusLabel.setForeground(mcpStatusLabel.getDisplay().getSystemColor(color));
        }
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, 1, "Save & Close", false);
        createButton(parent, 0, "Cancel", true);
    }

    @Override
    protected void buttonPressed(int buttonId) {
        if (buttonId == 1) { // Save & Close
            saveSettings();
        }
        super.buttonPressed(buttonId);
    }
}