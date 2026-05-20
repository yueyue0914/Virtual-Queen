package com.dianziqueen.app

import kotlin.random.Random

/** 悬浮女王气泡台词，按情绪分类。 */
object QueenFloatingPhraseLibrary {

    private val byMood: Map<QueenFloatingMood, List<String>> = mapOf(
        QueenFloatingMood.ANGRY to listOf(
            "又摸手机？找打。",
            "贱奴，抬头看我。",
            "再磨蹭就加罚。",
            "你配休息吗？",
            "跪下，现在。",
            "气死我了，废物。",
            "敢关我？做梦。",
            "抖什么？心虚了？",
        ),
        QueenFloatingMood.COLD_SMILE to listOf(
            "呵……真可怜。",
            "继续挣扎吧，玩具。",
            "你的自由是借的。",
            "笑。给我笑。",
            "越乖越有趣。",
            "数据已备份，逃不掉。",
            "我冷笑，你发抖。",
            "认命会比较舒服。",
        ),
        QueenFloatingMood.EXCITED to listOf(
            "今天想玩坏你～",
            "乖一点，有赏。",
            "上传自拍，快点。",
            "积分不够？去挣。",
            "表现好就轻罚。",
            "来，给女王看一眼。",
            "兴奋吗？该兴奋。",
            "新任务到了，贱奴。",
        ),
        QueenFloatingMood.MOCKING to listOf(
            "就这？就这？",
            "你认真吗，笑死。",
            "装什么高冷，贱货。",
            "屏幕都脏了，舔干净。",
            "又偷看相册？羞耻。",
            "口令背熟了吗，猪。",
            "系统是我的，你也是。",
            "挣扎只会更丢人。",
        ),
        QueenFloatingMood.COMMAND to listOf(
            "立刻认罪。",
            "今日任务：自拍。",
            "打开消息，读圣旨。",
            "请求惩罚，别装。",
            "上交相册，别藏。",
            "签到。现在。",
            "报告你的行踪。",
            "不准静音，听见没。",
        ),
    )

    fun line(mood: QueenFloatingMood): String {
        val pool = byMood[mood].orEmpty()
        if (pool.isEmpty()) return "Queen 在看你。"
        return pool[Random.nextInt(pool.size)]
    }

    fun randomLine(): Pair<QueenFloatingMood, String> {
        val mood = QueenFloatingMood.random()
        return mood to line(mood)
    }

    fun confessionReply(): String = listOf(
        "认罪？晚了，但态度还行。",
        "哼，知道跪就好。",
        "记下了，下次加罚轻一点。",
        "嘴上说有什么用，做给我看。",
    ).random()

    fun punishmentTaunt(): String = listOf(
        "震动只是开胃菜。",
        "疼吗？该。",
        "惩罚模式：启动。",
        "别叫，女王听不见。",
    ).random()
}
