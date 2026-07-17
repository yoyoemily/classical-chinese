#!/bin/bash
# ============================================================
# 全部典故注释批量导入（55篇）
# 遍历知识库中所有 art_*.json 文件，逐篇幂等导入
# ============================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
KNOWLEDGE_LIB="${KNOWLEDGE_LIB:-$HOME/Documents/knowledge_library}"
GLOSSARY_DIR="$KNOWLEDGE_LIB/文言文/选篇/典故注释"

echo ""
echo "📙 全部典故注释批量导入"
echo "   知识库路径: $GLOSSARY_DIR"
echo "   API: POST $BASE_URL/api/admin/import/glossary/{articleId}"
echo ""

TOTAL=0
FAILED=0

for f in "$GLOSSARY_DIR"/art_*.json; do
    ARTICLE_ID=$(basename "$f" .json)
    echo -n "  $ARTICLE_ID ... "

    if curl -s -o /dev/null -w "%{http_code}" \
        -X POST "$BASE_URL/api/admin/import/glossary/$ARTICLE_ID" \
        -H "Content-Type: application/json" \
        -d "@$f" | grep -q "200"; then
        echo "✅"
    else
        echo "❌"
        ((FAILED++)) || true
    fi

    ((TOTAL++)) || true
done

echo ""
echo "✅ 完成: $TOTAL 篇，成功 $((TOTAL - FAILED))，失败 $FAILED"
