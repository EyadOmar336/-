package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.data.db.CustomReminder
import com.example.data.db.PetDatabase
import com.example.data.db.PetSettings
import com.example.data.repository.PetRepository
import com.example.ui.components.PetCanvas
import com.example.ui.components.PetState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.Random

class FloatingPetService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private lateinit var repository: PetRepository

    private var petX = 100f
    private var petY = 500f
    private var screenWidth = 1000
    private var screenHeight = 1800

    private var activePetState by mutableStateOf(PetState.IDLE)
    private var selectedPetId by mutableStateOf("mochi")
    private var petSizeScale by mutableStateOf(1.0f)
    private var petSpeedScale by mutableStateOf(1.0f)
    private var bubbleFrequencyMinutes by mutableStateOf(5)
    private var isBatterySaverEnabled by mutableStateOf(true)

    // Shortcuts and Game settings flow states
    private var shortcutApp1 by mutableStateOf<String?>(null)
    private var shortcutApp2 by mutableStateOf<String?>(null)
    private var shortcutApp3 by mutableStateOf<String?>(null)
    private var isGameModeEnabled by mutableStateOf(true)

    private var isDockExpanded by mutableStateOf(false)

    private var activeSpeechText by mutableStateOf<String?>(null)
    private var isDragging by mutableStateOf(false)

    private var initialX = 0f
    private var initialY = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isMovingTouch = false
    private var isHandlingTouch = false

    private var physicsJob: Job? = null
    private var speechLoopJob: Job? = null
    private var gameCheckJob: Job? = null

    private var lastInteractionTime = System.currentTimeMillis()
    private var tickCount = 0

    private val random = Random()

    companion object {
        private const val CHANNEL_ID = "floating_pet_service_channel"
        private const val NOTIFICATION_ID = 9187
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val database = PetDatabase.getDatabase(this)
        repository = PetRepository(database.petDao())

        setupScreenDimensions()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        lifecycleScope.launch {
            repository.initializeDatabaseIfEmpty()
            repository.settings.collect { settings ->
                selectedPetId = settings.selectedPetId
                petSizeScale = settings.sizeScale
                petSpeedScale = settings.speedScale
                bubbleFrequencyMinutes = settings.bubbleFrequencyMinutes
                isBatterySaverEnabled = settings.isBatterySaverEnabled
                shortcutApp1 = settings.shortcutApp1
                shortcutApp2 = settings.shortcutApp2
                shortcutApp3 = settings.shortcutApp3
                isGameModeEnabled = settings.isGameModeEnabled

                updateWindowSize()
            }
        }

        createOverlayWindow()
        startPhysicsLoop()
        startSpeechController()
        startGameCheckLoop()
    }

    private fun setupScreenDimensions() {
        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    private fun updateWindowSize() {
        val baseSize = 145.dp
        val density = resources.displayMetrics.density
        val baseWidthPx = (baseSize.value * petSizeScale * density).toInt()
        val baseHeightPx = (baseSize.value * petSizeScale * density).toInt()
        
        val finalWidthPx = if (isDockExpanded) (baseWidthPx * 1.6).toInt() else baseWidthPx
        val finalHeightPx = if (isDockExpanded) (baseHeightPx * 1.6).toInt() else baseHeightPx

        layoutParams?.let { params ->
            params.width = finalWidthPx
            params.height = finalHeightPx
            if (composeView != null) {
                windowManager?.updateViewLayout(composeView, params)
            }
        }
    }

    private fun createOverlayWindow() {
        val baseSize = 145.dp
        val density = resources.displayMetrics.density
        val finalSizePx = (baseSize.value * petSizeScale * density).toInt()

        val params = WindowManager.LayoutParams(
            finalSizePx,
            finalSizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth / 2 - finalSizePx / 2
            y = screenHeight - finalSizePx - 200
        }

        layoutParams = params
        petX = params.x.toFloat()
        petY = params.y.toFloat()

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingPetService)
            setViewTreeSavedStateRegistryOwner(this@FloatingPetService)
            setViewTreeViewModelStoreOwner(this@FloatingPetService)

            setOnTouchListener { view, event ->
                val lp = this@FloatingPetService.layoutParams ?: return@setOnTouchListener false
                
                val petSizeDp = if (activeSpeechText != null) 72.dp else 90.dp
                val density = resources.displayMetrics.density
                val petSizePx = (petSizeDp.value * petSizeScale * density).toInt()

                val petLeft = ((lp.width - petSizePx) / 2) - (15 * density).toInt()
                val petRight = ((lp.width - petSizePx) / 2) + petSizePx + (15 * density).toInt()
                val petTop = (lp.height - petSizePx) - (15 * density).toInt()
                val petBottom = lp.height + (15 * density).toInt()

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (event.x >= petLeft && event.x <= petRight && event.y >= petTop && event.y <= petBottom) {
                            isHandlingTouch = true
                            initialX = lp.x.toFloat()
                            initialY = lp.y.toFloat()
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isMovingTouch = false
                            isDragging = true
                            activePetState = PetState.FALLING
                            lastInteractionTime = System.currentTimeMillis()
                            true
                        } else {
                            isHandlingTouch = false
                            false
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isHandlingTouch) {
                            val dx = event.rawX - initialTouchX
                            val dy = event.rawY - initialTouchY

                            if (Math.abs(dx) > 10 || Math.abs(dy) > 10 || isMovingTouch) {
                                isMovingTouch = true
                                lp.x = (initialX + dx).toInt()
                                lp.y = (initialY + dy).toInt()

                                val maxRightPx = screenWidth - lp.width
                                val maxBottomPx = screenHeight - lp.height - 100
                                lp.x = lp.x.coerceIn(0, maxRightPx)
                                lp.y = lp.y.coerceIn(0, maxBottomPx)

                                petX = lp.x.toFloat()
                                petY = lp.y.toFloat()

                                windowManager?.updateViewLayout(view, lp)
                            }
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isHandlingTouch) {
                            isDragging = false
                            if (isMovingTouch) {
                                activePetState = PetState.IDLE
                            } else {
                                isDockExpanded = !isDockExpanded
                                updateWindowSize()
                                triggerJumpInteraction()
                            }
                            lastInteractionTime = System.currentTimeMillis()
                            isHandlingTouch = false
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }

            setContent {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    activeSpeechText?.let { text ->
                        Card(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp)
                                .offset(y = (-4).dp)
                                .clickable { activeSpeechText = null },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF0F4FC),
                                contentColor = Color(0xFF1E293B)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                        ) {
                            Text(
                                text = text,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 6.dp),
                                letterSpacing = 0.2.sp
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isDockExpanded) {
                            val apps = listOfNotNull(shortcutApp1, shortcutApp2, shortcutApp3).filter { it.isNotBlank() }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                if (apps.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xE6E26EE5), RoundedCornerShape(8.dp))
                                            .clickable { 
                                                isDockExpanded = false 
                                                updateWindowSize()
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            "اضف تطبيقات باللوحة ⚙️",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    apps.forEach { pkg ->
                                        FloatingAppShortcutButton(pkg) {
                                            launchApp(pkg)
                                            isDockExpanded = false
                                            updateWindowSize()
                                        }
                                    }
                                }
                            }
                        }

                        PetCanvas(
                            characterId = selectedPetId,
                            state = activePetState,
                            modifier = Modifier.size(((if (activeSpeechText != null) 72.dp else 90.dp).value * petSizeScale).dp),
                            tick = tickCount
                        )
                    }
                }
            }
        }

        windowManager?.addView(composeView, params)
    }

    @Composable
    fun FloatingAppShortcutButton(pkg: String, onClick: () -> Unit) {
        val context = LocalContext.current
        val bitmap = remember(pkg) {
            try {
                val drawable = context.packageManager.getApplicationIcon(pkg)
                drawable.toBitmap().asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape)
                .border(1.dp, Color(0xFFE26EE5).copy(alpha = 0.3f), CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.DarkGray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    private fun launchApp(pkgName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(pkgName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                
                activeSpeechText = "جاري فتح التطبيق لك! 🚀 استمتع بوقتك"
                lifecycleScope.launch {
                    delay(3500)
                    activeSpeechText = null
                }
            } else {
                activeSpeechText = "تعذر العثور على التطبيق بالهاتف 😢"
            }
        } catch (e: Exception) {
            activeSpeechText = "تعذر تشغيل التطبيق ⚠️"
        }
    }

    private fun triggerJumpInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        if (activePetState != PetState.FALLING && activePetState != PetState.JUMPING) {
            activePetState = PetState.JUMPING
            val cuteSayings = when (selectedPetId) {
                "mochi" -> listOf("مياو! دغدغة لطيفة 😻", "قفزة خارقة! 🐾", "هل أحضرت لي تفاريح؟ 🥛")
                "astro" -> listOf("جاذبية منعدمة هنا! 🌌", "إلى اللانهائية وما بعدها! 🚀", "احذر الثقوب السوداء! 🪐")
                "kage" -> listOf("حركة ظل سريعة!  ⚔️", "فلير طائر! 🥋", "شينوبي متخفي! 💨")
                "rex" -> listOf("روووار! أنا وحش مرعب 🦖", "زلزال صغير! 💥", "هل لديك أوراق شجر؟ 🌱")
                else -> listOf("هاها قفزة ممتعة! 🎉")
            }
            activeSpeechText = cuteSayings[random.nextInt(cuteSayings.size)]
            lifecycleScope.launch {
                delay(3000)
                if (activeSpeechText in cuteSayings) activeSpeechText = null
            }
        }
    }

    private var gravityVel = 0f
    private var targetX = 0f
    private var petDirection = 1

    private fun startPhysicsLoop() {
        physicsJob = lifecycleScope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(60)
                tickCount++

                layoutParams?.let { params ->
                    val now = System.currentTimeMillis()

                    if (isBatterySaverEnabled && (now - lastInteractionTime > 15000)) {
                        activePetState = PetState.SLEEPING
                        continue
                    }

                    if (isDragging) {
                        gravityVel = 0f
                        continue
                    }

                    val baseWidth = params.width
                    val baseHeight = params.height
                    val maxRight = screenWidth - baseWidth
                    val floorY = screenHeight - baseHeight - 120

                    if (petY < floorY && activePetState != PetState.CLIMBING_LEFT && activePetState != PetState.CLIMBING_RIGHT) {
                        gravityVel += 1.5f * (petSpeedScale * 0.8f)
                        petY += gravityVel
                        activePetState = PetState.FALLING

                        if (petY >= floorY) {
                            petY = floorY.toFloat()
                            gravityVel = 0f
                            activePetState = PetState.IDLE
                            lastInteractionTime = System.currentTimeMillis()
                        }
                    } else {
                        gravityVel = 0f

                        when (activePetState) {
                            PetState.FALLING -> {
                                activePetState = PetState.IDLE
                            }
                            PetState.JUMPING -> {
                                gravityVel = -22f * (petSpeedScale * 0.9f)
                                petY += gravityVel
                                activePetState = PetState.FALLING
                            }
                            PetState.IDLE -> {
                                if (random.nextInt(100) < 3) {
                                    activePetState = PetState.WALKING
                                    petDirection = if (random.nextBoolean()) 1 else -1
                                    targetX = petX + petDirection * (150f + random.nextInt(350))
                                    targetX = targetX.coerceIn(0f, maxRight.toFloat())
                                } else if (random.nextInt(200) < 1) {
                                    activePetState = PetState.SLEEPING
                                }
                            }
                            PetState.WALKING -> {
                                val step = 4.5f * petSpeedScale * petDirection
                                petX += step

                                val reachedTarget = if (petDirection > 0) petX >= targetX else petX <= targetX
                                val hitWall = petX <= 0f || petX >= maxRight.toFloat()

                                if (reachedTarget || hitWall) {
                                    petX = petX.coerceIn(0f, maxRight.toFloat())
                                    if (hitWall && random.nextInt(100) < 40) {
                                        activePetState = if (petX <= 0f) PetState.CLIMBING_LEFT else PetState.CLIMBING_RIGHT
                                    } else {
                                        activePetState = PetState.IDLE
                                    }
                                }
                            }
                            PetState.CLIMBING_LEFT, PetState.CLIMBING_RIGHT -> {
                                petY -= 3.5f * petSpeedScale
                                if (petY <= 150f || random.nextInt(120) < 2) {
                                    petDirection = if (activePetState == PetState.CLIMBING_LEFT) 1 else -1
                                    activePetState = PetState.JUMPING
                                    petX += petDirection * 30f
                                }
                            }
                            PetState.SLEEPING -> {
                                if (random.nextInt(500) < 2) {
                                    activePetState = PetState.IDLE
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            }
                        }
                    }

                    params.x = petX.toInt()
                    params.y = petY.toInt()
                    windowManager?.updateViewLayout(composeView, params)
                }
            }
        }
    }

    private fun startSpeechController() {
        speechLoopJob = lifecycleScope.launch {
            while (isActive) {
                val frequencyMins = bubbleFrequencyMinutes
                if (frequencyMins <= 0) {
                    activeSpeechText = null
                    delay(5000)
                    continue
                }

                val delayMs = frequencyMins * 60 * 1000L
                delay(delayMs)

                if (activePetState == PetState.SLEEPING) continue

                try {
                    val remindersList = repository.reminders.first()
                    if (remindersList.isNotEmpty()) {
                        val randomReminder = remindersList[random.nextInt(remindersList.size)]
                        activeSpeechText = randomReminder.text

                        delay(7000)
                        activeSpeechText = null
                    }
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun startGameCheckLoop() {
        gameCheckJob = lifecycleScope.launch(Dispatchers.Default) {
            var wasInGame = false
            while (isActive) {
                delay(2000)
                if (!isGameModeEnabled) {
                    if (wasInGame) {
                        wasInGame = false
                        withContext(Dispatchers.Main) {
                            showOverlayAfterGame()
                        }
                    }
                    continue
                }

                val hasPerm = hasUsageStatsPermission(this@FloatingPetService)
                if (!hasPerm) {
                    continue
                }

                val foregroundPkg = getForegroundPackage(this@FloatingPetService) ?: continue
                
                if (foregroundPkg == packageName || isSystemLauncher(foregroundPkg)) {
                    if (wasInGame) {
                        wasInGame = false
                        withContext(Dispatchers.Main) {
                            showOverlayAfterGame()
                        }
                    }
                    continue
                }

                val isInGame = isGamePackage(this@FloatingPetService, foregroundPkg)
                if (isInGame && !wasInGame) {
                    wasInGame = true
                    withContext(Dispatchers.Main) {
                        hideOverlayForGame()
                    }
                } else if (!isInGame && wasInGame) {
                    wasInGame = false
                    withContext(Dispatchers.Main) {
                        showOverlayAfterGame()
                    }
                }
            }
        }
    }

    private fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun getForegroundPackage(context: Context): String? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 4000
        val usageStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        if (usageStats.isNullOrEmpty()) return null
        
        val recentStats = usageStats.maxByOrNull { it.lastTimeUsed }
        return recentStats?.packageName
    }

    private fun isSystemLauncher(pkgName: String): Boolean {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val homePack = resolveInfo?.activityInfo?.packageName
        return pkgName == homePack || pkgName == "com.android.launcher3" || pkgName == "com.sec.android.app.launcher" || pkgName == "com.google.android.apps.nexuslauncher"
    }

    private fun isGamePackage(context: Context, packageName: String): Boolean {
        val gameKeywords = listOf("game", "unity", "pubg", "freefire", "tencent", "subway", "roblox", "clash", "candycrush", "angrybirds", "minecraft")
        val lowerPkg = packageName.lowercase()
        if (gameKeywords.any { lowerPkg.contains(it) }) return true

        return try {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_GAME
            } else {
                (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun hideOverlayForGame() {
        activeSpeechText = "انا ذاهب لتلعب بأحسن حال! 🎮✨"
        activePetState = PetState.SLEEPING
        lifecycleScope.launch(Dispatchers.Main) {
            delay(3500)
            composeView?.visibility = View.GONE
        }
    }

    private fun showOverlayAfterGame() {
        composeView?.visibility = View.VISIBLE
        activePetState = PetState.IDLE
        activeSpeechText = "أهلاً بعودتك! كيف كانت لعبتك الهادئة؟ هل فزت؟ 🏆🐱"
        lifecycleScope.launch(Dispatchers.Main) {
            delay(5000)
            activeSpeechText = null
        }
    }

    private fun buildForegroundNotification(): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("مساعد الشاشة الحيّ نشط")
            .setContentText("رفيقك الصغير يلهو ويتحرك الآن على هاتفك. انقر عليه للعب!")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "خدمة مساعد الهاتف"
            val descriptionText = "قناة تنبيهات تشغيل رفيق شاشة الهاتف بالخلفية"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        setupScreenDimensions()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        physicsJob?.cancel()
        speechLoopJob?.cancel()
        gameCheckJob?.cancel()

        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED

        composeView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
            }
        }

        super.onDestroy()
    }
}
