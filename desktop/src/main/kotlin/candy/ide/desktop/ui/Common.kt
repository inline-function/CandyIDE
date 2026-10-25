package candy.ide.desktop.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import candy.ide.desktop.IdeState
import candy.ide.ui.theme.CandySurfaceVariant
import candy.ide.ui.theme.CandyText
import kotlinx.coroutines.delay

/** 轻提示浮层（主页/编辑器窗口共用） */
@Composable
fun BoxScope.ToastOverlay(state: IdeState) {
    val msg = state.toast
    if (msg != null) {
        LaunchedEffect(msg) { delay(2400); if (state.toast == msg) state.toast = null }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            color = CandySurfaceVariant,
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(msg, color = CandyText, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontSize = 13.sp)
        }
    }
}
