# WordSpring 面试答辩稿

> 按「面试官问 → 这个项目怎么答」组织。每题给：**结论 → 展开 → 代码在哪 → 追问怎么接**。
> 答不上的地方标注了「短板」，并给改法——比硬撑更加分。

---

## Q1｜自我介绍 + 项目（3 分钟）

**结论**：一个人做的离线背单词 App，Kotlin + Compose + Room + Hilt，后接了一个只负责同步的
Spring Boot 服务端。

**展开（照着说）**：

> 我做了一个离线优先的背单词 App，前端 Kotlin + Compose，本地 Room，依赖注入用 Hilt，
> 一共 13 个路由、10 来个页面，内置 8 本词书约 3.9 万词条。
>
> 核心是间隔重复：每个词在每本书里对应一张复习卡，记录阶段、到期时间、间隔和 ease 因子；
> 答对按 ease 放大间隔，答错退回学习步，保底一天，避免频繁打扰。
>
> 工程上有三点我比较满意：答题提交是幂等的（answerId 唯一 + 单事务）；学习会话存了队列
> 快照，杀进程能续；调度器是纯函数，不碰 Context 和时钟，所以能写 157 个单元测试穷举边界。
>
> 后来加了一个可选的服务端，登录后多设备同步进度。服务端不算调度，只存事件和卡片快照。

**代码位置**：`domain/scheduler/Scheduler.kt`、`data/repository/ReviewRepositoryImpl.kt`、
`server/sync/SyncService.java`

**追问「为什么做这个」**：自己背单词时市面 App 要么强制登录、要么广告多，想做一个打开就能背、
数据在自己手里的版本。

---

## Q2｜项目地图（页面、后端形态、真实程度）

**页面（13 个路由）**

| 模块 | 页面 | 作用 |
|---|---|---|
| 启动 | `onboarding` | 选目标、选词书、定每日数量 |
| 主 | `home` | 今日新学/复习、连续天数、开始学习 |
| 学习 | `study/{id}`、`study-summary/{id}` | 词卡、题型、结算 |
| 内容 | `books`、`book/{id}`、`word/{id}`、`search` | 词书列表/详情、单词详情、搜索 |
| 数据 | `stats`、`mistakes` | 统计、错题本 |
| 其他 | `settings`、`sync`、`import-csv/{id}` | 设置、云同步、CSV 导入 |

**后端**：Spring Boot 4.1 + MySQL 8（开发用 H2），4 张表、3 组接口（auth / sync / stats），
JWT 鉴权。**没有公网部署**，本地 `mvn spring-boot:run` 或 docker compose 起。

**真实程度**：未上架，GitHub Release 分发（debug 签名），给身边朋友装过。没有 DAU 数据——
这点我会直接说，不编。

---

## Q3｜首页「今日 20 / 待复习 35」怎么算出来的

**结论**：全部是**查出来的**，不是累加的；数量和列表用同一个 WHERE 条件。

**链路**（`HomeViewModel.loadSnapshot`）：

```
启用的词书
  → getBookLearningProgress()        每本书的总数/已掌握/学习中/未开始
  → 到期复习数 = countScheduledReviewDue(bookIds, now, dayStart)
  → 每本书的每日新词额度 = resolveDailyStudyPlan(剩余词量, 距目标天数, 自定义上限)
  → resolveTodayPlanTargets()  → 今日目标
  → resolveHomeMetrics()       → 待办数、预计分钟、是否全部完成
```

**到期复习的 SQL**（`ReviewCardDao.countScheduledReviewDue`）：

```sql
WHERE stage IN ('LEARNING','REVIEW')
  AND dueAt <= :now
  AND EXISTS (SELECT 1 FROM review_events p
              WHERE p.cardId = rc.id AND p.answeredAt < :dayStart)
```

最后那个 `EXISTS` 是产品细节：**今天刚学的词不算“复习”**，否则首页的复习数会把自己刚背的
词也算进去。

**跨天怎么变**：`dueAt` 是 UTC 毫秒，`dayStart` 用设备时区算当天零点；过了零点，原先
`dueAt <= now` 的卡自然进入集合，不需要“到点加数量”。`daily_summaries` 以 `localDate`
字符串为主键，跨天自动换行。

**代码位置**：`feature/home/HomeViewModel.kt`、`feature/home/HomeMetrics.kt`、
`data/db/Daos.kt`

---

## Q4｜词书加词/改顺序，已学进度会不会错位

**结论**：不会。进度不挂在下标上，挂在 `book_entries.id` 上。

**展开**：

- `book_entries` 是「书 × 词」的连接表，`UNIQUE(bookId, wordId)`，`position` 只用于排序和分页。
- `review_cards.bookEntryId` 唯一——**一张卡认的是“这个词在这本书里”**，不是第几行。
- 插词只影响新词条的位置，已存在的 entry 主键不变，卡片不动。

**追问「那词书升级、旧词书合并呢」**：`StarterBookImporter` 按 metadata 版本判断，导入时
按 `UNIQUE(bookId, wordId)` 忽略已存在项；旧版 “Complete 4500” 会合并到四级词书，卡片
跟着 entry 走，进度保留。

**短板（主动说）**：如果**删词或换词书实现**，entry id 会变，卡片就成了孤儿。目前靠“内置
词书只增不改”规避；要做增量下发，得先给词条一个稳定业务主键（见 Q12）。

---

## Q5｜复习到底怎么调度

**结论**：SM-2 变体，一个词一个状态机。

| 当前状态 | 评分 | 结果 |
|---|---|---|
| NEW | AGAIN | LEARNING，+10 分钟，ease −0.2 |
| NEW | HARD/GOOD/EASY | LEARNING step=1，+1 天 |
| LEARNING step0 | 非 AGAIN | step=1，+1 天 |
| LEARNING step1 | 非 AGAIN | 毕业进 REVIEW，+3 天（EASY 4 天） |
| REVIEW | AGAIN | 退回 LEARNING，+10 分钟，ease −0.2 |
| REVIEW | HARD | +max(1 天, interval×1.2)，ease −0.15 |
| REVIEW | GOOD | +interval×ease |
| REVIEW | EASY | +max(4 天, interval×ease×1.3)，ease +0.15 |

ease 初始 2.5，夹在 [1.3, 3.2]：防“一直错→间隔趋近 0”和“一直对→间隔爆炸”。

**为什么不用固定艾宾浩斯表**：固定表假设所有人遗忘速度一样；ease 让间隔随个人表现漂移。

**代码位置**：`domain/scheduler/Scheduler.kt`（`newCard` / `next` / `isDue`）

---

## Q6｜把算法写成数据：表里存了什么

**表**：`review_cards`

```sql
id, bookEntryId(unique), stage, dueAt, intervalDays, ease, step, updatedAt, version
```

**查询**：

```sql
-- 今天要复习多少
SELECT COUNT(*) FROM review_cards rc
JOIN book_entries be ON be.id = rc.bookEntryId
WHERE be.bookId IN (:bookIds) AND rc.stage IN ('LEARNING','REVIEW') AND rc.dueAt <= :now;

-- 具体是哪几张
SELECT rc.* FROM review_cards rc
JOIN book_entries be ON be.id = rc.bookEntryId
WHERE be.bookId IN (:bookIds) AND rc.stage IN ('LEARNING','REVIEW') AND rc.dueAt <= :now
ORDER BY rc.dueAt ASC LIMIT 10;
```

**答错那一次的推演**：REVIEW + AGAIN → `stage=LEARNING, step=0, dueAt=now+10min, ease-=0.2`。
10 分钟后它可以再次出现；再答对一次 → `step=1, +1 天`；再答对 → 回 REVIEW，+3 天。

---

## Q7｜“待复习 5 个”具体是哪 5 个 / 重装 / 欠账

**哪 5 个**：`getDueCards` 按 `dueAt` 升序取前 10（一次学习组上限），和数量查询同条件。

**重装**：本地库随卸载清空（`allowBackup=false`）。**登录过的用户**可以从服务端 `pull`
回卡片；**没登录就找不回**——这是产品取舍，我会明说，不糊弄。

**欠账**：不需要补算。`dueAt <= now` 天然包含所有逾期卡，用户三天没打开，第四天会看到
累积的复习量（也不会无限膨胀，因为卡的总数有限）。

**会话中途退出**：`study_sessions` 存 `queueSnapshot`（JSON）+ `currentIndex`，重进按
`sessionId` 恢复，不重新组卷。

---

## Q8｜页面与状态：ViewModel 怎么分、状态怎么进 UI

**怎么分**：一个页面一个 `@HiltViewModel`，各自持有 `StateFlow<XxxUiState>`。
`StudyViewModel` 通过 `SavedStateHandle` 拿 `sessionId`，所以学习页和结算页共享同一会话但不共享 VM。

**状态怎么进 UI**：`state.collectAsStateWithLifecycle()`；表单类临时状态（同步页的邮箱/密码）
用 `mutableStateOf` 放在 VM 里，不用 SavedStateHandle。

**一次跳转的链路**：

```
HomeScreen 点击
→ HomeViewModel.startStudy()
→ StartSessionUseCase（SessionPlanner 组卷 + 落 study_sessions）
→ 拿到 sessionId → navController.navigate("study/{id}")
→ StudyViewModel.load() 读会话 + 取词 + 取卡
```

**为什么不用 Activity 级共享 VM**：只有学习会话需要跨页共享，用 `sessionId` + 数据库
比共享 VM 更好测、更好恢复。

---

## Q9｜屏幕旋转数据还在吗

**结论**：这个 App **锁竖屏**（`screenOrientation="portrait"`），所以严格说没有旋转问题；
但配置变更（深色模式、字体大小）仍会重建 Activity。

**重建时为什么数据还在**：状态在 ViewModel 的 `StateFlow` 里，VM 由 `ViewModelStore` 持有，
Activity 重建后复用同一个 VM。

**首帧闪烁怎么处理**：`MainActivity` 在主线程 `runBlocking` 读一次 DataStore 拿主题，之后
用 `combine` 热更新。**这是个已知取舍**——有 ANR 风险，更好的做法是 SplashScreen 保活 +
异步读，我在文档里标了。

---

## Q10｜进程被杀之后

**VM 没了，数据在库里。**

- 学习进度：`study_sessions` 的 `queueSnapshot` + `currentIndex`，重进按 `sessionId` 续。
- 卡片状态：每次答题在一个事务里写 `review_events` / `review_cards` / `daily_summaries` /
  会话游标，要么全成功要么全回滚。
- 小状态（如 `sessionId`）：`SavedStateHandle`。

**没做的**：列表滚动位置、输入框草稿没做保存，重启会回到顶部。

**怎么自测**：`adb shell am kill com.wordspring.app`，从最近任务恢复。

---

## Q11｜协程快问快答（结合项目）

| 问题 | 答案 |
|---|---|
| `launch` vs `async` | 都是异步；`launch` 返回 `Job` 无结果、异常直接抛；`async` 返回 `Deferred`，`await()` 拿结果。项目里组卷、提交都是“发出去不回收”，所以用 `launch`；**没有必须 `async` 的并行场景**，我清楚用法但项目没用上——这点如实说 |
| 冷流/热流 | DAO 返回的 `Flow` 是冷流；VM 里 `stateIn(viewModelScope, WhileSubscribed(5000), ...)` 转成热流供 UI 订阅 |
| Dispatcher | Room 的 suspend 查询内部已切 IO；CSV 解析、备份读写在 `withContext(Dispatchers.IO)`；纯函数（调度/组卷/判题）不切线程 |
| 重组 | 状态变化 → 读到该状态的 Composable 重组；项目里学习卡用稳定的 `key`（`cardId:queueIndex`）避免串卡 |
| 取消 | 6 处 `catch (Throwable)` 已改成先判断 `CancellationException` 再重抛，避免吞掉取消 |

---

## Q12｜重试 / 重复提交会不会翻倍（我追问过两次的那题）

**不会。**

- 本地：`answerId` 是每次答题生成的 UUID，`review_events.answerId` **唯一索引**，写入在单事务里。
- 服务端：`UNIQUE(user_id, answer_id)`，重复推送只计 `duplicates`，不写事件、不加统计。
- 统计：服务端的 `daily_summaries` 是**从事件重算**（distinct 卡数 / 当日首见 / 非 AGAIN 数），
  不是 `+= 1`，所以重放安全。

**代码位置**：`ReviewRepositoryImpl.submitAnswer`、`SyncService.push`、
`ReviewEventRepository.countDistinctCards`

---

## 追问之外的加分点（主动抛）

1. **纯函数调度器**：`Scheduler` 不碰时钟，`now` 作参数传入 → 能测“第 30 天连续答错 5 次”。
2. **事件 + 投影**：`review_events` 只追加，卡片和统计都能从事件重建。
3. **版本即逻辑时钟**：卡片带 `version`，服务端只接受 `>=` 的写入，落后返回冲突。
4. **离线优先**：所有功能不依赖服务端，未登录零网络请求。

---

## 已知短板（被问到时主动承认，并给改法）

| 短板 | 影响 | 改法 |
|---|---|---|
| 同步的 `cardUid` 用本地自增 `bookEntryId` | 不同设备/不同词书版本下 id 可能不一致，同步会错配 | 换成稳定业务键：`bookUid + normalizedSpelling`，或给词条加 ECDICT 稳定 id |
| `MainActivity` 主线程 `runBlocking` 读 DataStore | 极端情况有 ANR 风险 | SplashScreen 保活 + 异步读取，读完再 setContent |
| `StudyScreen.kt` 1065 行 | 难维护 | 拆成 CardFront/CardBack/QuestionArea/Feedback 四个文件 |
| 客户端同步层没有单测 | 回归风险 | 用 MockWebServer + 内存 Room 补 3 个：推送幂等、冲突回写、游标拉取 |
| 没有账号注销/数据导出 | 国内合规 | 服务端加 `DELETE /me` + 导出接口 |
| AGP 8.5.2 配 compileSdk 36 靠 suppress | 升级隐患 | 升 AGP 8.9+ / Gradle 8.11 |
| 服务端未公网部署 | 无法真正多设备验证 | 部署到一台云主机 + 域名 + HTTPS |

---

## 一句话收尾

> 这个项目不是“功能最多的背单词 App”，而是“把离线优先、间隔重复、幂等同步这三件事讲清楚”
> 的工程练习——每个设计都能指到一段代码，每个短板我都知道怎么改。
