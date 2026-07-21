#!/bin/bash
# ============================================================
# 选篇正文全量导入（198篇，拆分为12个分文件）
# 从知识库 articles_*.json 读取，拼接后发送，幂等（先清空后插入）
# ============================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
KNOWLEDGE_LIB="${KNOWLEDGE_LIB:-$HOME/Documents/knowledge_library}"
ARTICLES_DIR="$KNOWLEDGE_LIB/文言文/选篇/正文"

if [[ ! -d "$ARTICLES_DIR" ]]; then
    echo "❌ 找不到选篇正文目录：$ARTICLES_DIR"
    exit 1
fi

FILE_COUNT=$(ls "$ARTICLES_DIR"/articles_*.json 2>/dev/null | wc -l | tr -d ' ')
if [[ "$FILE_COUNT" -eq 0 ]]; then
    echo "❌ 目录下无 articles_*.json 文件：$ARTICLES_DIR"
    exit 1
fi

echo "📖 选篇正文全量导入"
echo "   API: POST $BASE_URL/api/admin/import/articles"
echo "   源目录: $ARTICLES_DIR"
echo "   分文件数: $FILE_COUNT"
echo ""

# 本地开发：后端 Java 自动从目录读取分文件（无请求体模式）
# 线上部署：拼接 12 个文件后作为请求体发送
if [[ "${ONLINE:-}" == "true" ]]; then
    cd "$ARTICLES_DIR"
    python3 -c "
import json, sys
from pathlib import Path

files = sorted(Path('.').glob('articles_*.json'))
all_articles = []
for f in files:
    with open(f, 'r', encoding='utf-8') as fh:
        articles = json.load(fh)
        all_articles.extend(articles)
        print(f'  + {f.name}: {len(articles)} 篇', file=sys.stderr)

json.dump(all_articles, sys.stdout, ensure_ascii=False)
print(f'  合计: {len(all_articles)} 篇', file=sys.stderr)
" | curl -X POST "$BASE_URL/api/admin/import/articles" \
        -H "Content-Type: application/json" \
        -d @- \
        -w "\n" | python3 -m json.tool 2>/dev/null || true
else
    # 本地模式：后端 Java 自动读取 articlesDataDir 目录下的分文件
    curl -X POST "$BASE_URL/api/admin/import/articles" \
        -H "Content-Type: application/json" \
        -w "\n" | python3 -m json.tool 2>/dev/null || true
fi

echo ""
echo "✅ 导入完成"
