package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.db.CustomReminder
import com.example.data.db.PetSettings
import com.example.ui.components.PetCanvas
import com.example.ui.components.PetState
import android.app.AppOpsManager
import android.os.Build
import android.app.usage.UsageStatsManager
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsState by viewModel.settings.collectAsState()
    val remindersState by viewModel.reminders.collectAsState()

    // Local Overlay Permission State
    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    val checkUsageStatsPermission: (Context) -> Boolean = remember {
        { ctx ->
            val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            if (appOps == null) false else {
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        ctx.packageName
                    )
                } else {
                    @Suppress("DEPRECATION")
                    appOps.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        ctx.packageName
                    )
                }
                mode == AppOpsManager.MODE_ALLOWED
            }
        }
    }

    var hasUsageStatsPermission by remember {
        mutableStateOf(checkUsageStatsPermission(context))
    }

    // Refresh permission periodically when window gains focus
    LaunchedEffect(Unit) {
        while (true) {
            hasOverlayPermission = Settings.canDrawOverlays(context)
            hasUsageStatsPermission = checkUsageStatsPermission(context)
            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageIconsList().random(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp).size(26.dp)
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.shadow(2.dp)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // 1. Setup Overlay Permission Warning Card (if missing)
            if (!hasOverlayPermission) {
                item {
                    PermissionRequiredCard(
                        onGrantClicked = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // 2. Active Toggle Deck
            item {
                ActiveControlDeck(
                    settings = settingsState,
                    hasPermission = hasOverlayPermission,
                    onToggleActive = { active ->
                        if (!hasOverlayPermission) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } else {
                            viewModel.toggleOverlay(context, active)
                        }
                    }
                )
            }

            // 3. Interactive Sandbox Playground
            item {
                InteractivePlayground(
                    selectedPetId = settingsState.selectedPetId,
                    sizeScale = settingsState.sizeScale,
                    speedScale = settingsState.speedScale
                )
            }

            // 4. Character Selection Section
            item {
                CharacterSelectorSection(
                    selectedPetId = settingsState.selectedPetId,
                    onPetSelected = { viewModel.selectPet(it) }
                )
            }

            // 5. Customize Parameter Panel
            item {
                CustomizeParametersDeck(
                    settings = settingsState,
                    onSizeChange = { viewModel.updateSize(it) },
                    onSpeedChange = { viewModel.updateSpeed(it) },
                    onFrequencyChange = { viewModel.updateFrequency(it) },
                    onBatterySaverToggle = { viewModel.toggleBatterySaver(it) }
                )
            }

            // 5b. App Shortcuts Deck
            item {
                AppShortcutsDeck(
                    settings = settingsState,
                    onUpdateShortcut = { index, pkg -> viewModel.updateShortcutApp(index, pkg) }
                )
            }

            // 5c. Smart Game Mode Detector Deck
            item {
                SmartGameModeDeck(
                    settings = settingsState,
                    hasUsagePermission = hasUsageStatsPermission,
                    onToggleGameMode = { viewModel.toggleGameMode(it) },
                    onGrantPermission = {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    }
                )
            }

            // 5d. Collaborative Mode Multiplayer Deck
            item {
                CollaborativeModeDeck(
                    settings = settingsState,
                    onToggleCollaborative = { viewModel.toggleCollaborativeActive(it) },
                    onSelectCollaborativePet = { viewModel.updateCollaborativePet(it) },
                    onTriggerScenario = { viewModel.triggerCollaborativeScenario(it) }
                )
            }

            // 6. Custom Reminder Slogans List
            item {
                CustomRemindersSection(
                    reminders = remindersState,
                    onAddReminder = { viewModel.addReminder(it) },
                    onDeleteReminder = { viewModel.removeReminder(it) }
                )
            }
        }
    }
}

// -------------------------------------------------------------
// COMPOSABLE PANELS & HELPERS
// -------------------------------------------------------------

@Composable
fun PermissionRequiredCard(onGrantClicked: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = stringResource(R.string.permission_required_title),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.permission_required_desc),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            Button(
                onClick = onGrantClicked,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.grant_permission_btn),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onError
                )
            }
        }
    }
}

@Composable
fun ActiveControlDeck(
    settings: PetSettings,
    hasPermission: Boolean,
    onToggleActive: (Boolean) -> Unit
) {
    val isActive = settings.isOverlayActive && hasPermission

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isActive) stringResource(R.string.pet_state_active)
                           else stringResource(R.string.pet_state_inactive),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isActive) "رفيقك يلهو الآن! انقر عليه ليتفاعل."
                           else "قم بإطلاق المساعد ليتجول معك على الشاشة.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = { onToggleActive(!isActive) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Default.Close else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp).size(18.dp)
                )
                Text(
                    text = if (isActive) stringResource(R.string.deactivate_pet_btn)
                           else stringResource(R.string.activate_pet_btn),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// Sandbox local playground in the dashboard
@Composable
fun InteractivePlayground(
    selectedPetId: String,
    sizeScale: Float,
    speedScale: Float
) {
    var playX by remember { mutableStateOf(100f) }
    var playY by remember { mutableStateOf(150f) }
    var playState by remember { mutableStateOf(PetState.IDLE) }
    var touchActive by remember { mutableStateOf(false) }
    var tickCount by remember { mutableStateOf(0) }
    val random = remember { Random() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val maxW = constraints.maxWidth.toFloat()
            val maxH = constraints.maxHeight.toFloat()
            val density = LocalContext.current.resources.displayMetrics.density
            val petSizePx = 70f * sizeScale * density

            // Internal physics loop just for playground rendering
            LaunchedEffect(playState, touchActive) {
                var gravityVel = 0f
                var targetX = playX
                var direction = 1

                while (!touchActive) {
                    delay(60)
                    tickCount++

                    val floorY = maxH - petSizePx - 20f

                    if (playY < floorY && playState != PetState.CLIMBING_LEFT && playState != PetState.CLIMBING_RIGHT) {
                        gravityVel += 1.8f * speedScale
                        playY += gravityVel
                        playState = PetState.FALLING

                        if (playY >= floorY) {
                            playY = floorY
                            gravityVel = 0f
                            playState = PetState.IDLE
                        }
                    } else {
                        gravityVel = 0f

                        when (playState) {
                            PetState.FALLING -> playState = PetState.IDLE
                            PetState.JUMPING -> {
                                gravityVel = -18f * speedScale
                                playY += gravityVel
                                playState = PetState.FALLING
                            }
                            PetState.IDLE -> {
                                if (random.nextInt(100) < 5) {
                                    playState = PetState.WALKING
                                    direction = if (random.nextBoolean()) 1 else -1
                                    targetX = playX + direction * (100f + random.nextInt(200))
                                    targetX = targetX.coerceIn(20f, maxW - petSizePx - 20f)
                                } else if (random.nextInt(150) < 1) {
                                    playState = PetState.SLEEPING
                                }
                            }
                            PetState.WALKING -> {
                                val step = 4f * speedScale * direction
                                playX += step

                                val reachedTarget = if (direction > 0) playX >= targetX else playX <= targetX
                                val hitWall = playX <= 15f || playX >= maxW - petSizePx - 15f

                                if (reachedTarget || hitWall) {
                                    playX = playX.coerceIn(15f, maxW - petSizePx - 15f)
                                    if (hitWall && random.nextBoolean()) {
                                        playState = if (playX <= 15f) PetState.CLIMBING_LEFT else PetState.CLIMBING_RIGHT
                                    } else {
                                        playState = PetState.IDLE
                                    }
                                }
                            }
                            PetState.CLIMBING_LEFT, PetState.CLIMBING_RIGHT -> {
                                playY -= 3f * speedScale
                                if (playY <= 30f || random.nextInt(100) < 4) {
                                    direction = if (playState == PetState.CLIMBING_LEFT) 1 else -1
                                    playState = PetState.JUMPING
                                    playX += direction * 20f
                                }
                            }
                            PetState.SLEEPING -> {
                                if (random.nextInt(300) < 2) {
                                    playState = PetState.IDLE
                                }
                            }
                        }
                    }
                }
            }

            // Playground background pattern/label
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.test_inside_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.tip_drag_drop),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            // Drag indicator and pet Canvas representation
            Box(
                modifier = Modifier
                    .offset(
                        x = (playX / density).dp,
                        y = (playY / density).dp
                    )
                    .size(56.dp * sizeScale)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                touchActive = true
                                playState = PetState.FALLING
                            },
                            onDragEnd = {
                                touchActive = false
                            },
                            onDragCancel = {
                                touchActive = false
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                playX = (playX + dragAmount.x).coerceIn(0f, maxW - petSizePx)
                                playY = (playY + dragAmount.y).coerceIn(0f, maxH - petSizePx)
                            }
                        )
                    }
                    .clickable {
                        if (playState != PetState.JUMPING && playState != PetState.FALLING) {
                            playState = PetState.JUMPING
                        }
                    }
            ) {
                PetCanvas(
                    characterId = selectedPetId,
                    state = playState,
                    modifier = Modifier.fillMaxSize(),
                    tick = tickCount
                )
            }
        }
    }
}

@Composable
fun CharacterSelectorSection(
    selectedPetId: String,
    onPetSelected: (String) -> Unit
) {
    val context = LocalContext.current

    val characters = listOf(
        Triple("mochi", stringResource(R.string.pet_mochi_name), stringResource(R.string.pet_mochi_desc)),
        Triple("astro", stringResource(R.string.pet_astro_name), stringResource(R.string.pet_astro_desc)),
        Triple("kage", stringResource(R.string.pet_kage_name), stringResource(R.string.pet_kage_desc)),
        Triple("rex", stringResource(R.string.pet_rex_name), stringResource(R.string.pet_rex_desc))
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.choose_pet_title),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(characters) { (id, name, desc) ->
                val isSelected = id == selectedPetId

                Card(
                    modifier = Modifier
                        .width(220.dp)
                        .clickable { onPetSelected(id) }
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Render animated pet in circle background
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(
                                    when (id) {
                                        "mochi" -> Color(0xFFFFF7F0)
                                        "astro" -> Color(0xFFE3F2FD)
                                        "kage" -> Color(0xFFECEFF1)
                                        "rex" -> Color(0xFFE8F5E9)
                                        else -> Color(0xFFFAFAFA)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            PetCanvas(
                                characterId = id,
                                state = if (isSelected) PetState.WALKING else PetState.IDLE,
                                modifier = Modifier.size(56.dp)
                            )
                        }

                        Text(
                            text = name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = desc,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.height(52.dp),
                            lineHeight = 15.sp
                        )

                        if (isSelected) {
                            Row(
                                modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).padding(horizontal = 12.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("الرفيق النشط", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text("انقر للاختيار", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomizeParametersDeck(
    settings: PetSettings,
    onSizeChange: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onFrequencyChange: (Int) -> Unit,
    onBatterySaverToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "إعدادات وتخصيص الرفيق الأليف",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary
            )

            // Dynamic Size Slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("حجم المساعد الأليف", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = when {
                            settings.sizeScale <= 0.8f -> "صغير جداً 👶"
                            settings.sizeScale <= 1.1f -> "طبيعي 🦖"
                            else -> "ضخم ومرح 🦁"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = settings.sizeScale,
                    onValueChange = onSizeChange,
                    valueRange = 0.6f..1.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            // Speed scale slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("نشاط وطاقة الحركة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = when {
                            settings.speedScale <= 0.7f -> "هادي وكسول 💤"
                            settings.speedScale <= 1.2f -> "نشاط طبيعي 🐾"
                            else -> "فرط طاقة وحيوية! 🔥"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = settings.speedScale,
                    onValueChange = onSpeedChange,
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            // Frequency row
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.frequency_label),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                val freqOptions = listOf(
                    1 to "كل دقيقة",
                    5 to "5 د",
                    15 to "15 د",
                    30 to "30 د",
                    0 to "منكتم"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    freqOptions.forEach { (mins, label) ->
                        val isSelected = settings.bubbleFrequencyMinutes == mins
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { onFrequencyChange(mins) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Smart Battery Saver Switch
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.battery_saver_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Text(
                        text = stringResource(R.string.battery_saver_desc),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Switch(
                    checked = settings.isBatterySaverEnabled,
                    onCheckedChange = onBatterySaverToggle
                )
            }
        }
    }
}

@Composable
fun CustomRemindersSection(
    reminders: List<CustomReminder>,
    onAddReminder: (String) -> Unit,
    onDeleteReminder: (CustomReminder) -> Unit
) {
    var textInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.reminders_section_title),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary
            )

            // Adding input block
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.add_reminder_placeholder),
                            fontSize = 11.sp
                        )
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Button(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            onAddReminder(textInput)
                            textInput = ""
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(stringResource(R.string.add_btn), fontWeight = FontWeight.Bold)
                }
            }

            // Reminders list
            if (reminders.isEmpty()) {
                Text(
                    text = stringResource(R.string.reminders_list_empty),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 200.dp).padding(vertical = 4.dp)
                ) {
                    reminders.forEach { reminder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = reminder.text,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            IconButton(
                                onClick = { onDeleteReminder(reminder) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "حذف التذكير",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Quick list of random cute icons
private fun imageIconsList() = listOf(
    Icons.Default.Face,
    Icons.Default.Star,
    Icons.Default.Favorite,
    Icons.Default.Home
)

@Composable
fun AppShortcutsDeck(
    settings: PetSettings,
    onUpdateShortcut: (Int, String?) -> Unit
) {
    var activeDialogIndex by remember { mutableStateOf<Int?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "لوحة الاختصارات والتشغيل السريع للمساعد 🚀",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "تعديل تطبيقات التشغيل السريع المرافقة لرفيقك. انقر عليه للفتح الفوري أثناء التصفح!",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                (1..3).forEach { index ->
                    val pkg = when (index) {
                        1 -> settings.shortcutApp1
                        2 -> settings.shortcutApp2
                        3 -> settings.shortcutApp3
                        else -> null
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        ShortcutItemButton(
                            index = index,
                            packageName = pkg,
                            onClick = { activeDialogIndex = index },
                            onClear = { onUpdateShortcut(index, null) }
                        )
                    }
                }
            }
        }
    }

    activeDialogIndex?.let { index ->
        AppSelectionDialog(
            onDismissRequest = { activeDialogIndex = null },
            onAppSelected = { pkg ->
                onUpdateShortcut(index, pkg)
                activeDialogIndex = null
            }
        )
    }
}

@Composable
fun ShortcutItemButton(
    index: Int,
    packageName: String?,
    onClick: () -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    val appLabel = remember(packageName) {
        if (packageName.isNullOrBlank()) null else {
            try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName.split(".").lastOrNull() ?: "تطبيق"
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
            if (!packageName.isNullOrBlank()) {
                AppIconImage(packageName = packageName, modifier = Modifier.size(36.dp))
                // Clear button at top-right
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .clickable { onClear() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "مسح",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "إضافة",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Text(
            text = appLabel ?: "إضافة زر ${index}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (packageName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
fun SmartGameModeDeck(
    settings: PetSettings,
    hasUsagePermission: Boolean,
    onToggleGameMode: (Boolean) -> Unit,
    onGrantPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "مستشعر الألعاب الذكي تلقائي التخفي 🎮",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Switch(
                    checked = settings.isGameModeEnabled,
                    onCheckedChange = onToggleGameMode
                )
            }

            Text(
                text = "عند تفعيل هذا الخيار، سيقوم مساعدك بمجرد فتح أي لعبة بالوداع والسبات تلقائياً والاختفاء من الشاشة، لتوفير كامل عتاد الرام والبطارية لك والاستمتاع بأفضل أداء! وسيعود إليك فرحاً فور خروجك من اللعبة.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )

            if (settings.isGameModeEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                if (hasUsagePermission) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFE8F5E9))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "المستشعر نشط وجاهز للاندماج السريع 🎮✨",
                            color = Color(0xFF2E7D32),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "يتطلب تفعيل إذن الوصول لبيانات الاستخدام",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "يحتاج المساعد إلى إذن مراقبة تشغيل التطبيقات والألعاب ليقرر متى يودعك ويختفي لتسريع تجربتك.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp
                        )
                        Button(
                            onClick = onGrantPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("منح الوصول الفوري 🔓", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionDialog(
    onDismissRequest: () -> Unit,
    onAppSelected: (String) -> Unit
) {
    val context = LocalContext.current
    var appsList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolved = pm.queryIntentActivities(mainIntent, 0)
            appsList = resolved.map {
                it.activityInfo.packageName to it.loadLabel(pm).toString()
            }.sortedBy { it.second }
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("اختر التطبيق المطلوب", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 350.dp).fillMaxWidth()) {
                    items(appsList) { (pkg, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAppSelected(pkg) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconImage(packageName = pkg, modifier = Modifier.size(32.dp).padding(end = 10.dp))
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
fun AppIconImage(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawable.toBitmap().asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollaborativeModeDeck(
    settings: PetSettings,
    onToggleCollaborative: (Boolean) -> Unit,
    onSelectCollaborativePet: (String) -> Unit,
    onTriggerScenario: (String) -> Unit
) {
    var searchInput by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResultConnected by remember { mutableStateOf(settings.isCollaborativeActive) }
    val scope = rememberCoroutineScope()

    // Sync state with setting
    LaunchedEffect(settings.isCollaborativeActive) {
        searchResultConnected = settings.isCollaborativeActive
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header with Icon and Toggle Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "الوضع التعاوني المتعدد Multiplayer 🤝✨",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Switch(
                    checked = settings.isCollaborativeActive,
                    onCheckedChange = { active ->
                        onToggleCollaborative(active)
                        if (!active) {
                            searchResultConnected = false
                        }
                    }
                )
            }

            Text(
                text = "استدعِ مساعد صديقك ليتجول ويتفاعل مباشرة مع مساعدك على شاشة هاتفك! يمكنهما اللعب، تبادل الهدايا، والمشاركة في تحديات التركيز المشتركة.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )

            if (settings.isCollaborativeActive) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Find Friend bar "ابحث عن صديق للمشاركة"
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "ابحث عن صديق للمشاركة 📡",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchInput,
                            onValueChange = { searchInput = it },
                            placeholder = { 
                                Text("أدخل رمز صديقك (مثال: SH-8392)", fontSize = 11.sp) 
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )

                        Button(
                            onClick = {
                                isSearching = true
                                scope.launch {
                                    delay(2000)
                                    isSearching = false
                                    searchResultConnected = true
                                    onSelectCollaborativePet("panda") // Default to panda
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isSearching,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("ابحث", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    // Radar pulse search animation or Connected Success message
                    if (isSearching) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "جاري تتبع الاتصال المشترك عبر الرادار... 🛰️",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (searchResultConnected) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFE8F5E9)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "تم الاتصال بنجاح مع رفيق صديقك! 🟢",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFF2E7D32)
                                    )
                                    Text(
                                        text = "المساعد الأليف الإضافي يعيش على شاشتك الآن.",
                                        fontSize = 10.sp,
                                        color = Color(0xFF558B2F)
                                    )
                                }
                            }
                        }

                        // Select companion pet to display
                        Text(
                            text = "اختر رفيق صديقك المستدعى 🐾",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        val friends = listOf(
                            Pair("panda", "الباندا النشيط 🐼"),
                            Pair("astro", "أسترو الفضائي 👽"),
                            Pair("rex", "ريكس الصغير 🦖"),
                            Pair("kage", "كاجي النينجا 🥷")
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(friends) { (id, name) ->
                                val isSelected = settings.collaborativePetId == id
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onSelectCollaborativePet(id) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = name,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Play Scenarios / Challenges
                        Text(
                            text = "تحديات ثنائية ممتعة ونشاطات مشتعلة! 🔥🏆",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onTriggerScenario("gift")
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFECEF),
                                    contentColor = Color(0xFFD81B60)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("تبادل هدايا 🎁", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    onTriggerScenario("study")
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE8F5E9),
                                    contentColor = Color(0xFF1B5E20)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("تحدي المذاكرة 📖", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    onTriggerScenario("hide_seek")
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE3F2FD),
                                    contentColor = Color(0xFF0D47A1)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("لعب الغميضة 🎮", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
