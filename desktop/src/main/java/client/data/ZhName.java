package client.data;

import java.util.HashMap;
import java.util.Map;

/**
 * 物品名 -> 中文显示名（移植 game.js 的 CN_DIRECT + CN_WORDS）。
 * 直接映射优先，其次按英文显示名逐词翻译拼装。
 */
public final class ZhName {

    private ZhName() { }

    private static final Map<String, String> DIRECT = new HashMap<>();

    private static final Map<String, String> WORDS = new HashMap<>();

    static {
        putDirect("stone", "石头"); putDirect("granite", "花岗岩"); putDirect("diorite", "闪长岩");
        putDirect("andesite", "安山岩"); putDirect("grass_block", "草方块"); putDirect("dirt", "泥土");
        putDirect("coarse_dirt", "砂土"); putDirect("podzol", "灰化土"); putDirect("mycelium", "菌丝土");
        putDirect("cobblestone", "圆石"); putDirect("mossy_cobblestone", "苔石"); putDirect("bedrock", "基岩");
        putDirect("sand", "沙子"); putDirect("red_sand", "红沙"); putDirect("gravel", "沙砾");
        putDirect("sandstone", "砂岩"); putDirect("clay", "黏土"); putDirect("mud", "泥巴");
        putDirect("water", "水"); putDirect("lava", "岩浆"); putDirect("ice", "冰");
        putDirect("packed_ice", "浮冰"); putDirect("blue_ice", "蓝冰");
        putDirect("snow_block", "雪块"); putDirect("snow", "雪"); putDirect("powder_snow", "细雪");
        putDirect("glass", "玻璃"); putDirect("obsidian", "黑曜石");
        putDirect("oak_log", "橡木原木"); putDirect("spruce_log", "云杉原木"); putDirect("birch_log", "白桦原木");
        putDirect("jungle_log", "丛林原木"); putDirect("acacia_log", "金合欢原木"); putDirect("cherry_log", "樱花原木");
        putDirect("dark_oak_log", "深色橡木原木"); putDirect("mangrove_log", "红树原木");
        putDirect("oak_planks", "橡木木板"); putDirect("spruce_planks", "云杉木板"); putDirect("birch_planks", "白桦木板");
        putDirect("jungle_planks", "丛林木板"); putDirect("acacia_planks", "金合欢木板"); putDirect("cherry_planks", "樱花木板");
        putDirect("dark_oak_planks", "深色橡木木板"); putDirect("mangrove_planks", "红树木板");
        putDirect("oak_leaves", "橡树树叶"); putDirect("spruce_leaves", "云杉树叶"); putDirect("birch_leaves", "白桦树叶");
        putDirect("jungle_leaves", "丛林树叶"); putDirect("acacia_leaves", "金合欢树叶"); putDirect("cherry_leaves", "樱花树叶");
        putDirect("dark_oak_leaves", "深色橡树树叶"); putDirect("mangrove_leaves", "红树树叶");
        putDirect("oak_sapling", "橡树树苗"); putDirect("spruce_sapling", "云杉树苗"); putDirect("birch_sapling", "白桦树苗");
        putDirect("jungle_sapling", "丛林树苗"); putDirect("acacia_sapling", "金合欢树苗"); putDirect("cherry_sapling", "樱花树苗");
        putDirect("dark_oak_sapling", "深色橡树树苗");
        putDirect("deepslate", "深板岩"); putDirect("cobbled_deepslate", "深板岩圆石"); putDirect("tuff", "凝灰岩");
        putDirect("calcite", "方解石");
        putDirect("coal_ore", "煤矿石"); putDirect("iron_ore", "铁矿石"); putDirect("copper_ore", "铜矿石");
        putDirect("gold_ore", "金矿石"); putDirect("redstone_ore", "红石矿石"); putDirect("lapis_ore", "青金石矿石");
        putDirect("diamond_ore", "钻石矿石"); putDirect("emerald_ore", "绿宝石矿石"); putDirect("nether_gold_ore", "下界金矿石");
        putDirect("deepslate_coal_ore", "深板岩煤矿石"); putDirect("deepslate_iron_ore", "深板岩铁矿石");
        putDirect("deepslate_copper_ore", "深板岩铜矿石"); putDirect("deepslate_gold_ore", "深板岩金矿石");
        putDirect("deepslate_redstone_ore", "深板岩红石矿石"); putDirect("deepslate_lapis_ore", "深板岩青金石矿石");
        putDirect("deepslate_diamond_ore", "深板岩钻石矿石"); putDirect("deepslate_emerald_ore", "深板岩绿宝石矿石");
        putDirect("coal_block", "煤炭块"); putDirect("iron_block", "铁块"); putDirect("gold_block", "金块");
        putDirect("diamond_block", "钻石块"); putDirect("emerald_block", "绿宝石块"); putDirect("redstone_block", "红石块");
        putDirect("lapis_block", "青金石块"); putDirect("copper_block", "铜块"); putDirect("netherite_block", "下界合金块");
        putDirect("raw_iron_block", "粗铁块"); putDirect("raw_copper_block", "粗铜块"); putDirect("raw_gold_block", "粗金块");
        putDirect("crafting_table", "工作台"); putDirect("furnace", "熔炉"); putDirect("chest", "箱子");
        putDirect("torch", "火把"); putDirect("tnt", "TNT"); putDirect("bookshelf", "书架");
        putDirect("cactus", "仙人掌"); putDirect("sugar_cane", "甘蔗"); putDirect("melon", "西瓜");
        putDirect("pumpkin", "南瓜"); putDirect("carved_pumpkin", "雕刻南瓜"); putDirect("jack_o_lantern", "南瓜灯");
        putDirect("lily_pad", "睡莲"); putDirect("vine", "藤蔓"); putDirect("short_grass", "矮草丛");
        putDirect("tall_grass", "高草丛"); putDirect("fern", "蕨类植物");
        putDirect("dandelion", "蒲公英"); putDirect("poppy", "虞美人"); putDirect("blue_orchid", "兰花");
        putDirect("allium", "绒球葱"); putDirect("azure_bluet", "蓝花美耳草"); putDirect("red_tulip", "红色郁金香");
        putDirect("orange_tulip", "橙色郁金香"); putDirect("white_tulip", "白色郁金香"); putDirect("pink_tulip", "粉色郁金香");
        putDirect("oxeye_daisy", "滨菊"); putDirect("cornflower", "矢车菊"); putDirect("brown_mushroom", "棕色蘑菇");
        putDirect("red_mushroom", "红色蘑菇"); putDirect("mushroom_stem", "蘑菇柄");
        putDirect("wheat", "小麦"); putDirect("carrots", "胡萝卜"); putDirect("potatoes", "马铃薯");
        putDirect("beetroots", "甜菜"); putDirect("seagrass", "海草"); putDirect("kelp", "海带");
        putDirect("sponge", "海绵"); putDirect("wet_sponge", "湿海绵"); putDirect("hay_block", "干草块");
        putDirect("barrel", "木桶"); putDirect("anvil", "铁砧"); putDirect("beacon", "信标");
        putDirect("enchanting_table", "附魔台"); putDirect("brewing_stand", "酿造台"); putDirect("cauldron", "炼药锅");
        putDirect("hopper", "漏斗"); putDirect("piston", "活塞"); putDirect("sticky_piston", "粘性活塞");
        putDirect("rail", "铁轨"); putDirect("powered_rail", "充能铁轨"); putDirect("detector_rail", "探测铁轨");
        putDirect("activator_rail", "激活铁轨"); putDirect("ladder", "梯子");
        putDirect("stone_bricks", "石砖"); putDirect("mossy_stone_bricks", "苔石砖"); putDirect("cracked_stone_bricks", "裂纹石砖");
        putDirect("chiseled_stone_bricks", "錾制石砖"); putDirect("bricks", "红砖"); putDirect("glowstone", "荧石");
        putDirect("sea_lantern", "海晶灯"); putDirect("prismarine", "海晶石");
        putDirect("netherrack", "下界岩"); putDirect("soul_sand", "灵魂沙"); putDirect("basalt", "玄武岩");
        putDirect("blackstone", "黑石"); putDirect("ancient_debris", "远古残骸"); putDirect("crying_obsidian", "哭泣的黑曜石");
        putDirect("quartz_block", "石英块"); putDirect("slime_block", "黏液块"); putDirect("honey_block", "蜂蜜块");
        putDirect("honeycomb_block", "蜜脾块"); putDirect("sculk", "幽匿块"); putDirect("amethyst_block", "紫水晶块");
        putDirect("budding_amethyst", "紫水晶母岩"); putDirect("copper_grate", "铜格栅"); putDirect("light", "光源方块");
        putDirect("barrier", "屏障"); putDirect("lily_of_the_valley", "铃兰"); putDirect("lightning_rod", "避雷针");
        putDirect("end_rod", "末地烛"); putDirect("frogspawn", "蛙卵"); putDirect("pitcher_plant", "瓶子草");
        putDirect("blast_furnace", "高炉"); putDirect("daylight_detector", "阳光探测器"); putDirect("waxed_copper_block", "涂蜡铜块");

        String[][] w = {
            {"white", "白色"}, {"orange", "橙色"}, {"magenta", "品红色"}, {"light", "淡"}, {"blue", "蓝色"},
            {"yellow", "黄色"}, {"lime", "黄绿色"}, {"pink", "粉色"}, {"gray", "灰色"}, {"cyan", "青色"},
            {"purple", "紫色"}, {"brown", "棕色"}, {"green", "绿色"}, {"red", "红色"}, {"black", "黑色"},
            {"oak", "橡木"}, {"spruce", "云杉"}, {"birch", "白桦"}, {"jungle", "丛林"}, {"acacia", "金合欢"},
            {"cherry", "樱花"}, {"dark", "深色"}, {"mangrove", "红树"}, {"bamboo", "竹"}, {"crimson", "绯红"},
            {"warped", "诡异"}, {"stripped", "去皮"}, {"log", "原木"}, {"wood", "木头"}, {"planks", "木板"},
            {"leaves", "树叶"}, {"sapling", "树苗"}, {"stairs", "楼梯"}, {"slab", "台阶"}, {"fence", "栅栏"},
            {"gate", "栅栏门"}, {"door", "门"}, {"trapdoor", "活板门"}, {"button", "按钮"}, {"plate", "压力板"},
            {"wall", "墙"}, {"sign", "告示牌"}, {"ore", "矿石"}, {"block", "块"}, {"deepslate", "深板岩"},
            {"cobblestone", "圆石"}, {"stone", "石头"}, {"bricks", "砖"}, {"brick", "砖"}, {"glass", "玻璃"},
            {"pane", "玻璃板"}, {"wool", "羊毛"}, {"carpet", "地毯"}, {"concrete", "混凝土"}, {"terracotta", "陶瓦"},
            {"glazed", "上釉"}, {"stained", "染色"}, {"powder", "粉末"}, {"smooth", "平滑"}, {"chiseled", "錾制"},
            {"polished", "磨制"}, {"mossy", "苔藓"}, {"cracked", "裂纹"}, {"infested", "蛀蚀"}, {"cut", "切制"},
            {"waxed", "涂蜡"}, {"oxidized", "氧化"}, {"exposed", "斑驳"}, {"weathered", "风化"}, {"raw", "粗"},
            {"copper", "铜"}, {"iron", "铁"}, {"gold", "金"}, {"diamond", "钻石"}, {"emerald", "绿宝石"},
            {"redstone", "红石"}, {"lapis", "青金石"}, {"quartz", "石英"}, {"amethyst", "紫水晶"},
            {"nether", "下界"}, {"soul", "灵魂"}, {"end", "末地"}, {"netherite", "下界合金"},
            {"prismarine", "海晶石"}, {"cobbled", "圆石"}, {"dripstone", "滴水石"}, {"sculk", "幽匿"},
            {"shulker", "潜影"}, {"mushroom", "蘑菇"}, {"flower", "花"}, {"pot", "花盆"}, {"lantern", "灯笼"},
            {"candle", "蜡烛"}, {"cake", "蛋糕"}, {"egg", "蛋"}, {"snow", "雪"}, {"ice", "冰"}, {"sand", "沙"},
            {"gravel", "沙砾"}, {"dirt", "泥土"}, {"grass", "草"}, {"sea", "海"}, {"bed", "床"}, {"banner", "旗帜"},
            {"skull", "头颅"}, {"head", "头"}, {"torch", "火把"}, {"water", "水"}, {"clay", "黏土"}, {"wheat", "小麦"},
            {"apple", "苹果"}, {"bread", "面包"}, {"sword", "剑"}, {"gun", "枪"}, {"pickaxe", "镐"}, {"axe", "斧"}, {"shovel", "锹"},
            {"hoe", "锄"}, {"helmet", "头盔"}, {"chestplate", "胸甲"}, {"leggings", "护腿"}, {"boots", "靴子"},
            {"bow", "弓"}, {"arrow", "箭"}, {"shield", "盾牌"}, {"book", "书"}, {"seed", "种子"},
            {"carrot", "胡萝卜"}, {"potato", "马铃薯"}, {"beetroot", "甜菜"}, {"melon", "西瓜"}, {"pumpkin", "南瓜"},
            {"sugar", "糖"}, {"cane", "甘蔗"}, {"paper", "纸"}, {"stick", "木棍"}, {"string", "线"}, {"feather", "羽毛"},
            {"charcoal", "木炭"}, {"flint", "燧石"}, {"steel", "打火石"}, {"ingot", "锭"}, {"nugget", "粒"},
            {"dust", "粉"}, {"rod", "棒"}, {"crystal", "水晶"}, {"shell", "贝壳"}, {"bone", "骨"}, {"rotten", "腐"},
            {"flesh", "肉"}, {"gunpowder", "火药"}, {"bucket", "桶"}, {"milk", "奶"}, {"dye", "染料"}, {"ball", "球"},
            {"slime", "黏液"}, {"snowball", "雪球"}, {"fishing", "钓鱼"}, {"name", "命名"}, {"tag", "标签"},
            {"andesite", "安山岩"}, {"diorite", "闪长岩"}, {"granite", "花岗岩"}, {"tuff", "凝灰岩"},
            {"sandstone", "砂岩"}, {"blackstone", "黑石"}, {"basalt", "玄武岩"}, {"azalea", "杜鹃"}, {"moss", "苔藓"},
            {"mud", "泥"}, {"coal", "煤"}, {"lazuli", ""}, {"purpur", "紫珀"}, {"coral", "珊瑚"}, {"brain", "脑"},
            {"bubble", "气泡"}, {"horn", "角"}, {"tube", "管"}, {"fan", "扇"}, {"vein", "脉络"}, {"lichen", "地衣"},
            {"glow", "发光"}, {"glowstone", "荧石"}, {"mosaic", "马赛克"}, {"ender", "末影"}, {"nylium", "菌岩"},
            {"monster", "怪物"}, {"packed", "压实"}, {"gilded", "镶金"}, {"reinforced", "加固"}, {"rooted", "扎根"},
            {"frosted", "霜"}, {"suspicious", "可疑"}, {"tinted", "遮光"}, {"chipped", "缺口"}, {"damaged", "损坏"},
            {"decorated", "装饰"}, {"trapped", "陷阱"}, {"muddy", "泥泞"}, {"petrified", "石化"}, {"respawn", "重生"},
            {"anchor", "锚"}, {"lodestone", "磁石"}, {"pillar", "柱"}, {"bars", "栏杆"}, {"shard", "碎片"},
            {"bud", "芽"}, {"bulb", "灯"}, {"grate", "格栅"}, {"cluster", "簇"}, {"box", "盒"}, {"core", "核心"},
            {"tiles", "瓦"}, {"stem", "菌柄"}, {"roots", "根"}, {"hyphae", "菌丝"}, {"fungus", "菌"}, {"wart", "疣"},
            {"sensor", "传感器"}, {"catalyst", "催化器"}, {"shrieker", "尖啸体"}, {"spawner", "刷怪笼"},
            {"tripwire", "绊线"}, {"hook", "钩"}, {"note", "音符"}, {"chain", "锁链"}, {"lever", "拉杆"},
            {"bookshelf", "书架"}, {"hanging", "悬挂"}, {"pressure", "压力"}, {"weighted", "承重"},
            {"sticky", "粘性"}, {"powered", "充能"}, {"detector", "探测"}, {"activator", "激活"},
            {"campfire", "营火"}, {"piston", "活塞"}, {"lamp", "灯"}, {"magma", "岩浆"}, {"repeater", "中继器"},
            {"comparator", "比较器"}, {"beacon", "信标"}, {"scaffolding", "脚手架"}, {"conduit", "潮涌核心"},
            {"beehive", "蜂箱"}, {"nest", "巢"}, {"berry", "浆果"}, {"sweet", "甜"}, {"cocoa", "可可"},
            {"beans", "豆"}, {"slice", "片"}, {"pitcher", "瓶子草"}, {"pod", "荚"}, {"sniffer", "嗅探兽"},
            {"turtle", "海龟"}, {"dried", "干"}, {"skeleton", "骷髅"}, {"zombie", "僵尸"}, {"creeper", "苦力怕"},
            {"dragon", "龙"}, {"wither", "凋灵"}, {"piglin", "猪灵"}, {"player", "玩家"}, {"lily", "百合"},
            {"peony", "牡丹"}, {"lilac", "丁香"}, {"sunflower", "向日葵"}, {"rose", "玫瑰"}, {"bush", "丛"},
            {"chorus", "紫颂"}, {"flowering", "开花"}, {"petals", "花瓣"}, {"dripleaf", "滴水叶"}, {"spore", "孢子"},
            {"blossom", "花"}, {"cave", "洞穴"}, {"vines", "藤蔓"}, {"pickle", "泡菜"}, {"froglight", "蛙明灯"},
            {"ochre", "赭色"}, {"verdant", "翠绿"}, {"pearlescent", "珠光"}, {"shroomlight", "菌光体"},
            {"weeping", "垂泪"}, {"twisting", "缠怨"}, {"composter", "堆肥桶"}, {"crafter", "合成器"},
            {"dispenser", "发射器"}, {"dropper", "投掷器"}, {"observer", "侦测器"}, {"target", "标靶"},
            {"bell", "钟"}, {"jukebox", "唱片机"}, {"loom", "织布机"}, {"lectern", "讲台"}, {"grindstone", "砂轮"},
            {"stonecutter", "切石机"}, {"smoker", "烟熏炉"}, {"blast", "鼓风"}, {"cartography", "制图"},
            {"fletching", "制箭"}, {"smithing", "锻造"}, {"soil", "土"}, {"calibrated", "校准"}, {"heavy", "重型"},
            {"trial", "试炼"}, {"vault", "宝库"}, {"large", "大"}, {"medium", "中"}, {"small", "小"},
            {"pointed", "尖"}, {"seeds", "种子"}, {"plant", "植物"}, {"bee", "蜂"}, {"big", "大"}, {"table", "台"},
            {"anvil", "铁砧"}, {"dead", "死"}, {"tile", "瓦"}, {"kelp", "海带"}, {"crystals", "水晶"},
            {"furnace", "熔炉"}, {"of", ""}, {"fire", "火"}, {"torchflower", "火把花"}, {"chest", "箱子"},
        };
        for (String[] kv : w) WORDS.put(kv[0], kv[1]);
    }

    private static void putDirect(String key, String zh) {
        DIRECT.put(key, zh);
    }

    /** 物品名 -> 中文显示名（直接映射优先，其次按 displayName 逐词翻译）。 */
    public static String zhBlockName(String name, BlocksData data) {
        String direct = DIRECT.get(name);
        if (direct != null) return direct;
        BlockMeta meta = data.metaByName(name);
        String src = (meta != null && meta.displayName != null && !meta.displayName.isEmpty())
                ? meta.displayName : name;
        // 特殊句式 "Block of X" -> "X块"
        if (src.startsWith("Block of ")) {
            String rest = src.substring("Block of ".length()).trim();
            return translateWords(rest) + "块";
        }
        return translateWords(src);
    }

    private static String translateWords(String src) {
        String[] parts = src.split("[\\s_]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            String k = p.toLowerCase();
            String zh = WORDS.get(k);
            sb.append(zh != null ? zh : k);
        }
        String r = sb.toString();
        return r.isEmpty() ? src : r;
    }

    /** 收集所有中文翻译字符（去重），供 FreeType 预生成字形。 */
    public static String allChars() {
        StringBuilder sb = new StringBuilder();
        boolean[] seen = new boolean[65536];
        for (String v : DIRECT.values()) appendChars(sb, seen, v);
        for (String v : WORDS.values()) appendChars(sb, seen, v);
        return sb.toString();
    }

    private static void appendChars(StringBuilder sb, boolean[] seen, String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!seen[c]) {
                seen[c] = true;
                sb.append(c);
            }
        }
    }
}
