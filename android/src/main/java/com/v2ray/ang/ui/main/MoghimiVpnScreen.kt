package com.v2ray.ang.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.MoghimiCloudSync
import com.v2ray.ang.MoghimiUpdateInfo
import java.util.Locale

data class MoghimiSmartUiState(
    val isWorking: Boolean = false,
    val phase: String = "آماده‌ی اتصال",
    val progress: Float = 0f,
    val serverCount: Int = 0,
    val pingMillis: Long? = null,
    val publicIp: String? = null,
    val countryName: String? = null,
    val countryCode: String? = null,
    val errorMessage: String? = null,
    val internetVerified: Boolean? = null,
    val updateInfo: MoghimiUpdateInfo? = null
)

@Composable
fun MoghimiVpnScreen(
    mainViewModel: MainViewModel,
    smartState: MoghimiSmartUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onOpenUpdate: (String) -> Unit,
    onDismissUpdate: () -> Unit
) {
    val connection by mainViewModel.uiState.collectAsStateWithLifecycle()
    val cloudServerFlow = remember(mainViewModel) {
        mainViewModel.serversForGroup(MoghimiCloudSync.CLOUD_GROUP_ID)
    }
    val servers by cloudServerFlow.collectAsStateWithLifecycle()
    val isTunnelActive = connection.isRunning
    val isBusy = smartState.isWorking || connection.isTesting ||
        (isTunnelActive && smartState.internetVerified == null)
    val ping = smartState.pingMillis
        ?: servers.firstOrNull { it.guid == connection.selectedGuid }
            ?.testDelayMillis?.takeIf { it > 0L }

    Box(modifier = Modifier.fillMaxSize()) {
        MoghimiAtmosphereBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                )
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            HeaderRow(
                isConnected = isTunnelActive,
                isBusy = isBusy,
                internetVerified = smartState.internetVerified,
                onRefresh = onRefresh
            )

            Spacer(Modifier.height(25.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "اینترنت، با حالِ بهتر",
                    color = Color(0xFFF5F4FF),
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "یک لمس تا نزدیک‌ترین مسیرِ مناسب",
                    color = Color(0xFF9DABC9),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(14.dp))
            ConnectionOrb(
                isConnected = isConnected,
                isWorking = isBusy,
                phase = smartState.phase,
                onClick = {
                    if (isConnected) onDisconnect() else if (!isBusy) onConnect()
                }
            )

            AnimatedContent(
                targetState = Triple(isTunnelActive, smartState.internetVerified, smartState.phase),
                label = "connection-caption"
            ) { status ->
                val (tunnelActive, internetOk, phase) = status
                val message = when {
                    tunnelActive && internetOk == true -> "اینترنت از مسیر امن عبور می‌کند"
                    tunnelActive && internetOk == false -> "تونل روشن است؛ اینترنت پاسخ نمی‌دهد"
                    tunnelActive -> "در حال بررسی دسترسی اینترنت"
                    isBusy -> phase
                    else -> "برای شروع، دکمه را لمس کن"
                }
                Text(
                    text = message,
                    color = when {
                        tunnelActive && internetOk == true -> Color(0xFF71E5BE)
                        tunnelActive && internetOk == false -> Color(0xFFFFB36B)
                        else -> Color(0xFFB8C4DF)
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }

            Spacer(Modifier.height(25.dp))
            ConnectionStats(
                pingMillis = ping,
                publicIp = smartState.publicIp,
                countryName = smartState.countryName,
                countryCode = smartState.countryCode,
                isConnected = isConnected
            )

            Spacer(Modifier.height(15.dp))
            ConnectionModeCard(
                isTunnelActive = isTunnelActive,
                internetVerified = smartState.internetVerified,
                isBusy = isBusy,
                serverCount = maxOf(smartState.serverCount, servers.size)
            )

            AnimatedVisibility(visible = !smartState.errorMessage.isNullOrBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    color = Color(0xFF3A2034).copy(alpha = 0.92f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF9D456A))
                ) {
                    Text(
                        text = smartState.errorMessage.orEmpty(),
                        color = Color(0xFFFFD8E4),
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            BottomActionRow(
                isBusy = isBusy,
                onRefresh = onRefresh
            )
            Spacer(Modifier.height(28.dp))
        }
    }

    smartState.updateInfo?.let { update ->
        AlertDialog(
            onDismissRequest = {
                if (!update.mandatory) onDismissUpdate()
            },
            containerColor = Color(0xFF15213D),
            titleContentColor = Color(0xFFF5F4FF),
            textContentColor = Color(0xFFC6D0E7),
            title = {
                Text(
                    text = "نسخهٔ جدید آماده است",
                    fontWeight = FontWeight.Black
                )
            },
            text = {
                Column {
                    Text(
                        text = "نسخهٔ ${update.versionName}",
                        color = Color(0xFFFFD84D),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (update.releaseNotes.isNotBlank()) {
                        Text(
                            text = update.releaseNotes,
                            modifier = Modifier.padding(top = 9.dp),
                            fontSize = 12.sp,
                            lineHeight = 19.sp,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onOpenUpdate(update.downloadUrl) }) {
                    Text("دریافت به‌روزرسانی", color = Color(0xFFFFD84D))
                }
            },
            dismissButton = if (update.mandatory) null else ({
                TextButton(onClick = onDismissUpdate) {
                    Text("بعداً", color = Color(0xFFB8C4DF))
                }
            })
        )
    }
}

@Composable
private fun HeaderRow(
    isConnected: Boolean,
    isBusy: Boolean,
    internetVerified: Boolean?,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = androidx.compose.ui.res.painterResource(
                    id = com.v2ray.ang.R.drawable.moghimi_vpn_header
                ),
                contentDescription = "Moghimi VPN",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(42.dp)
                    .shadow(12.dp, RoundedCornerShape(14.dp), ambientColor = Color(0xFFA98BFF))
                    .clip(RoundedCornerShape(14.dp))
            )
            Column(Modifier.padding(start = 11.dp)) {
                Text(
                    "MOGHIMI",
                    color = Color(0xFFF5F4FF),
                    fontSize = 16.sp,
                    letterSpacing = 2.2.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "V P N",
                    color = Color(0xFF9DABC9),
                    fontSize = 9.sp,
                    letterSpacing = 3.2.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0xFF182643).copy(alpha = 0.92f))
                .border(1.dp, Color(0xFF3C4A6C), CircleShape)
                .padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isConnected && internetVerified == true -> Color(0xFF69E2B9)
                            isConnected && internetVerified == false -> Color(0xFFFFA45F)
                            isConnected -> Color(0xFFFFD84D)
                            isBusy -> Color(0xFFFFD84D)
                            else -> Color(0xFF8190B1)
                        }
                    )
            )
            Text(
                text = when {
                    isConnected && internetVerified == true -> "اتصال فعال"
                    isConnected && internetVerified == false -> "نیاز به بررسی"
                    isConnected -> "در حال بررسی"
                    isBusy -> "در حال آماده‌سازی"
                    else -> "آماده"
                },
                color = Color(0xFFDCE4FA),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 7.dp)
            )
        }
    }
}

@Composable
private fun MoghimiAtmosphereBackground() {
    val infinite = rememberInfiniteTransition(label = "ambient-motion")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(11_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient-drift"
    )
    val orbit by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(34_000, easing = FastOutSlowInEasing)),
        label = "ambient-orbit"
    )

    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF070D1B), Color(0xFF101A34), Color(0xFF0A1226)),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            )
        )
        val top = Offset(size.width * (0.14f + drift * 0.08f), size.height * 0.22f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF7856CE).copy(alpha = 0.19f), Color.Transparent),
                center = top,
                radius = size.minDimension * 0.62f
            ),
            radius = size.minDimension * 0.62f,
            center = top
        )
        val lower = Offset(size.width * (0.88f - drift * 0.08f), size.height * 0.70f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF16A98C).copy(alpha = 0.10f), Color.Transparent),
                center = lower,
                radius = size.minDimension * 0.60f
            ),
            radius = size.minDimension * 0.60f,
            center = lower
        )
        rotate(orbit, pivot = Offset(size.width / 2f, size.height * 0.43f)) {
            drawCircle(
                color = Color(0xFFB89BFF).copy(alpha = 0.055f),
                radius = size.minDimension * 0.47f,
                center = Offset(size.width / 2f, size.height * 0.43f),
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = Color(0xFFFFD84D).copy(alpha = 0.05f),
                radius = size.minDimension * 0.53f,
                center = Offset(size.width / 2f, size.height * 0.43f),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

@Composable
private fun ConnectionOrb(
    isConnected: Boolean,
    isWorking: Boolean,
    phase: String,
    onClick: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "connection-orb")
    val pulse by infinite.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isConnected) 1_450 else 2_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "connection-pulse"
    )
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isWorking) 2_300 else 14_000, easing = FastOutSlowInEasing)
        ),
        label = "connection-rotation"
    )
    val glow = if (isConnected) Color(0xFF54E4BC) else Color(0xFF9B7BFF)
    val centerBrush = if (isConnected) {
        Brush.linearGradient(listOf(Color(0xFF1C9B7D), Color(0xFF183E4E), Color(0xFF182A4B)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF453477), Color(0xFF222D56), Color(0xFF152642)))
    }

    Box(
        modifier = Modifier
            .size(264.dp)
            .clickable(enabled = !isWorking || isConnected, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val base = size.minDimension * 0.34f
            drawCircle(
                color = glow.copy(alpha = 0.045f),
                radius = base * 1.72f * pulse,
                center = c
            )
            drawCircle(
                color = glow.copy(alpha = 0.075f),
                radius = base * 1.42f * pulse,
                center = c,
                style = Stroke(width = 1.2.dp.toPx())
            )
            drawCircle(
                color = Color(0xFFB89BFF).copy(alpha = 0.14f),
                radius = base * 1.24f,
                center = c,
                style = Stroke(width = 1.dp.toPx())
            )
            rotate(rotation, pivot = c) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(Color.Transparent, glow.copy(alpha = 0.08f), glow, Color.Transparent),
                        center = c
                    ),
                    startAngle = 0f,
                    sweepAngle = 258f,
                    useCenter = false,
                    topLeft = Offset(c.x - base * 1.33f, c.y - base * 1.33f),
                    size = Size(base * 2.66f, base * 2.66f),
                    style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = Color(0xFFFFD84D).copy(alpha = 0.60f),
                    startAngle = 190f,
                    sweepAngle = 42f,
                    useCenter = false,
                    topLeft = Offset(c.x - base * 1.44f, c.y - base * 1.44f),
                    size = Size(base * 2.88f, base * 2.88f),
                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(184.dp)
                .shadow(
                    elevation = 28.dp,
                    shape = CircleShape,
                    ambientColor = glow.copy(alpha = 0.38f),
                    spotColor = glow.copy(alpha = 0.34f)
                )
                .clip(CircleShape)
                .background(centerBrush)
                .border(1.5.dp, glow.copy(alpha = 0.58f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.045f),
                    radius = size.minDimension * 0.44f,
                    center = Offset(size.width * 0.34f, size.height * 0.25f)
                )
                drawCircle(
                    color = glow.copy(alpha = 0.10f),
                    radius = size.minDimension * 0.40f,
                    center = Offset(size.width * 0.74f, size.height * 0.78f)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(48.dp)) {
                    val stroke = 4.dp.toPx()
                    drawArc(
                        color = Color(0xFFFFD84D),
                        startAngle = -48f,
                        sweepAngle = 276f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawLine(
                        color = Color(0xFFFFD84D),
                        start = Offset(size.width / 2f, 1.dp.toPx()),
                        end = Offset(size.width / 2f, size.height * 0.55f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
                Spacer(Modifier.height(9.dp))
                AnimatedContent(targetState = isConnected, label = "orb-label") { connected ->
                    Text(
                        text = when {
                            connected -> "متصل"
                            isWorking -> "در حال اتصال"
                            else -> "اتصال"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                }
                Text(
                    text = if (isConnected) "TAP TO DISCONNECT" else "SMART CONNECT",
                    color = Color(0xFFC6D0E7),
                    fontSize = 8.sp,
                    letterSpacing = 1.65.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }

        if (isWorking && !isConnected) {
            Text(
                text = "✦",
                color = Color(0xFFFFD84D),
                fontSize = 20.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 28.dp, end = 32.dp)
            )
        }
    }
}

@Composable
private fun ConnectionStats(
    pingMillis: Long?,
    publicIp: String?,
    countryName: String?,
    countryCode: String?,
    isConnected: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(21.dp),
        color = Color(0xFF141F3A).copy(alpha = 0.90f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF354363).copy(alpha = 0.72f))
    ) {
        Row(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatCell(
                title = "تأخیر",
                value = pingMillis?.let { it.toString() + " ms" } ?: "—",
                accent = Color(0xFF71E5BE),
                modifier = Modifier.weight(1f)
            )
            StatDivider()
            StatCell(
                title = "IP خروجی",
                value = publicIp ?: if (isConnected) "در حال بررسی" else "—",
                accent = Color(0xFFFFD84D),
                compact = true,
                modifier = Modifier.weight(1.25f)
            )
            StatDivider()
            StatCell(
                title = "موقعیت",
                value = countryName ?: if (isConnected) "در حال شناسایی" else "—",
                accent = Color(0xFFB9A0FF),
                modifier = Modifier.weight(1f),
                prefix = countryCode?.let(::flagEmoji)
            )
        }
    }
}

@Composable
private fun StatCell(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    prefix: String? = null
) {
    Column(
        modifier = modifier.padding(horizontal = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            color = Color(0xFF91A0BE),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier.padding(top = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (!prefix.isNullOrBlank()) {
                Text(prefix, fontSize = 12.sp, modifier = Modifier.padding(end = 3.dp))
            }
            Text(
                value,
                color = accent,
                fontSize = if (compact) 10.sp else 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(38.dp)
            .background(Color(0xFF374665))
    )
}

@Composable
private fun ConnectionModeCard(
    isTunnelActive: Boolean,
    internetVerified: Boolean?,
    isBusy: Boolean,
    serverCount: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF141F3A).copy(alpha = 0.84f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF344260))
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(43.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF302A58)),
                contentAlignment = Alignment.Center
            ) {
                Text("✦", color = Color(0xFFFFD84D), fontSize = 23.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = "اتصال هوشمند",
                    color = Color(0xFFF0F2FF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = when {
                        isTunnelActive && internetVerified == true -> "مسیر مناسب پیدا شده و اینترنت در دسترس است"
                        isTunnelActive && internetVerified == false -> "در حال نیاز به بررسی مسیر اتصال"
                        isTunnelActive -> "در حال بررسی دسترسی اینترنت"
                        isBusy -> "در حال پیدا کردن بهترین مسیر"
                        else -> "بهترین مسیر به‌صورت خودکار انتخاب می‌شود"
                    },
                    color = Color(0xFF91A0BE),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Text(
                text = if (serverCount > 0) "$serverCount مسیر" else "خودکار",
                color = Color(0xFFFFD84D),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BottomActionRow(
    isBusy: Boolean,
    onRefresh: () -> Unit
) {
    ActionTile(
        title = "به‌روزرسانی مسیرها",
        subtitle = "دریافت آخرین مسیرهای قابل استفاده",
        symbol = "↻",
        modifier = Modifier.fillMaxWidth(),
        enabled = !isBusy,
        onClick = onRefresh
    )
}

@Composable
private fun ActionTile(
    title: String,
    subtitle: String,
    symbol: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(17.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = Color(0xFF141F3A).copy(alpha = 0.78f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334260))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                symbol,
                color = Color(0xFFFFD84D),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Column(Modifier.padding(start = 9.dp)) {
                Text(
                    title,
                    color = Color(0xFFF0F2FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    subtitle,
                    color = Color(0xFF91A0BE),
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

private fun flagEmoji(countryCode: String): String {
    val normalized = countryCode.uppercase(Locale.ROOT).take(2)
    if (normalized.length != 2 || normalized.any { it !in 'A'..'Z' }) return "🌐"
    val points = normalized.map { 0x1F1E6 + (it.code - 'A'.code) }
    return String(Character.toChars(points[0])) + String(Character.toChars(points[1]))
}
