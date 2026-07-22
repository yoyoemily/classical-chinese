#!/bin/bash
# ============================================================
# 选篇正文导入（全量 / 单篇）
#   数据源：知识库 articles_*.json（12 个分文件）
#   全量 = 遍历所有文章，逐篇调用单篇导入接口
#   单篇 = 按 articleId 先删后插（幂等）
#
# 用法:
#   ./import_article.sh                引导选择：全量或单篇
#   ./import_article.sh --all          全量导入
#   ./import_article.sh --all prd      正式环境全量导入
#   ./import_article.sh art_031        单篇导入
#   ./import_article.sh art_031 prd    正式环境单篇导入
#   BASE_URL=xxx ./import_article.sh   自定义地址
# ============================================================
set -euo pipefail

KNOWLEDGE_LIB="${KNOWLEDGE_LIB:-$HOME/Documents/knowledge_library}"
ARTICLES_DIR="$KNOWLEDGE_LIB/文言文/选篇/正文"

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

if [[ ! -d "$ARTICLES_DIR" ]]; then
    echo "❌ 找不到选篇正文目录：$ARTICLES_DIR"
    exit 1
fi

# ============================================================
# 单篇导入：提取 JSON → POST 到单篇接口
# ============================================================
import_one() {
    local article_id="$1"

    local tmpfile
    tmpfile=$(mktemp)
    python3 -c "
import json, os, glob, sys

target_id = '$article_id'
base_dir = '$ARTICLES_DIR'
files = sorted(glob.glob(os.path.join(base_dir, 'articles_*.json')))

for f in files:
    with open(f, 'r', encoding='utf-8') as fh:
        articles = json.load(fh)
    for a in articles:
        if a.get('id') == target_id:
            with open('$tmpfile', 'w', encoding='utf-8') as out:
                json.dump(a, out, ensure_ascii=False)
            title = a.get('title', '')
            author = a.get('author', '')
            sent_count = len(a.get('sentences', []))
            print(f'  {target_id}  {title}  ({author})  {sent_count} 句')
            sys.exit(0)
print(f'❌ 未找到文章: {target_id}')
sys.exit(1)
" || { rm -f "$tmpfile"; return 1; }

    local http_code
    http_code=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST "$BASE_URL/api/admin/import/articles/$article_id" \
        -H "Content-Type: application/json" \
        -d "@$tmpfile")
    rm -f "$tmpfile"

    if [[ "$http_code" == "200" ]]; then
        echo "    ✅ ($http_code)"
    else
        echo "    ❌ HTTP $http_code"
        return 1
    fi
}

# ============================================================
# 全量导入：遍历所有文章，逐一调用单篇接口
# ============================================================
do_full_import() {
    echo "📖 选篇正文全量导入（逐篇）"
    echo "   API: $BASE_URL/api/admin/import/articles/{articleId}"
    echo "   源目录: $ARTICLES_DIR"
    echo ""

    # 列出所有 articleId
    local all_ids
    all_ids=$(python3 -c "
import json, glob, os

base_dir = '$ARTICLES_DIR'
files = sorted(glob.glob(os.path.join(base_dir, 'articles_*.json')))
ids = []
for f in files:
    with open(f, 'r', encoding='utf-8') as fh:
        articles = json.load(fh)
    for a in articles:
        ids.append(a['id'])
for i in ids:
    print(i)
")

    local total=0
    local failed=0
    while IFS= read -r article_id; do
        [[ -z "$article_id" ]] && continue
        import_one "$article_id" || ((failed++)) || true
        ((total++)) || true
    done <<< "$all_ids"

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
    echo "📖 单篇选篇导入"
    echo "   API: $BASE_URL/api/admin/import/articles/$1"
    echo ""
    import_one "$1"
    echo ""
    echo "✅ 导入完成"
    exit 0
fi

# 无参数：引导选择
echo ""
echo "📖 选篇正文导入"
echo "   源目录: $ARTICLES_DIR"
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
        read -r -p "请输入 articleId（如 art_031）: " ARTICLE_ID
        if [[ -z "$ARTICLE_ID" ]]; then
            echo "❌ articleId 不能为空"
            exit 1
        fi
        echo ""
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
