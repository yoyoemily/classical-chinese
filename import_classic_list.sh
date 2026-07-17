#!/bin/bash
# ============================================================
# 经典元数据导入
# 从知识库 classics.json 读取 52 部经典元数据，幂等 upsert
# ============================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
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

curl -X POST "$BASE_URL/api/admin/import/classics" \
    -H "Content-Type: application/json" \
    -w "\n" | python3 -m json.tool 2>/dev/null || true

echo ""
echo "✅ 导入完成"
