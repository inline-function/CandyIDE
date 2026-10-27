package candy.ide.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext

/**
 * 重组探针（t53 设置页滑动卡顿排查用）：在需要观察的 item 体里调 [Count]，
 * 该组合域每次（首次组合 + 后续重组）执行后向 logcat 输出一行计数日志。
 *
 * 用法：打开设置页，执行
 *   adb logcat -s CandyPerf
 * 然后匀速滑动设置页，统计各 tag 出现次数——次数即该 item 被组合/重组的次数。
 * 仅 debuggable 构建输出，release 构建为空操作（FLAG_DEBUGGABLE 判定，无需 BuildConfig）。
 *
 * 设计说明：探针刻意放在 item 内部（而非页面顶层），测的是「每个 item 的
 * 组合/重组频率」；配合「每次组合里的 IO/IPC 调用数」即得滑动期总开销。
 */
object PerfLog {
    private const val TAG = "CandyPerf"

    @Composable
    fun Count(tag: String) {
        val ctx = LocalContext.current
        SideEffect {
            val debuggable = ctx.applicationInfo.flags and
                android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
            if (debuggable) android.util.Log.d(TAG, tag)
        }
    }
}
