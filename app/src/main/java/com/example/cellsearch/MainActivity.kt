package com.example.cellsearch

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
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
        // デバッグ用に常時画面オンを入れておく
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

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

// メインレイアウト部
@Composable
fun MainApp(){
    var currentTime by remember { mutableStateOf(getCurrentTime()) }
    var cellInfoList by remember { mutableStateOf<List<ICell>>(emptyList())}
    var cellInfoHeader by remember { mutableStateOf<String?>(null) }
    val pingHost: String = "1.1.1.1"

    cellInfoList = GetCellInfo()
    LaunchedEffect(true) {
        while(true) {
            delay(1000)
            currentTime = getCurrentTime()
            println("${currentTime}")
            val pingResult = performPing(pingHost)
        }
    }
    cellInfoHeader = getCellInfoHeader(cellInfoList)
    Column {
        MaterialTheme(
            typography = Typography(
                bodyMedium = TextStyle(
                    fontFamily = FontFamily(Font(resId = R.font.roboto_mono_medium)),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )
            )
        ) {
            AppScreen(currentTime)
            AppScreen(cellInfoHeader!!)
            CellTableScreen(cellInfoList)
        }
        //LocationUpdates()
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

// なんとなく時計を表示しておく。画面の更新がうまくいっているか確認用。
fun getCurrentTime(): String {
    val dateFormat = SimpleDateFormat("YYYY/MM/dd HH:mm:ss", Locale.getDefault())
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



// テキストを表示するだけ
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
                fontSize = 24.sp,
                //fontFamily = FontFamily(Font(resId = R.font.roboto_mono_medium)),
            )
        }
    }
}

// セル情報取得
@Composable
fun GetCellInfo(): List<ICell>{
    val context = LocalContext.current
    return NetMonsterFactory.getTelephony(context).getAllCellInfo()
}

// セル情報ヘッダ(ECI, TAのみ)
fun getCellInfoHeader(allCellInfo: List<ICell>): String{
    var ci: Long? = null
    var enb: Long? = null
    var lcid: Long? = null
    var ta: Int? = null
    var headerString: String = ""

    for (cellInfo in allCellInfo) {
        if (cellInfo is CellLte) {
            ci = cellInfo.eci?.toLong()
            enb = ci?.div(256)
            lcid = ci?.rem(256)
            ta = cellInfo.signal.timingAdvance
            if (ci != null) {
                break
            }
        }
        if (cellInfo is CellNr) {
            ci = cellInfo.nci
            enb = ci?.div(4096)
            lcid = ci?.rem(4096)
            ta = cellInfo.signal.timingAdvance
            if (ci != null) {
                break
            }
        }
    }
    headerString = if (ta != null) {
        "CI:${enb}-${lcid} TA:${ta}"
    } else {
        "CI:${enb}-${lcid} TA:-"
    }
    return headerString
}

// セル情報テーブル
@Composable
fun CellTableScreen(allCellInfo: List<ICell>){

    var ci: Long? = null
    var pci: Int? = null
    var enb: Long? = null
    var lcid: Long? = null
    var arfcn: Int? = null
    var band: Int? = null
    var bandwidth: Int? = null
    var freq: Int? = null
    var ta: Int? = null
    var rsrp: Double? = null
    var rsrq: Double? = null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.background(Color.White),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TableCell(text = "Band", weight= .2f)
            TableCell(text = "ARFCN", weight= .2f)
            TableCell(text = "PCI", weight= .2f)
            TableCell(text = "RSRP", weight= .2f)
            TableCell(text = "RSRQ", weight= .2f)
        }
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
                rsrp = cellInfo.signal.rsrp
                rsrq = cellInfo.signal.rsrq
            }
            if (cellInfo is CellNr) {
                ci = cellInfo.nci
                pci = cellInfo.pci
                enb = ci?.div(4096)
                lcid = ci?.rem(4096)
                arfcn = cellInfo.band?.downlinkArfcn
                freq = cellInfo.band?.downlinkFrequency
                band = cellInfo.band?.number
                ta = cellInfo.signal.timingAdvance
                rsrp = cellInfo.signal.ssRsrp?.toDouble()
                rsrq = cellInfo.signal.ssRsrq?.toDouble()
            }
            Row (
                modifier = Modifier.background(Color.White),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableCell(text = "${band}", weight = .2f)
                TableCell(text = "${arfcn}", weight = .2f)
                TableCell(text = "${pci}", weight = .2f)
                TableCell(text = "${rsrp}", weight = .2f)
                TableCell(text = "${rsrq}", weight = .2f)
            }
        }
    }
}

// テーブル用のセル定義
@Composable
fun RowScope.TableCell(
    text: String,
    weight: Float,
) {
    Text(
        text = text,
        Modifier
            .weight(weight)
            .padding(4.dp),
        style = MaterialTheme.typography.bodyMedium
    )
}

@Preview(showBackground = true)
@Composable
fun DisplayPreview(){
    MainApp()
}