# 第四阶段实施控制提示

主工程代理应以 `doc/store-phase-4-proposal.md`、`doc/store-phase-4-design.md`、`doc/tasks/store-phase-4.md` 和 `doc/tasks/progress.md` 为事实来源。

每次只实现当前未阻塞的工作包，保留用户已有改动，不清理 `.venv-ragas`、`volumes` 或 RAG 相关运行数据。后端改动必须附带聚焦测试；前端改动必须通过 TypeScript 和 Vite 构建。完成后检查差异中是否存在 API Key、明文密码、无关重写或生成文件。

验证顺序：后端测试、前端构建、真实 MySQL 启动冒烟、最终差异审查。遇到会改变公开 API、数据破坏行为或外部依赖的歧义时暂停确认。

