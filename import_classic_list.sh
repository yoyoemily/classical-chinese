#!/bin/bash
# ============================================================
# 经典元数据导入
# 从知识库 classics.json 读取 52 部经典元数据，幂等 upsert
#
# 用法:
#   ./import_classic_list.sh           本地开发（localhost:8080）
#   ./import_classic_list.sh prd       正式环境（wyq.yinqueai.com）
#   BASE_URL=xxx ./import_classic_list.sh  自定义地址
# ============================================================
set -euo pipefail

if [[ "${1:-}" == "prd" || "${1:-}" == "prod" || "${1:-}" == "production" ]]; then
    BASE_URL="https://wyq.yinqueai.com"
else
    BASE_URL="${BASE_URL:-http://localhost:8080}"
fi
KNOWLEDGE_LIB="${KNOWLEDGE_LIB:-$HOME/Documents/knowledge_library}"
CLASSICS_JSON="$KNOWLEDGE_LIB/文言文/经典/classics.json"

if [[ ! -f "$CLASSICS_JSON" ]]; then
    echo "❌ 找不到经典元数据文件：$CLASSICS_JSON"
    exit 1
fi

echo ""
echo "📗 经典元数据导入"
echo "   API: POST $BASE_URL/api/admin/import/classics"
echo "   源文件: $CLASSICS_JSON"
echo ""

if [[ "$BASE_URL" == "https://"* ]]; then
    curl -X POST "$BASE_URL/api/admin/import/classics" \
        -H "Content-Type: application/json" \
        -d "@$CLASSICS_JSON" \
        -w "\n" | python3 -m json.tool 2>/dev/null || true
else
    curl -X POST "$BASE_URL/api/admin/import/classics" \
        -H "Content-Type: application/json" \
        -w "\n" | python3 -m json.tool 2>/dev/null || true
fi

echo ""
echo "✅ 导入完成"
