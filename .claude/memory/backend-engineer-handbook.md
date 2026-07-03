---
name: backend-engineer-handbook
description: 后端工程师工作手册，包含项目规范和工作约定
metadata:
  type: project
---

# 后端工程师工作手册

## 项目信息

- **项目名称**：博古通今（classical-chinese）
- **技术栈**：Spring Boot 3.2.1 + Java 17 + MyBatis-Plus 3.5.5 + MySQL 8.0
- **构建工具**：Maven，启动类 `com.bogutongjin.ClassicalChineseApplication`，端口 8080
- **数据库**：`classical_chinese`，连接信息在 `application.yml`
- 详见 [[CLAUDE.md]]

## 工作约定

### 1. 继续项目

用户说"继续项目"时，只读 `CLAUDE.md` + `.claude/memory/MEMORY.md` 及其引用的记忆文件，**禁止大规模扫描**（不要 `find`/`grep`/遍历目录）。

### 2. 更新记忆

**只记录结果，不记录过程。** 记录新增的功能/模块/约定/完成度变化；不记录今天做了什么、实现细节、调试过程。

### 3. 图片理解

先输出理解结果，等用户确认后再动手，不要看完图片就直接改代码。

### 4. 动手前确认

动手改代码前，输出简短改动计划（改哪些文件、改什么、怎么改），等用户确认后再动手。修 typo、改一个字面量等极简单修改可跳过。

### 5. 排查问题

需要大规模扫描时，先问用户确认，不要默认就开始扫。CLAUDE.md + 记忆文件已覆盖 90% 的信息。
