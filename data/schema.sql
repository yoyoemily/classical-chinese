-- ============================================
-- 博古通今 小程序 MySQL 建表语句
-- 基于 data/source.json 和 API 类型定义设计
-- ============================================

CREATE DATABASE IF NOT EXISTS classical_chinese
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
USE classical_chinese;

-- ============================================
-- 1. 词书
-- ============================================
CREATE TABLE word_book (
  id           VARCHAR(32)  NOT NULL PRIMARY KEY COMMENT '词书ID，如 wb_content_001',
  name         VARCHAR(64)  NOT NULL COMMENT '词书名称',
  description  VARCHAR(512) NOT NULL DEFAULT '' COMMENT '词书简介',
  category     VARCHAR(24)  NOT NULL DEFAULT 'middle_school' COMMENT '分类: middle_school/high_school/function/tongjia/ancient_modern',
  cover_color  VARCHAR(9)   NOT NULL DEFAULT '#4a6a5e' COMMENT '封面主题色',
  total_words  INT          NOT NULL DEFAULT 0 COMMENT '收录字词总数',
  sort_order   INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_category (category)
) ENGINE=InnoDB COMMENT='词书';

-- ============================================
-- 2. 字词
-- ============================================
CREATE TABLE word (
  id                VARCHAR(32)  NOT NULL PRIMARY KEY COMMENT '字词ID，如 wb_c_001',
  word_book_id      VARCHAR(32)  NOT NULL COMMENT '所属词书ID',
  `character`         VARCHAR(8)   NOT NULL COMMENT '汉字',
  pinyin            VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '拼音',
  character_type    VARCHAR(16)  NOT NULL DEFAULT '' COMMENT '字型: 象形字/指事字/会意字/形声字',
  explanation       VARCHAR(512) NOT NULL DEFAULT '' COMMENT '字形解释',
  oracle_form       VARCHAR(256) NOT NULL DEFAULT '' COMMENT '甲骨文图片URL',
  exam_frequency    VARCHAR(16)  NOT NULL DEFAULT '' COMMENT '考试频次，如 5年3考',
  mnemonic          VARCHAR(256) NOT NULL DEFAULT '' COMMENT '记忆口诀',
  sort_order        INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_word_book_id (word_book_id),
  INDEX idx_character (`character`)
) ENGINE=InnoDB COMMENT='字词';

-- ============================================
-- 3. 义项（一字多义）
-- ============================================
CREATE TABLE meaning (
  id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
  word_id     VARCHAR(32)  NOT NULL COMMENT '所属字词ID',
  definition  VARCHAR(256) NOT NULL COMMENT '释义说明',
  pinyin      VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '该义项的读音（多音字时区分）',
  example     VARCHAR(512) NOT NULL COMMENT '例句原文',
  translation VARCHAR(512) NOT NULL DEFAULT '' COMMENT '例句翻译',
  source      VARCHAR(128) NOT NULL DEFAULT '' COMMENT '例句出处，如《论语·为政》',
  sort_order  INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
  INDEX idx_word_id (word_id)
) ENGINE=InnoDB COMMENT='义项';

-- ============================================
-- 4. 考题句子
-- ============================================
CREATE TABLE sentence (
  id                    VARCHAR(32)  NOT NULL PRIMARY KEY COMMENT '句子ID，如 s_c_001_1',
  word_id               VARCHAR(32)  NOT NULL COMMENT '考查的字词ID',
  text                  VARCHAR(512) NOT NULL COMMENT '句子原文',
  source                VARCHAR(128) NOT NULL DEFAULT '' COMMENT '句子出处',
  translation           VARCHAR(512) NOT NULL DEFAULT '' COMMENT '整句翻译',
  target_word           VARCHAR(8)   NOT NULL COMMENT '考查的目标字',
  correct_meaning_index TINYINT      NOT NULL DEFAULT 0 COMMENT '正确答案在distractors中的序号(0-based)',
  difficulty            VARCHAR(10)  NOT NULL DEFAULT 'basic' COMMENT '难度: basic/medium/hard',
  full_text             TEXT         COMMENT '该句所在段落的全文',
  article_id            VARCHAR(32)  COMMENT '关联的名篇ID',
  audio_url             VARCHAR(256) COMMENT '预录音频URL',
  sort_order            INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
  created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_word_id (word_id),
  INDEX idx_article_id (article_id),
  INDEX idx_difficulty (difficulty)
) ENGINE=InnoDB COMMENT='考题句子';

-- ============================================
-- 5. 句子干扰项
-- ============================================
CREATE TABLE sentence_distractor (
  id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
  sentence_id VARCHAR(32)  NOT NULL COMMENT '所属句子ID',
  text        VARCHAR(128) NOT NULL COMMENT '干扰项文本',
  sort_order  TINYINT      NOT NULL DEFAULT 0 COMMENT '排序序号',
  INDEX idx_sentence_id (sentence_id)
) ENGINE=InnoDB COMMENT='句子干扰项';

-- ============================================
-- 6. 同音易混字
-- ============================================
CREATE TABLE similar_homophone (
  id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
  word_id     VARCHAR(32)  NOT NULL COMMENT '所属字词ID',
  `character`   VARCHAR(8)   NOT NULL COMMENT '同音易混字',
  sort_order  TINYINT      NOT NULL DEFAULT 0,
  INDEX idx_word_id (word_id)
) ENGINE=InnoDB COMMENT='同音易混字';

-- ============================================
-- 7. 形近字
-- ============================================
CREATE TABLE similar_shape (
  id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
  word_id     VARCHAR(32)  NOT NULL COMMENT '所属字词ID',
  `character`   VARCHAR(8)   NOT NULL COMMENT '形近字',
  sort_order  TINYINT      NOT NULL DEFAULT 0,
  INDEX idx_word_id (word_id)
) ENGINE=InnoDB COMMENT='形近字';

-- ============================================
-- 8. 名篇
-- ============================================
CREATE TABLE article (
  id                  VARCHAR(32)  NOT NULL PRIMARY KEY COMMENT '名篇ID，如 art_001',
  title               VARCHAR(64)  NOT NULL COMMENT '标题',
  author              VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '作者',
  dynasty             VARCHAR(16)  NOT NULL DEFAULT '' COMMENT '朝代',
  category            VARCHAR(16)  NOT NULL DEFAULT 'prose' COMMENT '文体: prose/argument/poem/verse',
  textbook            VARCHAR(10)  COMMENT '教材年级: grade7a~grade9b',
  full_text_audio_url VARCHAR(256) COMMENT '全文音频URL',
  sort_order          INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_category (category),
  INDEX idx_textbook (textbook)
) ENGINE=InnoDB COMMENT='名篇';

-- ============================================
-- 9. 名篇句子
-- ============================================
CREATE TABLE article_sentence (
  id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
  article_id  VARCHAR(32)  NOT NULL COMMENT '所属名篇ID',
  text        VARCHAR(1024) NOT NULL COMMENT '句子原文',
  translation VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '句子翻译',
  audio_url   VARCHAR(256) COMMENT '句子音频URL',
  sort_order  INT          NOT NULL DEFAULT 0 COMMENT '句子序号',
  INDEX idx_article_id (article_id)
) ENGINE=InnoDB COMMENT='名篇句子';

-- ============================================
-- 10. 名篇句子内联生词
-- ============================================
CREATE TABLE article_keyword (
  id                   BIGINT       AUTO_INCREMENT PRIMARY KEY,
  article_sentence_id  BIGINT       NOT NULL COMMENT '所属名篇句子ID',
  word_text            VARCHAR(32)  NOT NULL COMMENT '生词文本',
  definition           VARCHAR(256) NOT NULL COMMENT '释义',
  word_book_id         VARCHAR(32)  COMMENT '所属词书ID',
  mastery_level        VARCHAR(16)  COMMENT '掌握程度，可为空',
  sort_order           TINYINT      NOT NULL DEFAULT 0,
  INDEX idx_as_id (article_sentence_id)
) ENGINE=InnoDB COMMENT='名篇句子内联生词';

-- ============================================
-- 11. 名篇逐字标注
-- ============================================
CREATE TABLE article_char_annotation (
  id                   BIGINT       AUTO_INCREMENT PRIMARY KEY,
  article_sentence_id  BIGINT       NOT NULL COMMENT '所属名篇句子ID',
  char_text            VARCHAR(4)   NOT NULL COMMENT '单个汉字或标点',
  `role`                 VARCHAR(10)  NOT NULL COMMENT '角色: content(实词)/function(虚词)/punct(标点)',
  definition           VARCHAR(256) COMMENT '释义（实词必填，虚词可选，标点无）',
  sort_order           INT          NOT NULL DEFAULT 0 COMMENT '字符序号',
  INDEX idx_as_id (article_sentence_id)
) ENGINE=InnoDB COMMENT='名篇逐字标注';

-- ============================================
-- 12. 名篇关联字词（多对多）
-- ============================================
CREATE TABLE article_related_word (
  id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
  article_id  VARCHAR(32)  NOT NULL,
  word_id     VARCHAR(32)  NOT NULL,
  UNIQUE KEY uk_article_word (article_id, word_id),
  INDEX idx_article_id (article_id),
  INDEX idx_word_id (word_id)
) ENGINE=InnoDB COMMENT='名篇关联字词';

-- ============================================
-- 13. 勋章定义
-- ============================================
CREATE TABLE badge (
  id               VARCHAR(32)  NOT NULL PRIMARY KEY COMMENT '勋章ID，如 badge_streak_3',
  name             VARCHAR(32)  NOT NULL COMMENT '勋章名称',
  description      VARCHAR(128) NOT NULL COMMENT '勋章描述',
  icon             VARCHAR(8)   NOT NULL COMMENT '勋章图标(emoji)',
  category         VARCHAR(16)  NOT NULL DEFAULT 'streak' COMMENT '类别: streak/achievement/milestone',
  condition_type   VARCHAR(16)  NOT NULL COMMENT '获得条件类型，如 streak',
  condition_value  INT          NOT NULL COMMENT '获得条件阈值',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_category (category)
) ENGINE=InnoDB COMMENT='勋章定义';

-- ============================================
-- 14. 用户表
-- ============================================
CREATE TABLE `user` (
  id             BIGINT       AUTO_INCREMENT PRIMARY KEY,
  open_id        VARCHAR(64)  NOT NULL COMMENT '微信openId',
  union_id       VARCHAR(64)  COMMENT '微信unionId',
  avatar_url     VARCHAR(512) NOT NULL DEFAULT '' COMMENT '头像URL',
  nick_name      VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '昵称',
  grade          VARCHAR(10)  NOT NULL DEFAULT '' COMMENT '年级，如 grade8a',
  total_xp       INT          NOT NULL DEFAULT 0 COMMENT '累计经验值',
  current_streak INT          NOT NULL DEFAULT 0 COMMENT '当前连续学习天数',
  longest_streak INT          NOT NULL DEFAULT 0 COMMENT '历史最长连续天数',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_open_id (open_id),
  INDEX idx_union_id (union_id)
) ENGINE=InnoDB COMMENT='用户';

-- ============================================
-- 15. 用户字词学习进度
-- ============================================
CREATE TABLE user_word_progress (
  id                BIGINT       AUTO_INCREMENT PRIMARY KEY,
  user_id           BIGINT       NOT NULL COMMENT '用户ID',
  word_book_id      VARCHAR(32)  NOT NULL COMMENT '词书ID',
  word_id           VARCHAR(32)  NOT NULL COMMENT '字词ID',
  stage             VARCHAR(8)   NOT NULL DEFAULT '0' COMMENT '复习阶段: 0~6 或 done',
  next_review_date  DATE         COMMENT '下次复习日期',
  correct_count     INT          NOT NULL DEFAULT 0 COMMENT '累计答对次数',
  wrong_count       INT          NOT NULL DEFAULT 0 COMMENT '累计答错次数',
  reset_count       INT          NOT NULL DEFAULT 0 COMMENT '重置次数',
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_word (user_id, word_book_id, word_id),
  INDEX idx_user_id (user_id),
  INDEX idx_next_review (next_review_date)
) ENGINE=InnoDB COMMENT='用户字词学习进度';

-- ============================================
-- 16. 用户名篇阅读进度
-- ============================================
CREATE TABLE user_article_progress (
  id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT       NOT NULL COMMENT '用户ID',
  article_id      VARCHAR(32)  NOT NULL COMMENT '名篇ID',
  read_progress   INT          NOT NULL DEFAULT 0 COMMENT '已点击阅读的句子数',
  mastery         VARCHAR(16)  NOT NULL DEFAULT 'none' COMMENT '掌握程度: none/read/understood/memorized',
  last_read_date  DATE         COMMENT '最后阅读日期',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_article (user_id, article_id),
  INDEX idx_user_id (user_id)
) ENGINE=InnoDB COMMENT='用户名篇阅读进度';

-- ============================================
-- 17. 用户打卡记录
-- ============================================
CREATE TABLE user_checkin (
  id          BIGINT   AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT   NOT NULL COMMENT '用户ID',
  checkin_date DATE    NOT NULL COMMENT '打卡日期',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_date (user_id, checkin_date),
  INDEX idx_user_id (user_id),
  INDEX idx_checkin_date (checkin_date)
) ENGINE=InnoDB COMMENT='用户打卡记录';

-- ============================================
-- 18. 用户获得的勋章
-- ============================================
CREATE TABLE user_badge (
  id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT       NOT NULL COMMENT '用户ID',
  badge_id    VARCHAR(32)  NOT NULL COMMENT '勋章ID',
  earned_date DATE         NOT NULL COMMENT '获得日期',
  notified    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已通知用户',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_badge (user_id, badge_id),
  INDEX idx_user_id (user_id)
) ENGINE=InnoDB COMMENT='用户获得的勋章';

-- ============================================
-- 19. 答题历史记录
-- ============================================
CREATE TABLE user_answer_history (
  id               BIGINT       AUTO_INCREMENT PRIMARY KEY,
  user_id          BIGINT       NOT NULL COMMENT '用户ID',
  word_book_id     VARCHAR(32)  NOT NULL COMMENT '词书ID',
  word_id          VARCHAR(32)  NOT NULL COMMENT '字词ID',
  sentence_id      VARCHAR(32)  NOT NULL COMMENT '句子ID',
  selected_option  TINYINT      NOT NULL COMMENT '选择的选项序号(0-based)',
  correct          TINYINT(1)   NOT NULL COMMENT '是否答对',
  timestamp_ms     BIGINT       NOT NULL COMMENT '答题时间戳(ms)',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_word (user_id, word_book_id, word_id),
  INDEX idx_created (created_at)
) ENGINE=InnoDB COMMENT='答题历史记录';

-- ============================================
-- 20. 错误反馈
-- ============================================
CREATE TABLE feedback (
  id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT       COMMENT '用户ID（可为空，允许未登录反馈）',
  category      VARCHAR(24)  NOT NULL COMMENT '错误类别: sentence_text/translation/definition/source/annotation/article_info/other',
  source        VARCHAR(24)  NOT NULL COMMENT '反馈来源: learning/word_summary/article_reader',
  description   VARCHAR(512) NOT NULL DEFAULT '' COMMENT '用户补充描述',
  sentence_id   VARCHAR(32)  COMMENT '关联的句子ID',
  word_id       VARCHAR(32)  COMMENT '关联的字词ID',
  article_id    VARCHAR(32)  COMMENT '关联的名篇ID',
  reading_mode  VARCHAR(16)  COMMENT '名篇阅读模式',
  resolved      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已处理',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_category (category),
  INDEX idx_source (source),
  INDEX idx_resolved (resolved)
) ENGINE=InnoDB COMMENT='错误反馈';

-- ============================================
-- 21. 每日学习任务（当日生成，当日有效）
-- ============================================
CREATE TABLE daily_task (
  id                BIGINT       AUTO_INCREMENT PRIMARY KEY,
  user_id           BIGINT       NOT NULL COMMENT '用户ID',
  date              DATE         NOT NULL COMMENT '日期',
  word_book_id      VARCHAR(32)  NOT NULL COMMENT '词书ID',
  total_words       INT          NOT NULL DEFAULT 0 COMMENT '今日总词数',
  completed_count   INT          NOT NULL DEFAULT 0 COMMENT '已完成数',
  correct_count     INT          NOT NULL DEFAULT 0 COMMENT '答对数',
  wrong_count       INT          NOT NULL DEFAULT 0 COMMENT '答错数',
  status            TINYINT      NOT NULL DEFAULT 0 COMMENT '0=进行中, 1=已完成',
  completed_at      DATETIME     COMMENT '完成时间',
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_date_book (user_id, date, word_book_id),
  INDEX idx_user_id (user_id),
  INDEX idx_date (date)
) ENGINE=InnoDB COMMENT='每日学习任务';
