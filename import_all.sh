#!/bin/bash
# ============================================================
# 勋章导入
# 读取 source.json（classpath），导入 8 枚勋章
#
# 用法:
#   ./import_all.sh           本地开发（localhost:8080）
#   ./import_all.sh prd       正式环境（wyq.yinqueai.com）
#   BASE_URL=xxx ./import_all.sh  自定义地址
# ============================================================
set -euo pipefail

if [[ "${1:-}" == "prd" || "${1:-}" == "prod" || "${1:-}" == "production" ]]; then
    BASE_URL="https://wyq.yinqueai.com"
else
    BASE_URL="${BASE_URL:-http://localhost:8080}"
fi

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
