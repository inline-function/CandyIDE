package candy.ide

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import candy.ide.ui.CandyApp
import candy.ide.ui.theme.CandyTheme
import candy.ide.vm.CandyViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CandyTheme {
                val vm: CandyViewModel = viewModel()
                // 启动时带文件（jks/caco/capr）：交给 VM 落盘
                LaunchedEffect(Unit) { ingest(intent, vm) }
                CandyApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun ingest(intent: Intent?, vm: CandyViewModel) {
        val data: Uri? = intent?.data
            ?: intent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (data != null) vm.ingestSharedFile(data)
    }
}