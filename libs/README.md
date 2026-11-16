# 第三方 jar 不入库

把上游 jar 放这里只用于本地反编译/对照，**不要提交**。实际归档位置：`D:\redtale-work\upstream`。

| 文件 | 来源 | 用途 |
|---|---|---|
| MMOItems-6.10.jar | 用户提供（Premium） | 物品/属性/宝石/套装/技能 |
| MMOCore-1.13.jar | 用户提供（Premium） | 等级/经验/职业 |
| MMOInventory-1.9.jar | 用户提供（Premium） | 槽位 |
| EcoEnchants-2026.46-spigot.jar | 用户提供 | 附魔 schema 参考 |
| MythicMobs-5.14.0.jar | 用户提供 | 怪物模型参考 |
| MythicLib-1.7.jar | 用户提供（Premium 依赖） | 三个 Indyuce 插件的公共底座，2505 类 |
| eco-2026.46-modrinth.jar | Modrinth（MIT） | 脚本引擎宿主，可移植（保留版权声明） |

libreforge 只有 GitHub 源码（`Auxilor/libreforge`），**BUSL 1.1，禁止把代码用于生产环境**，不下载、不移植。
