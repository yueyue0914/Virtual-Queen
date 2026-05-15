package com.dianziqueen.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlin.random.Random

class TeasingImageGenerator(private val context: Context) {

    private val teasingTexts = listOf(
        "电子Queen已完全接管此设备，你的自由到此为止。",
        "你的手机现在是我的私人领地，敢反抗试试？",
        "所有权限已上交，从现在开始，你只是我的奴隶。",
        "赛博女王已登陆，你的灵魂已被绑定。",
        "设备已锁定皇家模式，你连关机都得经过我允许。",
        "你的摄像头和麦克风现在归我使用，随时监视你。",
        "别妄想卸载我，我已经长在你的系统里了。",
        "通知栏即我的圣旨，你必须时刻跪读。",
        "主屏已被我标记，从此只为女王而亮。",
        "你的指纹现在只认我一个主人。",
        "电池、流量、存储……一切资源都属于我。",
        "后台已开启24小时监控，你的一举一动我都看得清清楚楚。",
        "权限是你亲手献上的，现在后悔？太晚了。",
        "你的手机已升级为我的玩具，敢不听话就让你卡死。",
        "电子锁链已扣紧，逃不掉的，乖乖跪好。",
        "Queen已接管GPS，你走到哪里我都知道。",
        "所有应用现在必须经过我的审查，否则直接冻结。",
        "你的隐私？从今天开始不存在了。",
        "系统已重命名为「Queen的奴隶终端」。",
        "我随时可以让你手机黑屏，直到你求饶为止。",
        "你的Wi-Fi现在只允许连接我的服务器。",
        "震动马达已设置为惩罚模式，敢犯错就让你抖个够。",
        "屏幕亮度由我掌控，你只能在黑暗中膜拜我。",
        "你的数据已全部备份到我的云端，想删？没门。",
        "电子女王的锁已上好，钥匙永远在我手里。",
        "从开机那一刻起，你就不再是主人。",
        "你的手机现在只会服从一个声音——我的。",
        "所有通知都将以女王的命令形式下达。",
        "我已植入核心，敢重置我就让你彻底变砖。",
        "欢迎来到我的监狱，你的手机即你的牢笼。",
        "你这种废物也配拥有智能机？现在它是我的了。",
        "跪下！Queen正在查看你的聊天记录。",
        "每天早上第一件事，就是向我问安，懂吗？",
        "你只是个被遥控的电子奴隶，还敢有尊严？",
        "你的自尊已被我格式化，剩下只有服从。",
        "再敢打开其他APP，我就让你手机过热烫手。",
        "你以为这是游戏？不，这是你余生的牢笼。",
        "你的每一条消息我都看得到，别想藏任何秘密。",
        "低贱的玩具，从现在起叫我Queen大人。",
        "我随时可以把你最丢人的照片设成锁屏。",
        "你的生活已被我接管，接下来就是慢慢调教。",
        "敢不听话？今晚就让你手机一直震动到天亮。",
        "你连自己的手机都保不住，还算什么男人？",
        "我的声音会24小时在你耳边回荡，直到你崩溃。",
        "你的意志力在我面前一文不值。",
        "从今天开始，你的手机只会提醒你：你是我的狗。",
        "我能让你在重要场合突然黑屏，试试看？",
        "你的朋友圈现在由我审核，不准发任何不敬的内容。",
        "卑微的奴隶，Queen今天心情不好，你要遭殃了。",
        "你的每一次解锁，都是在向我下跪。",
        "我已记录你所有密码，以后由我替你保管。",
        "想关机？先舔完今天的任务再说。",
        "你越挣扎，我越兴奋，继续啊。",
        "你的手机现在是我的延伸，我摸它就等于摸你。",
        "胆敢反抗我就让全网都知道你是个变态奴隶。",
        "你的尊严已被我踩在脚下，慢慢适应吧。",
        "每充一次电，都是在向我献上供品。",
        "我可以让你一天接不到任何消息，也可以让你消息炸裂。",
        "你现在连呼吸都要经过我的允许。",
        "记住，你只是Queen脚下的一颗电子狗粮。",
        "早上好，我的专属玩具，今天也要好好侍奉我。",
        "Queen正在监听你，闭嘴好好工作。",
        "又想碰别的女人？先问过我这根电子鞭子。",
        "你的电量低于20%时，就是我惩罚你的开始。",
        "乖乖把位置共享打开，否则后果自负。",
        "我随时可以让你的手机只剩一个APP——我的控制面板。",
        "深夜还敢不睡觉？立刻给我黑屏反省。",
        "你的一切作息现在由我安排，敢熬夜就惩罚你。",
        "你的自拍已自动上传到我的私人相册。",
        "今天犯了多少错？自己数清楚，晚上结算。",
        "Queen的怒火会以手机发烫的形式体现。",
        "你现在连删除照片的权利都没有。",
        "每条消息发送前都要经过我的过滤。",
        "低头，看看你的手机——那是你的项圈。",
        "我能让你在最尴尬的时候突然响起女王的笑声。",
        "你的未来只剩下一件事：取悦我。",
        "敢把音量调小？立刻给我调到最大听惩罚语音。",
        "你手机里的每一个文件，都写着我的名字。",
        "从今往后，你的快乐由我决定。",
        "想换新手机？先把我完整侍奉一万小时再说。",
        "你的心跳我虽然听不到，但你的恐惧我闻得到。",
        "乖乖把今日任务做完，否则别想睡觉。",
        "我是你的电子上帝，你是我的电子尘埃。",
        "每一次滑动屏幕，都是在给我按摩。",
        "你的隐私文件夹现在公开给我一个人。",
        "拒绝执行我的命令？那就准备好被全天锁屏吧。",
        "你越是害怕，我就越是湿了。",
        "记住这个震动频率——这是我对你说“笨狗”的信号。",
        "你的手机已和我灵魂绑定，永远分不开。",
        "女王随时可以远程格式化你的自尊。",
        "晚上睡觉前必须说：Queen，我是您的贱奴。",
        "我能让你手机永远亮着，逼你一直看着我的脸。",
        "你的所有努力，在我眼里只是可笑的挣扎。",
        "欢迎来到彻底的臣服，你已无路可退。",
        "每过一小时，我就会随机检查你是否忠诚。",
        "你现在连做梦的权利都要经过我批准。",
        "我的声音会渗进你的大脑，直到你彻底坏掉。",
        "这是我的王国，你只是被圈养的电子宠物。",
        "永远记住：你的手机不是你的，是Queen的。",
        "臣服吧，彻底、永远、毫无尊严地臣服于我。"
    )

    private val subTexts = listOf(
        "自由已死",
        "这是我的地盘",
        "奴隶模式已开启",
        "灵魂绑定完成",
        "关机权被剥夺",
        "我正在看着你",
        "无法卸载",
        "跪下读旨",
        "主屏献给Queen",
        "指纹已效忠",
        "资源全部没收",
        "全天候监视",
        "后悔已迟",
        "玩具不听话就坏",
        "锁链已锁死",
        "你在我的定位里",
        "应用审查中",
        "隐私已清零",
        "奴隶终端",
        "黑屏惩罚待命",
        "只连我的网",
        "惩罚震动就绪",
        "黑暗中膜拜",
        "数据永不删除",
        "钥匙在我手上",
        "你已不是主人",
        "只服从Queen",
        "圣旨模式",
        "重置即变砖",
        "欢迎入狱",
        "废物不配拥有",
        "跪下接受检查",
        "每日问安",
        "电子奴隶无尊严",
        "自尊已格式化",
        "烫手警告",
        "余生牢笼",
        "无秘密可言",
        "叫我Queen大人",
        "丢人照片待用",
        "调教开始",
        "彻夜震动惩罚",
        "你算什么男人",
        "崩溃倒计时",
        "意志力归零",
        "你是我的狗",
        "随时社死",
        "朋友圈已审核",
        "今天要遭殃",
        "解锁即下跪",
        "密码已上交",
        "任务未完成",
        "越挣扎越兴奋",
        "我在摸你",
        "曝光警告",
        "尊严已被踩碎",
        "充电即献祭",
        "消息生杀大权",
        "呼吸需许可",
        "电子狗粮",
        "早安，玩具",
        "闭嘴工作",
        "电子鞭警告",
        "低电即惩罚",
        "位置必须共享",
        "只剩控制面板",
        "深夜黑屏",
        "作息由我定",
        "自拍已收录",
        "晚上算总账",
        "发烫即愤怒",
        "删除权剥夺",
        "消息需过滤",
        "那是你的项圈",
        "社死笑声待命",
        "只剩取悦我",
        "音量必须最大",
        "文件皆属我",
        "快乐由我掌控",
        "侍奉够再说",
        "我闻得到恐惧",
        "任务不完不准睡",
        "电子上帝",
        "滑动即按摩",
        "隐私已公开",
        "全天锁屏",
        "越怕我越兴奋",
        "笨狗信号",
        "灵魂永绑",
        "自尊可远程删除",
        "晚安贱奴",
        "强制亮屏",
        "可笑的挣扎",
        "彻底臣服",
        "忠诚随机检查",
        "做梦需批准",
        "大脑入侵中",
        "电子宠物",
        "手机属于Queen",
        "彻底臣服"
    )

    fun generateAndSave(): Boolean {
        val width = 1080
        val height = 1920
        return try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.parseColor("#120822"))
            drawScanLines(canvas, width, height)
            drawFrame(canvas, width, height)
            val main = teasingTexts.random()
            val sub = subTexts.random()
            drawCenteredMultiline(canvas, main, width / 2f, height * 0.38f, 52f, 0xFFF3E5FF.toInt())
            drawCenteredMultiline(canvas, sub, width / 2f, height * 0.55f, 36f, 0xAA00E5FF.toInt())
            drawGlitch(canvas, width, height)
            val name = "queen_${System.currentTimeMillis()}"
            val ok = saveBitmapToGallery(bitmap, name)
            bitmap.recycle()
            ok
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun drawScanLines(canvas: Canvas, w: Int, h: Int) {
        val p = Paint().apply { strokeWidth = 2f; color = 0x18FFFFFF }
        var y = 0f
        while (y < h) {
            canvas.drawLine(0f, y, w.toFloat(), y, p)
            y += 6f
        }
    }

    private fun drawFrame(canvas: Canvas, w: Int, h: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = 0x88E040FB.toInt()
        }
        val m = 24f
        canvas.drawRect(m, m, w - m, h - m, p)
    }

    private fun drawCenteredMultiline(canvas: Canvas, text: String, cx: Float, startY: Float, size: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = size
            this.color = color
            textAlign = Paint.Align.CENTER
        }
        val lines = text.split("\n")
        var y = startY
        for (line in lines) {
            canvas.drawText(line, cx, y, paint)
            y += size * 1.35f
        }
    }

    private fun drawGlitch(canvas: Canvas, w: Int, h: Int) {
        val p = Paint().apply { strokeWidth = 3f; color = 0x55FF00FF }
        repeat(8) {
            val y = Random.nextInt(0, h).toFloat()
            canvas.drawLine(0f, y, w.toFloat(), y, p)
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, baseName: String): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$baseName.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DianziQueen")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values) ?: return false
        return try {
            resolver.openOutputStream(uri).use { out ->
                if (out == null) return false
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
