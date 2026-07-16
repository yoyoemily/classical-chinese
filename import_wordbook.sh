#!/bin/bash
# ============================================================
# 词书导入工具
#   ./import_wordbook.sh             交互式单本导入
#   ./import_wordbook.sh --all       一键导入全部 9 本词书
#   ./import_wordbook.sh wb_xxx      直接导入指定词书
# ============================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
KNOWLEDGE_LIB="${KNOWLEDGE_LIB:-$HOME/Documents/knowledge_library}"
WORD_BOOK_DIR="$KNOWLEDGE_LIB/文言文/词书"

ALL_BOOKS=(
    "wb_zhongkao_shixu"
    "wb_zhongkao_tongjia"
    "wb_zhongkao_gujinyi"
    "wb_zhongkao_cileihuoyong"
    "wb_gaokao_shixu"
    "wb_gaokao_tongjia"
    "wb_gaokao_gujinyi"
    "wb_gaokao_cileihuoyong"
    "wb_function_words"
)

import_one() {
    local book_id="$1"
    local book_file="$WORD_BOOK_DIR/${book_id}.json"

    if [[ ! -f "$book_file" ]]; then
        echo "❌ 找不到文件: $book_file"
        return 1
    fi

    echo "   📄 $(basename "$book_file") ... \c"

    # 捕获 HTTP 状态码
    local http_code
    http_code=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST "$BASE_URL/api/admin/import/wordbook" \
        -H "Content-Type: application/json" \
        -d "@$book_file")

    if [[ "$http_code" == "200" ]]; then
        echo "✅ ($http_code)"
    else
        echo "❌ HTTP $http_code"
        return 1
    fi
}

# ---- 一次性全量导入 ----
if [[ "${1:-}" == "--all" ]]; then
    echo ""
    echo "📗 批量导入全部词书 ($BASE_URL)"
    echo "   共 ${#ALL_BOOKS[@]} 本"
    echo ""

    fail_count=0
    for book_id in "${ALL_BOOKS[@]}"; do
        import_one "$book_id" || ((fail_count++))
    done

    echo ""
    if [[ $fail_count -eq 0 ]]; then
        echo "✅ 全部导入成功"
    else
        echo "⚠️  $fail_count 本失败"
    fi
    exit 0
fi

# ---- 直接指定 ID（非交互）----
if [[ -n "${1:-}" ]]; then
    book_id="$1"
    if [[ ! -f "$WORD_BOOK_DIR/${book_id}.json" ]]; then
        echo "❌ 找不到文件: $WORD_BOOK_DIR/${book_id}.json"
        exit 1
    fi
    echo ""
    echo "📗 导入 $book_id"
    import_one "$book_id"
    echo ""
    echo "✅ 导入完成"
    exit 0
fi

# ---- 交互模式 ----
echo ""
echo "📗 词书导入（交互模式）"
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

import_one "$BOOK_ID"

echo ""
echo "✅ 导入完成"
