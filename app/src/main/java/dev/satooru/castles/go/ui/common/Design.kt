package dev.satooru.castles.go.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 浮いて見える影（`floating`）つきの面． */
fun Modifier.floating(shape: androidx.compose.ui.graphics.Shape): Modifier =
    shadow(6.dp, shape, ambientColor = Color(0x26000000), spotColor = Color(0x33000000))
        .background(Color.White, shape)

/** 角を丸めた影つきの白いカード．`title` があれば小さな見出しを出す． */
@Composable
fun AppCard(modifier: Modifier = Modifier, title: String? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.fillMaxWidth().floating(shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

/** 40dp の正方形に近い，影つきのアイコンボタン． */
@Composable
fun AppIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier.size(40.dp).floating(shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * 左端に貼り付く，距離つきの城名のチップ．高さは 1 行分（27dp），右側のみ角を丸める．
 */
@Composable
fun CastleDistanceChip(name: String, distance: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)
    Row(
        modifier.height(27.dp).floating(shape).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(distance, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 黒地の主ボタン（角 10dp）． */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, outlined: Boolean = false) {
    val shape = RoundedCornerShape(10.dp)
    val base = modifier.height(40.dp).let {
        if (outlined) it.background(Color.White, shape).then(Modifier) else it.background(Color.Black, shape)
    }
    Box(
        base.clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (outlined) Color.Black else Color.White)
    }
}
