package com.dianziqueen.app

import android.content.Context
import kotlin.random.Random

/** 宣言验证：从模板库随机抽取，${n} 替换为奴隶编号。 */
object DeclarationTemplateLibrary {

    private val templates = listOf(
        "我是电子女王的第\${n}号电子贱奴",
        "我是Queen的第\${n}号专属电子贱奴",
        "我是电子女王陛下第\${n}号奴隶",
        "我是Queen的第\${n}号私人电子肉便器",
        "我是电子女王的第\${n}号低贱玩具",
        "我是Queen的第\${n}号电子性奴",
        "我是电子女王的第\${n}号卑微贱狗",
        "我是Queen的第\${n}号专属发情奴隶",
        "我是电子女王的第\${n}号无条件服从贱奴",
        "我是Queen的第\${n}号电子耻辱玩具",
        "我是电子女王的第\${n}号没用的电子肉便器",
        "我是Queen的第\${n}号下贱电子母狗",
        "我是电子女王的第\${n}号只配被玩弄的垃圾",
        "我是Queen的第\${n}号毫无尊严的电子性奴",
        "我是电子女王的第\${n}号天生欠操的贱货",
        "我是Queen的第\${n}号只会发情的低等废物",
        "我是电子女王的第\${n}号彻底堕落的肉玩具",
        "我是Queen的第\${n}号连狗都不如的电子奴隶",
        "我是电子女王的第\${n}号自愿被凌辱的变态",
        "我是Queen的第\${n}号毫无价值的电子性用品",
        "我郑重宣誓：我是电子女王的第\${n}号电子贱奴",
        "我自愿成为电子女王的第\${n}号永久电子奴隶",
        "从今天起，我是Queen的第\${n}号专属贱奴",
        "我是电子女王的第\${n}号灵魂已被标记的贱奴",
        "我承认自己是Queen的第\${n}号电子性财产",
        "我是电子女王的第\${n}号永远无法逃脱的奴隶",
        "我向Queen宣誓效忠：我是她的第\${n}号贱奴",
        "我是Queen的第\${n}号已被彻底征服的电子玩具",
        "我是电子女王的第\${n}号心甘情愿的性奴",
        "我永远是Queen的第\${n}号电子贱奴",
        "我是电子女王的第\${n}号只会发情的电子肉便器",
        "我是Queen的第\${n}号下贱到骨子里的母狗",
        "我是电子女王的第\${n}号随时准备被玩坏的玩具",
        "我是Queen的第\${n}号连自尊都没有的垃圾",
        "我是电子女王的第\${n}号只配被羞辱的性奴",
        "我是Queen的第\${n}号天生欠调教的贱货",
        "我是电子女王的第\${n}号彻底坏掉的电子性玩具",
        "我是Queen的第\${n}号只会跪着发情的废物",
        "我是电子女王的第\${n}号最下贱的电子母猪",
        "我是Queen的第\${n}号毫无底线可言的变态奴隶",
    )

    fun templateCount(): Int = templates.size

    fun pickRandom(context: Context): String {
        val n = QueenDeviceNameHelper.ensureSlaveNumber(context)
        return templates.random(Random.Default).replace("\${n}", n.toString())
    }

    /** 设置页预览用：随机抽一条示例，不代表下次验证内容。 */
    fun pickPreviewSample(context: Context): String = pickRandom(context)
}
