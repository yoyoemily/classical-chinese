#!/bin/bash
# ============================================================
# 业务数据清理工具
#   可选择清理：
#     all      — 全部 23 张表
#     user     — 用户数据（8 张表）
#     wordbook — 词书数据（6 张表）
#     article  — 选篇数据（4 张表）
#     classic  — 经典数据（4 张表）
#
# 用法：
#   ./clear_data.sh             引导选择
#   ./clear_data.sh all         清理全部（跳过确认）
#   ./clear_data.sh user        清理用户数据
#   ./clear_data.sh wordbook    清理词书数据
#   ./clear_data.sh article     清理选篇数据
#   ./clear_data.sh classic     清理经典数据
# ============================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

SCOPE_DESC_ALL="全部 24 张表（用户 + 词书 + 选篇 + 经典 + 勋章定义 + 用户账号）"
SCOPE_DESC_USER="用户数据：user, user_word_progress, user_answer_history, user_checkin, user_badge, study_mistake_sentence, study_mistake, daily_task, feedback（9 张）"
SCOPE_DESC_WORDBOOK="词书数据：word_book, word_book_entry, word_entry_keyword_ref, quiz_item, quiz_distractor, word_usage（6 张）"
SCOPE_DESC_ARTICLE="选篇数据：article, article_sentence, article_keyword, article_glossary（4 张）"
SCOPE_DESC_CLASSIC="经典数据：classic, classic_chapter, classic_paragraph, classic_glossary（4 张）"

# ---- 直接指定 scope ----
if [[ -n "${1:-}" ]]; then
    SCOPE="$1"
    case "$SCOPE" in
        all)     DESC="$SCOPE_DESC_ALL" ;;
        user)    DESC="$SCOPE_DESC_USER" ;;
        wordbook) DESC="$SCOPE_DESC_WORDBOOK" ;;
        article) DESC="$SCOPE_DESC_ARTICLE" ;;
        classic) DESC="$SCOPE_DESC_CLASSIC" ;;
        *)
            echo "❌ 无效选项: $SCOPE"
            echo "   可用: all | user | wordbook | article | classic"
            exit 1
            ;;
    esac

    echo ""
    echo "🧹 清理业务数据: $SCOPE"
    echo "   $DESC"
    echo ""

    # all 模式跳过确认，其他模式需确认
    if [[ "$SCOPE" != "all" ]]; then
        read -r -p "确认清理？输入 yes 继续: " CONFIRM
        if [[ "$CONFIRM" != "yes" ]]; then
            echo "已取消"
            exit 0
        fi
        echo ""
    fi

    echo "   执行中..."
    HTTP_CODE=$(curl -s -o /tmp/clear_data_response.json -w "%{http_code}" \
        -X POST "$BASE_URL/api/admin/clear-data?scope=$SCOPE" \
        -H "Content-Type: application/json")

    if [[ "$HTTP_CODE" == "200" ]]; then
        python3 -m json.tool /tmp/clear_data_response.json 2>/dev/null || cat /tmp/clear_data_response.json
        echo ""
        echo "✅ 清理完成"
    else
        echo "❌ 请求失败，HTTP $HTTP_CODE"
        cat /tmp/clear_data_response.json 2>/dev/null || true
        exit 1
    fi
    exit 0
fi

# ---- 引导模式 ----
echo ""
echo "🧹 业务数据清理工具"
echo "   API: POST $BASE_URL/api/admin/clear-data?scope="
echo ""
echo "   [1] 全部      — $SCOPE_DESC_ALL"
echo "   [2] 用户数据  — $SCOPE_DESC_USER"
echo "   [3] 词书数据  — $SCOPE_DESC_WORDBOOK"
echo "   [4] 选篇数据  — $SCOPE_DESC_ARTICLE"
echo "   [5] 经典数据  — $SCOPE_DESC_CLASSIC"
echo "   [Q] 退出"
echo ""

read -r -p "请选择 (1/2/3/4/5/Q): " CHOICE

case "$CHOICE" in
    1) SCOPE="all";     DESC="$SCOPE_DESC_ALL" ;;
    2) SCOPE="user";    DESC="$SCOPE_DESC_USER" ;;
    3) SCOPE="wordbook"; DESC="$SCOPE_DESC_WORDBOOK" ;;
    4) SCOPE="article"; DESC="$SCOPE_DESC_ARTICLE" ;;
    5) SCOPE="classic"; DESC="$SCOPE_DESC_CLASSIC" ;;
    [Qq]) echo "已退出"; exit 0 ;;
    *)   echo "❌ 无效选择: $CHOICE"; exit 1 ;;
esac

echo ""
echo "⚠️  危险操作：清理 $SCOPE"
echo "   $DESC"
echo ""

# all 模式跳过确认，其他模式需确认
if [[ "$SCOPE" != "all" ]]; then
    read -r -p "确认清理？输入 yes 继续: " CONFIRM
    if [[ "$CONFIRM" != "yes" ]]; then
        echo "已取消"
        exit 0
    fi
    echo ""
fi

echo "   执行中..."
HTTP_CODE=$(curl -s -o /tmp/clear_data_response.json -w "%{http_code}" \
    -X POST "$BASE_URL/api/admin/clear-data?scope=$SCOPE" \
    -H "Content-Type: application/json")

if [[ "$HTTP_CODE" == "200" ]]; then
    python3 -m json.tool /tmp/clear_data_response.json 2>/dev/null || cat /tmp/clear_data_response.json
    echo ""
    echo "✅ 清理完成"
else
    echo "❌ 请求失败，HTTP $HTTP_CODE"
    cat /tmp/clear_data_response.json 2>/dev/null || true
    exit 1
fi
