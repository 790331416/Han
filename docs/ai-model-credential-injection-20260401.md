# 2026-04-01 AI Model Credential Injection Notes

## Summary

This note captures the `ai-model` credential investigation on the `95` environment, the actual fix path we validated, and the minimum repeatable steps needed to make the regression pass honestly.

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

At the start of the investigation on 2026-04-01, `95` did not expose any usable provider key to the running `han-ai` service:

- `han-ai` container environment does not contain `DASHSCOPE_API_KEY`, `DEEPSEEK_API_KEY`, `OPENAI_API_KEY`, or `ZHIPU_API_KEY`
- `public.ai_model.api_key` is empty for all seeded models
- `sys_config` does not contain any AI provider key override
- direct lookup for Nacos config `han-ai.yml` returned `config data not exist`

That confirmed the original `ai-model` failure was a real environment blocker, not a UI or routing problem.

## What We Actually Used On 95

For the final validation, we did not invent a fake credential or write one back to the Han database. We reused an already-working DeepSeek key from the existing `maxkb` deployment on `95`, injected it into `han-ai` as runtime environment variables, and kept the secret value out of logs and docs.

Validated runtime shape:

- source key length from `maxkb`: `35`
- `han-ai` container `DEEPSEEK_API_KEY` length: `35`
- `han-ai` container `HAN_AI_PROVIDER_DEEPSEEK_API_KEY` length: `35`
- `han-ai` actuator health: `UP`

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

The current Playwright `ai-model` flow no longer has to be pinned to `qwen`. The spec supports provider override, so the minimum honest fix is:

1. choose a provider that actually has a valid server-side key
2. inject that key into `han-ai`
3. recreate `han-ai`
4. run the Playwright spec with matching provider parameters

The validated path on `95` used DeepSeek via runtime environment variables:

```bash
cd /opt/han/docker
export DEEPSEEK_API_KEY=<server-env-only>
export HAN_AI_PROVIDER_DEEPSEEK_API_KEY=<server-env-only>
docker compose -p hanfull -f docker-compose-full.yml up -d ai
```

If the environment later prefers Qwen again, the same pattern works with:

```bash
cd /opt/han/docker
export DASHSCOPE_API_KEY=<server-env-only>
export HAN_AI_PROVIDER_QWEN_API_KEY=<server-env-only>
docker compose -p hanfull -f docker-compose-full.yml up -d ai
```

## Verification

After recreating `han-ai`, verify in this order:

```bash
docker exec han-ai sh -lc "printf 'DEEPSEEK_API_KEY-len=%s\n' \"\${#DEEPSEEK_API_KEY}\"; printf 'HAN_AI_PROVIDER_DEEPSEEK_API_KEY-len=%s\n' \"\${#HAN_AI_PROVIDER_DEEPSEEK_API_KEY}\""
curl -s http://127.0.0.1:9208/actuator/health
curl -s http://127.0.0.1:9090/ai/model/list?pageNum=1&pageSize=20
```

Then rerun Playwright with matching provider parameters:

```bash
cd /opt/han/source/Han-jobflow-monitor-20260401/han-ui
PW_BASE_URL=http://10.18.35.95:3000 \
PW_API_URL=http://10.18.35.95:9090 \
PW_AI_MODEL_PROVIDER=deepseek \
PW_AI_MODEL_CODE=deepseek-chat \
PW_AI_MODEL_BASE_URL=https://api.deepseek.com/v1 \
npx playwright test tests/e2e/specs/ai-model.spec.ts --project=chromium
```

## Final Result

The 2026-04-01 rerun on `95` passed:

- Playwright [ai-model.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-model.spec.ts): `1 passed (31.0s)`
- created model row showed `已配置 / 环境变量`
- `/ai/model/test/{id}` returned `200`
- editing the model kept the masked credential behavior and `credentialSource=env`
