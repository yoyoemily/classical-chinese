#!/bin/bash
# ============================================================
# 语音合成（讯飞长文本 TTS → OSS）
#   调用后端管理接口 POST /api/admin/tts/...
#   音频输出到 ~/upload/wyq/tts/（通过 OSSFS 挂载）
#
# 用法（直接传参 / 无参引导式）:
#   ./tts_synthesize.sh                             无参引导式选择
#   ./tts_synthesize.sh article art_001             合成选篇全文
#   ./tts_synthesize.sh article art_001 prd         正式环境合成选篇
#   ./tts_synthesize.sh article art_001 --vcn x4_yezi  指定发音人
#   ./tts_synthesize.sh classic-chapter 22 42       合成经典章节 (经典ID + 章节ID)
#   ./tts_synthesize.sh classic-chapter 22 42 prd   正式环境合成经典章节
#   ./tts_synthesize.sh classic-chapter 22 42 --vcn x4_mingge 指定发音人
#
#   发音人列表（长文本 TTS 支持）:
#     x4_xiaoguo (默认)  x4_yezi    x4_mingge   x4_guanshan
#     x4_pengfei         x4_qianxue x4_yeting   x4_xiuying
#     x4_xiaozhong       x4_doudou  x5_lingfeizhe x5_lingxiaoxue
#     x4_lingbosong      x4_chaoge  x4_feidie   x4_wangqianqian
# ============================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
VCN=""
TYPE=""
CLASSIC_ID=""
TARGET=""

# 单次遍历解析参数（兼容 POSIX sh）
while [ $# -gt 0 ]; do
    case "$1" in
        prd|prod|production)
            BASE_URL="https://wyq.yinqueai.com"
            shift
            ;;
        --vcn)
            VCN="$2"
            shift 2
            ;;
        *)
            # 位置参数：依次保存 TYPE / CLASSIC_ID / TARGET / VCN
            if [ -z "$TYPE" ]; then
                TYPE="$1"
            elif [ "$TYPE" = "classic-chapter" ] && [ -z "$CLASSIC_ID" ]; then
                CLASSIC_ID="$1"
            elif [ -z "$TARGET" ]; then
                TARGET="$1"
            fi
            shift
            ;;
    esac
done

if [ -z "$TYPE" ]; then
    echo ""
    echo "🎙️  文言雀语音合成（讯飞长文本 TTS）"

    # 1. 环境
    echo ""
    echo "环境:"
    echo "  [1] 本地开发 ($BASE_URL)"
    echo "  [2] 正式环境 (https://wyq.yinqueai.com)"
    read -r -p "请选择 (1/2，默认 1): " ENV_CHOICE
    if [ "$ENV_CHOICE" = "2" ]; then
        BASE_URL="https://wyq.yinqueai.com"
    fi

    # 2. 类型
    echo ""
    echo "类型:"
    echo "  [1] 选篇全文合成"
    echo "  [2] 经典章节合成"
    read -r -p "请选择 (1/2): " TYPE_CHOICE

    case "$TYPE_CHOICE" in
        1) TYPE="article" ;;
        2) TYPE="classic-chapter" ;;
        *) echo "❌ 无效选择: $TYPE_CHOICE"; exit 1 ;;
    esac

    # 3. 目标 ID
    if [ "$TYPE" = "article" ]; then
        CLASSIC_ID=""
        read -r -p "请输入选篇 ID（如 art_001）: " ARTICLE_ID
        if [ -z "$ARTICLE_ID" ]; then
            echo "❌ 选篇 ID 不能为空"
            exit 1
        fi
        TARGET="$ARTICLE_ID"
    else
        echo ""
        read -r -p "请输入经典 ID（classic 表主键，如 22=孙子兵法）: " CLASSIC_ID
        read -r -p "请输入篇章 ID（classic_chapter 表主键，如 42）: " CHAPTER_ID
        if [ -z "$CLASSIC_ID" ] || [ -z "$CHAPTER_ID" ]; then
            echo "❌ 经典 ID 和篇章 ID 均不能为空"
            exit 1
        fi
        TARGET="$CHAPTER_ID"
    fi

    # 4. 发音人
    echo ""
    echo "发音人（直接回车使用默认 x4_xiaoguo）:"
    echo "  1  x4_xiaoguo    沉稳男声（默认）"
    echo "  2  x4_yezi       轻柔女声"
    echo "  3  x4_mingge     成熟男声"
    echo "  4  x4_guanshan   浑厚男声"
    echo "  5  x4_pengfei    新闻男声"
    echo "  6  x4_qianxue    知性女声"
    echo "  7  x4_yeting     温婉女声"
    echo "  8  x4_xiuying    亲切女声"
    echo "  9  x4_lingbosong 朗读男声"
    echo " 10  x4_doudou     可爱女声"
    echo " 11  x5_lingfeizhe 精品男声"
    echo " 12  x5_lingxiaoxue 精品女声"
    echo "  0  手动输入"
    echo ""
    read -r -p "请选择 (1-12/0，默认 1): " VCN_CHOICE

    case "${VCN_CHOICE:-1}" in
        1)  VCN="x4_xiaoguo" ;;
        2)  VCN="x4_yezi" ;;
        3)  VCN="x4_mingge" ;;
        4)  VCN="x4_guanshan" ;;
        5)  VCN="x4_pengfei" ;;
        6)  VCN="x4_qianxue" ;;
        7)  VCN="x4_yeting" ;;
        8)  VCN="x4_xiuying" ;;
        9)  VCN="x4_lingbosong" ;;
        10) VCN="x4_doudou" ;;
        11) VCN="x5_lingfeizhe" ;;
        12) VCN="x5_lingxiaoxue" ;;
        0)
            read -r -p "请输入发音人名称: " VCN
            if [ -z "$VCN" ]; then
                VCN="x4_xiaoguo"
            fi
            ;;
        *)  VCN="x4_xiaoguo" ;;
    esac
fi

# ============================================================
# 参数校验
# ============================================================

if [[ "$TYPE" != "article" && "$TYPE" != "classic-chapter" ]]; then
    echo "❌ 类型只能是 article 或 classic-chapter，当前: $TYPE"
    exit 1
fi

if [[ -z "$TARGET" ]]; then
    echo "❌ 目标 ID 不能为空"
    exit 1
fi

if [ "$TYPE" = "classic-chapter" ] && [ -z "$CLASSIC_ID" ]; then
    echo "❌ 经典章节合成需要提供经典 ID 和篇章 ID"
    exit 1
fi

# ============================================================
# 调用 API
# ============================================================

API_PATH="/api/admin/tts/$TYPE/$TARGET"
NEED_QM="1"
if [ -n "$VCN" ]; then
    API_PATH="${API_PATH}?vcn=$VCN"
    NEED_QM=""
fi
if [ -n "$CLASSIC_ID" ]; then
    if [ -n "$NEED_QM" ]; then
        API_PATH="${API_PATH}?classicId=$CLASSIC_ID"
    else
        API_PATH="${API_PATH}&classicId=$CLASSIC_ID"
    fi
fi

FULL_URL="$BASE_URL$API_PATH"

echo ""
echo "────────────────────────────────────"
echo "🎙️  语音合成"
echo "   类型: $TYPE"
echo "   目标: $TARGET"
if [ -n "$CLASSIC_ID" ]; then
    echo "   经典: ID=$CLASSIC_ID"
fi
echo "   环境: $BASE_URL"
echo "   发音人: ${VCN:-默认}"
echo "   接口: $FULL_URL"
echo "────────────────────────────────────"
echo ""

read -r -p "确认合成? [Y/n] " CONFIRM
case "$CONFIRM" in
    [Nn]*) echo "已取消"; exit 0 ;;
esac

echo ""
echo "🔍 检测后端连通性..."
if ! curl -s --connect-timeout 5 --max-time 5 "$BASE_URL" > /dev/null 2>&1; then
    echo "❌ 后端不可达: $BASE_URL"
    echo "   请先启动后端服务再试。"
    exit 1
fi
echo "✅ 后端连通正常"

echo ""
echo "⏳ 合成中（可能需要 30-300 秒，取决于文本长度）..."
echo ""

HTTP_CODE=$(curl -s --connect-timeout 10 --max-time 600 \
    -o /tmp/tts_response.json -w "%{http_code}" \
    -X POST "$FULL_URL" \
    -H "Content-Type: application/json")

RESP=$(cat /tmp/tts_response.json)
rm -f /tmp/tts_response.json

if [ "$HTTP_CODE" = "200" ]; then
    echo "$RESP" | python3 -c "
import json, sys

data = json.load(sys.stdin)
code = data.get('code', -1)
if code == 0:
    d = data.get('data', {})
    print('✅ 合成成功！')
    print('')
    if d.get('summary'):
        print('   ' + d['summary'])
else:
    print('❌ 合成失败: ' + data.get('message', '未知错误'))
    if d.get('data'):
        print('   ' + json.dumps(data['data'], ensure_ascii=False))
"
else
    echo "❌ HTTP $HTTP_CODE"
    echo "$RESP"
    exit 1
fi

echo ""
echo "✅ 完成"
