# SaaS 注册赠送额度方案

**状态：设计稿，未实施**
**目标：** 对符合活动资格的新用户赠送 20 元模型额度，同时避免“公开接口 + 客户端固定密钥”被抓包、反编译或批量注册滥用。默认注册不赠送任何额度。

## 1. 结论与范围

应把“注册送 20 元”实现为 SaaS 服务端的**促销入账**，而不是在 LinkMind 浏览器页面、开源代码或客户端配置中直接写金额，也不能由客户端传入金额。

推荐方案由以下三部分组成：

1. SaaS 维护可启停、限额的赠送套餐（Promotion Plan），默认没有启用套餐，因此普通注册余额仍为 `0`。
2. LinkMind 的服务端在完成注册后，以服务端凭据请求 SaaS 私有赠送接口；浏览器只请求既有的 `/user/register`，永远拿不到赠送凭据。
3. SaaS 引入追加式余额账本。赠送、微信充值、模型扣费均通过同一个余额变更服务，在一个数据库事务内同时更新余额和写账本。

这不是“给开源客户端加一个注册 Key”。**任何编译进前端、桌面端、Docker 镜像或公开仓库的固定 Key 都等价于公开 Key**；HMAC 只能防篡改和重放，不能挽救已经交给不可信客户端的长期秘密。

## 2. 现状核对

### 2.1 注册链路

当前浏览器调用 `POST /user/register`；LinkMind 的 `UserServlet` 校验图形验证码后转给 `UserService.register`。后者向 SaaS 调用 `POST /registerChannel`，并把用户名同时作为 `domainName`。相关实现：

- `lagi-web/src/main/java/ai/servlet/UserServlet.java:133`
- `lagi-web/src/main/java/ai/migrate/service/UserService.java:46`

`registerChannel` 属于 SaaS 旧的渠道/租户注册逻辑，会创建渠道、用户等对象。它没有赠送套餐参数，也没有余额初始化或赠送流水。因此当前新注册用户默认余额为零是预期行为。

### 2.2 现有资金与计费记录

SaaS 已经有以下数据，但它们尚不是统一账本：

| 作用 | 现有表/代码 | 现状 |
| --- | --- | --- |
| 当前余额 | `lagi_model_user_balance` | 仅保存可变余额，没有每一次变动的完整来源。 |
| 微信充值订单 | `lagi_model_charge_details`、`CreditService.finishCreditPayment` | 支付回调验签、订单状态和余额增加在同一事务内，具备幂等处理。 |
| 模型扣费 | `lagi_model_api_key_usage`、`ApiKeyService.calculateAndChargeUsage` | 用量记录、价格计算、余额扣减在同一事务内。 |
| LinkMind 上报 | `TokenUsageService` | 通过 `/saas/api/apikey/calculateUsage` 异步上报用量，不应阻塞模型流式首包。 |

因此“有记账能力”这个判断是**部分成立**：已有充值记录和用量记录，但没有一个可以统一表达“充值、赠送、扣费、退款、人工调整”的余额账本。注册送额必须补齐这一层，不能只执行 `balance = balance + 20`。

### 2.3 身份边界问题

当前部分余额/充值查询以 `userId` 参数查询。新增涉及资金的接口不得沿用“请求方给一个 userId 就可操作”的授权模型。赠送接口必须从可信的注册事件解析用户，而不是信任请求体里的 `userId`。

## 3. 目标行为

### 3.1 默认行为

| 场景 | 结果 |
| --- | --- |
| 普通注册 | 创建账号，余额为 `0`，不产生赠送流水。 |
| 已配置且启用的赠送套餐 | 注册成功后按套餐给满足资格的用户入账；当前首个套餐可定义为 `WELCOME_CNY20`、金额 `20.00`。 |
| 用户重复注册/重复提交/网络重试 | 最多获得一次；返回已处理结果，余额不重复增加。 |
| 套餐暂停、过期、预算耗尽、风控未通过 | 账号照常注册，赠送不发生，记录拒绝原因。 |
| 赠送流程暂时失败 | 注册结果不回滚；注册事件保留为待发放，由服务端重试任务补偿。 |

### 3.2 不允许的行为

- 不接受 `amount`、`balance`、`userId` 作为客户端可决定的赠送参数。
- 不把赠送密钥放入 JavaScript、公开仓库、默认 `lagi.yml`、Dockerfile、镜像层或日志。
- 不开放一个仅靠通用静态 Bearer Key 即可给任意用户加余额的公网接口。
- 不直接修改 `lagi_model_user_balance` 而不写账本。

## 4. 推荐架构

```text
浏览器
  | POST /user/register（用户名、密码、图形验证码；不含赠送信息）
  v
受控 LinkMind 服务端
  | POST /registerChannel
  v
SaaS 注册服务
  | 创建账号 + 记录 registration_event（一次性注册事件）
  v
受控 LinkMind 服务端
  | 带服务端签名的 POST /saas/api/internal/promotion-grants/redeem-registration
  v
SaaS PromotionGrantService
  | 校验调用方、套餐、注册事件、资格、幂等和预算
  | 同一事务：余额 + 账本 + 赠送记录 + API Key 状态
  v
余额中心 / 交易记录 / 模型调用鉴权
```

### 4.1 “注册事件”而非裸 `userId`

SaaS 在 `registerChannel` 成功创建账号后，创建一条 `registration_event`：

- `registration_id`：服务端随机 UUID；
- `user_id`：由 SaaS 自己写入；
- `registered_at`、注册来源、必要的风控标记；
- `grant_status`：`PENDING`、`GRANTED`、`REJECTED`、`RETRYABLE_FAILED`；
- 不向浏览器暴露可用于任意加钱的凭据。

赠送接口只接受 `registrationId`，从事件中获得用户 ID。这样即使调用方伪造 `userId`，也无法把一笔新注册赠送转给另一个账号。

## 5. 套餐模型

新增 `lagi_model_promotion_plan`，由 SaaS 运维/受控后台管理，不向普通用户开放写入权限。

建议字段：

| 字段 | 说明 |
| --- | --- |
| `id`、`code`（唯一） | 如 `WELCOME_CNY20`。`code` 不等于金额。 |
| `status` | `DRAFT`、`ACTIVE`、`PAUSED`、`EXHAUSTED`、`EXPIRED`。默认 `DRAFT`。 |
| `grant_amount` | 固定金额 `DECIMAL(18,8)`，示例 `20.00000000`。 |
| `currency` | 当前固定 `CNY_CREDIT`，避免以后把现金与模型额度混用。 |
| `start_at`、`end_at` | 活动窗口。 |
| `total_budget`、`issued_amount` | 总预算与已发放金额；同一事务锁定更新。 |
| `per_user_limit` | 首期固定为 `1`。 |
| `eligibility_policy` | 如 `VERIFIED_PHONE_AND_NEW_ACCOUNT` 或 `INVITE_CODE`。 |
| `allowed_issuer_id` | 哪个受控 LinkMind 服务实例可发起该套餐。 |
| `created_by`、`approved_by`、`created_at`、`updated_at` | 审批和审计。 |

**默认配置：** 不存在 `ACTIVE` 套餐，或 LinkMind 的 `registrationPromotion.enabled=false`、`planCode` 为空。也就是说部署升级后不会自动送 20 元；只有显式创建并启用 `WELCOME_CNY20` 才生效。

### 5.1 资格与反滥用

只靠用户名 + 图形验证码可以无限批量注册，不能作为 20 元赠送的资格条件。首期至少采用以下规则：

1. 已验证手机号（推荐）或运营人员发放的一次性邀请码，二者至少满足一种；
2. 同一手机号、同一邀请兑换码、同一注册事件最多一笔；
3. 套餐设总预算、有效期和每小时/每日发放上限；
4. 记录来源实例、IP 哈希、设备/请求指纹哈希，仅用于频控与审计，不把原始敏感信息写入账本；
5. 异常比例触发自动暂停套餐，需人工恢复。

若首期不做手机号验证，则只能把该能力当成内部邀请制：每位客户由后台生成一次性邀请码，不能开放为“所有公开注册自动赠送”。

## 6. 私有赠送接口与鉴权

### 6.1 接口

`POST /saas/api/internal/promotion-grants/redeem-registration`

请求体：

```json
{
  "registrationId": "c7b699aa-0e65-4c43-85ce-5e3f282063d6",
  "promotionCode": "WELCOME_CNY20",
  "idempotencyKey": "registration:c7b699aa-0e65-4c43-85ce-5e3f282063d6:WELCOME_CNY20"
}
```

接口中**没有** `amount`、`userId`、`balanceAfter` 等可改变资金归属或金额的字段。

响应示例：

```json
{
  "status": "GRANTED",
  "registrationId": "c7b699aa-0e65-4c43-85ce-5e3f282063d6",
  "ledgerEntryNo": "LGR-20261118-...",
  "grantedAmount": "20.00000000",
  "balance": "20.00000000"
}
```

对于相同 `idempotencyKey`，必须返回首次结果；不得再加钱。资格不满足应返回明确的业务状态，如 `NOT_ELIGIBLE`、`PLAN_PAUSED`、`BUDGET_EXHAUSTED`，而非 500。

### 6.2 调用方认证

该接口只允许 **LinkMind 服务端 -> SaaS 服务端** 调用。推荐组合：

1. 每个受控部署实例独立的 `issuerId` 与签名密钥或 mTLS 客户端证书；不使用公司共享的永久“万能 Key”。
2. 请求包含 `X-Issuer-Id`、`X-Key-Id`、`X-Timestamp`、`X-Nonce`、`X-Body-SHA256` 和 `X-Signature`。
3. 签名覆盖 `HTTP method + path + timestamp + nonce + body SHA-256`；SaaS 校验 5 分钟时间窗，并把 `(issuerId, nonce)` 记录为已使用，防重放。
4. 额外启用 HTTPS、mTLS 或来源 IP 白名单。签名密钥轮换时支持双 Key 过渡与立即撤销。
5. LinkMind 密钥只在受控服务的运行环境变量/密钥库读取，例如 `LINKMIND_PROMOTION_SIGNING_KEY`；仓库、`lagi.yml` 模板、镜像、前端和日志仅保存 `issuerId` / `keyId`。

签名可以防止抓到浏览器请求后构造赠送请求，也能使已抓到的服务间报文无法修改或重放。它**不能**防止拥有一台自托管服务器完全控制权的操作者盗用其运行时密钥；该威胁模型下，应不给自托管/盒子实例配置赠送资格，或改用 SaaS 托管的人工/短信验证兑换。

### 6.3 LinkMind 侧参数

在 LinkMind 的私有部署配置中只增加如下非敏感开关；开源默认值保持关闭：

```yaml
registrationPromotion:
  enabled: false
  promotionCode: ""
  issuerId: ""
  keyId: ""
```

签名私钥不写入 YAML。`enabled=true` 只允许用于公司实际控制的部署；SaaS 也会验证该 `issuerId` 是否获准使用目标套餐，所以仅改配置不能提高套餐额度或选用未授权套餐。

## 7. 统一账本设计

新增追加式表 `lagi_model_balance_ledger`：

| 字段 | 说明 |
| --- | --- |
| `id`、`entry_no`（唯一） | 全局可追踪流水号。 |
| `user_id` | 余额所属用户。 |
| `direction` | `CREDIT` 或 `DEBIT`。 |
| `amount` | 正数金额；方向由 `direction` 表示。 |
| `balance_after` | 同一事务中计算出的余额快照，便于对账。 |
| `event_type` | `PROMOTION_GRANT`、`PAYMENT_TOPUP`、`MODEL_USAGE`、`REFUND`、`MANUAL_ADJUSTMENT`。 |
| `source_type`、`source_id` | 分别关联注册事件、支付订单、用量记录等。 |
| `idempotency_key`（唯一） | 防重试重复入账。 |
| `metadata_json` | 仅保留套餐编码、模型 ID、价格版本等非敏感元数据。 |
| `created_at` | 写入时间。 |

索引/约束至少包括：`UNIQUE(idempotency_key)`、`UNIQUE(event_type, source_type, source_id)`、`INDEX(user_id, created_at)`。

### 7.1 余额变更服务

新增 SaaS 内部 `BalanceLedgerService.apply(...)`，成为唯一允许修改 `lagi_model_user_balance` 的代码入口：

1. 以用户为粒度锁定余额行（无行时安全创建）；
2. 校验幂等键和业务来源是否已入账；
3. 计算新余额，扣费不得使可用余额低于约定阈值；
4. 更新余额；
5. 插入账本；
6. 按余额状态启用/停用该用户的 LinkMind API Key；
7. 在同一数据库事务提交。

接入顺序：

| 现有流程 | 改造后 |
| --- | --- |
| 微信支付回调 `CreditService.finishCreditPayment` | 将支付订单状态更新和 `PAYMENT_TOPUP` 账本入账放进同一事务。 |
| `/apikey/calculateUsage` 的 `ApiKeyService.calculateAndChargeUsage` | 用量明细写入后，以用量记录 ID 作为来源，写 `MODEL_USAGE` 借记账本并扣余额。 |
| 新注册送额 | 写 `PROMOTION_GRANT` 贷记账本并增加余额。 |
| 人工补偿/退款 | 仅限受控管理接口，必须提供原因、审批人与来源单据，写对应账本。 |

现有 `lagi_model_charge_details` 继续作为支付订单表，`lagi_model_api_key_usage` 继续作为模型用量表；账本将二者关联起来，而不是替换或删掉它们。

## 8. 发放事务与失败处理

`PromotionGrantService.redeemRegistration()` 的单次事务应按下列顺序执行：

1. 校验服务间认证、时间窗、随机数未使用和请求签名；
2. 锁定 `registration_event`，确认它存在、归属用户有效、尚未成功发放；
3. 锁定套餐，验证套餐启用状态、有效期、调用方授权、预算与资格；
4. 以 `registrationId + promotionCode` 构造固定幂等键；若账本已存在，直接返回原结果；
5. 调用 `BalanceLedgerService` 写 `PROMOTION_GRANT`；
6. 写 `promotion_grant` 结果与套餐已发金额；
7. 提交。

注册成功但赠送调用超时不能让用户重复注册。此时注册事件应保持 `RETRYABLE_FAILED` 或 `PENDING`，由仅在 SaaS 内运行的定时任务按同一幂等键重试；超过阈值转人工审核。LinkMind 只向用户显示“赠送处理中”，不要自行加余额或重试无上限。

## 9. 审计、查询与告警

1. 余额中心增加“交易记录”，从账本读取，展示充值、赠送、扣费的时间、金额、类型、关联单号和状态。
2. 普通用户只能查看本人记录；服务端从登录会话确定用户，不能接受任意 `userId` 越权查询。
3. 管理查询与导出只部署在 SaaS 私有后台，记录操作者、查询条件和导出审计。
4. 每日对账：`余额表` 与账本按用户求和核对；支付成功订单、用量记录和账本来源逐一核对。
5. 告警：套餐预算接近耗尽、发放失败、相同手机号/来源高频注册、签名失败、nonce 重放、余额与账本不一致。
6. 日志只记录 `issuerId`、套餐码、注册事件 ID、账本号、金额和结果；不得记录密码、API Key、签名私钥或完整手机号/IP。

## 10. 实施分期

### 第一阶段：账本底座

1. 建表：套餐、注册事件、赠送记录、余额账本、nonce 防重放表；补充唯一索引。
2. 实现并覆盖单元测试 `BalanceLedgerService`。
3. 将充值和模型扣费迁移到该服务；保留旧明细表，先双向对账，不改用户可见逻辑。

### 第二阶段：受控赠送

1. 实现 SaaS 私有赠送接口、HMAC/mTLS 验证、幂等与资格校验。
2. 注册成功时写注册事件；LinkMind 服务端增加可关闭的赠送调用和“处理中”补偿逻辑。
3. 默认保持 `enabled=false`，创建 `DRAFT` 的 `WELCOME_CNY20`，在测试环境用 `0.01` 套餐完成全链路验证。

### 第三阶段：上线与风控

1. 为正式受控实例签发独立凭据，设置套餐预算、有效期、手机号/邀请码资格和告警。
2. 小流量启用 `WELCOME_CNY20`；核对账本、余额、API Key 激活、交易记录和重试结果。
3. 观察注册异常率与预算消耗；异常时一键将套餐置为 `PAUSED`，不需要停注册服务。

## 11. 验收清单

- [ ] 新用户在套餐关闭时注册，余额为 `0`，无 `PROMOTION_GRANT` 流水。
- [ ] 合格用户在 `WELCOME_CNY20` 启用时只得到一笔 `20.00000000` 赠送，余额、账本和 API Key 状态一致。
- [ ] 同一请求重放、同一注册事件重复提交、并发重试均不会多发。
- [ ] 请求体改写 `userId`、`amount`、`promotionCode` 或过期 timestamp/重复 nonce 会被拒绝。
- [ ] 未配置服务端凭据的开源/盒子部署无法调用赠送接口。
- [ ] 支付充值、模型扣费、赠送均能在交易记录中查询，并能和余额表逐日对平。
- [ ] 赠送接口失败不影响账号注册，补偿任务最终可发放或产生可审计的拒绝/失败记录。

## 12. 需要确认的产品决策

1. 20 元赠送是否必须绑定手机号；若否，则首期改为运营人员下发的一次性邀请码。
2. 赠送是否仅限公司托管的 `ai.linkmind.top`，还是允许盒子/自托管实例参与。后者无法达到“客户端被控制仍不能盗用赠送”的安全目标。
3. 赠送额度是否有有效期。若有，应增加额度分桶/到期扣回规则；若没有，当前统一余额账本即可。
4. 是否需要人工补款、退款、冻结的后台流程；如需要，应一并接入账本，不再直接更新余额表。
