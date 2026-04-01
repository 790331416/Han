# 2026-04-01 AI Model Credential Injection Notes

## Summary

This note captures the final `full`-tier blocker for `ai-model` on the `95` environment and the minimum steps needed to make the validation pass honestly.

## What The Backend Actually Reads

`han-ai` resolves model credentials in this order:

1. Environment variables
2. Persisted `ai_model.api_key`
3. Otherwise mark the model as `credentialConfigured=false`

The resolver lives in [AiModelCredentialResolver.java](/D:/code/Han/han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiModelCredentialResolver.java).

Supported environment patterns include:

- `HAN_AI_MODEL_<MODEL_CODE>_API_KEY`
- `HAN_AI_PROVIDER_<PROVIDER>_API_KEY`
- `HAN_AI_<PROVIDER>_API_KEY`
- `<PROVIDER>_API_KEY`
- `DASHSCOPE_API_KEY` for `qwen`

## 95 Environment Findings

As of 2026-04-01, `95` does not expose any usable provider key to the running `han-ai` service:

- `han-ai` container environment does not contain `DASHSCOPE_API_KEY`, `DEEPSEEK_API_KEY`, `OPENAI_API_KEY`, or `ZHIPU_API_KEY`
- `public.ai_model.api_key` is empty for all seeded models
- `sys_config` does not contain any AI provider key override
- direct lookup for Nacos config `han-ai.yml` returned `config data not exist`

That means the current `ai-model` failure is a real environment blocker, not a UI or routing problem.

## Compose Support

To avoid host secrets getting lost before they reach the container, [docker-compose-full.yml](/D:/code/Han/docker-compose-full.yml) now passes through the common provider variables used by the resolver:

- `DASHSCOPE_API_KEY`
- `DEEPSEEK_API_KEY`
- `OPENAI_API_KEY`
- `ZHIPU_API_KEY`
- `OLLAMA_API_KEY`
- `AZURE_OPENAI_API_KEY`
- `ANTHROPIC_API_KEY`
- `SILICONFLOW_API_KEY`
- `HAN_AI_PROVIDER_QWEN_API_KEY`
- `HAN_AI_PROVIDER_DEEPSEEK_API_KEY`
- `HAN_AI_PROVIDER_OPENAI_API_KEY`
- `HAN_AI_PROVIDER_ZHIPU_API_KEY`
- `HAN_AI_PROVIDER_OLLAMA_API_KEY`
- `HAN_AI_PROVIDER_AZURE_OPENAI_API_KEY`
- `HAN_AI_PROVIDER_ANTHROPIC_API_KEY`
- `HAN_AI_PROVIDER_SILICONFLOW_API_KEY`

## Minimum Fix On 95

The current Playwright `ai-model` flow creates a `qwen-plus` model, so the minimum fix is to inject a valid `DASHSCOPE_API_KEY` into the `han-ai` service and recreate that container.

Example:

```bash
cd /opt/han/docker
export DASHSCOPE_API_KEY=<server-env-only>
docker compose -p hanfull -f docker-compose-full.yml up -d ai
```

If the operator prefers a provider-specific variable instead, this also works after the compose update:

```bash
cd /opt/han/docker
export HAN_AI_PROVIDER_QWEN_API_KEY=<server-env-only>
docker compose -p hanfull -f docker-compose-full.yml up -d ai
```

## Verification

After recreating `han-ai`, verify in this order:

```bash
docker exec han-ai sh -lc "env | sort | grep -E 'DASHSCOPE_API_KEY|HAN_AI_PROVIDER_QWEN_API_KEY' || true"
curl -s http://127.0.0.1:9208/actuator/health
curl -s http://127.0.0.1:9090/ai/model/list?pageNum=1&pageSize=20
```

Then rerun Playwright:

```bash
cd /opt/han/source/Han-jobflow-monitor-20260401/han-ui
PW_BASE_URL=http://10.18.35.95:3000 PW_API_URL=http://10.18.35.95:9090 npx playwright test tests/e2e/specs/ai-model.spec.ts --project=chromium
```
