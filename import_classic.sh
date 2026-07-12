#!/bin/bash
# ============================================================
# 经典著作章节导入（交互式）
# 输入经典 ID，自动映射到知识库 JSON 文件，幂等导入
# ============================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
KNOWLEDGE_LIB="${KNOWLEDGE_LIB:-$HOME/Documents/knowledge_library}"
CLASSICS_DIR="$KNOWLEDGE_LIB/文言文/经典"

echo ""
echo "📗 经典著作章节导入"
echo "   知识库路径: $CLASSICS_DIR"
echo "   API: POST $BASE_URL/api/admin/import/classic/{classicId}"
echo ""
echo "已知经典 ID → 文件映射:"
echo "   ID=18 → 老子       $CLASSICS_DIR/老子/chapters.json"
echo "   ID=22 → 孙子兵法   $CLASSICS_DIR/孙子兵法/chapters.json"
echo "   ID=36 → 山海经     $CLASSICS_DIR/山海经/chapters.json"
echo "   ID=33 → 世说新语   $CLASSICS_DIR/世说新语/entries.json"
echo "   ID=28 → 唐诗三百首 $CLASSICS_DIR/唐诗三百首/entries.json"
echo ""
echo "（其他 ID 需手动输入文件路径）"
echo ""

read -r -p "请输入经典 ID（数字，或输入 q 退出）: " CLASSIC_ID

if [[ "$CLASSIC_ID" =~ ^[Qq]$ ]]; then
    echo "已退出"
    exit 0
fi

if [[ -z "$CLASSIC_ID" ]]; then
    echo "❌ 经典 ID 不能为空"
    exit 1
fi

# 自动映射已知 ID → 文件
FILE=""
case "$CLASSIC_ID" in
    18) FILE="$CLASSICS_DIR/老子/chapters.json" ;;
    22) FILE="$CLASSICS_DIR/孙子兵法/chapters.json" ;;
    36) FILE="$CLASSICS_DIR/山海经/chapters.json" ;;
    33) FILE="$CLASSICS_DIR/世说新语/entries.json" ;;
    28) FILE="$CLASSICS_DIR/唐诗三百首/entries.json" ;;
esac

if [[ -z "$FILE" ]]; then
    echo "未找到已知映射，请手动输入 JSON 文件路径"
    echo "（知识库经典目录: $CLASSICS_DIR）"
    read -r -p "文件路径: " FILE
fi

if [[ ! -f "$FILE" ]]; then
    echo "❌ 找不到文件: $FILE"
    exit 1
fi

echo ""
echo "📄 导入文件: $FILE"
echo ""

# classic 导入需要提取 chapters 数组
curl -X POST "$BASE_URL/api/admin/import/classic/$CLASSIC_ID" \
    -H "Content-Type: application/json" \
    -d "@$FILE" \
    -w "\n" | python3 -m json.tool 2>/dev/null || true

echo ""
echo "✅ 导入完成"
