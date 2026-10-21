# WordSpring 项目说明

> 一个离线优先的背单词 App：Android 端用 Kotlin + Compose + Room 负责全部学习体验，
> Spring Boot 服务端只负责「换设备时进度不丢」。

---

## 1. 一句话定位

**学习必须离线可用，同步只是锦上添花。**

背单词的高频场景是地铁、飞机、电梯——没有网络。所以内容（8 本词书、4 万多词条）随包
发布，调度算法跑在本地，答题、复习、统计全部不依赖服务端。服务端存在的唯一理由，是让
用户在换手机、重装之后，进度能回来。

这个取舍决定了后面所有的架构选择。

---

## 2. 整体架构

```
┌──────────────────────── Android (Kotlin / Compose) ────────────────────────┐
│  feature/      home  study  books  bookdetail  word  search  mistakes      │
│                stats  settings  onboarding  importflow                     │
│      │  UiState + 事件回调                                                  │
│  domain/       Scheduler（纯函数） SessionPlanner（纯函数） UseCase  TTS     │
│      │                                                                     │
│  data/         Repository ── Room（10 张表）  DataStore  CSV/JSON 导入导出   │
│      │                                                                     │
│  sync/         SyncClient ── Retrofit ── HTTPS ──┐   （可选，登录后启用）           │
└───────────────────────────────────────────────────┼────────────────────────┘
                                                    │ JWT
┌──────────────────────── Spring Boot 4 ────────────▼────────────────────────┐
│  auth/   注册登录（BCrypt + JWT）                                           │
│  sync/   push（事件 + 卡片快照）  pull（游标增量）                            │
│  stats/  按日聚合                                                           │
│  MySQL 8 + Flyway（4 张表）                                                 │
└────────────────────────────────────────────────────────────────────────────┘
```

**依赖方向**：feature → domain → data。`domain` 里没有 Android import，`Scheduler` 连时钟
都不碰（时间由参数传入），所以能被单元测试穷举。

---

## 3. 客户端：数据模型

10 张表，核心是 4 张：

```
word_books ─1:N─ book_entries ─1:1─ review_cards ─1:N─ review_events
                      │
                      └─N:1─ words ─1:N─ definitions

user_word_flags（收藏/难词）   daily_summaries（按日聚合）
study_sessions（队列快照）     import_jobs（导入任务）
```

三个关键设计：

1. **进度挂在 `book_entries` 上，不是 `words` 上。**
   `book_entries` 是「书 × 词」的连接表，带 `position` 排序；`review_cards.bookEntryId` 唯一。
   同一个词在四级和考研两本书里是两套独立进度，互不污染。

2. **一张卡一行，而不是一个计数器。**
   `review_cards(stage, dueAt, intervalDays, ease, step)`。
   「今天要复习多少」是 `COUNT(*) WHERE dueAt <= now` 查出来的，不是累加出来的——
   所以数量和列表永远对得上。

3. **事件不可变。**
   `review_events.answerId` 唯一，配合 Room 单事务，重复提交天然幂等。

---

## 4. 客户端：调度算法

`Scheduler` 是 SM-2 变体，纯函数：`next(card, grade, now) -> card`。

| 当前状态 | 评分 | 结果 |
|---|---|---|
| NEW | AGAIN | LEARNING，+10 分钟，ease −0.2 |
| NEW | HARD / GOOD / EASY | LEARNING step=1，+1 天 |
| LEARNING step0 | 非 AGAIN | LEARNING step=1，+1 天 |
| LEARNING step1 | 非 AGAIN | 毕业进 REVIEW，+3 天（EASY 取 max(3×1.3, 4)=4 天） |
| REVIEW | AGAIN | 退回 LEARNING，+10 分钟，ease −0.2 |
| REVIEW | HARD | +max(1 天, interval×1.2)，ease −0.15 |
| REVIEW | GOOD | +interval×ease |
| REVIEW | EASY | +max(4 天, interval×ease×1.3)，ease +0.15 |

- ease 初始 **2.5**，夹在 **[1.3, 3.2]**：防止「一直错→间隔趋近 0」和「一直对→间隔爆炸」。
- `isRelearnRequest`：用户主动点「重学」，无论什么状态都回到 10 分钟。
- `SUSPENDED`：暂停的卡永不自动出现。

**为什么不用固定艾宾浩斯表？** 固定表假设所有人遗忘速度一样。ease 因子让间隔随个人表现
漂移：同一天学的词，有人 3 天后复习，有人 15 天后复习。

---

## 5. 客户端：一次学习的完整链路

```
首页 loadSnapshot()
  ├─ 查 review_cards：dueAt <= now 且今天之前有过作答 → 待复习数
  ├─ 按词书剩余词量与目标日期算每日新词额度
  └─ 组合成 UiState（新学 X/Y、复习 A/B、预计时长、连续天数）

点「开始学习」
  └─ StartSessionUseCase
       ├─ SessionPlanner.plan()：到期卡优先 → 补 NEW（受每本书日额度限制）
       │   → 最多 10 张 → 题型混排（同类不超过连续 2 张）
       └─ 写入 study_sessions(queueSnapshot JSON, currentIndex=0)

每张卡
  ├─ 状态机：QUESTION →（翻开）ANSWER_REVEALED →（评分）SUBMITTING → FEEDBACK → 下一张
  └─ SubmitAnswerUseCase → 单事务：
       插入 review_events（answerId 唯一）
       更新 review_cards（Scheduler.next 的结果）
       累加 daily_summaries
       推进 study_sessions.currentIndex
```

**杀进程会怎样？** 事务已提交的部分都在库里；重进按 `sessionId` 读 `queueSnapshot` 和
`currentIndex`，从断点继续。

---

## 6. 服务端：为什么它不实现调度算法

这是整个项目最值得讲的取舍。

如果服务端也算一遍 SM-2，就会有两份实现、两套测试、两个「真相」。客户端离线时算了一次，
联网后服务端又算一次，只要版本不一致（比如客户端改了参数），进度就会打架。

所以：

- **客户端是调度权威**：它算出下一状态，把快照发上去。
- **服务端是共享日志**：存事件（不可变）+ 卡片投影（可重建）。
- **冲突显式返回**：版本不一致时返回服务端当前卡，客户端 rebase 后重推。

服务端因此只有 4 张表、3 组接口，却足以支撑多设备同步。

---

## 7. 服务端：数据模型与接口

```sql
users(id, email, password_hash, created_at)
review_cards(id, user_id, card_uid, book_uid, stage, due_at,
             interval_days, ease, step, updated_at, version, deleted)
review_events(id, user_id, answer_id, card_uid, grade, question_type,
              answered_at, local_date, duration_ms, received_at)
daily_summaries(id, user_id, local_date, reviewed_count, new_count,
                correct_count, study_seconds, updated_at)
```

唯一索引：`users(email)`、`review_cards(user_id, card_uid)`、`review_events(user_id, answer_id)`、
`daily_summaries(user_id, local_date)`。

| 接口 | 说明 |
|---|---|
| `POST /api/v1/auth/register` | 注册，返回 JWT |
| `POST /api/v1/auth/login` | 登录，返回 JWT |
| `POST /api/v1/sync/push` | 批量上传答题事件 + 卡片快照 |
| `GET /api/v1/sync/pull?since=` | 拉取游标之后变化的卡片 |
| `GET /api/v1/stats/daily?from=&to=` | 按日统计 |

**安全**：Spring Security 7 + JWT（HS256，7 天），BCrypt 存密码，所有查询强制带
`user_id`（从 token 取，不接受客户端传）。

---

## 8. 同步协议

```
设备 A                         服务端                        设备 B
  │  答题（本地事务）             │                              │
  │  push(events + cards) ──────▶│                              │
  │                              │ 按 (user, answerId) 去重      │
  │                              │ 按 version 应用卡片快照        │
  │                              │ 重算当日统计                   │
  │◀── {accepted, duplicates,   │                              │
  │      conflicts[], serverTime}│                              │
  │                              │◀──── login / pull(since=0) ──│
  │                              │───── cards + cursor ────────▶│
  │                              │                              │ 重建本地 Room
```

- **幂等**：`answerId` 由客户端生成（UUID），重推不会重复计数。
- **增量**：`pull` 用 `updated_at > since` 的游标，只传变化。
- **版本即逻辑时钟**：客户端每次答题把本地 `review_cards.version` 加一；推送时带上该值。
  服务端只有在 `snapshot.version >= server.version` 时才接受，否则返回 `conflicts`。
- **统计**：服务端从事件重算（distinct 卡片数 / 当日首次出现 / 非 AGAIN 数），
  所以重试安全。

**实现位置**：客户端 `com.wordspring.app.sync`（`SyncRepository` / `SyncStore` /
`WordSpringApi` / `SyncWorker`），UI 入口在「设置 → 账号 → 云同步」；后台每 6 小时
在联网时尝试一次，未登录直接跳过。

---

## 9. 测试策略

| 层 | 数量 | 内容 |
|---|---|---|
| 客户端单元测试 | **约 160 个 `@Test` / 27 个文件** | Scheduler 边界、SessionPlanner 组卷、导入解析、UiState 计算、手势判定 |
| 客户端仪器测试 | 3 个 | 启动、Hilt 注入、主流程冒烟 |
| 服务端 API 测试 | **13 个** | 注册/登录/校验/401、幂等推送、游标拉取、用户隔离、版本冲突、统计重算 |

调度器和组卷器是纯函数，所以能测「第 30 天、连续答错 5 次」这种人工测不出来的场景。

---

## 10. 关键取舍

| 取舍 | 选择 | 代价 |
|---|---|---|
| 离线优先 vs 实时同步 | 离线优先 | 冲突需要显式处理 |
| 客户端权威 vs 服务端权威 | 客户端算、服务端存 | 服务端无法独立校验算法正确性 |
| 内容随包 vs 服务端下发 | 随包（8 本词书） | 包体约 6 MB，更新词书要发版 |
| 事件日志 vs 状态覆盖 | 事件 + 投影 | 表更多，但可重放、可审计 |
| 单模块 vs 多模块 | 单模块分包 | 编译快；规模再大需拆分 |

---

## 11. 运行

```bash
# 客户端
./gradlew :app:assembleDebug          # 或 Android Studio 直接运行

# 服务端（内存 H2，无需 MySQL）
cd server && mvn spring-boot:run

# 服务端（MySQL）
docker compose up -d mysql
cd server && mvn spring-boot:run -Dspring-boot.run.profiles=mysql

# 测试
cd server && mvn test
```

---

## 12. 已知限制与演进

1. `pull` 的游标是毫秒时间戳，同毫秒写入可能漏拉；改为单调 `change_seq` 更稳。
2. 词书随包发布，更新需要发版；后续可做「词书增量下发 + 版本号」。
3. 没有 refresh token，JWT 过期需重新登录。
4. 服务端无速率限制与账号锁定。
5. 冲突策略是「版本新者胜」，没有字段级合并；同一张卡在两端同时作答会丢一次。
6. 服务端未做账号注销与数据导出（合规要求，上架前必须补）。

---

## 13. 面试可能追问

**Q：为什么复习数量是查出来的？**
因为每个词有自己的 `dueAt`。计数器无法回答「是哪几个词」，也无法在换设备后重建。
`COUNT(*)` 和列表用同一个 WHERE 条件，天然一致。

**Q：重复提交怎么防？**
`review_events.answerId` 唯一索引 + 单事务；先查后插，已存在直接返回已提交结果。

**Q：两台设备同时改同一张卡？**
各自带 `version` 推；服务端发现版本落后就把当前卡放进 `conflicts` 返回，客户端合并后重推。

**Q：为什么服务端不算调度？**
避免两份实现。客户端离线也能算，服务端只做存储和分发，职责单一。

**Q：杀进程丢进度吗？**
不丢。事务里同时写事件、卡片、统计、会话游标；重进读 `queueSnapshot` 和 `currentIndex`。

**Q：160 个测试怎么分层？**
纯函数（Scheduler/Planner/Formatter）占大头，Repository 用内存 Room，Compose 只测状态计算，
仪器测试只保冒烟。
