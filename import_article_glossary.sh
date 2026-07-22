#!/bin/bash
# ============================================================
# 选篇典故注释导入（全量 / 单篇）
#   数据源：知识库 典故注释/art_*.json（179 篇）
#   全量 = 遍历所有 art_*.json，逐篇调用单篇接口
#   单篇 = 按 articleId 先删后插（幂等）
#
# 用法:
#   ./import_article_glossary.sh                引导选择：全量或单篇
#   ./import_article_glossary.sh --all          全量导入
#   ./import_article_glossary.sh --all prd      正式环境全量导入
#   ./import_article_glossary.sh art_031        单篇导入
#   ./import_article_glossary.sh art_031 prd    正式环境单篇导入
#   BASE_URL=xxx ./import_article_glossary.sh   自定义地址
# ============================================================
set -euo pipefail

KNOWLEDGE_LIB="${KNOWLEDGE_LIB:-$HOME/Documents/knowledge_library}"
GLOSSARY_DIR="$KNOWLEDGE_LIB/文言文/选篇/典故注释"

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

if [[ ! -d "$GLOSSARY_DIR" ]]; then
    echo "❌ 找不到典故注释目录：$GLOSSARY_DIR"
    exit 1
fi

FILE_COUNT=$(ls "$GLOSSARY_DIR"/art_*.json 2>/dev/null | wc -l | tr -d ' ')
if [[ "$FILE_COUNT" -eq 0 ]]; then
    echo "❌ 目录下无 art_*.json 文件：$GLOSSARY_DIR"
    exit 1
fi

# ============================================================
# 单篇导入
# ============================================================
import_one() {
    local article_id="$1"
    local glossary_file="$GLOSSARY_DIR/${article_id}.json"

    if [[ ! -f "$glossary_file" ]]; then
        echo "  ❌ 找不到文件: $glossary_file"
        return 1
    fi

    local http_code
    http_code=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST "$BASE_URL/api/admin/import/glossary/$article_id" \
        -H "Content-Type: application/json" \
        -d "@$glossary_file")

    if [[ "$http_code" == "200" ]]; then
        echo "    ✅ ($http_code)"
    else
        echo "    ❌ HTTP $http_code"
        return 1
    fi
}

# ============================================================
# 全量导入：遍历所有 art_*.json，逐一调用单篇接口
# ============================================================
do_full_import() {
    echo "📙 典故注释全量导入（逐篇）"
    echo "   API: $BASE_URL/api/admin/import/glossary/{articleId}"
    echo "   源目录: $GLOSSARY_DIR"
    echo ""

    local total=0
    local failed=0

    for f in "$GLOSSARY_DIR"/art_*.json; do
        local article_id
        article_id=$(basename "$f" .json)
        echo -n "  $article_id "
        import_one "$article_id" || ((failed++)) || true
        ((total++)) || true
    done

    echo ""
    if [[ $failed -eq 0 ]]; then
        echo "✅ 全部导入成功 ($total 篇)"
    else
        echo "⚠️  完成: $total 篇，成功 $((total - failed))，失败 $failed"
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

# 指定了 articleId：单篇导入
if [[ -n "${1:-}" ]]; then
    echo "📙 单篇典故注释导入"
    echo "   API: $BASE_URL/api/admin/import/glossary/$1"
    echo ""
    echo -n "  $1 "
    import_one "$1"
    echo ""
    echo "✅ 导入完成"
    exit 0
fi

# 无参数：引导选择
echo ""
echo "📙 选篇典故注释导入"
echo "   源目录: $GLOSSARY_DIR ($FILE_COUNT 篇)"
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
echo "   [A] 全量导入（逐篇）"
echo "   [1] 单篇导入（指定 articleId）"
echo "   [Q] 退出"
echo ""

read -r -p "请选择 (A/1/Q): " CHOICE

case "$CHOICE" in
    [Aa])
        do_full_import
        ;;
    1)
        echo ""
        # 列出可用文件
        echo "可用的典故注释文件:"
        ls -1 "$GLOSSARY_DIR"/art_*.json 2>/dev/null | while read f; do
            basename "$f" .json
        done | paste -s -d ' ' -
        echo ""
        echo ""
        read -r -p "请输入 articleId（如 art_031）: " ARTICLE_ID
        if [[ -z "$ARTICLE_ID" ]]; then
            echo "❌ articleId 不能为空"
            exit 1
        fi
        echo ""
        echo -n "  $ARTICLE_ID "
        import_one "$ARTICLE_ID"
        echo ""
        echo "✅ 导入完成"
        ;;
    [Qq])
        echo "已退出"
        exit 0
        ;;
    *)
        echo "❌ 无效选择: $CHOICE"
        exit 1
        ;;
esac
