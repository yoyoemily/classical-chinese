#!/bin/bash
# ============================================================
# 单本词书导入（交互式）
# 输入词书 ID，自动从知识库定位 JSON 文件，幂等导入
# ============================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
KNOWLEDGE_LIB="${KNOWLEDGE_LIB:-$HOME/Documents/knowledge_library}"
WORD_BOOK_DIR="$KNOWLEDGE_LIB/文言文/词书"

echo ""
echo "📗 单本词书导入"
echo "   知识库路径: $WORD_BOOK_DIR"
echo "   API: POST $BASE_URL/api/admin/import/wordbook"
echo ""
echo "可用的词书文件:"
ls -1 "$WORD_BOOK_DIR"/wb_*.json 2>/dev/null | while read f; do
    echo "   $(basename "$f")"
done
echo ""
echo "词书 ID 对照表:"
echo "   wb_zhongkao_shixu      — 中考实词虚词一本通"
echo "   wb_zhongkao_tongjia    — 中考通假字一本通"
echo "   wb_zhongkao_gujinyi    — 中考古今异义一本通"
echo "   wb_zhongkao_cileihuoyong — 中考词类活用一本通"
echo "   wb_gaokao_shixu        — 高考实词虚词一本通"
echo "   wb_gaokao_tongjia      — 高考通假字一本通"
echo "   wb_gaokao_gujinyi      — 高考古今异义一本通"
echo "   wb_gaokao_cileihuoyong — 高考词类活用一本通
   wb_function_words       — 文言文虚词深度解析"
echo ""

read -r -p "请输入词书 ID（如 wb_zhongkao_shixu，或输入 q 退出）: " BOOK_ID

if [[ "$BOOK_ID" =~ ^[Qq]$ ]]; then
    echo "已退出"
    exit 0
fi

if [[ -z "$BOOK_ID" ]]; then
    echo "❌ 词书 ID 不能为空"
    exit 1
fi

BOOK_FILE="$WORD_BOOK_DIR/${BOOK_ID}.json"

if [[ ! -f "$BOOK_FILE" ]]; then
    echo "❌ 找不到文件: $BOOK_FILE"
    exit 1
fi

echo ""
echo "📄 导入文件: $BOOK_FILE"
echo ""

curl -X POST "$BASE_URL/api/admin/import/wordbook" \
    -H "Content-Type: application/json" \
    -d "@$BOOK_FILE" \
    -w "\n" | python3 -m json.tool 2>/dev/null || true

echo ""
echo "✅ 导入完成"
