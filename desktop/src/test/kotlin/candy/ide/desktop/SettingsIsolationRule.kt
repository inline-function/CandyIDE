package candy.ide.desktop

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * 测试隔离：java.util.prefs 是机器级存储（注册表），测试改过的设置若不还原会污染真实 App。
 * 每条测试前后保存/恢复被改写的字段（空串 = 走默认值）。
 */
class SettingsIsolationRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val ws = Settings.workspaceRoot
            val recent = Settings.recentProject
            try {
                base.evaluate()
            } finally {
                Settings.workspaceRoot = ws
                Settings.recentProject = recent
            }
        }
    }
}
