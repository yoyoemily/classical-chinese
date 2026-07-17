#!/bin/bash
# ============================================================
# 选篇正文全量导入（55篇）
# 从知识库 articles.json 读取，幂等（先清空后插入）
# ============================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
KNOWLEDGE_LIB="${KNOWLEDGE_LIB:-$HOME/Documents/knowledge_library}"
ARTICLES_JSON="$KNOWLEDGE_LIB/文言文/选篇/正文/articles.json"

if [[ ! -f "$ARTICLES_JSON" ]]; then
    echo "❌ 找不到选篇正文文件：$ARTICLES_JSON"
    exit 1
fi

echo "📖 选篇正文全量导入"
echo "   API: POST $BASE_URL/api/admin/import/articles"
echo "   源文件: $ARTICLES_JSON"
echo ""

curl -X POST "$BASE_URL/api/admin/import/articles" \
    -H "Content-Type: application/json" \
    -w "\n" | python3 -m json.tool 2>/dev/null || true

echo ""
echo "✅ 导入完成"
