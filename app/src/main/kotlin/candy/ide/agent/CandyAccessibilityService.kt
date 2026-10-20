package candy.ide.agent

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Agent 的无障碍服务占位：目前只做事件透传，
 * 后续让 agent 操作 IDE 界面（点击/滚动/读取节点）时在这里实现。
 */
class CandyAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 预留：agent 界面操作
    }

    override fun onInterrupt() {
    }
}
