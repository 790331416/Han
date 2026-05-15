# Han 发布镜像清单

本目录用于保存已经过 95 环境验证的镜像 digest 发布清单。

## 使用边界

- 清单只保存镜像仓库地址、不可变 tag、来源提交和 `repo@sha256:<digest>`。
- 清单不得保存镜像仓库凭据、服务器密码、数据库密码、令牌或其他 secrets。
- 清单用于复现或回滚已经验证过的发布批次，不替代构建、推送、拉取和业务验证。
- 新增或更新清单后，必须同步更新部署手册、测试与验收手册、运维与 95 环境手册。

## 生成方式

在 95 正式仓库 `/opt/han/repo/Han` 中执行：

```bash
deploy/scripts/generate-image-release-manifest-95.sh \
  --tag <release-tag> \
  --source-commit <commit> \
  --services gateway,auth,system,job,tenant,workflow,open,file,ai,gen,ui \
  --output deploy/release-manifests/<release-tag>.env
```

## 演练方式

```bash
deploy/scripts/rehearse-image-digest-deploy-95.sh \
  --manifest deploy/release-manifests/<release-tag>.env \
  --target all \
  --services gateway,auth,system,job,tenant,workflow,open,file,ai,gen,ui \
  --apply
```

脚本只通过临时环境变量向 Docker Compose 传入 digest pin，不修改 `.env`，不删除 volume，不清库。
