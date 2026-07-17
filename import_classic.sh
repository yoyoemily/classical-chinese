#!/bin/bash
# ============================================================
# 经典著作章节导入
#   ./import_classic.sh             引导选择：全部导入或指定单本
#   ./import_classic.sh --all       一键导入全部 11 部已上线经典
#   ./import_classic.sh <id>        直接导入指定经典
# ============================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
KNOWLEDGE_LIB="${KNOWLEDGE_LIB:-$HOME/Documents/knowledge_library}"
CLASSICS_DIR="$KNOWLEDGE_LIB/文言文/经典"

# 已上线经典列表（按菜单顺序排列）
# 格式: "id|名称|文件名"
declare -a ONLINE_CLASSICS=(
    "3|大学|大学/chapters.json"
    "4|中庸|中庸/chapters.json"
    "18|老子|老子/chapters.json"
    "22|孙子兵法|孙子兵法/chapters.json"
    "27|楚辞|楚辞/chapters.json"
    "28|唐诗三百首|唐诗三百首/entries.json"
    "33|世说新语|世说新语/entries.json"
    "36|山海经|山海经/chapters.json"
    "38|孝经|孝经/chapters.json"
    "43|三字经|三字经/chapters.json"
    "44|千字文|千字文/chapters.json"
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
echo "   API: POST $BASE_URL/api/admin/import/classic/{id}"
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
