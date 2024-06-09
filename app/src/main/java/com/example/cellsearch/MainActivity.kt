package com.example.cellsearch

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import cz.mroczis.netmonster.core.INetMonster
import cz.mroczis.netmonster.core.factory.NetMonsterFactory
import cz.mroczis.netmonster.core.model.cell.CellLte
import cz.mroczis.netmonster.core.model.cell.CellNr
import cz.mroczis.netmonster.core.model.cell.ICell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : ComponentActivity() {

    private lateinit var earfcnTextView: TextView
    private lateinit var netMonster: INetMonster

    private var permissionGranted by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            if (permissionGranted) {
                MainApp()
                Log.d("MainApplication", "Start application!")
            } else {
                RequestPermissions { granted ->
                    permissionGranted = granted
                }
            }
        }
    }

}

// 必要であれば権限を確認する部分
@Composable
fun RequestPermissions(onPermissionResult: (Boolean) -> Unit) {
    val context = LocalContext.current
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.READ_PHONE_STATE] == true &&
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        onPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        requestPermissionLauncher.launch(arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION
        ))
    }
}

// ARFCN取得
@Composable
fun getEarfcn(): String {
    var mess: String = ""
    val context = LocalContext.current
    NetMonsterFactory.getTelephony(context).apply {
        val allCellInfo: List<ICell> = getAllCellInfo()
        val state = getServiceState()

        var ci: Long? = null
        var pci: Int? = null
        var enb: Long? = null
        var lcid: Long? = null
        var arfcn: Int? = null
        var band: Int? = null
        var bandwidth: Int? = null
        var freq: Int? = null
        var ta: Int? = null


        for (cellInfo in allCellInfo) {
            if (cellInfo is CellLte) {
                ci = cellInfo.eci?.toLong()
                pci = cellInfo.pci
                enb = ci?.div(256)
                lcid = ci?.rem(256)
                arfcn = cellInfo.band?.downlinkEarfcn
                band = cellInfo.band?.number
                bandwidth = cellInfo.bandwidth//?.div(1000)
                ta = cellInfo.signal.timingAdvance
                //AppScreen(mainValue = "aa")
                //AppScreen(mainValue = "CI:${enb}-${lcid} ARFCN: ${arfcn} ${bandwidth} MHz")
                mess = "PCI:${pci} CI:${enb}-${lcid}\nBAND:${band} ARFCN:${arfcn} TA:${ta}"
                break
            }
            if (cellInfo is CellNr) {
                ci = cellInfo.nci
                enb = ci?.div(256)
                lcid = ci?.rem(256)
                arfcn = cellInfo.band?.downlinkArfcn
                freq = cellInfo.band?.downlinkFrequency
                band = cellInfo.band?.number
                ta = cellInfo.signal.timingAdvance
                //earfcnTextView.text = "CI:${ci} ${gnb},freq:${freq} ${band} ${ta}"
                break
            }
        }
    }
    return mess
}
// メインレイアウト部
@Composable
fun MainApp(){
    var currentTime by remember { mutableStateOf(getCurrentTime()) }
    var cell by remember { mutableStateOf<String?>(null) }
    val pingHost: String = "1.1.1.1"

    LaunchedEffect(true) {
        while(true) {
            delay(1000)
            currentTime = getCurrentTime()
            println("${currentTime}")
            val pingResult = performPing(pingHost)
        }
    }
    cell = getEarfcn()
    Column {
        AppScreen(currentTime)
        AppScreen("${cell}")
        //CellInfoUpdate()
        LocationUpdates()
    }
}

// TA更新のためにはPingを飛ばさないといけない。。。
suspend fun performPing(host: String): String{
    return withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("ping -c 1 $host")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            reader.close()
            process.waitFor()
            Log.d("PingTask", output)
            output
        } catch (e: Exception) {
            Log.e("PingTask", "Error pinging", e)
            "Ping failed"
        }
    }

}
// GoogleMap表示部
@Composable
fun LocationUpdates() {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(LocalContext.current)
    val context = LocalContext.current
    val location = remember { mutableStateOf<LatLng?>(null) }
    val cameraPositionState = rememberCameraPositionState()

    LaunchedEffect(Unit) {
        while (true) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
                    loc?.let {
                        location.value = LatLng(it.latitude, it.longitude)
                        // カメラの位置を現在の位置に更新
                        val newLocation = LatLng(it.latitude, it.longitude)
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(newLocation, 15f)
                    }
                }
            }
            delay(1000)
        }
    }

    location.value?.let { currentLocation ->
        GoogleMapView(cameraPositionState, location.value)
    }

}


fun getCurrentTime(): String {
    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return dateFormat.format(Date())
}

@Composable
fun GoogleMapView(cameraPositionState: CameraPositionState, currentLocation: LatLng?) {
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState
    ) {
        currentLocation?.let {
            Marker(
                state = MarkerState(position = it),
                title = "Current Location",
            )
        }
    }
}




@Composable
fun AppScreen(mainValue: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(vertical = 4.dp, horizontal = 8.dp)
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)){
            Text(
                text = "${mainValue}",
                fontSize = 16.sp,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DisplayPreview(){
    MainApp()
}