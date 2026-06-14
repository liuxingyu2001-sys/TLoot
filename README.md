# TLoot

Minecraft 寻宝插件 — 玩家发起寻宝活动，其他玩家参与争夺。

## 功能

- **创建寻宝**：玩家花费金币创建寻宝活动
- **参与寻宝**：其他玩家支付费用参与
- **自动开奖**：设定时间后自动开奖，随机选出获胜者
- **Vault 经济**：通过 Vault 处理金币流转

## 命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/treasure create <金额> <费用>` | 创建寻宝 | 玩家 |
| `/treasure join <ID>` | 参与寻宝 | 玩家 |
| `/treasure list` | 查看寻宝列表 | 玩家 |
| `/treasure info <ID>` | 查看寻宝详情 | 玩家 |
| `/treasure reload` | 重载配置 | `tloot.admin` |

别名: `/tloot`, `/t`

## 依赖

- **必需**: Vault
