package com.jadennam.glucose.ui.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jadennam.glucose.ui.theme.GlucoseTheme

/** Required by Health Connect: explains how health permissions are used (privacy policy). */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlucoseTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("건강 데이터 사용 안내", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("혈당 관리 앱은 Health Connect에서 다음 데이터를 읽기만 합니다: 걸음 수, 운동 기록, 수면, 체중.")
                        Text("• 목적: 혈당 추세 화면에서 그날의 활동·수면·체중을 함께 보여주기 위해서입니다.")
                        Text("• 저장: 읽은 데이터는 앱 데이터베이스에 복사하지 않고, 화면에 표시할 때만 사용합니다.")
                        Text("• 전송: 어떤 서버로도 보내지 않습니다. 카카오톡 메시지에도 포함하지 않습니다.")
                        Text("• 쓰기: Health Connect에 데이터를 쓰지 않습니다.")
                        Text("• 해제: Health Connect 설정에서 언제든 권한을 끌 수 있습니다.")
                        TextButton(onClick = { finish() }) { Text("닫기") }
                    }
                }
            }
        }
    }
}
