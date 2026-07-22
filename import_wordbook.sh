#!/bin/bash
# ============================================================
# 词书导入（全量 / 单本）
#   数据源：知识库 词书/wb_*.json（9 本）
#   全量 = 遍历所有词书，逐本调用单本导入接口
#   单本 = 幂等（先删后插）
#
# 用法:
#   ./import_wordbook.sh                引导选择：全量或单本
#   ./import_wordbook.sh --all          全量导入
#   ./import_wordbook.sh --all prd      正式环境全量导入
#   ./import_wordbook.sh wb_zhongkao_shixu        单本导入
#   ./import_wordbook.sh wb_zhongkao_shixu prd    正式环境单本导入
#   BASE_URL=xxx ./import_wordbook.sh   自定义地址
# ============================================================
set -euo pipefail

KNOWLEDGE_LIB="${KNOWLEDGE_LIB:-$HOME/Documents/knowledge_library}"
WORD_BOOK_DIR="$KNOWLEDGE_LIB/文言文/词书"

# 先处理 prd 参数（可能在任意位置）
BASE_URL="${BASE_URL:-http://localhost:8080}"
for arg in "$@"; do
    if [[ "$arg" == "prd" || "$arg" == "prod" || "$arg" == "production" ]]; then
        BASE_URL="https://wyq.yinqueai.com"
    fi
done
FILTERED_ARGS=()
for arg in "$@"; do
    if [[ "$arg" != "prd" && "$arg" != "prod" && "$arg" != "production" ]]; then
        FILTERED_ARGS+=("$arg")
    fi
done
set -- ${FILTERED_ARGS[@]+"${FILTERED_ARGS[@]}"}

if [[ ! -d "$WORD_BOOK_DIR" ]]; then
    echo "❌ 找不到词书目录：$WORD_BOOK_DIR"
    exit 1
fi

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

BOOK_NAMES=(
    "中考实词虚词一本通"
    "中考通假字一本通"
    "中考古今异义一本通"
    "中考词类活用一本通"
    "高考实词虚词一本通"
    "高考通假字一本通"
    "高考古今异义一本通"
    "高考词类活用一本通"
    "文言文虚词深度解析"
)

# ============================================================
# 单本导入
# ============================================================
import_one() {
    local book_id="$1"
    local book_file="$WORD_BOOK_DIR/${book_id}.json"

    if [[ ! -f "$book_file" ]]; then
        echo "    ❌ 找不到文件: $book_file"
        return 1
    fi

    local http_code
    http_code=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST "$BASE_URL/api/admin/import/wordbook" \
        -H "Content-Type: application/json" \
        -d "@$book_file")

    if [[ "$http_code" == "200" ]]; then
        echo "    ✅ ($http_code)"
    else
        echo "    ❌ HTTP $http_code"
        return 1
    fi
}

# ============================================================
# 全量导入：遍历所有词书，逐本调用单本接口
# ============================================================
do_full_import() {
    echo "📗 词书全量导入（逐本）"
    echo "   API: $BASE_URL/api/admin/import/wordbook"
    echo "   源目录: $WORD_BOOK_DIR"
    echo "   共 ${#ALL_BOOKS[@]} 本"
    echo ""

    local total=0
    local failed=0

    for i in "${!ALL_BOOKS[@]}"; do
        local book_id="${ALL_BOOKS[$i]}"
        local book_name="${BOOK_NAMES[$i]}"
        echo -n "  $book_id — $book_name "
        import_one "$book_id" || ((failed++)) || true
        ((total++)) || true
    done

    echo ""
    if [[ $failed -eq 0 ]]; then
        echo "✅ 全部导入成功 ($total 本)"
    else
        echo "⚠️  完成: $total 本，成功 $((total - failed))，失败 $failed"
    fi
}

# ============================================================
# 主流程
# ============================================================

# --all：全量导入
if [[ "${1:-}" == "--all" ]]; then
    do_full_import
    exit 0
fi

# 指定了词书 ID：单本导入
if [[ -n "${1:-}" ]]; then
    book_id="$1"
    echo ""
    echo "📗 单本词书导入"
    echo "   API: $BASE_URL/api/admin/import/wordbook"
    echo ""
    echo -n "  $book_id "
    import_one "$book_id"
    echo ""
    echo "✅ 导入完成"
    exit 0
fi

# 无参数：引导选择
echo ""
echo "📗 词书导入"
echo "   源目录: $WORD_BOOK_DIR"
echo ""

read -r -p "选择环境 [1] 本地开发  [2] 正式环境  [Q] 退出 (1/2/Q): " ENV_CHOICE

case "$ENV_CHOICE" in
    1) BASE_URL="${BASE_URL:-http://localhost:8080}" ;;
    2) BASE_URL="https://wyq.yinqueai.com" ;;
    [Qq]) echo "已退出"; exit 0 ;;
    *) echo "❌ 无效选择"; exit 1 ;;
esac

echo ""
echo "   当前环境: $BASE_URL"
echo ""
echo "   [A] 全量导入（共 ${#ALL_BOOKS[@]} 本）"

for i in "${!ALL_BOOKS[@]}"; do
    printf "   [%d] %-30s — %s\n" $((i + 1)) "${ALL_BOOKS[$i]}" "${BOOK_NAMES[$i]}"
done

echo "   [Q] 退出"
echo ""

read -r -p "请选择 (A/1-${#ALL_BOOKS[@]}/Q): " CHOICE

case "$CHOICE" in
    [Aa])
        do_full_import
        ;;
    [Qq])
        echo "已退出"
        exit 0
        ;;
    *)
        if [[ "$CHOICE" =~ ^[0-9]+$ ]] && (( CHOICE >= 1 && CHOICE <= ${#ALL_BOOKS[@]} )); then
            idx=$((CHOICE - 1))
            book_id="${ALL_BOOKS[$idx]}"
            book_name="${BOOK_NAMES[$idx]}"
            echo ""
            echo "📗 导入 $book_id — $book_name"
            echo ""
            echo -n "  $book_id "
            import_one "$book_id"
            echo ""
            echo "✅ 导入完成"
        else
            echo "❌ 无效选择: $CHOICE"
            exit 1
        fi
        ;;
esac
