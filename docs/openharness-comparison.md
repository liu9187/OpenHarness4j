# OpenHarness 功能对比

检查日期：2026-04-27

## 参照对象

本次对比以 [HKUDS/OpenHarness](https://github.com/HKUDS/OpenHarness) 为参照对象。选择它的原因是该项目 README 明确定位为 Python 版 Open Agent Harness，并列出 tool-use、skills、memory、multi-agent coordination 等能力，这与 OpenHarness4j 的产品目标最接近。

同名项目还包括 `open-harness.dev` / `docs.open-harness.dev` 的 TypeScript SDK，以及 `openharness.ai` 的通用 Agent API 项目。它们的目标分别偏向 composable TypeScript primitives 和跨 harness 统一 API，不作为本次 parity 判定的主参照。

## 总体结论

OpenHarness4j v1.5 已覆盖企业内嵌式 Java Agent Runtime 的核心闭环，包括 Agent Loop、LLM Adapter、Tool Registry、Permission Hook、Audit、Observability、Memory、Skill、Task、Multi-Agent、Plugin、Spring Boot Starter、Markdown Skill、上下文文件约定、Provider Profile、CLI/dry-run，以及可选 personal-agent/team runtime。

如果目标是“达到当前产品文档定义的 v1.0 生产可用 Java Runtime”，当前能力已完成并通过验证。

如果目标是“与 HKUDS/OpenHarness 公开 README 中列出的完整 Harness 能力完全对齐”，当前仍存在产品形态上的差异，例如不内置托管式聊天机器人服务或插件市场。v1.1 已补齐 Runtime 执行层 parity，v1.2 已补齐 Toolkit & Governance parity，v1.3 已补齐 Skills、Context 和 Provider Profile parity，v1.4-v1.5 已补齐 CLI/dry-run 与可选 personal-agent/team runtime 分片。

## 功能矩阵

| OpenHarness 能力 | 上游公开说明 | OpenHarness4j 当前状态 | 结论 |
| --- | --- | --- | --- |
| Agent Loop | Streaming tool-call cycle、API retry、parallel tool execution、token counting and cost tracking | 已有同步 Agent Loop、tool call 循环、max iteration、usage 聚合、失败处理、streaming events、LLM/tool retry、parallel tools、cost tracking | 已覆盖当前 Java Runtime 执行层 |
| Harness Toolkit | 43 tools，包括 File、Shell、Search、Web、MCP | 已有 Tool 接口、InMemoryToolRegistry，以及 File、Shell、Web Fetch、Search、MCP Client 标准工具 | 已覆盖核心标准工具，未追求 43 个工具完全等量 |
| Skills | On-demand `.md` skill loading，兼容 Anthropic skills/plugins | 已有 Java DSL、YAML Skill loading、Markdown Skill loading，支持 Anthropic-style front matter 和 Prompt + Workflow | 已覆盖当前 Java Runtime skill 层 |
| Plugin Ecosystem | Skills + Hooks + Agents，兼容插件生态 | 已有 plugin-engine，可贡献 Tool、Skill、Task、SubAgent | 部分覆盖，缺少 hooks 与上游插件兼容层 |
| Context & Memory | CLAUDE.md discovery、auto-compact、MEMORY.md persistent memory、session resume/history | 已有 session memory、InMemory/Redis/JDBC stores、window policy、summarizer、CLAUDE.md/MEMORY.md discovery/injection/persist 和 MemorySessionManager | 已覆盖当前 Java Runtime context/memory 层 |
| Governance | 多级权限模式、路径/命令规则、PreToolUse/PostToolUse hooks、交互式审批 | 已有 PermissionPolicy、allow/deny tools、audit、敏感参数脱敏、path/command policy、Pre/Post Tool hooks、approval abstraction | 已覆盖当前 Java Runtime 治理层 |
| Swarm Coordination | Subagent spawning、delegation、team registry、task management、background task lifecycle | 已有 Multi-Agent planning/sub-agent/aggregation/conflict、Task Engine，以及 personal-agent 模块中的 Team Runtime、Team Registry、spawn/query/cancel/archive | 已覆盖 Java embedded runtime 范围 |
| Provider Compatibility | Claude/OpenAI/Copilot/Codex/Moonshot/GLM/MiniMax 等 workflow/profile | 已有 LLMAdapter、OpenAI-compatible adapter、registry、fallback adapter、Provider Profile、环境变量解析和 fallback order | 已覆盖 profile 配置和 fallback；不覆盖上游订阅/账户桥接 |
| CLI/TUI | `oh` CLI、interactive UI、non-interactive output、dry-run preview | 已有 `cli` 模块，支持脚本模式、JSON/stream-json 输出和 dry-run readiness 预览 | 已覆盖当前 Java embedded runtime 的 CLI/dry-run 范围 |
| ohmo personal agent | Feishu/Slack/Telegram/Discord personal agent | 已有可选 `personal-agent` 模块，提供通道适配接口、Feishu/Slack/Telegram/Discord/direct payload adapter、workspace/history/task/audit 和 team runtime | 覆盖库级集成能力；不内置托管式聊天机器人服务 |

## 后续版本计划

| 版本 | 主题 | 覆盖缺口 | 验收重点 |
| --- | --- | --- | --- |
| v1.1 | Runtime Execution Parity | streaming runtime events、API retry、tool retry、parallel tool execution、cost tracking | 已完成：typed runtime event 流；LLM/tool 重试策略可配置；多个独立 tool call 可并行执行；usage 可换算 cost；examples 增加 runtime execution parity 验证 |
| v1.2 | Toolkit & Governance Parity | 内置 File/Shell/Search/Web/MCP 工具包、PreToolUse/PostToolUse hooks、path-level rule、command-level rule、交互式审批抽象 | 已完成：标准工具模块；高危文件/命令可被策略拦截；工具执行前后 hook 可扩展；审批接口可由 CLI/业务系统实现 |
| v1.3 | Skills, Context & Provider Profiles | `.md` skill 按需加载、Anthropic skills 兼容、CLAUDE.md/MEMORY.md 文件约定、auto-compact、session resume、provider profile | 已完成：Skill loader 支持 Markdown；Starter 支持 Markdown Skill 发现；上下文文件可发现、注入和持久化；记忆可压缩并恢复；多 provider profile 可配置、切换和 fallback；新增 `docs/usage.md` |
| v1.4 | CLI/TUI & Dry Run | `oh` 类 CLI、interactive UI、non-interactive prompt、JSON/stream-json output、dry-run preview | 已完成：提供可执行 CLI；支持交互与脚本模式；dry-run 不调用模型/工具但能预览配置、工具、skills、权限和 readiness |
| v1.5 | Personal Agent & Team Runtime | ohmo personal agent、Feishu/Slack/Telegram/Discord 通道、team registry、进程级 subagent spawning、background task lifecycle | 已完成：提供可选 personal-agent 模块；通道接入可插拔；子 Agent 可长生命周期运行、查询、取消和归档；个人与团队协作状态可审计 |

## 必补缺口

若目标是与 OpenHarness 主能力对齐，v1.1 已完成以下能力：

1. Streaming runtime events：输出 text delta、tool start、tool done、done、error 等事件。
2. Tool retry policy：支持 LLM 调用和工具调用的重试、退避、可配置最大次数。
3. Parallel tool execution：当模型一次返回多个互不依赖 tool call 时并行执行。

v1.2 已完成以下能力：

1. Built-in tools：提供 file、shell、web fetch/search、MCP client 的标准工具包。
2. Permission hooks：补齐 PreToolUse / PostToolUse 生命周期，并加入 path-level、command-level 规则。

v1.3 已完成以下能力：

1. Markdown skill compatibility：支持 `.md` skill 按需发现、加载，并尽量兼容 Anthropic skills 约定。
2. Context file conventions：支持 CLAUDE.md / MEMORY.md 发现、注入、持久化和 session resume。
3. Provider profile：提供多 provider profile、默认模型选择、环境变量配置和 fallback 顺序。

v1.4-v1.5 已继续补齐以下能力：

1. CLI/TUI：支持 `oh` 类交互式 CLI、脚本输出和 dry-run preview。
2. Personal Agent & Team Runtime：支持可选个人助理通道、team registry、长期子 Agent 和后台任务生命周期。

## 可暂缓能力

以下能力更接近 CLI 产品或个人助理应用，不建议塞进 OpenHarness4j 核心 runtime：

* 完整托管式 `oh` TUI 产品体验。
* 完整托管式 `ohmo` 多渠道聊天机器人服务。
* ClawTeam 集成。
* 插件市场、远程插件安装和分发。

## 对使用文档的处理

由于 v1.5 已补齐 optional personal-agent/team runtime 分片，README 已新增 personal-agent/team runtime 快速使用说明。

当前可用说明以 `README.md`、`docs/usage.md` 和 `docs/cli.md` 为准，覆盖 v1.5 Java Runtime 的嵌入式使用、Spring Boot Starter 配置、Toolkit/Governance 配置、Skill、Memory、Provider Profile、CLI/dry-run、personal-agent/team runtime、兼容性承诺和验证命令。
