package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.sos.SosPlan
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonRose
import com.example.ui.theme.SpaceCardElevated
import com.example.ui.theme.TextSecondary

/**
 * Bang vi tri cuu nan: ba dong Vĩ độ / Kinh độ / Địa điểm in THAT MAN, kem nut
 * doc lai, va loi doc tu dong sau 2 giay.
 *
 * Tam goi rieng de ca bang SOS lan hop thu canh bao cung dung mot niem: man
 * hinh cuu nan khong duoc phep co hai kieu trinh bay toa do, va cung khong
 * duoc phep noi "vi tri" ma khong ghi ro la vi do hay kinh do — nguoi cuu nan
 * chi can mo sai mot dong la di lac.
 */
@Composable
fun SosRescuePositionPanel(
    position: SosPlan.Position?,
    modifier: Modifier = Modifier,
    /** Co doc ra khi man hinh con mo khong — tat khi nguoi dung da nghe roi. */
    autoRead: Boolean = true
) {
    // Nhat trong TOAN BO panel, khong phai trong `if`: mo/tat vi tri khong duoc
    // khoi dong lai TTS va doc trung mot cau da nghe.
    val voice = rememberSosVoice()
    var speechIssue by remember { mutableStateOf<String?>(null) }
    val speechText = position?.let { SosPlan.speechText(it) }.orEmpty()

    SosAutoReadPosition(
        voice = voice,
        text = speechText,
        autoRead = autoRead && position != null,
        onStatus = { speechIssue = it }
    )

    Column(modifier = modifier.fillMaxWidth()) {
        if (position == null) {
            SosPositionLine(
                label = "Vị trí cầu cứu",
                value = SosPlan.UNKNOWN_POSITION,
                accent = NeonRose
            )
        } else {
            SosPlan.positionLines(position).forEach { (label, value) ->
                SosPositionLine(label = label, value = value, accent = NeonCyan)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (position.hasFix)
                        "Đọc to sau 2 giây — bấm nút loa để nghe lại."
                    else
                        "Máy chưa định vị được, nên không có toạ độ để đọc.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        // Bam doc lai khi may khong co giong Viet thi van goi
                        // speak(): engine khong co thi khong co gi xay ra, chu
                        // loi da hien o duoi.
                        voice.speak(speechText)
                    },
                    modifier = Modifier.testTag("sos_read_again")
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "Đọc lại vị trí",
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        speechIssue?.let { issue ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = issue,
                style = MaterialTheme.typography.bodySmall,
                color = NeonRose,
                modifier = Modifier.testTag("sos_speech_issue")
            )
        }
    }
}

@Composable
private fun SosPositionLine(label: String, value: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SpaceCardElevated)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("sos_position_line_${label.filter { it.isLetter() }.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.GpsFixed,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

/**
 * Dong trang thai thay cho chuoi thuong mai cu: luc bam 113 thi nguoi dung can
 * biet may dang mo ban quay so, khong can biet app ten gi.
 */
@Composable
fun SosStatusStrip(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NeonRose.copy(alpha = 0.18f))
            .border(1.dp, NeonRose.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("sos_status_strip"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.GpsFixed,
            contentDescription = null,
            tint = NeonRose,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}
