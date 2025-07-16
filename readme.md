## Jetbrains AI Ollama Proxy Plugin
Docs TBD

## Claude Code Support
The plugin now supports Anthropic/Claude API endpoints with automatic conversion to OpenAI/OpenWebUI format.

### Configuration
Configure the following settings in the plugin settings UI (Settings -> Tools -> Ollama OpenAI Proxy):

**Anthropic Settings:**
- **Anthropic Base URL**: Base URL for the API (e.g., `https://api.anthropic.com` or `https://openrouter.ai/api/v1`)
- **Anthropic API Key**: Your API key for Anthropic/OpenRouter
- **Main Model**: Primary model (e.g., `claude-3-sonnet-20240229` or `anthropic/claude-sonnet-4`)
- **Small Fast Model**: Fast model for quick responses (e.g., `claude-3-haiku-20240307`)
- **Proxy Mode**: Target API format - `openai` for OpenRouter/OpenAI-compatible or `openwebui` for OpenWebUI
- **OpenRouter Provider**: Optional provider preference for OpenRouter (e.g., `groq`, `cerebras`)

### Available Endpoints
When the proxy server is running, the following Anthropic-compatible endpoints are available:

- `POST /v1/messages` - Anthropic chat completions (supports streaming)
- `GET /v1/models` - List available models
- `GET /v1/health` - Health check endpoint

### Features
- **Format Conversion**: Automatically converts between Anthropic and OpenAI message formats
- **Tool Support**: Handles tool calls and tool results with proper validation
- **Streaming**: Full support for Server-Sent Events streaming
- **OpenRouter Integration**: Special handling for OpenRouter with provider preferences
- **OpenWebUI Support**: Compatible with OpenWebUI endpoints

### Example Usage
Once configured and the proxy server is started (default port 11434), you can use standard Anthropic API calls:

```bash
# Non-streaming chat completion
curl -X POST http://localhost:11434/v1/messages \
  -H "Content-Type: application/json" \
  -d '{
    "model": "claude-3-sonnet-20240229",
    "messages": [
      {"role": "user", "content": "Hello, Claude!"}
    ],
    "max_tokens": 100
  }'

# Streaming chat completion
curl -X POST http://localhost:11434/v1/messages \
  -H "Content-Type: application/json" \
  -d '{
    "model": "claude-3-sonnet-20240229",
    "messages": [
      {"role": "user", "content": "Tell me a story"}
    ],
    "stream": true,
    "max_tokens": 500
  }'

# List available models
curl http://localhost:11434/v1/models
```

The proxy will automatically convert these Anthropic-format requests to OpenAI format and route them to your configured backend (OpenRouter, OpenWebUI, etc.).
- OPENROUTER_FAST_PROVIDER => Optional, targets a specific provider for the small fast model (e.g. groq, cerebras), only works when using openrouter

Create a new file in:
$HOME/.claude/settings.json and add the following:
{
  "apiKeyHelper": "echo <API KEY>"
}

set ANTHROPIC_BASE_URL=http://127.0.0.1:11434
set ANTHROPIC_API_KEY=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
set ANTHROPIC_MODEL=moonshotai/kimi-k2
set ANTHROPIC_SMALL_FAST_MODEL=mistral/devstral-medium
set CLAUDE_CODE_PROXY_MODE=openai
set OPENROUTER_PROVIDER=groq
