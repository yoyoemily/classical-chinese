# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

博古通今（classical-chinese）——微信小程序的 Java 后端服务，面向中学生文言文实词/虚词/通假字学习。提供 15 个 HTTP API 端点，基于艾宾浩斯遗忘曲线管理学习与复习节奏。

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
| 认证 | JWT（jjwt 0.12.3，依赖已引入，**待实现**） |
| 响应格式 | 统一 `Result<T>`，`code=0` 成功，非 0 为业务异常 |

### 响应格式约定

```json
{ "code": 0, "message": "ok", "data": { ... } }
```

成功时 `code=0`；参数校验失败 `code=10001`；资源不存在 `code=10003`；服务端异常 `code=10006`。`data` 为 `null` 时不序列化（`non_null`）。

### 代码组织约定

- **Controller**：`@RestController` + `@RequestMapping`，仅做路由和参数接收，委托给 Service。使用 `@RequiredArgsConstructor` 注入依赖。
- **Service**：`@Service` + `@Transactional`，所有业务逻辑在此层。对外统一返回 `Map<String, Object>` 结构（方便前端直接消费，不强制 DTO）。
- **Mapper**：继承 MyBatis-Plus `BaseMapper<T>`，零 XML，使用 `LambdaQueryWrapper` 构建查询条件。
- **Entity**：`@TableName` 指定表名，驼峰字段名自动映射数据库下划线字段名。
- **common**：跨层通用类（`Result`、异常、全局异常处理）。

### 配置项

配置文件 `src/main/resources/application.yml`：

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `spring.datasource.url` | MySQL 连接 | `jdbc:mysql://localhost:3306/classical_chinese?...` |
| `spring.datasource.password` | 数据库密码 | `${MYSQL_PASSWORD:root}`（环境变量覆盖） |
| `wechat.app-id` | 微信小程序 AppID | `wxc50759cc61eda134` |
| `wechat.app-secret` | 微信密钥 | `${WECHAT_APP_SECRET:}`（空，需环境变量覆盖） |
| `app.source-data-path` | 冷启动数据源路径 | `classpath:source.json` |

## 源码结构

```
src/main/java/com/bogutongjin/
├── ClassicalChineseApplication.java  # Spring Boot 入口
├── common/
│   ├── Result.java                   # 统一响应 {code, message, data}
│   ├── BusinessException.java        # 业务异常（含 code 字段）
│   ├── ResourceNotFoundException.java # 资源不存在异常
│   └── GlobalExceptionHandler.java   # 全局异常处理（@RestControllerAdvice）
├── config/
│   ├── MyBatisPlusConfig.java        # 分页插件
│   └── CorsConfig.java              # 跨域配置（允许所有来源）
├── controller/（11 个）
│   ├── WordBookController.java       # 词书列表 + 详情
│   ├── StudyController.java          # 今日任务 + 提交答案 + 完成学习
│   ├── ArticleController.java        # 名篇列表 + 详情
│   ├── ContentController.java        # 单字详情 + 全文阅读
│   ├── ProgressController.java       # 学习进度
│   ├── VocabularyController.java     # 生词本
│   ├── CheckinController.java        # 打卡记录
│   ├── BadgeController.java          # 勋章系统
│   ├── UserController.java           # 用户信息（等级/个人信息 GET+PUT）
│   ├── FeedbackController.java       # 错误反馈
│   └── ImportController.java         # 数据导入（管理后台）
├── service/（10 个）
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
├── dto/（4 个）
│   ├── SourceData.java               # 数据源 JSON 结构（嵌套类定义完整层级）
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

| 分类 | Controller | 方法 | 路径 | 对应前端 API |
|------|-----------|------|------|-------------|
| 词书 | WordBookController | GET | `/api/wordbooks` | fetchWordBooks |
| 词书 | WordBookController | GET | `/api/wordbooks/{id}` | fetchWordBookDetail |
| 学习 | StudyController | GET | `/api/study/today?wordBookId=&userId=` | fetchTodayTask |
| 学习 | StudyController | POST | `/api/study/answer?userId=` | submitAnswer |
| 学习 | StudyController | POST | `/api/study/complete?userId=` | completeStudy |
| 进度 | ProgressController | GET | `/api/progress?wordBookId=&userId=` | fetchProgress |
| 生词本 | VocabularyController | GET | `/api/vocabulary?wordBookId=&tab=&userId=` | fetchVocabulary |
| 打卡 | CheckinController | GET | `/api/checkin?year=&month=&userId=` | fetchCheckinRecords |
| 勋章 | BadgeController | GET | `/api/badges?userId=` | fetchBadges |
| 用户 | UserController | GET | `/api/user/profile?userId=` | fetchUserProfile |
| 用户 | UserController | GET | `/api/user/info?userId=` | fetchUserInfo |
| 用户 | UserController | PUT | `/api/user/info?userId=` | saveUserInfo |
| 名篇 | ArticleController | GET | `/api/articles?category=&textbook=` | fetchArticles |
| 名篇 | ArticleController | GET | `/api/articles/{id}` | fetchArticleDetail |
| 内容 | ContentController | GET | `/api/words/{id}` | fetchWordDetail |
| 内容 | ContentController | GET | `/api/full-text/{sentenceId}` | fetchFullText |
| 反馈 | FeedbackController | POST | `/api/feedback?userId=` | submitFeedback |
| 管理 | ImportController | POST | `/api/admin/import` | （管理后台，无前端对应） |

> **注意**：当前所有接口通过 `userId` 请求参数传递用户身份（默认值 `1`），未集成微信登录。后续改为从 JWT token 中解析 userId。

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
Controller（路由 + 参数接收，@Valid 校验）
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
- **异常处理**：`GlobalExceptionHandler` 拦截 4 类异常（参数校验、资源不存在、业务异常、未知异常），统一返回 `Result.fail(code, msg)`。

### 冷启动数据导入

`POST /api/admin/import` → `DataImportService.importFromJson()`

流程：
1. 读取 `source.json`（188KB，Jackson 反序列化 → `SourceData` DTO）
2. `SET FOREIGN_KEY_CHECKS = 0` → 清空 13 张业务表（保留 user 相关表数据）→ 恢复外键检查
3. 批量导入勋章 → 词书（含字词、义项、句子、干扰项、同音字、形近字）→ 名篇（含句子、生词、逐字标注、关联字词）
4. JDBC Template 批处理（`jdbc.batchUpdate`），单事务保护

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

- ✅ 11 个 Controller 完整对接前端 15 个 API 端点
- ✅ 21 张表 DDL（含完整索引、外键、注释）
- ✅ 冷启动数据导入（188KB source.json → 13 张业务表，JDBC 批处理 + 事务保护）
- ✅ 统一响应格式 `Result<T>`（`code=0` 约定）
- ✅ 全局异常处理（参数校验/资源不存在/业务异常/未知异常）
- ✅ 跨域配置（允许所有来源，适配小程序开发调试）
- ✅ MyBatis-Plus 分页插件
- ✅ 艾宾浩斯引擎服务端实现（与前端 `utils/ebbinghaus.ts` 逻辑对齐）
- ✅ 勋章自动检测与发放
- ✅ 打卡 + 连续天数计算

### 待开发

- **微信登录**：JWT 依赖已引入（jjwt 0.12.3），需实现微信 code2Session 换取 openId → 签发 token → 拦截器校验。当前所有接口用 `userId` 参数模拟用户身份
- **用户注册**：首次登录时自动创建 user 记录
- **接口鉴权**：JWT 拦截器统一校验，从 token 中解析 userId（替换当前 `@RequestParam userId` 模式）
- **单元测试**：测试依赖已引入（`spring-boot-starter-test`），尚未编写

## 与前端的关系

- **前端工程路径**：`/Users/zhutx/weixin_applet_space/classical-chinese-applet/`
- **前端 API 层**：`api/index.ts`（15 个端点，`USE_MOCK = true` 默认 Mock 模式）
- **前端请求封装**：`utils/request.ts`（`BASE_URL` 占位值 `https://api.example.com`）
- **对接方式**：
  1. 前端将 `api/index.ts` 中 `USE_MOCK` 设为 `false`
  2. 前端将 `utils/request.ts` 中 `BASE_URL` 替换为 `http://localhost:8080`
  3. 后端启动后先调用 `POST /api/admin/import` 导入冷启动数据
- **前端 CLAUDE.md**：包含完整的前端架构、15 页面清单、API 端点表、样式体系、页面开发规范等

前后端的 API 端点是一一对应的，前端 Mock 数据与后端 `source.json` 内容相同（3 词书 75 字 + 20 篇名篇 + 8 勋章），切换时数据结构兼容。
