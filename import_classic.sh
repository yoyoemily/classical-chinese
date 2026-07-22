#!/bin/bash
# ============================================================
# 经典著作章节导入
#   ./import_classic.sh             引导选择：全部导入或指定单本
#   ./import_classic.sh prd         正式环境（wyq.yinqueai.com）
#   ./import_classic.sh --all       一键导入全部已上线经典
#   ./import_classic.sh --all prd   正式环境一键导入全部经典
#   ./import_classic.sh <id>        直接导入指定经典
#   ./import_classic.sh <id> prd    正式环境导入指定经典
#   BASE_URL=xxx ./import_classic.sh  自定义地址
# ============================================================
set -euo pipefail

# 先处理 prd 参数（可能在第 1 或第 2 个位置）
BASE_URL="${BASE_URL:-http://localhost:8080}"
for arg in "$@"; do
    if [[ "$arg" == "prd" || "$arg" == "prod" || "$arg" == "production" ]]; then
        BASE_URL="https://wyq.yinqueai.com"
    fi
done
# 过滤掉 prd 参数，剩下的传给后续解析
FILTERED_ARGS=()
for arg in "$@"; do
    if [[ "$arg" != "prd" && "$arg" != "prod" && "$arg" != "production" ]]; then
        FILTERED_ARGS+=("$arg")
    fi
done
set -- ${FILTERED_ARGS[@]+"${FILTERED_ARGS[@]}"}
KNOWLEDGE_LIB="${KNOWLEDGE_LIB:-$HOME/Documents/knowledge_library}"
CLASSICS_DIR="$KNOWLEDGE_LIB/文言文/经典"

# 已上线经典列表（按 ID 排序，共 25 部）
# 格式: "id|名称|文件名"
declare -a ONLINE_CLASSICS=(
    "3|大学|大学/chapters.json"
    "4|中庸|中庸/chapters.json"
    "5|周易|周易/chapters.json"
    "17|荀子|荀子/chapters.json"
    "18|老子|老子/chapters.json"
    "20|韩非子|韩非子/chapters.json"
    "21|墨子|墨子/chapters.json"
    "22|孙子兵法|孙子兵法/chapters.json"
    "24|鬼谷子|鬼谷子/chapters.json"
    "27|楚辞|楚辞/chapters.json"
    "28|唐诗三百首|唐诗三百首/entries.json"
    "29|宋词三百首|宋词三百首/entries.json"
    "33|世说新语|世说新语/entries.json"
    "36|山海经|山海经/chapters.json"
    "37|列子|列子/chapters.json"
    "38|孝经|孝经/chapters.json"
    "40|颜氏家训|颜氏家训/chapters.json"
    "43|三字经|三字经/chapters.json"
    "44|千字文|千字文/chapters.json"
    "46|晏子春秋|晏子春秋/chapters.json"
    "47|菜根谭|菜根谭/chapters.json"
    "48|西厢记|西厢记/entries.json"
    "55|浮生六记|浮生六记/chapters.json"
    "57|陶渊明集|陶渊明集/chapters.json"
    "58|围炉夜话|围炉夜话/chapters.json"
)

import_one() {
    local classic_id="$1"
    local file="$CLASSICS_DIR/$2"

    if [[ ! -f "$file" ]]; then
        echo "❌ 找不到文件: $file"
        return 1
    fi

    echo "   📄 $(basename "$file") (ID=$classic_id) ... \c"

    local http_code
    http_code=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST "$BASE_URL/api/admin/import/classic/$classic_id" \
        -H "Content-Type: application/json" \
        -d "@$file")

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
    echo "📗 批量导入全部经典 ($BASE_URL)"
    echo "   共 ${#ONLINE_CLASSICS[@]} 部"
    echo ""

    fail_count=0
    for entry in "${ONLINE_CLASSICS[@]}"; do
        IFS='|' read -r cid cname cfile <<< "$entry"
        import_one "$cid" "$cfile" || ((fail_count++))
    done

    echo ""
    if [[ $fail_count -eq 0 ]]; then
        echo "✅ 全部导入成功"
    else
        echo "⚠️  $fail_count 部失败"
    fi
    exit 0
fi

# ---- 直接指定 ID（非交互）----
if [[ -n "${1:-}" ]]; then
    cid="$1"
    cfile=""
    for entry in "${ONLINE_CLASSICS[@]}"; do
        IFS='|' read -r id name file <<< "$entry"
        if [[ "$id" == "$cid" ]]; then
            cfile="$file"
            break
        fi
    done
    if [[ -z "$cfile" ]]; then
        echo "❌ 未找到已上线经典 ID=$cid，请手动输入文件路径"
        read -r -p "文件路径(相对于 $CLASSICS_DIR): " cfile
    fi

    FULL_PATH="$CLASSICS_DIR/$cfile"
    if [[ ! -f "$FULL_PATH" ]]; then
        echo "❌ 找不到文件: $FULL_PATH"
        exit 1
    fi
    echo ""
    echo "📗 导入经典 ID=$cid"
    import_one "$cid" "$cfile"
    echo ""
    echo "✅ 导入完成"
    exit 0
fi

# ---- 引导模式：选择全部导入或指定单本 ----
echo ""
echo "📗 经典著作章节导入"
echo "   知识库路径: $CLASSICS_DIR"
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
echo "   [A] 全部导入（共 ${#ONLINE_CLASSICS[@]} 部）"

idx=1
for entry in "${ONLINE_CLASSICS[@]}"; do
    IFS='|' read -r cid cname cfile <<< "$entry"
    printf "   [%d] %-10s — %s\n" "$idx" "$cname" "$cfile"
    ((idx++))
done

echo "   [Q] 退出"
echo ""

read -r -p "请选择 (A/1-${#ONLINE_CLASSICS[@]}/Q): " CHOICE

case "$CHOICE" in
    [Aa])
        echo ""
        echo "📗 批量导入全部经典 ($BASE_URL)"
        echo "   共 ${#ONLINE_CLASSICS[@]} 部"
        echo ""

        fail_count=0
        for entry in "${ONLINE_CLASSICS[@]}"; do
            IFS='|' read -r cid cname cfile <<< "$entry"
            import_one "$cid" "$cfile" || ((fail_count++))
        done

        echo ""
        if [[ $fail_count -eq 0 ]]; then
            echo "✅ 全部导入成功"
        else
            echo "⚠️  $fail_count 部失败"
        fi
        ;;
    [Qq])
        echo "已退出"
        exit 0
        ;;
    *)
        if [[ "$CHOICE" =~ ^[0-9]+$ ]] && (( CHOICE >= 1 && CHOICE <= ${#ONLINE_CLASSICS[@]} )); then
            i=$((CHOICE - 1))
            IFS='|' read -r cid cname cfile <<< "${ONLINE_CLASSICS[$i]}"
            echo ""
            echo "📗 导入经典 ID=$cid ($cname)"
            import_one "$cid" "$cfile"
            echo ""
            echo "✅ 导入完成"
        else
            echo "❌ 无效选择: $CHOICE"
            exit 1
        fi
        ;;
esac
