#!/bin/bash
# ============================================================
# 冷启动全量导入
# 读取 source.json，导入词书 + 勋章 + 经典元数据
# ============================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo ""
echo "🚀 冷启动全量导入"
echo "   API: POST $BASE_URL/api/admin/import"
echo "   源文件: classpath:source.json"
echo ""

curl -X POST "$BASE_URL/api/admin/import" \
    -H "Content-Type: application/json" \
    -w "\n" | python3 -m json.tool 2>/dev/null || true

echo ""
echo "✅ 导入完成"
