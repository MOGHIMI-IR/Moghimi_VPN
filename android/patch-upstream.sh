#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
UPSTREAM="$ROOT/android/upstream"
PROJECT="$UPSTREAM/V2rayNG"
APP="$PROJECT/app"
JAVA="$APP/src/main/java/com/v2ray/ang"
RES="$APP/src/main/res"

if [[ ! -f "$APP/build.gradle.kts" || ! -f "$JAVA/ui/main/MainActivity.kt" ]]; then
  echo "Pinned Android engine source is missing."
  exit 1
fi

mkdir -p "$JAVA/ui/main"
cp "$ROOT/android/src/main/java/com/v2ray/ang/MoghimiCloudSync.kt" "$JAVA/MoghimiCloudSync.kt"
cp "$ROOT/android/src/main/java/com/v2ray/ang/MoghimiSyncWorker.kt" "$JAVA/MoghimiSyncWorker.kt"
cp "$ROOT/android/src/main/java/com/v2ray/ang/MoghimiNetworkIdentity.kt" "$JAVA/MoghimiNetworkIdentity.kt"
cp "$ROOT/android/src/main/java/com/v2ray/ang/MoghimiUpdateChecker.kt" "$JAVA/MoghimiUpdateChecker.kt"
cp "$ROOT/android/src/main/java/com/v2ray/ang/ui/main/MoghimiVpnScreen.kt" "$JAVA/ui/main/MoghimiVpnScreen.kt"

python3 - "$PROJECT" <<'PY'
from pathlib import Path
import re
import shutil
import sys

project = Path(sys.argv[1])
app = project / "app"
java = app / "src/main/java/com/v2ray/ang"
res = app / "src/main/res"

def replace_once(path: Path, old: str, new: str, label: str):
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match for {label} in {path}; found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")

# New standalone package identity; keep the upstream Kotlin namespace for internal components.
gradle = app / "build.gradle.kts"
source = gradle.read_text(encoding="utf-8")
for old, new, label in [
    ('applicationId = "com.v2ray.ang"', 'applicationId = "ir.moghimi.vpn"', "applicationId"),
    ("versionCode = 755", "versionCode = 6", "versionCode"),
    ('versionName = "2.3.15"', 'versionName = "1.0.5"', "versionName"),
]:
    if source.count(old) != 1:
        raise SystemExit(f"Expected one Gradle match for {label}")
    source = source.replace(old, new, 1)
source = source.replace('outputFileName = "v2rayNG_', 'outputFileName = "MoghimiVPN_')
if 'ndkVersion = "30.0.16248370"' not in source:
    marker = "compileSdk = 37"
    if source.count(marker) != 1:
        raise SystemExit("Could not locate compileSdk")
    source = source.replace(marker, marker + '\n    ndkVersion = "30.0.16248370"', 1)
gradle.write_text(source, encoding="utf-8")

# Deep navy theme with yellow, violet and aqua accents; disable wallpaper-driven dynamic colors.
theme_file = java / "ui/compose/Theme.kt"
theme = theme_file.read_text(encoding="utf-8")
start = theme.index("private val DarkColor = darkColorScheme(")
marker = "\n)\n\n// Semantic Colors"
end = theme.index(marker, start) + 2
dark_scheme = """private val DarkColor = darkColorScheme(
    primary = Color(0xFFFFD84D),
    onPrimary = Color(0xFF111827),
    primaryContainer = Color(0xFF51431C),
    onPrimaryContainer = Color(0xFFFFEBA0),
    secondary = Color(0xFFA98BFF),
    onSecondary = Color(0xFF24164B),
    secondaryContainer = Color(0xFF392B61),
    onSecondaryContainer = Color(0xFFE8DEFF),
    tertiary = Color(0xFF63DDB6),
    onTertiary = Color(0xFF073A2C),
    tertiaryContainer = Color(0xFF155342),
    onTertiaryContainer = Color(0xFFB6F5DF),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0B1226),
    onBackground = Color(0xFFF5F6FF),
    surface = Color(0xFF111C35),
    onSurface = Color(0xFFF5F6FF),
    surfaceVariant = Color(0xFF243453),
    onSurfaceVariant = Color(0xFFBAC6DF),
    outline = Color(0xFF7180A1),
    outlineVariant = Color(0xFF334363),
    inverseSurface = Color(0xFFE6E9F2),
    inverseOnSurface = Color(0xFF111827),
    inversePrimary = Color(0xFF66551C),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFFFFD84D),
    surfaceContainerLowest = Color(0xFF080E1D),
    surfaceContainerLow = Color(0xFF0D1730),
    surfaceContainer = Color(0xFF141F39),
    surfaceContainerHigh = Color(0xFF1A2948),
    surfaceContainerHighest = Color(0xFF243453),
)"""
theme = theme[:start] + dark_scheme + theme[end:]
theme = theme.replace(
    'MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"',
    'MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "2") ?: "2"'
)
theme = theme.replace(
    "MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)",
    "MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)"
)
theme = theme.replace("val colorPing = Color(0xFF009966)", "val colorPing = Color(0xFF4CE0B2)")
theme = theme.replace("val colorPingRed = Color(0xFFFF0099)", "val colorPingRed = Color(0xFFFF5578)")
theme = theme.replace("val colorConfigType = Color(0xFFf97910)", "val colorConfigType = Color(0xFFA98BFF)")
theme = theme.replace("val colorFabActive = Color(0xFFf97910)", "val colorFabActive = Color(0xFFFFD84D)")
theme = theme.replace("val dividerColorDark = Color(0xFF424242)", "val dividerColorDark = Color(0xFF334363)")
theme_file.write_text(theme, encoding="utf-8")

# Replace visible app/notification names. Internal package and protocol implementation stay private.
for values_file in [res / "values/strings.xml", res / "values-fa/strings.xml"]:
    text = values_file.read_text(encoding="utf-8")
    text, app_count = re.subn(
        r'(<string name="app_name"[^>]*>).*?(</string>)',
        r'\1Moghimi VPN\2',
        text,
        count=1
    )
    if app_count != 1:
        raise SystemExit(f"Could not brand app name in {values_file}")
    text = re.sub(
        r'(<string name="title_server"[^>]*>).*?(</string>)',
        r'\1Moghimi VPN\2',
        text,
        count=1
    )
    text = re.sub(
        r'(<string name="notification_channel_service"[^>]*>).*?(</string>)',
        r'\1Moghimi VPN\2',
        text,
        count=1
    )
    replacements = {
        "notification_action_stop_v2ray": "قطع اتصال" if values_file.parent.name == "values-fa" else "Disconnect",
        "title_service_restart": "راه‌اندازی دوباره" if values_file.parent.name == "values-fa" else "Restart VPN",
        "notification_channel_subscription_updates": "به‌روزرسانی مسیرها" if values_file.parent.name == "values-fa" else "Route updates",
        "notification_channel_connection_checks": "بررسی اتصال" if values_file.parent.name == "values-fa" else "Connection checks",
    }
    for name, value in replacements.items():
        text = re.sub(
            rf'(<string name="{name}"[^>]*>).*?(</string>)',
            lambda match, value=value: match.group(1) + value + match.group(2),
            text,
            count=1
        )
    if values_file.parent.name == "values-fa":
        text = text.replace("سرویس v2rayNG", "سرویس Moghimi VPN")
    values_file.write_text(text, encoding="utf-8")

# The custom dashboard is the only normal app screen. The native service lifecycle remains upstream.
activity_file = java / "ui/main/MainActivity.kt"
activity = activity_file.read_text(encoding="utf-8")
activity = activity.replace(
    "import androidx.lifecycle.lifecycleScope\n",
    """import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.v2ray.ang.MoghimiCloudSync
import com.v2ray.ang.MoghimiNetworkIdentityLookup
import com.v2ray.ang.MoghimiSyncWorker
import com.v2ray.ang.MoghimiUpdateChecker
import com.v2ray.ang.dto.entities.ServersCache
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.TimeUnit
""",
    1
)
activity = activity.replace(
    "class MainActivity : HelperBaseComponentActivity() {\n",
    """class MainActivity : HelperBaseComponentActivity() {

    private val smartUiState = MutableStateFlow(MoghimiSmartUiState())
    private val cloudSyncMutex = Mutex()
    private var smartConnectJob: Job? = null
    private var cloudSyncJob: Job? = null
    private var smartAttemptJob: Job? = null
    private var identityLookupJob: Job? = null
    private var internetCheckJob: Job? = null
    private var updateCheckJob: Job? = null
    private var dismissedUpdateVersion: String? = null
    private var smartCandidateGuids: List<String> = emptyList()
    private var smartCandidatePings: Map<String, Long> = emptyMap()
    private var smartPingIsReal: Boolean = true
    private var wasVpnRunning = false
""",
    1
)
activity = activity.replace(
    "if (it.resultCode == RESULT_OK) startV2Ray()",
    """if (it.resultCode == RESULT_OK) {
                startV2Ray()
            } else {
                smartUiState.update {
                    it.copy(
                        isWorking = false,
                        phase = "آماده‌ی اتصال",
                        errorMessage = "مجوز اتصال VPN تأیید نشد."
                    )
                }
                smartCandidateGuids = emptyList()
            }""",
    1
)

old_on_create = """        mainViewModel.onAction(MainAction.Initialize)

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}"""
new_on_create = """        mainViewModel.onAction(MainAction.Initialize)
        scheduleMoghimiBackgroundSync()
        observeVpnState()
        syncMoghimiCloud(silent = true)
        checkForMoghimiUpdates()

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}"""
if activity.count(old_on_create) != 1:
    raise SystemExit("Could not locate MainActivity initialization")
activity = activity.replace(old_on_create, new_on_create, 1)

screen_start = activity.index("    @Composable\n    override fun ScreenContent() {")
screen_end = activity.index("\n    private fun shareToClipboard", screen_start)
new_screen = """    @Composable
    override fun ScreenContent() {
        BackHandler { moveTaskToBack(false) }
        val smartState by smartUiState.collectAsStateWithLifecycle()
        MoghimiVpnScreen(
            mainViewModel = mainViewModel,
            smartState = smartState,
            onConnect = { startSmartConnection() },
            onDisconnect = { disconnectMoghimiVpn() },
            onRefresh = { syncMoghimiCloud(silent = false) },
            onOpenUpdate = { url ->
                runCatching {
                    startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(url)
                        )
                    )
                }.onFailure {
                    smartUiState.update {
                        it.copy(errorMessage = "باز کردن لینک دریافت نسخهٔ جدید انجام نشد.")
                    }
                }
            },
            onDismissUpdate = {
                dismissedUpdateVersion = smartUiState.value.updateInfo?.versionName
                smartUiState.update { it.copy(updateInfo = null) }
            }
        )
    }
"""
activity = activity[:screen_start] + new_screen + activity[screen_end:]

insertion = "    private fun shareToClipboard(guid: String): Boolean ="
methods = """    private fun scheduleMoghimiBackgroundSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<MoghimiSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "moghimi-server-refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private suspend fun refreshCloudGroup(): List<ServersCache> {
        var syncFailure: Exception? = null
        try {
            withContext(Dispatchers.IO) {
                cloudSyncMutex.withLock {
                    MoghimiCloudSync.sync()
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Keep using the last known-good local routes if the cloud is temporarily unreachable.
            syncFailure = error
        }

        mainViewModel.setupGroupTab(forceRefresh = true).join()
        if (mainViewModel.uiState.value.groups.none { it.id == MoghimiCloudSync.CLOUD_GROUP_ID }) {
            throw syncFailure ?: IOException("هنوز مسیری برای اتصال دریافت نشده است.")
        }

        mainViewModel.subscriptionIdChanged(MoghimiCloudSync.CLOUD_GROUP_ID)
        val serverFlow = mainViewModel.serversForGroup(MoghimiCloudSync.CLOUD_GROUP_ID)
        val loaded = withTimeoutOrNull(12_000L) {
            serverFlow.first { it.isNotEmpty() }
        }
        val servers = loaded ?: serverFlow.value
        if (servers.isEmpty()) {
            throw syncFailure ?: IOException("هیچ مسیر قابل استفاده‌ای پیدا نشد.")
        }
        return servers
    }

    private fun syncMoghimiCloud(silent: Boolean) {
        if (cloudSyncJob?.isActive == true) return
        cloudSyncJob = lifecycleScope.launch {
            try {
                val servers = refreshCloudGroup()
                smartUiState.update {
                    it.copy(serverCount = servers.size, errorMessage = null)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!silent) {
                    smartUiState.update {
                        it.copy(
                            errorMessage = error.localizedMessage
                                ?: "دریافت مسیرها انجام نشد؛ اتصال ذخیره‌شده حفظ شده است."
                        )
                    }
                }
            }
        }
    }

    private fun checkForMoghimiUpdates() {
        if (updateCheckJob?.isActive == true) return
        updateCheckJob = lifecycleScope.launch {
            val update = withContext(Dispatchers.IO) {
                MoghimiUpdateChecker.check()
            }
            if (update != null && update.versionName != dismissedUpdateVersion) {
                smartUiState.update { it.copy(updateInfo = update) }
            }
        }
    }

    private fun startSmartConnection() {
        if (mainViewModel.uiState.value.isRunning || smartConnectJob?.isActive == true) return
        smartConnectJob = lifecycleScope.launch {
            smartUiState.update {
                it.copy(
                    isWorking = true,
                    phase = "در حال آماده‌سازی اتصال",
                    progress = 0.08f,
                    errorMessage = null,
                    publicIp = null,
                    countryName = null,
                    countryCode = null,
                    pingMillis = null,
                    internetVerified = null
                )
            }
            try {
                // Finish any startup sync first; connection can still fall back to cached routes.
                cloudSyncJob?.join()
                val servers = refreshCloudGroup()
                smartUiState.update {
                    it.copy(
                        isWorking = true,
                        phase = "در حال سنجش کیفیت مسیرها",
                        progress = 0.30f,
                        serverCount = servers.size
                    )
                }

                mainViewModel.cancelAllPing()
                mainViewModel.testAllRealPing()
                val realTestFinished = withTimeoutOrNull(25_000L) {
                    mainViewModel.uiState.first { !it.isTesting }
                    true
                } == true
                if (!realTestFinished) mainViewModel.cancelAllPing()

                val serverFlow = mainViewModel.serversForGroup(MoghimiCloudSync.CLOUD_GROUP_ID)
                var measured = serverFlow.value
                    .filter { it.testDelayMillis > 0L }
                    .sortedBy { it.testDelayMillis }
                var hasRealPing = realTestFinished && measured.isNotEmpty()

                if (measured.isEmpty()) {
                    smartUiState.update {
                        it.copy(
                            phase = "در حال بررسی دسترسی مسیرها",
                            progress = 0.55f
                        )
                    }
                    mainViewModel.testAllRealPing(onlyTcp = true)
                    val tcpTestFinished = withTimeoutOrNull(12_000L) {
                        mainViewModel.uiState.first { !it.isTesting }
                        true
                    } == true
                    if (!tcpTestFinished) mainViewModel.cancelAllPing()
                    measured = serverFlow.value
                        .filter { it.testDelayMillis > 0L }
                        .sortedBy { it.testDelayMillis }
                    hasRealPing = false
                }

                // Do not discard candidates just because a ping endpoint rejected the test.
                val measuredIds = measured.mapTo(HashSet()) { it.guid }
                val unmeasured = serverFlow.value.filter { it.guid !in measuredIds }
                val ordered = measured + unmeasured
                if (ordered.isEmpty()) {
                    throw IOException("هیچ مسیر قابل استفاده‌ای پیدا نشد. بعداً دوباره امتحان کن.")
                }

                smartCandidateGuids = ordered.map { it.guid }
                smartCandidatePings = ordered.associate { it.guid to it.testDelayMillis }
                smartPingIsReal = hasRealPing
                mainViewModel.updateSelectedGuid(smartCandidateGuids.first())
                smartUiState.update {
                    it.copy(
                        isWorking = true,
                        phase = "بهترین مسیر پیدا شد؛ در حال اتصال",
                        progress = 0.82f,
                        pingMillis = smartCandidatePings[smartCandidateGuids.first()]
                            .takeIf { smartPingIsReal && it > 0L },
                        serverCount = servers.size,
                        errorMessage = null
                    )
                }
                delay(250L)
                requestServiceStart()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                smartCandidateGuids = emptyList()
                mainViewModel.cancelAllPing()
                smartUiState.update {
                    it.copy(
                        isWorking = false,
                        phase = "آماده‌ی اتصال",
                        progress = 0f,
                        errorMessage = error.localizedMessage ?: "اتصال هوشمند انجام نشد."
                    )
                }
            }
        }
    }

    private fun monitorSmartConnection(startIndex: Int) {
        smartAttemptJob?.cancel()
        smartAttemptJob = lifecycleScope.launch {
            var index = startIndex
            while (index < smartCandidateGuids.size) {
                val started = withTimeoutOrNull(15_000L) {
                    mainViewModel.uiState.first { it.isRunning }
                } != null

                if (started && mainViewModel.uiState.value.isRunning) {
                    delay(700L)
                    val internetOk = withContext(Dispatchers.IO) {
                        MoghimiNetworkIdentityLookup.verifyInternetThroughTunnel()
                    }
                    if (internetOk && mainViewModel.uiState.value.isRunning) {
                        val guid = smartCandidateGuids[index]
                        smartUiState.update {
                            it.copy(
                                isWorking = false,
                                phase = "اینترنت آماده است",
                                progress = 1f,
                                pingMillis = smartCandidatePings[guid]
                                    .takeIf { smartPingIsReal && it > 0L },
                                internetVerified = true,
                                errorMessage = null
                            )
                        }
                        smartCandidateGuids = emptyList()
                        return@launch
                    }
                }

                LauncherManager.stopService(this@MainActivity)
                withTimeoutOrNull(5_000L) {
                    mainViewModel.uiState.first { !it.isRunning }
                }
                index += 1
                if (index < smartCandidateGuids.size) {
                    val nextGuid = smartCandidateGuids[index]
                    smartUiState.update {
                        it.copy(
                            isWorking = true,
                            phase = "مسیر اول پاسخ نداد؛ مسیر بعدی در حال آزمایش است",
                            progress = (index.toFloat() / smartCandidateGuids.size.toFloat())
                                .coerceIn(0.1f, 0.95f),
                            pingMillis = smartCandidatePings[nextGuid]
                                .takeIf { smartPingIsReal && it > 0L },
                            internetVerified = null
                        )
                    }
                    mainViewModel.updateSelectedGuid(nextGuid)
                    delay(400L)
                    LauncherManager.startService(this@MainActivity)
                }
            }

            LauncherManager.stopService(this@MainActivity)
            smartCandidateGuids = emptyList()
            smartUiState.update {
                it.copy(
                    isWorking = false,
                    phase = "دسترسی اینترنت برقرار نشد",
                    internetVerified = false,
                    errorMessage = "سرویس اتصال روشن شد، اما عبور اینترنت تأیید نشد. مسیرهای دیگر هم بررسی شدند."
                )
            }
        }
    }

    private fun disconnectMoghimiVpn() {
        smartConnectJob?.cancel()
        smartAttemptJob?.cancel()
        identityLookupJob?.cancel()
        internetCheckJob?.cancel()
        mainViewModel.cancelAllPing()
        smartCandidateGuids = emptyList()
        LauncherManager.stopService(this)
        smartUiState.update {
            it.copy(
                isWorking = false,
                phase = "اتصال قطع شد",
                publicIp = null,
                countryName = null,
                countryCode = null,
                internetVerified = null,
                errorMessage = null
            )
        }
    }

    private fun observeVpnState() {
        lifecycleScope.launch {
            mainViewModel.uiState.collect { state ->
                if (state.isRunning && !wasVpnRunning) {
                    val smartAttemptInProgress = smartCandidateGuids.isNotEmpty()
                    smartUiState.update {
                        it.copy(
                            isWorking = smartAttemptInProgress,
                            phase = "در حال بررسی دسترسی اینترنت",
                            internetVerified = null,
                            errorMessage = null
                        )
                    }

                    if (!smartAttemptInProgress) {
                        internetCheckJob?.cancel()
                        internetCheckJob = lifecycleScope.launch {
                            val reachable = withContext(Dispatchers.IO) {
                                MoghimiNetworkIdentityLookup.verifyInternetThroughTunnel()
                            }
                            if (mainViewModel.uiState.value.isRunning) {
                                smartUiState.update {
                                    it.copy(
                                        isWorking = false,
                                        phase = if (reachable) "اینترنت آماده است" else "اتصال روشن است؛ اینترنت پاسخ نداد",
                                        internetVerified = reachable,
                                        errorMessage = if (reachable) null
                                            else "تونل روشن است، اما آزمون دسترسی اینترنت ناموفق بود."
                                    )
                                }
                            }
                        }
                    }

                    identityLookupJob?.cancel()
                    identityLookupJob = lifecycleScope.launch {
                        delay(1_000L)
                        val identity = withContext(Dispatchers.IO) {
                            MoghimiNetworkIdentityLookup.lookup()
                        }
                        if (mainViewModel.uiState.value.isRunning) {
                            smartUiState.update {
                                it.copy(
                                    publicIp = identity?.ipAddress ?: "دریافت نشد",
                                    countryName = identity?.countryName ?: "شناسایی نشد",
                                    countryCode = identity?.countryCode
                                )
                            }
                        }
                    }
                    checkForMoghimiUpdates()
                } else if (!state.isRunning && wasVpnRunning) {
                    identityLookupJob?.cancel()
                    internetCheckJob?.cancel()
                    smartUiState.update {
                        it.copy(
                            isWorking = false,
                            publicIp = null,
                            countryName = null,
                            countryCode = null,
                            internetVerified = null
                        )
                    }
                }
                wasVpnRunning = state.isRunning
            }
        }
    }

"""
if activity.count(insertion) != 1:
    raise SystemExit("Could not locate MainActivity method insertion point")
activity = activity.replace(insertion, methods + insertion, 1)
activity_file = project / "app/src/main/java/com/v2ray/ang/ui/main/MainActivity.kt"
activity_file.write_text(activity, encoding="utf-8")

# After the service is launched, watch its real state and walk through ranked alternatives if needed.
replace_once(
    activity_file,
    "        LauncherManager.startService(this)\n    }\n\n    private fun importManually",
    """        LauncherManager.startService(this)
        if (smartCandidateGuids.isNotEmpty()) {
            monitorSmartConnection(0)
        }
    }

    private fun importManually""",
    "smart connection monitor hook"
)
# The edited activity was read and written via replace_once; continue with VPN service patch.

# Keep the app's own HTTP requests inside the tunnel so the displayed egress IP is the VPN exit IP.
vpn_service = java / "service/CoreVpnService.kt"
vpn_text = vpn_service.read_text(encoding="utf-8")
method_start = vpn_text.index("    private fun configurePerAppProxy(builder: Builder) {")
method_end = vpn_text.index("    /**\n     * Runs the tun2socks process.", method_start)
new_method = """    private fun configurePerAppProxy(builder: Builder) {
        val selfPackageName = BuildConfig.APPLICATION_ID
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY) == false) {
            // Include the app itself in the tunnel. Its native transport sockets use VpnService.protect().
            return
        }

        val apps = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)
        if (apps.isNullOrEmpty()) {
            return
        }

        val bypassApps = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS)
        // Always route this app through the tunnel, even when a per-app allowlist is active.
        if (bypassApps) apps.remove(selfPackageName) else apps.add(selfPackageName)

        apps.forEach {
            try {
                if (bypassApps) {
                    builder.addDisallowedApplication(it)
                } else {
                    builder.addAllowedApplication(it)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                LogUtil.e(AppConfig.TAG, "VPN: Failed to configure application route", e)
            }
        }
    }

"""
vpn_text = vpn_text[:method_start] + new_method + vpn_text[method_end:]
vpn_service.write_text(vpn_text, encoding="utf-8")

# Keep all foreground notifications branded and never expose the selected route name.
notification_file = java / "handler/NotificationManager.kt"
notification_text = notification_file.read_text(encoding="utf-8")
notification_text = notification_text.replace(
    '.setSmallIcon(R.drawable.ic_stat_name)',
    '.setSmallIcon(R.drawable.ic_moghimi_vpn_notification)'
)
notification_text = notification_text.replace(
    '.setSmallIcon(R.drawable.ic_stat_proxy)',
    '.setSmallIcon(R.drawable.ic_moghimi_vpn_notification)'
)
notification_text = notification_text.replace(
    '.setSmallIcon(R.drawable.ic_stat_direct)',
    '.setSmallIcon(R.drawable.ic_moghimi_vpn_notification)'
)
notification_text = notification_text.replace(
    '.setContentTitle(currentConfig?.remarks ?: service.getString(R.string.app_name))',
    '.setContentTitle(service.getString(R.string.app_name))'
)
append_start = notification_text.index("    private fun appendSpeedString(")
append_end = notification_text.index("    /**\\n     * Updates the speed notification once.", append_start)
notification_text = notification_text[:append_start] + notification_text[append_end:]
import re
notification_text = re.sub(r"\\nimport kotlin\\.math\\.min\\n", "\\n", notification_text)
speed_start = notification_text.index("        if (!zeroSpeed || !lastZeroSpeed) {")
speed_end = notification_text.index("\\n        lastQueryTime = queryTime", speed_start)
notification_text = (
    notification_text[:speed_start]
    + """        if (!zeroSpeed || !lastZeroSpeed) {
            val totalUp = proxyUplink + directUplink
            val totalDown = proxyDownlink + directDownlink
            val text = "دریافت " + totalDown.toSpeedString() + " • ارسال " + totalUp.toSpeedString()
            updateNotification(text, proxyTotal, directTotal)
        }"""
    + notification_text[speed_end:]
)
notification_file.write_text(notification_text, encoding="utf-8")

# Install the custom, image-based Moghimi VPN launcher mark.
icon_source = project.parents[2] / "android/assets/moghimi-vpn-icon.png"
if not icon_source.is_file():
    raise SystemExit(f"Missing custom launcher icon: {icon_source}")

manifest = app / "src/main/AndroidManifest.xml"
manifest_text = manifest.read_text(encoding="utf-8")
application_match = re.search(r"<application[^>]*>", manifest_text, flags=re.DOTALL)
if application_match is None:
    raise SystemExit("Could not locate the Android application tag")

application_tag = application_match.group(0)
if re.search(r'android:icon="[^"]+"', application_tag):
    application_tag = re.sub(
        r'android:icon="[^"]+"',
        'android:icon="@mipmap/moghimi_vpn"',
        application_tag,
        count=1
    )
else:
    application_tag = application_tag[:-1] + ' android:icon="@mipmap/moghimi_vpn">'

if re.search(r'android:roundIcon="[^"]+"', application_tag):
    application_tag = re.sub(
        r'android:roundIcon="[^"]+"',
        'android:roundIcon="@mipmap/moghimi_vpn"',
        application_tag,
        count=1
    )
else:
    application_tag = application_tag[:-1] + ' android:roundIcon="@mipmap/moghimi_vpn">'

manifest_text = (
    manifest_text[:application_match.start()]
    + application_tag
    + manifest_text[application_match.end():]
)
manifest.write_text(manifest_text, encoding="utf-8")

# Pre-adaptive-icon fallback and adaptive foreground use the same artwork.
# The in-app Compose header must resolve to a raster image, never the adaptive-icon XML.
header_icon_dir = res / "drawable-nodpi"
header_icon_dir.mkdir(parents=True, exist_ok=True)
shutil.copyfile(icon_source, header_icon_dir / "moghimi_vpn_header.png")

mipmap_nodpi = res / "mipmap-nodpi"
mipmap_nodpi.mkdir(parents=True, exist_ok=True)
shutil.copyfile(icon_source, mipmap_nodpi / "moghimi_vpn.png")
shutil.copyfile(icon_source, mipmap_nodpi / "moghimi_vpn_foreground.png")

adaptive_dir = res / "mipmap-anydpi-v26"
adaptive_dir.mkdir(parents=True, exist_ok=True)
(adaptive_dir / "moghimi_vpn.xml").write_text(
    """<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/moghimi_icon_background" />
    <foreground android:drawable="@mipmap/moghimi_vpn_foreground" />
</adaptive-icon>
""",
    encoding="utf-8"
)
(res / "values/moghimi_launcher_colors.xml").write_text(
    """<resources>
    <color name="moghimi_icon_background">#080F20</color>
</resources>
""",
    encoding="utf-8"
)

# A white monochrome shield mark for Android's foreground-service notification.
notification_icon = res / "drawable/ic_moghimi_vpn_notification.xml"
notification_icon.parent.mkdir(parents=True, exist_ok=True)
notification_icon.write_text(
    """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M12,2 L20,5.5 L20,11 C20,16.5 16.5,20.2 12,22 C7.5,20.2 4,16.5 4,11 L4,5.5 Z" />
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M9,10 L9,8.5 C9,4.5 15,4.5 15,8.5 L15,10 L17,10 L17,18 L7,18 L7,10 Z" />
</vector>
""",
    encoding="utf-8"
)

print("Moghimi VPN dashboard, smart routing, notifications and update checks prepared.")
PY

echo "Prepared Moghimi VPN Android source under $PROJECT"
