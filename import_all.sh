#!/bin/bash
# ============================================================
# 勋章导入
# 读取 source.json（classpath），导入 8 枚勋章
# ============================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo ""
echo "🚀 勋章导入"
echo "   API: POST $BASE_URL/api/admin/import"
echo "   源文件: classpath:source.json"
echo ""

curl -X POST "$BASE_URL/api/admin/import" \
    -H "Content-Type: application/json" \
    -w "\n" | python3 -m json.tool 2>/dev/null || true

echo ""
echo "✅ 导入完成"
