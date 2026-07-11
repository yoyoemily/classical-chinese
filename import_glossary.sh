#!/bin/bash
# ============================================================
# 单篇典故注释导入（交互式）
# 输入 articleId，自动从知识库定位 JSON 文件，幂等导入
# ============================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
KNOWLEDGE_LIB="${KNOWLEDGE_LIB:-$HOME/Documents/knowledge_library}"
GLOSSARY_DIR="$KNOWLEDGE_LIB/文言文/选篇/典故注释"

echo ""
echo "📙 单篇典故注释导入"
echo "   知识库路径: $GLOSSARY_DIR"
echo "   API: POST $BASE_URL/api/admin/import/glossary/{articleId}"
echo ""

# 列出可选文件
echo "可用的典故注释文件:"
ls -1 "$GLOSSARY_DIR"/art_*.json 2>/dev/null | while read f; do
    basename "$f" .json
done | paste -s -d ' ' -

echo ""
echo ""

# 提示输入
read -r -p "请输入 articleId（如 art_001，或输入 q 退出）: " ARTICLE_ID

if [[ "$ARTICLE_ID" =~ ^[Qq]$ ]]; then
    echo "已退出"
    exit 0
fi

if [[ -z "$ARTICLE_ID" ]]; then
    echo "❌ articleId 不能为空"
    exit 1
fi

GLOSSARY_FILE="$GLOSSARY_DIR/${ARTICLE_ID}.json"

if [[ ! -f "$GLOSSARY_FILE" ]]; then
    echo "❌ 找不到文件: $GLOSSARY_FILE"
    exit 1
fi

echo ""
echo "📄 导入文件: $GLOSSARY_FILE"
echo ""

curl -X POST "$BASE_URL/api/admin/import/glossary/$ARTICLE_ID" \
    -H "Content-Type: application/json" \
    -d "@$GLOSSARY_FILE" \
    -w "\n" | python3 -m json.tool 2>/dev/null || true

echo ""
echo "✅ 导入完成"
