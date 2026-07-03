# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 工作流程（必读）

**修改任何代码之前，必须先给出方案并等待确认，再动手执行。** 流程如下：

1. **分析**：阅读相关代码，理清问题根因
2. **方案**：列出受影响文件、具体改动内容、改动原因，输出给用户确认
3. **执行**：用户确认后再动手改代码
4. **更新手册**：改动完成后同步更新本文件中的相关描述（如架构、配置项、API 签名等）

> 此规则适用于所有代码修改。typo 修正、单行注释修正等极微小的修正可酌情跳过第 2 步，但仍需在第 4 步更新手册。

## 项目概述

博古通今（classical-chinese）——微信小程序的 Java 后端服务，面向中学生文言文实词/虚词/通假字学习。提供 16 个 HTTP API 端点（含登录），基于艾宾浩斯遗忘曲线管理学习与复习节奏。

**前端工程**位于 `/Users/zhutx/weixin_applet_space/classical-chinese-applet/`——微信原生小程序（WXML + SCSS + TS），15 个页面全部搭建完成，核心学习回路已跑通。后端 API 完整覆盖前端所有接口需求，前后端通过 `{code: 0, message: "ok", data: ...}` 统一响应格式约定。

## 开发环境

- **IDE**：IntelliJ IDEA，直接打开项目根目录即可
- **Java**：17
- **构建工具**：Maven（`pom.xml`）
- **框架**：Spring Boot 3.2.1
- **启动类**：`com.bogutongjin.ClassicalChineseApplication`，运行其 `main()` 方法
- **端口**：`8080`
- **数据库**：MySQL 8.0，数据库名 `classical_chinese`，连接信息在 `src/main/resources/application.yml`

没有 CLI 测试命令，一切调试和接口测试通过 IDEA 直接运行。

## 技术栈与约定

| 项 | 选型 |
|----|------|
| 框架 | Spring Boot 3.2.1 |
| Java 版本 | 17 |
| ORM | MyBatis-Plus 3.5.5（BaseMapper + LambdaQueryWrapper） |
| 数据库 | MySQL 8.0，库名 `classical_chinese`，字符集 `utf8mb4` |
| 工具库 | Hutool 5.8.25 |
| 简化代码 | Lombok（`@Data`、`@RequiredArgsConstructor`、`@Slf4j`） |
| 参数校验 | Spring Boot Validation（`@Valid`、`@NotBlank`、`@NotNull`） |
| JSON | Jackson（自动驼峰-下划线互转） |
| 认证 | JWT（jjwt 0.12.3）——微信登录→JWT 签发→拦截器校验→@CurrentUser 参数注入，完整链路已实现 |
| 响应格式 | 统一 `Result<T>`，`code=0` 成功，非 0 为业务异常 |

### 响应格式约定

```json
{ "code": 0, "message": "ok", "data": { ... } }
```

成功时 `code=0`；参数校验失败 `code=10001`；认证失败 `code=10401`；资源不存在 `code=10003`；服务端异常 `code=10006`。`data` 为 `null` 时不序列化（`non_null`）。

### 代码组织约定

- **Controller**：`@RestController` + `@RequestMapping`，仅做路由和参数接收，委托给 Service。使用 `@RequiredArgsConstructor` 注入依赖。需要用户身份的接口用 `@CurrentUser Long userId` 参数注入。
- **Service**：`@Service` + `@Transactional`，所有业务逻辑在此层。对外统一返回 `Map<String, Object>` 结构（方便前端直接消费，不强制 DTO）。
- **Mapper**：继承 MyBatis-Plus `BaseMapper<T>`，零 XML，使用 `LambdaQueryWrapper` 构建查询条件。
- **Entity**：`@TableName` 指定表名，驼峰字段名自动映射数据库下划线字段名。
- **common**：跨层通用类（`Result`、异常、全局异常处理）。

### 配置项

配置文件 `src/main/resources/application.yml`：

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `spring.datasource.url` | MySQL 连接 | `jdbc:mysql://localhost:3306/classical_chinese?...` |
| `spring.datasource.password` | 数据库密码 | `${MYSQL_PASSWORD:123456}`（环境变量覆盖） |
| `wechat.app-id` | 微信小程序 AppID | `wxc50759cc61eda134` |
| `wechat.app-secret` | 微信密钥 | `${WECHAT_APP_SECRET:}`（空，**开发模式**：未配时固定使用 `dev-openid` 兜底，上线前必须替换为真值） |
| `jwt.secret` | JWT 签名密钥 | `${JWT_SECRET:bogutongjin-jwt-secret-key-2024-miniapp}`（环境变量覆盖） |
| `jwt.expire-hours` | JWT 过期时间 | `168`（7 天） |
| `app.source-data-path` | 冷启动数据源路径 | `classpath:source.json` |

## 源码结构

```
src/main/java/com/bogutongjin/
├── ClassicalChineseApplication.java  # Spring Boot 入口
├── common/
│   ├── Result.java                   # 统一响应 {code, message, data}
│   ├── BusinessException.java        # 业务异常（含 code 字段）
│   ├── ResourceNotFoundException.java # 资源不存在异常
│   ├── AuthException.java            # 认证异常（code=10401）
│   └── GlobalExceptionHandler.java   # 全局异常处理（@RestControllerAdvice，含 401 处理）
├── annotation/
│   └── CurrentUser.java              # @CurrentUser 注解，标记需要注入 userId 的 Controller 参数
├── util/
│   └── JwtUtil.java                  # JWT 签发/解析/校验，密钥与过期时间取自 yml 配置
├── config/
│   ├── MyBatisPlusConfig.java        # 分页插件
│   ├── MyMetaObjectHandler.java      # 自动填充 createdAt/updatedAt
│   ├── CorsConfig.java               # 跨域配置（允许所有来源）
│   ├── LoginInterceptor.java         # JWT 拦截器：从 Authorization header 解析 JWT → request.setAttribute("userId")
│   ├── CurrentUserResolver.java      # @CurrentUser 参数解析器，从 request.getAttribute("userId") 取值
│   └── WebMvcConfig.java             # 注册拦截器（放行 /api/auth/**, /api/admin/**）+ 参数解析器
├── controller/（12 个）
│   ├── AuthController.java           # POST /api/auth/login（微信 code → JWT）— 无需认证
│   ├── WordBookController.java       # 词书列表 + 详情
│   ├── StudyController.java          # 今日任务 + 提交答案 + 完成学习（@CurrentUser）
│   ├── ArticleController.java        # 名篇列表 + 详情
│   ├── ContentController.java        # 单字详情 + 全文阅读
│   ├── ProgressController.java       # 学习进度（@CurrentUser）
│   ├── VocabularyController.java     # 生词本（@CurrentUser）
│   ├── CheckinController.java        # 打卡记录（@CurrentUser）
│   ├── BadgeController.java          # 勋章系统（@CurrentUser）
│   ├── UserController.java           # 用户信息（等级/个人信息 GET+PUT）（@CurrentUser）
│   ├── FeedbackController.java       # 错误反馈（@CurrentUser）
│   └── ImportController.java         # 数据导入（管理后台，放行）
├── service/（11 个）
│   ├── AuthService.java              # 微信 code2session → openId → 查找/创建用户 → 签发 JWT
│   ├── WordBookService.java
│   ├── StudyService.java             # 核心：艾宾浩斯调度、答题、完成
│   ├── ArticleService.java
│   ├── ContentService.java
│   ├── ProgressService.java
│   ├── VocabularyService.java
│   ├── CheckinService.java
│   ├── BadgeService.java
│   ├── UserService.java
│   ├── FeedbackService.java
│   └── DataImportService.java        # 冷启动数据导入
├── mapper/（21 个，继承 BaseMapper，零 XML）
│   ├── WordBookMapper.java
│   ├── WordMapper.java
│   ├── MeaningMapper.java
│   ├── SentenceMapper.java
│   ├── SentenceDistractorMapper.java
│   ├── SimilarHomophoneMapper.java
│   ├── SimilarShapeMapper.java
│   ├── ArticleMapper.java
│   ├── ArticleSentenceMapper.java
│   ├── ArticleKeywordMapper.java
│   ├── ArticleCharAnnotationMapper.java
│   ├── ArticleRelatedWordMapper.java
│   ├── BadgeMapper.java
│   ├── UserMapper.java
│   ├── UserWordProgressMapper.java
│   ├── UserArticleProgressMapper.java
│   ├── UserCheckinMapper.java
│   ├── UserBadgeMapper.java
│   ├── UserAnswerHistoryMapper.java
│   ├── FeedbackMapper.java
│   └── DailyTaskMapper.java
├── entity/（21 个，与 mapper 一一对应，略）
├── dto/（5 个）
│   ├── SourceData.java               # 数据源 JSON 结构（嵌套类定义完整层级）
│   ├── LoginRequest.java             # 微信登录请求 { code }
│   ├── SubmitAnswerRequest.java
│   ├── CompleteStudyRequest.java
│   ├── SaveUserInfoRequest.java
│   └── SubmitFeedbackRequest.java
src/main/resources/
├── application.yml                    # 应用配置
└── source.json                        # 冷启动数据源（188KB，与前端 data/source.json 相同）
data/
└── schema.sql                         # 完整 DDL（21 张表，含索引和外键）
```

## API 端点对照表（Controller ↔ 前端）

| 分类 | Controller | 方法 | 路径 | 认证 | 对应前端 API |
|------|-----------|------|------|------|-------------|
| 认证 | AuthController | POST | `/api/auth/login` | — | app.ts 登录流程（wx.login → code → token） |
| 词书 | WordBookController | GET | `/api/wordbooks` | Bearer | fetchWordBooks |
| 词书 | WordBookController | GET | `/api/wordbooks/{id}` | Bearer | fetchWordBookDetail |
| 学习 | StudyController | GET | `/api/study/today?wordBookId=&dailyNew=&dailyReview=` | Bearer | fetchTodayTask |
| 学习 | StudyController | POST | `/api/study/answer` | Bearer | submitAnswer |
| 学习 | StudyController | POST | `/api/study/complete` | Bearer | completeStudy |
| 进度 | ProgressController | GET | `/api/progress?wordBookId=` | Bearer | fetchProgress |
| 生词本 | VocabularyController | GET | `/api/vocabulary?wordBookId=&tab=` | Bearer | fetchVocabulary |
| 打卡 | CheckinController | GET | `/api/checkin?year=&month=` | Bearer | fetchCheckinRecords |
| 勋章 | BadgeController | GET | `/api/badges` | Bearer | fetchBadges |
| 用户 | UserController | GET | `/api/user/profile` | Bearer | fetchUserProfile |
| 用户 | UserController | GET | `/api/user/info` | Bearer | fetchUserInfo |
| 用户 | UserController | PUT | `/api/user/info` | Bearer | saveUserInfo |
| 名篇 | ArticleController | GET | `/api/articles?category=&textbook=` | Bearer | fetchArticles |
| 名篇 | ArticleController | GET | `/api/articles/{id}` | Bearer | fetchArticleDetail |
| 内容 | ContentController | GET | `/api/words/{id}` | Bearer | fetchWordDetail |
| 内容 | ContentController | GET | `/api/full-text/{sentenceId}` | Bearer | fetchFullText |
| 反馈 | FeedbackController | POST | `/api/feedback` | Bearer | submitFeedback |
| 管理 | ImportController | POST | `/api/admin/import` | — | （管理后台，无前端对应） |

> **认证机制**：`LoginInterceptor` 拦截 `/api/**`（放行 `/api/auth/**`、`/api/admin/**`），从 `Authorization: Bearer <token>` 解析 JWT 获取 userId，写入 `request.setAttribute("userId")`。Controller 通过 `@CurrentUser Long userId` 参数注入（由 `CurrentUserResolver` 从 request attribute 读取）。前端 `utils/request.ts` 自动带 token、401 时自动 re-login 并重试。

## 认证流程

```
小程序 wx.login() → code
    ↓
POST /api/auth/login { code }
    ↓
AuthService.code2session(code) → 微信 API → openId
    ↓
UserMapper 查找 openId → 不存在则 INSERT 创建新用户
    ↓
JwtUtil.generate(userId) → 签发 JWT（有效期 7 天，yml 可配）
    ↓
返回 { token, userId }
    ↓
小程序存储 token → 后续请求带 Authorization: Bearer <token>
    ↓
LoginInterceptor 解析 JWT → @CurrentUser 注入 userId → Controller
```

- 新用户首次登录自动创建账号（无需注册流程）
- Token 过期（401）→ 前端自动 re-login 并重试请求（内置防并发）
- JWT payload 仅含 userId（sub），无其他敏感信息
- /api/auth/login 和 /api/admin/import 两个路径放行，不校验 token

## 数据库设计（21 张表）

DDL 位于 `data/schema.sql`，覆盖完整业务模型：

| # | 表名 | 说明 | 关联 |
|---|------|------|------|
| 1 | `word_book` | 词书 | — |
| 2 | `word` | 字词 | FK → word_book |
| 3 | `meaning` | 义项（一字多义） | FK → word |
| 4 | `sentence` | 考题句子 | FK → word |
| 5 | `sentence_distractor` | 句子干扰项 | FK → sentence |
| 6 | `similar_homophone` | 同音易混字 | FK → word |
| 7 | `similar_shape` | 形近字 | FK → word |
| 8 | `article` | 名篇 | — |
| 9 | `article_sentence` | 名篇句子 | FK → article |
| 10 | `article_keyword` | 名篇句子内联生词 | FK → article_sentence |
| 11 | `article_char_annotation` | 名篇逐字标注（实词/虚词/标点） | FK → article_sentence |
| 12 | `article_related_word` | 名篇-字词多对多关联 | FK → article + word |
| 13 | `badge` | 勋章定义 | — |
| 14 | `user` | 用户（openId/头像/昵称/年级/经验/连续天数） | — |
| 15 | `user_word_progress` | 用户字词学习进度（艾宾浩斯阶段） | FK → user + word_book + word |
| 16 | `user_article_progress` | 用户名篇阅读进度 | FK → user + article |
| 17 | `user_checkin` | 用户打卡记录 | FK → user |
| 18 | `user_badge` | 用户获得的勋章 | FK → user + badge |
| 19 | `user_answer_history` | 答题历史记录 | FK → user |
| 20 | `feedback` | 错误反馈 | FK → user (SET NULL) |
| 21 | `daily_task` | 每日学习任务 | FK → user |

所有外键设置 `ON DELETE CASCADE`（feedback 除外，使用 `SET NULL`）。

## 核心架构模式

### 分层架构

```
Controller（路由 + 参数接收，@Valid 校验，@CurrentUser 获取 userId）
    ↓
Service（业务逻辑 + @Transactional 事务）
    ↓
Mapper（MyBatis-Plus BaseMapper，零 XML）
    ↓
Database（MySQL 8.0，21 张表）
```

- **Service 层返回值**：统一 `Map<String, Object>`，Controller 用 `Result.ok(map)` 包装后返回。
- **分页**：MyBatis-Plus 分页插件已配置（`MyBatisPlusConfig`），直接使用 `Page<T>` 即可。
- **事务**：`StudyService` 和 `DataImportService` 核心方法标注 `@Transactional`。
- **异常处理**：`GlobalExceptionHandler` 拦截 5 类异常（参数校验、认证、资源不存在、业务异常、未知异常），统一返回 `Result.fail(code, msg)`。

### 冷启动数据导入

`POST /api/admin/import` → `DataImportService.importFromJson()`

流程：
1. 从 `@Value("${app.source-data-path}")` 读取配置的路径，支持 `classpath:` 前缀（`ClassPathResource`）和文件系统路径两种方式
2. 读取 `source.json`（188KB，Hutool JSONUtil 反序列化 → `SourceData` DTO）
3. `SET FOREIGN_KEY_CHECKS = 0` → 清空 13 张业务表（保留 user 相关表数据）→ 恢复外键检查
4. 批量导入勋章 → 词书（含字词、义项、句子、干扰项、同音字、形近字）→ 名篇（含句子、生词、逐字标注、关联字词）
5. JDBC Template 批处理（`jdbc.batchUpdate`），单事务保护

数据源结构与前端 `data/source.json` 完全相同：3 词书 75 字 + 20 篇名篇 + 8 勋章。

### 艾宾浩斯调度

`StudyService` 中实现，已与客户端调度逻辑对齐（对照 `utils/ebbinghaus.ts`）：

- **进度管理**：`user_word_progress` 表记录每个字词的复习阶段（stage 0-6 / done）和下次复习日（next_review_date）
- **任务生成**：`getTodayTask()` 查询 `next_review_date <= today` 的待复习词 + 未开始的新学词
- **阶段推进**：答对→stage+1（到 6 变 done），答错→stage-1（最低 0）
- **复习间隔**：stage 0→0d, 1→1d, 2→2d, 3→4d, 4→7d, 5→15d, 6→30d→done
- **每日上限**：新学词最多 20 个/天

前端保留客户端调度能力（离线冗余），服务端作为权威数据源。实际使用时，服务端 `getTodayTask` 返回今日待学列表，客户端按该列表顺序显示；答题后调用 `submitAnswer` 保存结果并更新进度。

### 学习完成（completeStudy）

`StudyService.completeStudy()` 在一个事务中完成：
1. 打卡（幂等：同一天多次调用不重复插入）
2. 计算连续学习天数（向前回溯 user_checkin 表）
3. 更新用户经验值（正确数 × 10 XP）和连续天数
4. 更新当日 DailyTask 完成状态
5. 检查新勋章（streak 维度，自动发放未获得的勋章）

## 当前完成度

### 已完成

- ✅ 12 个 Controller 完整对接前端 15 个 API 端点 + 1 个登录接口 + 1 个管理导入接口
- ✅ 微信登录认证体系：code2session → openId 查找/创建用户 → JWT 签发 → LoginInterceptor 校验 → @CurrentUser 参数注入（含开发模式 AppSecret 兜底）
- ✅ 21 张表 DDL（含完整索引、外键、注释）
- ✅ 冷启动数据导入（188KB source.json → 13 张业务表，JDBC 批处理 + 事务保护）
- ✅ 统一响应格式 `Result<T>`（`code=0` 约定）
- ✅ 全局异常处理（参数校验/认证/资源不存在/业务异常/未知异常）
- ✅ 跨域配置（允许所有来源，适配小程序开发调试）
- ✅ MyBatis-Plus 分页插件
- ✅ 艾宾浩斯引擎服务端实现（与前端 `utils/ebbinghaus.ts` 逻辑对齐）
- ✅ 勋章自动检测与发放
- ✅ 打卡 + 连续天数计算

### 待开发

- **单元测试**：测试依赖已引入（`spring-boot-starter-test`），尚未编写
- **深层字词标注**：更多数据覆盖

### ⚠️ 上线前必须完成

- **替换 `WECHAT_APP_SECRET` 为真实值**：当前开发模式未配 AppSecret 时固定使用 `dev-openid` 兜底（`AuthService.resolveOpenId()`），仅适合本地开发。上线前需在微信公众平台 → 开发管理 → 开发设置 → AppSecret 获取真实值，设为环境变量 `WECHAT_APP_SECRET`。

## 与前端的关系

- **前端工程路径**：`/Users/zhutx/weixin_applet_space/classical-chinese-applet/`
- **前端 API 层**：`api/index.ts`（15 个端点，`USE_MOCK = false`）
- **前端请求封装**：`utils/request.ts`（`BASE_URL = 'http://localhost:8080'`，自动带 JWT、401 自动 re-login）
- **对接方式**：
  1. 前端 `app.ts` 启动时调用 `wx.login()` → `POST /api/auth/login` 获取 token
  2. 前端 `utils/request.ts` 自动在请求头带 `Authorization: Bearer <token>`
  3. 后端启动后先调用 `POST /api/admin/import` 导入冷启动数据
- **前端 CLAUDE.md**：包含完整的前端架构、15 页面清单、API 端点表、样式体系、页面开发规范等

前后端的 API 端点是一一对应的，前端 Mock 数据与后端 `source.json` 内容相同（3 词书 75 字 + 20 篇名篇 + 8 勋章），切换时数据结构兼容。
