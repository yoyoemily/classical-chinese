package com.bogutongjin.util;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

import java.util.*;

/**
 * 生僻字拼音工具类
 * <p>
 * 自动识别不在常用 3500 字范围内的生僻字，生成拼音标注映射。
 * 常用字范围来自《通用规范汉字表》一级字表（3,500 字）。
 * </p>
 */
public final class PinyinUtils {

    private PinyinUtils() {
    }

    /**
     * 常用 3,500 字（《通用规范汉字表》一级字表）
     */
    private static final Set<Character> COMMON_CHARS = new HashSet<>();

    /**
     * 手动扩充更多常用字
     */
    private static final String CLASSICAL_COMMON_CHARS =
            "曰吾汝矣焉乎耶哉兮奚桂韭婴呵猿仑鸳鸯馨溺乾昧祠敖媚颊渭杖邓郁湘犀匈聂奢" +
                    "薰亥禹仞曼兑舜昊琴渊梧斋巫玄弘娲豹冀遂淮卒婿琵琶枫浔瑟轴抑嘈莺幽" +
                    "涩乍吟敛妆啼唧鹃嘲凄泣郡铮浦穆曹悯沦憔悴恬帛阑瞑咆殷霹雳峦崩冥霓鸾悸恍岳赋涯晖淫霏啸谗讥萧澜鸥鳞芷皓璧怡宠翔";

    /**
     * pinyin4j 字库覆盖不到的冷僻字（如 Extension A/B 区的字），手动标注拼音。
     * key: 字符, value: 带声调拼音（用 1-4 数字表示，如 "bei4" 对应 bèi）。
     * 按需追加即可。
     */
    private static final Map<String, String> HARDCODED_RARE_PINYIN = new LinkedHashMap<>();

    static {
        // 手动补充pinyin4j库无法识别的非常用字
        HARDCODED_RARE_PINYIN.put("䰽", "bèi");
        HARDCODED_RARE_PINYIN.put("㔮", "nuó");
        HARDCODED_RARE_PINYIN.put("㻬", "tú");
        HARDCODED_RARE_PINYIN.put("䔄", "yáo");
        HARDCODED_RARE_PINYIN.put("䟣", "chù");
    }

    static {
        String chars = "一乙二十丁厂七卜人入八九几儿了力乃刀又三于干亏士工土才寸下大丈与万上"
                + "小口巾山千乞川亿个勺久凡及夕丸么广亡门义之尸弓己已子卫也女飞刃习叉马"
                + "乡丰王井开夫天无元专云扎艺木五支厅不太犬区历尤友匹车巨牙屯比互切瓦止少"
                + "日中冈贝内水见午牛手毛气升长仁什片仆化仇币仍仅斤爪反介父从今凶分乏公"
                + "仓月氏勿欠风丹匀乌凤勾文六方火为斗忆订计户认心尺引丑巴孔队办以允予劝"
                + "双书幻玉刊示末未击打巧正扑扒功扔去甘世古节本术可丙左厉右石布龙平灭轧"
                + "东卡北占业旧帅归且旦目叶甲申叮电号田由史只央兄叼叫另叨叹四生失禾丘付"
                + "仗代仙们仪白仔他斥瓜乎丛令用甩印乐句匆册犯外处冬鸟务包饥主市立闪兰半"
                + "汁汇头汉宁穴它讨写让礼训必议讯记永司尼民出辽奶奴加召皮边发孕圣对台矛"
                + "纠母幼丝式刑动扛寺吉扣考托老执巩圾扩扫地扬场耳共芒亚芝朽朴机权过臣再"
                + "协西压厌在有百存而页匠夸夺灰达列死成夹轨邪划迈毕至此贞师尘尖劣光当早"
                + "吐吓虫曲团同吊吃因吸吗屿帆岁回岂刚则肉网年朱先丢舌竹迁乔伟传乒乓休伍"
                + "伏优伐延件任伤价份华仰仿伙伪自血向似后行舟全会杀合兆企众爷伞创肌朵杂"
                + "危旬旨负各名多争色壮冲冰庄庆亦刘齐交次衣产决充妄闭问闯羊并关米灯州汗"
                + "污江池汤忙兴宇守宅字安讲军许论农讽设访寻那迅尽导异孙阵阳收阶阴防奸如"
                + "妇好她妈戏羽观欢买红纤级约纪驰巡寿弄麦形进戒吞远违运扶抚坛技坏扰拒找"
                + "批扯址走抄坝贡攻赤折抓扮抢孝均抛投坟抗坑坊抖护壳志扭块声把报却劫芽花"
                + "芹芬苍芳严芦劳克苏杆杠杜材村杏极李杨求更束豆两丽医辰励否还歼来连步坚"
                + "旱盯呈时吴助县里呆园旷围呀吨足邮男困吵串员听吩吹呜吧吼别岗帐财针钉告"
                + "我乱利秃秀私每兵估体何但伸作伯伶佣低你住位伴身皂佛近彻役返余希坐谷妥"
                + "含邻岔肝肚肠龟免狂犹角删条卵岛迎饭饮系言冻状亩况床库疗应冷这序辛弃冶"
                + "忘闲间闷判灶灿弟汪沙汽沃泛沟没沈沉怀忧快完宋宏牢究穷灾良证启评补初社"
                + "识诉诊词译君灵即层尿尾迟局改张忌际陆阿陈阻附妙妖妨努忍劲鸡驱纯纱纳纲"
                + "驳纵纷纸纹纺驴纽奉玩环武青责现表规抹拢拔拣担坦押抽拐拖拍者顶拆拥抵拘"
                + "势抱垃拉拦拌幸招坡披拨择抬其取苦若茂苹苗英范直茄茎茅林枝杯柜析板松枪"
                + "构杰述枕丧或画卧事刺枣雨卖矿码厕奔奇奋态欧垄妻轰顷转斩轮软到非叔肯齿"
                + "些虎虏肾贤尚旺具果味昆国昌畅明易昂典固忠咐呼鸣咏呢岸岩帖罗帜岭凯败贩"
                + "购图钓制知垂牧物乖刮秆和季委佳侍供使例版侄侦侧凭侨佩货依的迫质欣征往"
                + "爬彼径所舍金命斧爸采受乳贪念贫肤肺肢肿胀朋股肥服胁周昏鱼兔狐忽狗备饰"
                + "饱饲变京享店夜庙府底剂郊废净盲放刻育闸闹郑券卷单炒炊炕炎炉沫浅法泄河"
                + "沾泪油泊沿泡注泻泳泥沸波泼泽治怖性怕怜怪学宝宗定宜审宙官空帘实试郎诗"
                + "肩房诚衬衫视话诞询该详建肃录隶居届刷屈弦承孟孤陕降限妹姑姐姓始驾参艰"
                + "线练组细驶织终绊驼绍经贯奏春帮珍玻毒型挂封持项垮挎城挠政赴赵挡挺括拴"
                + "拾挑指垫挣挤拼挖按挥挪某甚革荐巷带草茧茶荒茫荡荣故胡南药标枯柄栋相查"
                + "柏柳柱柿栏树要咸威歪研砖厘厚砌砍面耐耍牵残殃轻鸦皆背战点临览竖省削尝"
                + "是盼眨哄显哑冒映星昨畏趴胃贵界虹虾蚁思蚂虽品咽骂哗咱响哈咬咳哪炭峡罚"
                + "贱贴骨钞钟钢钥钩卸缸拜看矩怎牲选适秒香种秋科重复竿段便俩贷顺修保促侮"
                + "俭俗俘信皇泉鬼侵追俊盾待律很须叙剑逃食盆胆胜胞胖脉勉狭狮独狡狱狠贸怨"
                + "急饶蚀饺饼弯将奖哀亭亮度迹庭疮疯疫疤姿亲音帝施闻阀阁差养美姜叛送类迷"
                + "前首逆总炼炸炮烂剃洁洪洒浇浊洞测洗活派洽染济洋洲浑浓津恒恢恰恼恨举觉"
                + "宣室宫宪突穿窃客冠语扁袄祖神祝误诱说诵垦退既屋昼费陡眉孩除险院娃娃姥"
                + "姨姻娇怒架贺盈勇怠柔垒绑绒结骄绘给络骆绝绞统耕耗艳泰珠班素蚕顽盏匪捞"
                + "栽捕振载赶起盐捎捏埋捉捆捐损都哲逝捡换挽热恐壶挨耻耽恭莲莫荷获晋恶真"
                + "框档桐株桥桃格校核样根索哥速逗栗配翅辱唇夏础破原套逐烈殊顾轿较顿毙致"
                + "柴桌虑监紧党晒眠晓鸭晃晌晕蚊哨哭恩唤啊唉罢峰圆贼贿钱钳钻铁铃铅缺氧特"
                + "牺造乘敌秤租积秧秩称秘透笔笑笋债借值倚倾倒倘俱倡候俯倍倦健臭射躬息徒"
                + "徐舰舱般航途拿爹爱颂翁脆脂胸胳脏胶脑狸狼逢留皱饿恋桨浆衰高席准座症病"
                + "疾疼疲效离唐资凉站剖竞部旁旅畜阅羞瓶拳粉料益兼烤烘烦烧烛烟递涛浙涝酒"
                + "涉消浩海涂浴浮流润浪浸涨烫涌悟悄悔悦害宽家宵宴宾窄容宰案请朗诸读扇袜"
                + "袖袍被祥课谁调冤谅谈谊剥恳展剧屑弱陵陶陷陪娱娘通能难预桑绢绣验继球理"
                + "捧堵描域掩捷排掉推掀授教掏掠培接控探据掘职基著勒黄萌萝菌菜萄菊萍菠营"
                + "械梦梢梅检梳梯桶救副票戚爽聋袭盛雪辅辆虚雀堂常匙晨睁眯眼悬野啦晚啄距"
                + "跃略蛇累唱患唯崖崭崇圈铜铲银甜梨犁移笨笼笛符第敏做袋悠偿偶偷您售停偏"
                + "假得衔盘船斜盒鸽悉欲彩领脚脖脸脱象够猜猪猎猫猛馅馆凑减毫麻痒痕廊康庸"
                + "鹿盗章竟商族旋望率着盖粘粗粒断剪兽清添淋淹渠渐混渔淘液淡深婆梁渗情惜"
                + "惭悼惧惕惊惨惯寇寄宿窑密谋谎祸谜逮敢屠弹随蛋隆隐婚婶颈绩绪续骑绳维绵"
                + "绸绿替款接塔搭越趁趋超提堤博揭喜插揪搜煮援裁搁搂搅握揉斯期欺联散惹葬"
                + "葛董葡敬葱落朝辜葵棒棋植森椅椒棵棍棉棚棕惠惑逼厨厦硬确雁殖裂雄暂雅辈"
                + "悲紫辉敞赏掌晴暑最量喷晶喇遇喊景践跌跑遗蛙蛛蜓喝喂喘喉幅帽赌赔黑铸铺"
                + "链销锁锄锅锈锋锐短智毯鹅剩稍程稀税筐等筑策筛筒答筋筝傲傅牌堡集焦傍储"
                + "奥街惩御循艇舒番释禽腊脾腔鲁猾猴然馋装蛮就痛童阔善羡普粪尊道曾焰港湖"
                + "渣湿温渴滑湾渡游滋溉愤慌惰愧愉慨割寒富窜窝窗遍裕裤裙谢谣谦属屡强粥疏"
                + "隔隙絮嫂登缎缓编骗缘瑞魂肆摄摸填搏塌鼓摆携搬摇搞塘摊蒜勤鹊蓝墓幕蓬蓄"
                + "蒙蒸献禁楚想槐榆楼概赖酬感碍碑碎碰碗碌雷零雾雹输督龄鉴睛睡睬鄙愚暖盟"
                + "歇暗照跨跳跪路跟遣蛾蜂嗓置罪罩错锡锣锤锦键锯矮辞稠愁筹签简毁舅鼠催傻"
                + "像躲微愈遥腰腥腹腾腿触解酱痰廉新韵意粮数煎塑慈煤煌满漠源滤滥滔溪溜滚"
                + "滨粱滩慎誉塞谨福群殿辟障嫌嫁叠缝缠静碧璃墙撇嘉摧截誓境摘摔聚蔽慕暮蔑"
                + "模榴榜榨歌遭酷酿酸磁愿需弊裳颗嗽蜻蜡蝇蜘赚锹锻舞稳算箩管僚鼻魄貌膜膊"
                + "膀鲜疑馒裹敲豪膏遮腐瘦辣竭端旗精歉熄熔漆漂漫滴演漏慢寨赛察蜜谱嫩翠熊"
                + "凳骡缩慧撕撒趣趟撑播撞撤增聪鞋蔬横槽樱橡飘醋醉震霉瞒题暴瞎影踢踏踩踪"
                + "蝴蝠墨镇靠稻黎稿稼箱箭篇僵躺僻德艘膝膛熟摩颜毅糊遵潜潮懂额慰劈操燕薯"
                + "薪薄颠橘整融醒餐嘴蹄器赠默镜赞篮邀衡膨雕磨凝辨辩糖糕燃澡激懒壁避缴戴"
                + "擦鞠藏霜霞瞧蹈螺穗繁辫赢糟糠燥臂翼骤鞭覆蹦镰翻鹰警攀蹲颤瓣爆疆壤耀躁"
                + "嚼嚷籍魔灌蠢霸露囊罐";
        for (char c : chars.toCharArray()) {
            COMMON_CHARS.add(c);
        }
        // 合并文言高频字
        for (char c : CLASSICAL_COMMON_CHARS.toCharArray()) {
            COMMON_CHARS.add(c);
        }
    }

    /**
     * 标点、数字、字母等非汉字字符
     */
    private static boolean isNonChinese(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        return ub != Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                && ub != Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                && ub != Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                && ub != Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    private static final HanyuPinyinOutputFormat PINYIN_FORMAT = new HanyuPinyinOutputFormat();

    static {
        PINYIN_FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        PINYIN_FORMAT.setToneType(HanyuPinyinToneType.WITH_TONE_MARK);
        PINYIN_FORMAT.setVCharType(HanyuPinyinVCharType.WITH_U_UNICODE);
    }

    /**
     * 对文本中的生僻字（不在常用 3500 字内）生成拼音映射。
     *
     * @param text 原文
     * @return { "字": "拼音" } 映射，无生僻字时返回空 Map
     */
    public static Map<String, String> buildRareCharPinyin(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isNonChinese(c)) continue;
            if (COMMON_CHARS.contains(c)) continue;
            try {
                String[] pinyins = PinyinHelper.toHanyuPinyinStringArray(c, PINYIN_FORMAT);
                if (pinyins != null && pinyins.length > 0) {
                    result.put(String.valueOf(c), pinyins[0]);
                } else {
                    // pinyin4j 字库覆盖不到的字，回退到硬编码字典
                    String fallback = HARDCODED_RARE_PINYIN.get(String.valueOf(c));
                    if (fallback != null) {
                        result.put(String.valueOf(c), fallback);
                    }
                }
            } catch (BadHanyuPinyinOutputFormatCombination ignored) {
                // 不应发生
            }
        }
        return result;
    }
}
