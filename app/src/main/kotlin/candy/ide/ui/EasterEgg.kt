package candy.ide.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import candy.ide.ui.theme.CandyBg
import candy.ide.ui.theme.CandyPrimary
import candy.ide.ui.theme.CandyText
import candy.ide.ui.theme.CandyTextDim
import kotlinx.coroutines.delay

/** 颜文字列表（每次打开 IDE 随机一个，显示在 CandyIDE 字样右边） */
private val KAOMOJIS = listOf(
    "(^3^)", "(^o^)y", "\\(^o^)/", "(^u^)", "(^w^)", "(^q^)",
    "(o^-^o)", "(^_^)/",
)

/** 点击标题/颜文字弹出的随机文本 */
private val EGG_TEXTS = listOf(
    "长按编辑页的保存可以保存整个工程哦~",
    "长按编辑页的撤回可以拉起时间回溯条哦~",
    "长按编辑页的运行可以直接打包哦~",
    "长按编辑页的信息可以进入终端哦~",
    "长按编辑页的设置可以进入工程信息页哦~",
    "长按编辑框可以弹出快捷操作菜单哦~",
    "双击编辑框可以弹出文本操作栏哦~",
    "长按诊断悬浮窗可以快速修正哦~",
    "长按补全悬浮窗条目可以跳转定义哦~",
    "长按补全悬浮窗关键词可以生成模板哦~",
    "日志和诊断页面可以设置等级过滤哦~",
    "文件页面右上角可以开启多选哦~",
    "您可以导出自己的工程与配色哦~",
    "您可以自定义自己的高亮配色哦~",
    "欢迎在IDE设置加入作者的QQ群哦~",
    "在IDE设置中可以配置与开启AGENT哦~",
    "在IDE设置中可以开启与使用SHIZUKU哦~",
    "这里很多工程都是可以打包成APK的哦~",
    "致瞬息万变之物，及亘古不变之物",
    "奇变偶不变，符号看象限",
    "20:11 剧烈变形",
    "为什么要演奏春日影！？",
    "我变成了！？一只。鸡？！",
    "国会拒绝交涉！",
    "武汉出现不明新冠病毒",
    "老实赚钱，很枯燥吧？",
    "本喵明明什么坏事也没做喵",
    "你是无理数吗？",
    "我没有时间考虑过去，我只考虑未来",
    "我才不擅■■，一提到■■我就高兴！",
    "你什么时候能飞的这么高了？",
    "从没觉得组乐队开心过！",
    "有哪些优秀的百合作品？",
    "我当时睡得可香了",
    "谁吃了豆子？",
    "水是剧毒的！",
    "■■■折戟■■",
    "大家换了新手机，旧的呢？",
    "丢人现眼……",
    "再无话说，请速动手……",
    "钾钠银氢正一价，氟氯溴碘负一价",
    "■■■■把我逼到了■■的地步",
    "快端上来吧，我等不及了",
    "果然人类还是差不多该灭绝了",
    "站好，为你的罪行接受审判！",
    "我要给丫丫，完整的一生…",
    "非常的新鲜，非常的美味",
    "自然选择，前进四！",
    "装逼我让你飞起来！",
    "小鸡裹麻袋",
    "我喜欢你，你喜欢我",
    "何意味",
)

/**
 * 主页标题：CandyIDE + 随机颜文字。点击弹真悬浮窗（Popup，不参与父布局、不顶起标题），
 * 显示在颜文字右下，可拖动，5 秒自动消失；再点立即换新文本并重置计时。
 */
@Composable
fun CandyTitleWithEgg() {
    val density = LocalDensity.current
    val kaomoji = remember { KAOMOJIS.random() }
    var eggIndex by remember { mutableIntStateOf(-1) }
    var tick by remember { mutableIntStateOf(0) }
    // 悬浮窗位置（px）：初始在标题行右下（颜文字右侧偏下，可遮挡右上角设置按钮）
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var dragTick by remember { mutableIntStateOf(0) }
    val shown = eggIndex >= 0
    val pick: () -> Unit = {
        val pool = (EGG_TEXTS.indices).filter { it != eggIndex }
        eggIndex = pool.random()
        tick++
    }
    val text = if (eggIndex in EGG_TEXTS.indices) EGG_TEXTS[eggIndex] else null

    Box {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "CandyIDE",
                fontWeight = FontWeight.Bold,
                color = CandyPrimary,
                modifier = Modifier.eggClick(onClick = pick),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                kaomoji,
                color = CandyTextDim,
                fontSize = 13.sp,
                modifier = Modifier.eggClick(onClick = pick),
            )
        }
        // 真悬浮窗：Popup 独立于父布局，标题永远不会被顶起
        if (shown && text != null) {
            val baseX = with(density) { 150.dp.toPx() }
            val baseY = with(density) { 32.dp.toPx() }
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset((baseX + offsetX).toInt(), (baseY + offsetY).toInt()),
                properties = PopupProperties(focusable = false),
                onDismissRequest = {},
            ) {
                Surface(
                    color = CandyBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, CandyPrimary),
                    shadowElevation = 8.dp,
                    modifier = Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                                dragTick++
                            },
                        )
                    },
                ) {
                    Text(
                        text,
                        color = CandyText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }
            // 5 秒自动消失；点击或拖动都会重置倒计时
            LaunchedEffect(tick, dragTick) {
                delay(5000L)
                eggIndex = -1
                // 复位拖动偏移，下次弹出回到初始位置
                offsetX = 0f
                offsetY = 0f
            }
        }
    }
}

/** 无水波纹的轻量点击（标题、颜文字共用） */
@Composable
private fun Modifier.eggClick(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.then(
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        ),
    )
}
