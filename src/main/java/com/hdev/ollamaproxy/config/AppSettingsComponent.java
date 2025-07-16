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

    // General model settings UI components
    private JTextField normalModelField;
    private JTextField smallModelField;

    // OpenRouter specific settings UI components (provider preferences only)
    private JTextField openrouterNormalProviderField;
    private JTextField openrouterSmallProviderField;

    public AppSettingsComponent() {
        // Populate the dropdown with values from the enum
        serviceTypeComboBox.setModel(new DefaultComboBoxModel<>(AppSettingsState.ServiceType.values()));
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
        return autoStartCheckbox != null ? autoStartCheckbox.isSelected() : false;
    }

    public String getModelFilter() {
        return modelFilterArea != null ? modelFilterArea.getText() : "";
    }

    public AppSettingsState.ServiceType getServiceType() {
        return serviceTypeComboBox != null ? (AppSettingsState.ServiceType) serviceTypeComboBox.getSelectedItem() : AppSettingsState.ServiceType.OPENAI;
    }

    // General model getters
    public String getNormalModel() {
        return normalModelField != null ? normalModelField.getText() : "";
    }

    public String getSmallModel() {
        return smallModelField != null ? smallModelField.getText() : "";
    }

    // OpenRouter provider preference getters
    public String getOpenrouterNormalProvider() {
        return openrouterNormalProviderField != null ? openrouterNormalProviderField.getText() : "";
    }

    public String getOpenrouterSmallProvider() {
        return openrouterSmallProviderField != null ? openrouterSmallProviderField.getText() : "";
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
        if (serviceTypeComboBox != null) {
            serviceTypeComboBox.setSelectedItem(serviceType);
        }
    }

    // General model setters
    public void setNormalModel(String text) {
        if (normalModelField != null) normalModelField.setText(text);
    }

    public void setSmallModel(String text) {
        if (smallModelField != null) smallModelField.setText(text);
    }

    // OpenRouter provider preference setters
    public void setOpenrouterNormalProvider(String text) {
        if (openrouterNormalProviderField != null) openrouterNormalProviderField.setText(text);
    }

    public void setOpenrouterSmallProvider(String text) {
        if (openrouterSmallProviderField != null) openrouterSmallProviderField.setText(text);
    }
}
