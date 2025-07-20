## JetBrains AI & Claude Code Proxy Plugin

This plugin enables JetBrains IDEs (IntelliJ IDEA, PyCharm, WebStorm, etc.) to work with various AI providers (Ollama, Claude, OpenRouter) by providing OpenAI-compatible API endpoints. It also enables **Claude Code** to work seamlessly with different AI backends.

## Use Cases

### 1. JetBrains AI Assistant
Connect JetBrains AI Assistant to local Ollama models or cloud providers like OpenRouter.

### 2. Claude Code Integration  
Enable Claude Code to use different AI providers through OpenAI-compatible endpoints.

## Quick Start Guide

### Configuration
Configure the following settings in the plugin settings UI (Settings -> Tools -> AI Service Proxy):

1. **Service Type**: Select the type of AI service you are using (OpenAI, OpenWebUI, etc.)
2. **API Key**: Enter your API key for the selected service
3. **Base URL**: Enter the base URL of the API (e.g., https://openrouter.ai, https://api.openai.com/)
4. **Port**: Choose the port for the proxy server (default is 11434)
5. **Auto-start Server**: Check this box to automatically start the proxy server when the IDE opens
6. **Model Filter**: Enter a list of models you want to expose (one model per line)
7. **Normal Model**: Enter the name of the model you want to use for normal requests (Claude Code Main Model, original default model used by claude code: anthropic/claude-sonnet-4, new default: moonshotai/kimi-k2)
8. **Small Model**: Enter the name of the model you want to use for small requests (Claude Code Small Fast Model, original default model used by claude code: anthropic/claude-haiku-3.5, new default: mistralai/devstral-small)
9. **OpenRouter Normal Provider**: Enter the name of the provider you want to use for normal requests (only relevant when using OpenRouter, list of available providers: https://openrouter.ai/docs/features/provider-routing#json-schema-for-provider-preferences)
10. **OpenRouter Small Provider**: Enter the name of the provider you want to use for small requests (only relevant when using OpenRouter, list of available providers: https://openrouter.ai/docs/features/provider-routing#json-schema-for-provider-preferences)

### JetBrains AI Assistant
1. Go to Settings -> Tools -> AI Assistant -> Models
2. Tick the checkbox on "Enable Ollama"
3. Set the ollama URL to "http://localhost:11434"
4. Click on "Test Connection" to verify the connection
5. Select a model for core features (code gen, commit messages etc.)
6. Select a model for instant helpers (name suggestions etc.)

### Claude Code
1. Install Claude Code: `npm install -g @anthropic-ai/claude-code`
2. Add the Jetbrains Claude Code Plugin (optional, but recommended for better integration) via the Jetbrains Plugin Marketplace
3. Open the respective config file
   - Windows: `%USERPROFILE%\.claude\settings.json`
   - Linux: `~/.claude/settings.json`
4. Set the following settings:
```json
{
  "apiKeyHelper": "echo [API-KEY]",
  "env": {
    "ANTHROPIC_BASE_URL": "http://127.0.0.1:11434",
    "ANTHROPIC_API_KEY": "[API-KEY]",
    "ANTHROPIC_MODEL": "base_model",
    "ANTHROPIC_SMALL_FAST_MODEL": "small_fast_model"
  }
}
```
! DO NOT CHANGE THE `ANTHROPIC_MODEL` AND `ANTHROPIC_SMALL_FAST_MODEL` VALUES! THEY ARE USED BY THE PROXY PLUGIN TO MAP THE MODELS YOU CONFIGURED IN THE PLUGIN SETTINGS IN JETBRAINS!
5. Start Claude Code via: `claude` or in the upper right corner of the IDE
 