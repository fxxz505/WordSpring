# WordSpring 项目文件说明

> 逐一说明每个目录和每个源码文件的职责、实现内容与所用技术。
> **不含配置文件**：`build.gradle.kts`、`settings.gradle.kts`、`gradle.properties`、
> `local.properties`、`libs.versions.toml`、`gradle/wrapper/*`、`proguard-rules.pro`、
> `.gitignore`、`pom.xml`、`application*.yml`、`docker-compose.yml`、CI YAML。
>
> 统计：**140 个 Kotlin/Java 源文件**（116 Kotlin + 24 Java），约 1.9 万行；另有 8 本词库、
> 4 版 Room schema、27 个单测文件、3 个仪器测试。

---

## 0. 顶层目录

| 目录/文件 | 作用 |
|---|---|
| `app/` | Android 客户端（单模块），Kotlin + Compose + Room |
| `server/` | Spring Boot 同步服务端（独立 Maven 工程） |
| `tools/` | 词库构建脚本：ECDICT → 内置词书 JSON |
| `docs/` | 设计文档：项目说明、词库导入说明 |
| `gradle/` | Version Catalog 与 wrapper（配置，不展开） |
| `dist/` | 本地打出的 APK（被 .gitignore 忽略） |
| `app/schemas/` | Room 导出的数据库 schema，供迁移测试使用 |

---

## 1. app/src/main/java/com/wordspring/app —— 客户端根包

### 1.1 应用入口

| 文件 | 行数 | 作用 | 技术 |
|---|---:|---|---|
| `WordSpringApp.kt` | 67 | Application 入口：Hilt 宿主；提供 WorkManager `Configuration.Provider`（用 `HiltWorkerFactory` 注入 Worker）；创建通知渠道；初始化 TTS；注册 6 小时一次的云同步任务 | Hilt、WorkManager、NotificationChannel |
| `ui/MainActivity.kt` | 72 | 唯一的 Activity：安装 SplashScreen、开启 edge-to-edge、**主线程 `runBlocking` 读一次 DataStore** 拿初始主题避免首帧闪烁，之后用 `combine` 热更新主题，`setContent` 挂 `WordSpringNavHost` | Compose、Activity Result、DataStore、Coroutines |

### 1.2 ui/ —— 导航、主题、通用组件

| 文件 | 行数 | 作用 | 技术 |
|---|---:|---|---|
| `ui/nav/Routes.kt` | 25 | 全部路由常量与带参路由构造函数（`study/{sessionId}`、`book/{bookId}` 等） | Navigation Compose 约定 |
| `ui/nav/WordSpringNavHost.kt` | 257 | 导航总装：底部 4 个 Tab（首页/词书/搜索/统计）、沉浸式路由判定（学习页隐藏底栏）、详情页 padding 计算、返回逻辑、所有 `composable{}` 注册与页面回调连线 | Navigation Compose、Scaffold、BottomBar |
| `ui/theme/Color.kt` | 24 | 品牌色板与浅/深色基础色 | Material3 ColorScheme |
| `ui/theme/Type.kt` | 23 | 字体排版（字号/行高/字重） | Material3 Typography |
| `ui/theme/Shape.kt` | 11 | 圆角规格（卡片、按钮） | Material3 Shapes |
| `ui/theme/Theme.kt` | 86 | 主题装配：`ThemeMode` 三态（跟随系统/浅/深）、Android 12+ 动态取色、`reduceMotion` 降动效、状态栏配色 | Material3、Dynamic Color |
| `ui/theme/GradeColors.kt` | 32 | 评分语义色：忘记红 / 模糊橙 / 记得绿，深色模式单独取值 | Compose 语义色封装 |
| `ui/components/StateViews.kt` | 121 | 三个通用状态视图：`LoadingState`、`EmptyState`、`ErrorState`（含重试按钮） | Compose 可复用组件 |

### 1.3 feature/ —— 按页面组织的 UI + ViewModel

#### 首页 `feature/home/`

| 文件 | 行数 | 作用 | 技术 |
|---|---:|---|---|
| `HomeScreen.kt` | 368 | 首页 UI：今日新学/复习进度、连续天数、词量统计、开始学习按钮、空态与加载态 | Compose、LazyColumn、状态提升 |
| `HomeViewModel.kt` | 129 | 聚合首页数据：启用的词书 → 每日新词额度 → 到期复习数 → 今日目标 → `HomeUiState`；触发建会话 | Hilt、StateFlow、`stateIn(WhileSubscribed)` |
| `HomeMetrics.kt` | 39 | 纯函数：把「到期数 / 可用新词 / 今日已完成」换算成待办数、预计分钟、是否全部完成 | 纯 Kotlin（可单测） |

#### 学习 `feature/study/`

| 文件 | 行数 | 作用 | 技术 |
|---|---:|---|---|
| `StudyScreen.kt` | **1065** | 学习主界面：卡片翻转、左右上滑评分、题型区（回忆/拼写/选择）、反馈层、进度条、TTS 播放、退出确认。项目里最大的文件 | Compose、`Animatable`/`animate*AsState`、`pointerInput`、`AnchoredDraggable` 思路 |
| `StudyViewModel.kt` | 410 | 学习状态机：`CardState`（QUESTION/ANSWER_REVEALED/SUBMITTING/FEEDBACK/COMPLETE/ERROR）、`StudyUiState`、组卷加载、判题、提交、错卡回队、TTS 状态 | Hilt、SavedStateHandle、StateFlow、Coroutines |
| `CardContent.kt` | 89 | 卡片内容模型与解析：按词性分组释义（`SenseLine`）、词形变化（`WordFormItem`，解析 ECDICT `p:/d:/i:` 格式） | 纯 Kotlin |
| `CardTransition.kt` | 13 | 卡片切换时的偏移复位与稳定的 `key`（`cardId:queueIndex`） | Compose 动画辅助 |
| `RecallCardGestures.kt` | 101 | 自定义手势：拖拽 + 速度判定 + 回弹，封装成 `Modifier.recallCardGestures` | Compose `pointerInput`、`Animatable` |
| `RecallCardActionResolver.kt` | 5 | 轻点 vs 拖拽的判定（touchSlop） | 纯函数 |
| `SwipeGradeResolver.kt` | 43 | 滑动方向 → 评分（右滑=忘记、上滑=模糊、左滑=记得）+ 视觉色调 | 纯函数 + 枚举 |
| `StudyGroupProgress.kt` | 16 | 单组学习进度（当前/总数），排除已回队的卡 | 纯函数 |
| `DefinitionDisplayFormatter.kt` | 56 | 释义展示格式化：去重、截断、词性标签 | 纯 Kotlin |
| `summary/StudySummaryScreen.kt` | 82 | 学习结算页：本轮数量、正确率、用时、去复习错题/回首页 | Compose |
| `summary/StudySummaryViewModel.kt` | 67 | 结算数据组装（读会话 + 事件统计） | Hilt、StateFlow |

#### 词书 `feature/books/`

| 文件 | 行数 | 作用 | 技术 |
|---|---:|---|---|
| `BooksScreen.kt` | 140 | 词书列表：启用/停用、进度、词数、进入详情、导入入口 | Compose、LazyColumn |
| `BooksViewModel.kt` | 42 | 词书列表状态与开关操作 | Hilt、StateFlow |
| `bookdetail/BookDetailScreen.kt` | 256 | 词书详情：词表浏览、每日新词设置、目标完成日期、进度预览、删除词书确认 | Compose、LazyColumn、Dialog |
| `bookdetail/BookDetailViewModel.kt` | 119 | 详情页状态：词条分页、计划参数、保存设置、删除影响评估 | Hilt、StateFlow |
| `bookdetail/CompletionPlanPreview.kt` | 16 | 纯函数：按剩余词量与目标日期反推每日新词数 | 纯 Kotlin（可单测） |

#### 其他页面

| 文件 | 行数 | 作用 | 技术 |
|---|---:|---|---|
| `onboarding/OnboardingScreen.kt` | 183 | 首次引导三步：选目标 → 选词书 → 定每日数量 | Compose、Pager/步骤状态 |
| `onboarding/OnboardingViewModel.kt` | 113 | 引导状态机（`OnboardingGoal/Step`），建词书、写设置、跳首页 | Hilt、StateFlow |
| `search/SearchScreen.kt` | 98 | 搜索页：输入框、结果列表、空态 | Compose、防抖 |
| `search/SearchViewModel.kt` | 36 | 搜索查询（拼写前缀 + 释义关键字），结果状态 | Hilt、Flow、debounce |
| `mistakes/MistakesScreen.kt` | 97 | 错题本：错词列表、进入词详情、一键重学 | Compose |
| `mistakes/MistakesViewModel.kt` | 46 | 错题查询（按时间窗/词书过滤）与重学会话 | Hilt、StateFlow |
| `stats/StatsScreen.kt` | 109 | 统计页：连续天数、日/周学习量、正确率 | Compose |
| `stats/StatsViewModel.kt` | 65 | 统计聚合（读 daily_summaries + 事件） | Hilt、Flow |
| `word/WordDetailScreen.kt` | 162 | 单词详情：音标、释义、词形、例句、收藏/难词、发音、重学 | Compose、TTS |
| `word/WordDetailViewModel.kt` | 102 | 单词详情状态与标记、重学入口 | Hilt、StateFlow |
| `settings/SettingsScreen.kt` | 442 | 设置页：学习偏好、外观、TTS、提醒、账号（云同步入口）、备份/恢复、清除数据、关于 | Compose、SAF（`CreateDocument`/`OpenDocument`）、权限请求 |
| `settings/SettingsViewModel.kt` | 108 | 设置读写（DataStore）、备份导出/校验/恢复、清库 | Hilt、DataStore、Coroutines |
| `importflow/ImportCsvScreen.kt` | 89 | CSV 导入页：选文件、解析预览、确认导入、错误展示 | Compose、SAF |
| `importflow/ImportCsvViewModel.kt` | 62 | 导入流程：解析 → 预览 → 提交，状态与错误处理 | Hilt、StateFlow |
| `sync/SyncScreen.kt` | 160 | 云同步页：未登录显示邮箱/密码 + 登录/注册；已登录显示待上传数、上次同步、立即同步、退出 | Compose、`collectAsStateWithLifecycle` |
| `sync/SyncViewModel.kt` | 43 | 同步页表单与动作（登录/注册/同步/退出） | Hilt、Compose State |

---

## 2. domain/ —— 纯业务层（不依赖 Android）

| 文件 | 行数 | 作用 | 技术 |
|---|---:|---|---|
| `model/Enums.kt` | 24 | 全项目枚举：`Stage`（NEW/LEARNING/REVIEW/SUSPENDED）、`Grade`（AGAIN/HARD/GOOD/EASY）、`QuestionType`、`SessionStatus`、`ImportJobStatus`、`BookSourceType` | Kotlin enum |
| `model/Models.kt` | 123 | 领域模型：`WordBook`、`Word`、`Definition`、`BookEntry`、`ReviewCard`、`ReviewEvent`、`DailySummary`、`ImportJob`、`StudySession`、`SessionCardRef`、`UserWordFlag`、`BookLearningProgress` | 不可变 data class |
| `scheduler/Scheduler.kt` | **209** | **核心算法**：SM-2 变体。`newCard()` 建初始卡；`next(card, grade, now)` 纯函数推进状态（学习步 10 分钟/1 天 → 毕业 3 天；REVIEW 按 ease 放大或退回）；`isDue()`、`isNewPickup()`；ease 夹在 [1.3, 3.2] | 纯 Kotlin，无 Context/无时钟 |
| `session/AnswerChecker.kt` | 58 | 判题：拼写归一化比对（大小写/空格/连字符）、选择题选项判定，返回结构化结果 | 纯 Kotlin |
| `session/SessionPlanner.kt` | 104 | **组卷**：到期卡优先 → 按每本书剩余额度补新词 → 上限 10 张 → 题型混排（同类型不连续超过 2 张） | 纯函数 + 可注入 `Random` |
| `study/DailyStudyPlan.kt` | 25 | 每日计划：给定剩余词量与目标天数，反推建议新词数（向上取整），支持自定义上限 | 纯函数 |
| `tts/TtsController.kt` | 108 | 系统 TTS 封装：初始化、可用性探测（`EnglishLanguage`）、`speak()`、语速、`speaking` 状态流 | Android TextToSpeech、StateFlow |
| `usecase/StartSessionUseCase.kt` | 83 | 建会话用例：读词书与进度 → 调 `SessionPlanner` → 落 `study_sessions` 快照 → 返回 `StudySession?` | Hilt、Coroutines |
| `usecase/SubmitAnswerUseCase.kt` | 38 | 提交答案用例：组装 `AnswerCommand`（含 UUID `answerId`）→ 调 Repository 幂等事务 → 返回 `AnswerResult` | Hilt、UUID |

---

## 3. data/ —— 数据层

### 3.1 数据库 `data/db/`

| 文件 | 行数 | 作用 | 技术 |
|---|---:|---|---|
| `Entities.kt` | 141 | **10 张表**：`word_books`、`words`、`definitions`、`book_entries`（书×词，唯一索引 + position）、`review_cards`（一卡一行，stage/dueAt/ease/step/version）、`review_events`（answerId 唯一 + synced）、`user_word_flags`、`daily_summaries`、`import_jobs`、`study_sessions`（队列 JSON） | Room `@Entity`/`@Index` |
| `Daos.kt` | **404** | 10 个 DAO：词书/词/释义/词条/复习卡/事件/标记/日统计/导入任务/会话。含聚合查询（`countDue`、`countScheduledReviewDue`、`getBookLearningProgress`）、五表联查错题、随机干扰项（COUNT + OFFSET）、未同步事件 | Room `@Dao`/`@Query`/`@Transaction` |
| `WordSpringDatabase.kt` | 51 | 数据库声明（version 4、10 个实体、导出 schema）；`Converters`；`build()` 挂全部迁移，明确**禁用 destructive migration** | Room `@Database`/`@TypeConverter` |
| `WordSpringMigrations.kt` | 64 | 1→2 加 `targetEpochDay`；2→3 加 `forms`/`source` 并去重释义；3→4 加 `version`、`synced` | Room `Migration`、原生 SQL |

### 3.2 导入导出 `data/imp/`

| 文件 | 行数 | 作用 | 技术 |
|---|---:|---|---|
| `CsvParser.kt` | 154 | CSV 解析：引号转义、逗号/换行、表头识别、逐行校验，输出有效行 + 错误行 | 手写状态机（无第三方 CSV 库） |
| `CsvImporter.kt` | 128 | 写库：建自定义词书 → 去重插 `words` → 插 `definitions` → 建 `book_entries` → 记录 `import_jobs` | Room 事务、归一化 |
| `JsonBackup.kt` | 201 | 备份/恢复：导出用户数据 JSON（schemaVersion）、校验、按表合并恢复；失败不留半套数据 | kotlinx.serialization、Room 事务 |

### 3.3 偏好与工具

| 文件 | 行数 | 作用 | 技术 |
|---|---:|---|---|
| `prefs/SettingsRepository.kt` | 82 | DataStore 封装：主题、动态色、降动效、触觉、高级评分、答错显答案、TTS 语速、提醒时间 | DataStore Preferences、Flow |
| `Mappers.kt` | 144 | Entity ↔ Domain 双向映射；会话 `queueSnapshot` 在此 JSON 序列化 | 扩展函数、kotlinx.serialization |
| `NormalizerUtil.kt` | 18 | 拼写归一化（小写、去空格/连字符），用于查重与判题 | 纯函数 |
| `TimeUtil.kt` | 65 | `nowMillis()`、今日零点、`localDateString()`（设备时区）、epochDay 转换 | java.time（minSdk 26） |

### 3.4 仓库 `data/repository/`

| 文件 | 行数 | 作用 | 技术 |
|---|---:|---|---|
| `ContentRepository.kt` | 55 | 内容仓库接口：词书 CRUD、词条分页、单词/释义、干扰项、建卡、进度聚合、`DeleteBookImpact` | 接口隔离 |
| `ContentRepositoryImpl.kt` | 170 | 实现：词书开关/日限/目标日期、按 position 分页、`getOrCreateReviewCard`、随机干扰项、删除影响评估 | Room、协程 |
| `ReviewRepository.kt` | 53 | 复习仓库接口：到期/新卡统计、提交答案（`AnswerCommand`/`AnswerResult`）、错题、暂停、重学 | 领域契约 |
| `ReviewRepositoryImpl.kt` | 206 | **事务核心**：`submitAnswer` 单事务完成「幂等检查 → 写事件 → 更新卡（version+1）→ 累加日统计 → 推进会话语义」；失败整体回滚 | Room `withTransaction` |
| `SessionRepository.kt` | 23 | 学习会话接口：读写会话、推进游标、取活跃会话 | 接口隔离 |
| `SessionRepositoryImpl.kt` | 69 | 会话持久化：快照 JSON 存取、状态迁移、清理 | Room、JSON |
| `StatsRepository.kt` | 27 | 统计接口：今日/区间汇总、连续天数、`StatsSnapshot` | 接口隔离 |
| `StatsRepositoryImpl.kt` | 34 | 统计实现：按区间读 `daily_summaries` 并计算 streak | Room、java.time |

### 3.5 内置词书 `data/seed/`

| 文件 | 行数 | 作用 | 技术 |
|---|---:|---|---|
| `BuiltInBookCatalog.kt` | 39 | 8 本内置词书的 asset 路径、名称、默认日限、license | 常量表 |
| `StarterBookDto.kt` | 53 | 词书 JSON 的 DTO：metadata、entry（拼写/音标/词形/多词性释义） | kotlinx.serialization |
| `StarterBookImporter.kt` | **300** | 首次启动导入：读 assets → 建词书 → 批量插词/释义/词条 → 建卡 → 升级时合并旧词书并保留进度 → 幂等跳过 | 协程、Room 事务、JSON |

---

## 4. di/ —— 依赖注入

| 文件 | 行数 | 作用 | 技术 |
|---|---:|---|---|
| `DatabaseModule.kt` | 37 | 提供 `WordSpringDatabase` 单例与 10 个 DAO | Hilt `@Provides` |
| `RepositoryModule.kt` | 22 | `@Binds` 把 4 个仓库实现绑定到接口 | Hilt `@Binds` |
| `SyncModule.kt` | 58 | 提供同步用 `Json`、`OkHttpClient`（超时 + 日志）、Retrofit `WordSpringApi`（`BuildConfig.SYNC_BASE_URL`），绑定 `SyncRepositoryImpl` | Hilt、Retrofit、OkHttp |

---

## 5. sync/ —— 云同步

| 文件 | 行数 | 作用 | 技术 |
|---|---:|---|---|
| `SyncDtos.kt` | 45 | 与后端一一对应的 DTO：注册/登录、`PushRequest`/`PushEvent`/`CardSnapshot`、`PullResponse`、`Conflict` | kotlinx.serialization |
| `WordSpringApi.kt` | 22 | Retrofit 接口：`register`、`login`、`push`、`pull`（Bearer） | Retrofit |
| `SyncStore.kt` | 64 | DataStore 会话：token、email、上次同步、pull 游标、设备 ID（首次生成 UUID） | DataStore、UUID |
| `SyncRepository.kt` | 24 | 同步契约与 `SyncUiState`（登录态/待上传/同步中/提示） | 接口 + StateFlow |
| `SyncRepositoryImpl.kt` | 173 | 编排：取未同步事件 → 组快照推送 → 标记已同步 → 回写冲突 → 按游标拉增量 → 事务写库 | 协程、Room 事务、Retrofit |
| `SyncWorker.kt` | 25 | `@HiltWorker` 后台任务：`syncNow()`，失败最多重试 3 次 | WorkManager |

---

## 6. worker/ —— 学习提醒

| 文件 | 行数 | 作用 | 技术 |
|---|---:|---|---|
| `ReminderScheduler.kt` | 69 | 计算下次提醒时间并排 `OneTimeWorkRequest`（重启后恢复） | WorkManager、java.time |
| `ReminderWorker.kt` | 100 | 发提醒通知（渠道 + PendingIntent 打开 App），无待学内容则跳过 | `@HiltWorker`、NotificationCompat |

---

## 7. app/src/main/res —— 资源

| 文件 | 作用 |
|---|---|
| `values/strings.xml` | 171 条文案：页面标题、按钮、错误提示、通知文案 |
| `values/themes.xml` | 应用主题与 Splash 主题（启动窗口背景） |
| `drawable/ic_launcher_background.xml`、`ic_launcher_foreground.xml` | 自适应图标的前景/背景矢量图 |
| `mipmap-anydpi-v26/ic_launcher.xml`、`ic_launcher_round.xml` | 自适应图标声明（Android 8+） |
| `xml/file_paths.xml` | FileProvider 可共享路径（备份文件导出） |
| `xml/backup_rules.xml`、`data_extraction_rules.xml` | 系统备份/迁移规则（应用内数据默认不参与云备份） |
| `xml/network_security_config.xml` | 只对 `10.0.2.2`/`localhost`/`127.0.0.1` 放开明文 HTTP，其余强制 HTTPS |

---

## 8. app/src/main/assets/books —— 内置词库

8 个目录结构一致，每本 3 个文件：

| 目录 | 词数 | `metadata.json` | `entries.json` | `LICENSE.txt` |
|---|---:|---|---|---|
| `zhongkao/` 中考 | 1603 | 书名、来源、默认日限 | 词条数组：拼写、音标、词形、按词性分组的释义 | ECDICT 授权说明 |
| `gaokao/` 高考 | 3677 | 同上 | 同上 | 同上 |
| `cet4/` 四级 | 3849 | 同上 | 同上 | 同上 |
| `cet6/` 六级 | 5407 | 同上 | 同上 | 同上 |
| `kaoyan/` 考研 | 4801 | 同上 | 同上 | 同上 |
| `ielts/` 雅思 | 5040 | 同上 | 同上 | 同上 |
| `toefl/` 托福 | 6974 | 同上 | 同上 | 同上 |
| `gre/` GRE | 7504 | 同上 | 同上 | 同上 |

`entries.json` 由 `tools/` 下的脚本从 ECDICT 生成，词序按词频排列。首次启动时由
`StarterBookImporter` 解析入库。

---

## 9. app/schemas —— Room schema 快照

| 文件 | 作用 |
|---|---|
| `4 个 JSON（1/2/3/4）` | 每次数据库版本升级的导出结构，`MigrationTestHelper` 用它验证迁移前后一致性；同时在代码评审时可直接 diff 表结构 |

---

## 10. app/src/test —— 26 个单元测试文件（约 157 个 @Test）

### 10.1 数据层

| 文件 | 覆盖内容 |
|---|---|
| `data/NormalizerUtilTest.kt` | 归一化：大小写、空格、连字符、空串 |
| `data/TimeUtilTest.kt` | 今日零点、localDate、时区边界 |
| `data/imp/CsvParserTest.kt` | 引号转义、逗号、空行、表头缺失、非法行 |
| `data/imp/JsonBackupTest.kt` | 导出/校验/恢复、版本不兼容、损坏 JSON |
| `data/repository/ReviewRepositoryImplTest.kt` | 内存 Room：提交答案幂等、事务回滚、日统计累加（Robolectric） |
| `data/seed/BuiltInBookCatalogTest.kt` | 8 本书元数据完整性 |
| `data/seed/StarterBookImporterTest.kt` | 首次导入、重复导入幂等、旧词书合并保留进度 |

### 10.2 领域层

| 文件 | 覆盖内容 |
|---|---|
| `domain/model/BookLearningProgressTest.kt` | 进度聚合（已掌握/学习中/未开始） |
| `domain/scheduler/SchedulerTest.kt` | **算法边界**：NEW/LEARNING/REVIEW 全分支、ease 夹取、EASY 下限、重学、SUSPENDED |
| `domain/session/AnswerCheckerTest.kt` | 拼写判定（大小写/空格）、选择题正误 |
| `domain/session/SessionPlannerTest.kt` | 到期优先、每书额度、上限 10、题型不连续重复 |
| `domain/study/DailyStudyPlanTest.kt` | 向上取整、目标日期已过、自定义上限 |

### 10.3 UI 状态与纯函数

| 文件 | 覆盖内容 |
|---|---|
| `feature/books/bookdetail/CompletionPlanPreviewTest.kt` | 完成日期反推每日词数 |
| `feature/home/HomeMetricsTest.kt` | 待办数、预计时长、全部完成判定 |
| `feature/study/CardContentTest.kt` | 词性分组、词形解析 |
| `feature/study/CardTransitionTest.kt` | 切卡偏移复位、key 稳定性 |
| `feature/study/DefinitionDisplayFormatterTest.kt` | 释义去重/截断 |
| `feature/study/RecallCardActionResolverTest.kt` | 点击 vs 拖拽阈值 |
| `feature/study/RecallCardGesturesTest.kt` | 手势状态机、速度判定 |
| `feature/study/RecallCardInteractionPolicyTest.kt` | 交互策略（何时可翻面/可评分） |
| `feature/study/StudyGroupProgressTest.kt` | 组进度（排除回队卡） |
| `feature/study/SwipeFlingTest.kt` | 甩动速度阈值 |
| `feature/study/SwipeGradeResolverTest.kt` | 方向 → 评分映射 |
| `feature/word/WordDetailDefinitionsTest.kt` | 详情页释义排序与合并 |
| `ui/nav/BottomNavigationTest.kt` | 底栏选中态与路由映射 |
| `ui/nav/DetailScreenContentPaddingTest.kt` | 详情页 inset 计算 |

---

## 11. app/src/androidTest —— 3 个仪器测试

| 文件 | 作用 |
|---|---|
| `HiltTestRunner.kt` | 自定义 Runner，用 `HiltTestApplication` 跑仪器测试 |
| `AppSmokeTest.kt` | 冷启动冒烟：Application 能起来、Hilt 图完整 |
| `MainActivityLaunchTest.kt` | 启动 MainActivity，验证 Compose 首屏渲染 |

---

## 12. server/ —— Spring Boot 同步服务端

### 12.1 启动与配置

| 文件 | 作用 | 技术 |
|---|---|---|
| `WordSpringServerApplication.java` | `@SpringBootApplication` 启动类 | Spring Boot 4.1 |
| `config/SecurityConfig.java` | 无状态安全链：放行 `/api/v1/auth/**`，其余需认证；注册 `JwtAuthenticationFilter`；401 入口 | Spring Security 7 |
| `security/JwtService.java` | 签发/解析 JWT（HS256，subject=userId，7 天过期） | jjwt 0.12 |
| `security/JwtAuthenticationFilter.java` | 解析 `Authorization: Bearer`，把 userId 放进 `SecurityContext`；非法 token 降级为匿名 | OncePerRequestFilter |
| `common/ApiExceptionHandler.java` | 校验异常 → 400 + 字段级错误信息 | `@RestControllerAdvice` |

### 12.2 认证

| 文件 | 作用 | 技术 |
|---|---|---|
| `auth/AuthController.java` | `POST /register`（201）、`POST /login`（200） | REST |
| `auth/AuthService.java` | 邮箱归一化、重复注册 409、BCrypt 校验、签发 token | BCrypt、事务 |
| `auth/AuthDtos.java` | 请求/响应 record：`RegisterRequest`、`LoginRequest`、`TokenResponse` | Java record + Bean Validation |
| `user/User.java` | `users` 表实体：email、password_hash、created_at | JPA |
| `user/UserRepository.java` | `findByEmail`、`existsByEmail` | Spring Data JPA |

### 12.3 同步

| 文件 | 作用 | 技术 |
|---|---|---|
| `sync/SyncController.java` | `POST /api/v1/sync/push`、`GET /api/v1/sync/pull?since=`，userId 从 token 取 | REST + `@AuthenticationPrincipal` |
| `sync/SyncService.java` | **协议核心**：按 `answerId` 去重 → 写事件 → 每卡取最新快照 → 版本比较（`>=` 接受，落后返回 `Conflict`）→ 重算当日统计 | `@Transactional` |
| `sync/SyncDtos.java` | `PushRequest`/`EventItem`/`CardSnapshot`/`Conflict`/`PullResponse`/`DailySummary` | record + 校验注解 |
| `sync/ReviewCardEntity.java` | `review_cards` 实体：card_uid、stage、due_at、interval_days、ease、step、version、deleted | JPA |
| `sync/ReviewCardRepository.java` | 按 `(userId, cardUid)` 查、按 `updatedAt > since` 拉增量 | Spring Data |
| `sync/ReviewEventEntity.java` | `review_events` 实体：answer_id、grade、answered_at、local_date、duration_ms | JPA |
| `sync/ReviewEventRepository.java` | 幂等判断 + 三条 JPQL 聚合（distinct 卡数、当日首见、非 AGAIN 数）+ 时长求和 | JPQL |
| `sync/DailySummaryEntity.java` | `daily_summaries` 实体（一人一天一行） | JPA |
| `sync/DailySummaryRepository.java` | 按人+日期查、区间查 | Spring Data |
| `stats/StatsController.java` | `GET /api/v1/stats/daily?from=&to=` 返回按日统计 | REST |

### 12.4 服务端测试（4 个文件）

| 文件 | 覆盖内容 |
|---|---|
| `ApiTestSupport.java` | 测试基类：`RANDOM_PORT` 起真实服务，用 `java.net.http` 发请求、Jackson 解析 |
| `ApplicationContextTest.java` | 上下文能起来（等于验证 Flyway + JPA 映射 + 数据源） |
| `AuthApiTest.java` | 注册 201、重复邮箱 409、密码错误 401、邮箱格式 400、未带 token 401 |
| `SyncApiTest.java` | 同 answerId 重推幂等、游标拉取、用户隔离、版本落后返回冲突、统计重算 |

---

## 13. tools/ —— 词库与打包脚本

| 文件 | 作用 | 技术 |
|---|---|---|
| `convert_ecdict.py` | 解析 ECDICT CSV，按考试标签筛词、按词频排序、抽取音标/词形/释义 | Python |
| `build_wordbooks.py` | 把转换结果写成 8 本词书的 `entries.json` + `metadata.json` | Python |
| `build-debug-apk.ps1` | 一条命令构建 debug APK 并复制到 `dist/` | PowerShell |
| `ECDICT_LICENSE.txt` | 词库授权原文 | — |

---

## 14. docs/ —— 文档

| 文件 | 作用 |
|---|---|
| `PROJECT.md` | 项目总说明：定位、架构、数据模型、调度算法、同步协议、取舍、限制、面试问答 |
| `FILE-GUIDE.md` | 本文：逐目录逐文件说明 |
| `ecdict-import.md` | ECDICT 来源、过滤规则、词书生成流程、授权说明 |

---

## 15. 按技术点索引（面试时按这条线讲）

| 技术 | 落在哪些文件 |
|---|---|
| **Kotlin 协程 / Flow** | `Scheduler` 之外的几乎所有 Repository、全部 ViewModel、`SyncRepositoryImpl` |
| **Compose** | `feature/**/​*Screen.kt`、`ui/theme/*`、`ui/components/StateViews.kt` |
| **状态管理（UDF）** | 每个 `*UiState` + `StateFlow` + `collectAsStateWithLifecycle` |
| **Room** | `data/db/*`（实体/DAO/迁移/schema），仓库层全部走 DAO |
| **Hilt** | `WordSpringApp`、`di/*`、所有 `@HiltViewModel`、`@HiltWorker` |
| **WorkManager** | `worker/*`（提醒）、`sync/SyncWorker`（后台同步） |
| **kotlinx.serialization** | `data/seed/StarterBookDto`、`data/imp/JsonBackup`、`sync/SyncDtos`、`Mappers` |
| **Retrofit / OkHttp** | `sync/WordSpringApi`、`di/SyncModule` |
| **DataStore** | `data/prefs/SettingsRepository`、`sync/SyncStore` |
| **测试** | JUnit4 + Truth + MockK + Turbine + Robolectric + 内存 Room；服务端用 JUnit5 + 真实 HTTP |
| **Spring Boot** | `server/**`：Web + JPA + Security + Validation + Flyway |
| **JWT** | `security/JwtService`、`JwtAuthenticationFilter`、`config/SecurityConfig` |
