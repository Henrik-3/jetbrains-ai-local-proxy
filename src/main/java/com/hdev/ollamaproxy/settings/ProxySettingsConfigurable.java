// File: src/main/java/com/hdev/ollamaproxy/settings/ProxySettingsConfigurable.java
package com.hdev.ollamaproxy.settings;

import com.hdev.ollamaproxy.model.ApiMode;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ProxySettingsConfigurable implements Configurable {
    private JTextField openaiApiUrlField;
    private JTextField openaiApiKeyField;
    private JTextField proxyPortField;
    private JTextField defaultModelField;
    private JCheckBox autoStartCheckbox;
    private JComboBox<ApiMode> apiModeComboBox;
    private JPanel mainPanel;

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "Ollama Proxy";
    }

    @Override
    public @Nullable JComponent createComponent() {
        mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // API Mode
        gbc.gridx = 0; gbc.gridy = 0;
        mainPanel.add(new JLabel("Target Backend API Type:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        apiModeComboBox = new JComboBox<>(ApiMode.values());
        mainPanel.add(apiModeComboBox, gbc);

        // OpenAI API URL
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        mainPanel.add(new JLabel("Backend Service URL:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        openaiApiUrlField = new JTextField(30);
        mainPanel.add(openaiApiUrlField, gbc);

        // API Key
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        mainPanel.add(new JLabel("Backend API Key:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        openaiApiKeyField = new JTextField(30);
        mainPanel.add(openaiApiKeyField, gbc);

        // Proxy Port
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        mainPanel.add(new JLabel("Ollama Proxy Port:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        proxyPortField = new JTextField(10);
        mainPanel.add(proxyPortField, gbc);

        // Default Model
        gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        mainPanel.add(new JLabel("Default Model:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        defaultModelField = new JTextField(20);
        mainPanel.add(defaultModelField, gbc);

        // Auto Start
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        autoStartCheckbox = new JCheckBox("Auto-start Ollama Proxy server on IDE launch");
        mainPanel.add(autoStartCheckbox, gbc);

        // Info label
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        JLabel infoLabel = new JLabel("<html><i>Configure the Ollama Proxy to connect to your preferred backend OpenAI-compatible service (e.g., OpenWebUI, OpenRouter, local Ollama instance).</i></html>");
        mainPanel.add(infoLabel, gbc);

        return mainPanel;
    }

    @Override
    public boolean isModified() {
        ProxySettingsState settings = ProxySettingsState.getInstance();
        return !openaiApiUrlField.getText().equals(settings.openaiApiUrl) ||
                !openaiApiKeyField.getText().equals(settings.openaiApiKey) ||
                !proxyPortField.getText().equals(String.valueOf(settings.proxyPort)) ||
                !defaultModelField.getText().equals(settings.defaultModel) ||
                autoStartCheckbox.isSelected() != settings.autoStart ||
                !apiModeComboBox.getSelectedItem().equals(settings.apiMode);
    }

    @Override
    public void apply() {
        ProxySettingsState settings = ProxySettingsState.getInstance();
        settings.openaiApiUrl = openaiApiUrlField.getText();
        settings.openaiApiKey = openaiApiKeyField.getText();
        try {
            settings.proxyPort = Integer.parseInt(proxyPortField.getText());
        } catch (NumberFormatException e) {
            settings.proxyPort = 1234;
        }
        settings.defaultModel = defaultModelField.getText();
        settings.autoStart = autoStartCheckbox.isSelected();
        settings.apiMode = (ApiMode) apiModeComboBox.getSelectedItem();
    }

    @Override
    public void reset() {
        ProxySettingsState settings = ProxySettingsState.getInstance();
        openaiApiUrlField.setText(settings.openaiApiUrl);
        openaiApiKeyField.setText(settings.openaiApiKey);
        proxyPortField.setText(String.valueOf(settings.proxyPort));
        defaultModelField.setText(settings.defaultModel);
        autoStartCheckbox.setSelected(settings.autoStart);
        apiModeComboBox.setSelectedItem(settings.apiMode);
    }
}