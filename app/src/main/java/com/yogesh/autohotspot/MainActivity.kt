package com.yogesh.autohotspot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yogesh.autohotspot.ui.theme.AutoHotspotTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity(), WifiMonitorService.WifiMonitorServiceListener {

    data class PermissionStatus(
        val name: String? = null,
        val status: Boolean = false
    )

    class MainActivityViewModel : ViewModel() {
        private val _updatePermission = MutableStateFlow(PermissionStatus())
        private val _serviceStatus = MutableStateFlow(false)
        val serviceStatus: StateFlow<Boolean> = _serviceStatus.asStateFlow()
        val updatePermission: StateFlow<PermissionStatus> = _updatePermission.asStateFlow()

        fun updatePermissinStatus(status: Boolean) {
            _updatePermission.update { currentState ->
                currentState.copy(
                    status = status
                )
            }
        }

        fun setRunningStatus(status: Boolean) {
            _serviceStatus.update { status }
        }
    }

    private val viewModel by lazy { ViewModelProvider(this)[MainActivityViewModel::class.java] }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.updatePermissinStatus(Settings.System.canWrite(this))
        WifiMonitorService.listener = this
        enableEdgeToEdge()
        setContent {
            AutoHotspotTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        MainActivityView(
                            { setSetHotspotService(it) },
                            viewModel,
                            askPermission = { requestWriteSettingsPermission() })
                    }
                }
            }
        }
    }

    private fun setSetHotspotService(status: Boolean) {
        val serviceIntent = Intent(this, WifiMonitorService::class.java)
        if (status) startService(serviceIntent)
        else stopService(serviceIntent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.updatePermissinStatus(Settings.System.canWrite(this))
    }

    private var resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            viewModel.updatePermissinStatus(Settings.System.canWrite(this))
        }

    private fun requestWriteSettingsPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        resultLauncher.launch(intent)
    }

    override fun onWifiMonitor(status: Boolean) {
        viewModel.setRunningStatus(status)
    }
}

@Composable
fun MainActivityView(
    toggleService: (Boolean) -> Unit,
    viewModel: MainActivity.MainActivityViewModel = viewModel(),
    askPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canWriteSetting by viewModel.updatePermission.collectAsState()
    Log.d(TAG, "onCreate: $canWriteSetting")
    if (canWriteSetting.status) {
        Log.d(TAG, "onCreate: canWriteSetting")
        OnOffServiceView(toggleService)
    } else {
        Log.d(TAG, "onCreate: AskPermissionView")
        AskPermissionView(
            onClick = askPermission
        )
    }
}

@Composable
fun AskPermissionView(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            "Write settings permission required for turn on/off hotspot",
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        Button(onClick = onClick) {
            Text("Give permission")
        }
    }
}

@Composable
fun OnOffServiceView(
    onClick: (Boolean) -> Unit,
    viewModel: MainActivity.MainActivityViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
    ) {
        val isRunning by viewModel.serviceStatus.collectAsState(WifiMonitorService.isRunning)
        Text("Auto Hotspot service", modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Switch(isRunning, {
            onClick(it)
            viewModel.setRunningStatus(it)
        })
    }
}

@Preview(showBackground = true)
@Composable
fun Preview() {
    AutoHotspotTheme {
        OnOffServiceView({})
    }
}