# 2026-04-01 Full UI Menu And Proxy Validation

## Summary

This note captures the 95 environment fixes and validation results for the remaining full-tier UI blockers around menu routing and `ai-model`.

## Findings

1. Existing PostgreSQL volumes on `95` had `sys_menu = 0` in all three databases.
2. The root cause was not controller logic. `sql/postgres/init-base-data.sql` only runs on fresh PostgreSQL initialization, so old volumes never received the baseline menu seed.
3. Full UI on `http://10.18.35.95:3000` was returning `502` for `/system/runtime/capabilities`, `/system/user/current`, `/auth/captcha`, and related API calls.
4. The `han-ui` nginx config was correct in the image, but nginx had cached an old gateway upstream IP (`172.19.0.7`) while the live `han-gateway` had already moved to `172.19.0.13`.

## Actions Taken

1. Applied baseline menu seed to all three PostgreSQL containers:
   - `han-postgres`
   - `han-medium-postgres`
   - `han-small-postgres`
2. Added upgrade script [phase9_base_menu_backfill.sql](/D:/code/Han/sql/upgrade/phase9_base_menu_backfill.sql) so this backfill is no longer a one-off manual action.
3. Restarted `han-ui` and `han-ui-medium` on `95` to refresh nginx upstream resolution.
4. Re-ran targeted Playwright validation against the repaired full UI.

## Verification

### Database

- `sys_menu` restored to `37` rows.
- `/system/menu/routers` now returns `5` top-level routes on `small`, `medium`, and `full`.

### UI Proxy

- `http://127.0.0.1:3000/system/runtime/capabilities` now returns `200`.
- `http://127.0.0.1:3200/system/runtime/capabilities` now returns `200`.

### Playwright

- `auth-login.spec.ts -g "authenticated session should enter dashboard"`:
  - Result: `1 passed`
  - Base URL: `http://10.18.35.95:3000`
  - API URL: `http://10.18.35.95:9090`

- `ai-model.spec.ts`:
  - Result: `1 failed`
  - Failure point: assistant-created model row still shows `credentialConfigured=false`, `credentialSource=none`
  - This is now a real environment blocker, not a UI routing or auth-fixture blocker

## Current Remaining Blocker

`ai-model` is still blocked by missing provider credentials in `han-ai`. As of 2026-04-01, the full-tier environment does not expose any of the expected model secrets such as:

- `DASHSCOPE_API_KEY`
- `DEEPSEEK_API_KEY`
- `OPENAI_API_KEY`
- `ZHIPU_API_KEY`

Until at least one supported provider key is injected into the running `han-ai` service, the `ai-model` full-tier credential validation will continue to fail honestly.
