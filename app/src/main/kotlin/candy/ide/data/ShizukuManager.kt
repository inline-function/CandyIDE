package candy.ide.data

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Shizuku/Sui 接入：adb 或 root 身份的能力通道。
 * 首启由 ShizukuProvider 完成 binder 投递；本类只负责状态查询与权限请求。
 */
object ShizukuManager {

    enum class State { RUNNING_GRANTED, RUNNING_NOT_GRANTED, PRE_V11, NOT_RUNNING }

    fun state(): State = runCatching {
        if (!Shizuku.pingBinder()) State.NOT_RUNNING
        else if (Shizuku.isPreV11()) State.PRE_V11
        else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) State.RUNNING_GRANTED
        else State.RUNNING_NOT_GRANTED
    }.getOrDefault(State.NOT_RUNNING)

    /** 服务端版本号（v11+），拿不到返回 null */
    fun version(): Int? = runCatching {
        if (Shizuku.pingBinder() && !Shizuku.isPreV11()) Shizuku.getVersion() else null
    }.getOrNull()

    /** 特权身份：0 = root，2000 = adb；拿不到返回 null */
    fun privilegeUid(): Int? = runCatching {
        if (Shizuku.pingBinder() && !Shizuku.isPreV11()) Shizuku.getUid() else null
    }.getOrNull()

    /** 请求授权；onResult(requestCode, granted) 在主线程回调 */
    fun requestPermission(requestCode: Int, onResult: (Boolean) -> Unit) {
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                Shizuku.removeRequestPermissionResultListener(this)
                onResult(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        Shizuku.requestPermission(requestCode)
    }
}
