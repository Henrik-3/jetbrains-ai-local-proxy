package com.hdev.ollamaproxy.config;

import javax.swing.*;

public class AppSettingsComponent {

    private JComboBox<AppSettingsState.ServiceType> serviceTypeComboBox;
    private JPanel mainPanel;
    private JPasswordField apiKeyField;
    private JTextField baseUrlField;
    private JSpinner portSpinner;
    private JCheckBox autoStartCheckbox;
    private JTextArea modelFilterArea;

    // Anthropic/Claude Code settings UI components
    private JPasswordField anthropicApiKeyField;
    private JTextField anthropicBaseUrlField;
    private JTextField anthropicModelField;
    private JTextField anthropicSmallFastModelField;
    private JComboBox<String> claudeCodeProxyModeComboBox;
    private JTextField openrouterProviderField;

    public AppSettingsComponent() {
        // Populate the dropdown with values from the enum
        serviceTypeComboBox.setModel(new DefaultComboBoxModel<>(AppSettingsState.ServiceType.values()));

        // Initialize Claude Code proxy mode dropdown
        claudeCodeProxyModeComboBox.setModel(new DefaultComboBoxModel<>(new String[]{"openai", "openwebui"}));
    }

    private void createUIComponents() {
        portSpinner = new JSpinner(new SpinnerNumberModel(11434, 1024, 65535, 1));
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    // --- GETTERS ---

    public String getApiKey() {
        return apiKeyField != null ? new String(apiKeyField.getPassword()) : "";
    }

    public String getBaseUrl() {
        return baseUrlField != null ? baseUrlField.getText() : "";
    }

    public int getPort() {
        return portSpinner != null ? (Integer) portSpinner.getValue() : 11434;
    }

    public boolean getAutoStart() {
        return autoStartCheckbox != null ? autoStartCheckbox.isSelected() : false;  // Explicit default
    }

    public String getModelFilter() {
        return modelFilterArea != null ? modelFilterArea.getText() : "";
    }

    public AppSettingsState.ServiceType getServiceType() {
        return serviceTypeComboBox != null ? (AppSettingsState.ServiceType) serviceTypeComboBox.getSelectedItem() : AppSettingsState.ServiceType.OPENAI_COMPATIBLE;
    }

    // Anthropic/Claude Code getters
    public String getAnthropicApiKey() {
        return anthropicApiKeyField != null ? new String(anthropicApiKeyField.getPassword()) : "";
    }

    public String getAnthropicBaseUrl() {
        return anthropicBaseUrlField != null ? anthropicBaseUrlField.getText() : "";
    }

    public String getAnthropicModel() {
        return anthropicModelField != null ? anthropicModelField.getText() : "";
    }

    public String getAnthropicSmallFastModel() {
        return anthropicSmallFastModelField != null ? anthropicSmallFastModelField.getText() : "";
    }

    public String getClaudeCodeProxyMode() {
        return claudeCodeProxyModeComboBox != null ? (String) claudeCodeProxyModeComboBox.getSelectedItem() : "openai";
    }

    public String getOpenrouterProvider() {
        return openrouterProviderField != null ? openrouterProviderField.getText() : "";
    }

    // --- SETTERS ---

    public void setApiKey(String text) {
        if (apiKeyField != null) apiKeyField.setText(text);
    }

    public void setBaseUrl(String text) {
        if (baseUrlField != null) baseUrlField.setText(text);
    }

    public void setPort(int value) {
        if (portSpinner != null) portSpinner.setValue(value);
    }

    public void setAutoStart(boolean selected) {
        if (autoStartCheckbox != null) autoStartCheckbox.setSelected(selected);
    }

    public void setModelFilter(String text) {
        if (modelFilterArea != null) modelFilterArea.setText(text);
    }

    public void setServiceType(AppSettingsState.ServiceType serviceType) {
        if (serviceTypeComboBox != null) serviceTypeComboBox.setSelectedItem(serviceType);
    }

    // Anthropic/Claude Code setters
    public void setAnthropicApiKey(String text) {
        if (anthropicApiKeyField != null) anthropicApiKeyField.setText(text);
    }

    public void setAnthropicBaseUrl(String text) {
        if (anthropicBaseUrlField != null) anthropicBaseUrlField.setText(text);
    }

    public void setAnthropicModel(String text) {
        if (anthropicModelField != null) anthropicModelField.setText(text);
    }

    public void setAnthropicSmallFastModel(String text) {
        if (anthropicSmallFastModelField != null) anthropicSmallFastModelField.setText(text);
    }

    public void setClaudeCodeProxyMode(String mode) {
        if (claudeCodeProxyModeComboBox != null) claudeCodeProxyModeComboBox.setSelectedItem(mode);
    }

    public void setOpenrouterProvider(String text) {
        if (openrouterProviderField != null) openrouterProviderField.setText(text);
    }

}
