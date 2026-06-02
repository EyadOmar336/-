package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

enum class PetState {
    IDLE,
    WALKING,
    CLIMBING_LEFT,
    CLIMBING_RIGHT,
    SLEEPING,
    JUMPING,
    FALLING
}

@Composable
fun PetCanvas(
    characterId: String,
    state: PetState,
    modifier: Modifier = Modifier,
    tick: Int = 0
) {
    // Elegant transition animations between tick changes
    val infiniteTransition = rememberInfiniteTransition(label = "pet_tick")
    val internalTick by infiniteTransition.animateValue(
        initialValue = 0,
        targetValue = 100,
        typeConverter = Int.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "tick"
    )

    // Combine parameters for timing
    val activeTick = if (tick != 0) tick else internalTick

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val minDim = minOf(w, h)
        val r = minDim / 2.5f // reference radius of character

        when (characterId.lowercase()) {
            "mochi" -> drawMochi(cx, cy, r, state, activeTick)
            "astro" -> drawAstro(cx, cy, r, state, activeTick)
            "kage" -> drawKage(cx, cy, r, state, activeTick)
            "rex" -> drawRex(cx, cy, r, state, activeTick)
            "panda" -> drawPanda(cx, cy, r, state, activeTick)
            else -> drawMochi(cx, cy, r, state, activeTick)
        }
    }
}

// -------------------------------------------------------------
// 1. MOCHI THE CAT (DRAWING LOGIC)
// -------------------------------------------------------------
private fun DrawScope.drawMochi(
    cx: Float,
    cy: Float,
    r: Float,
    state: PetState,
    tick: Int
) {
    val walkOffset = if (state == PetState.WALKING) sin(tick * 0.4f) * 6f else 0f
    val sleepPulse = if (state == PetState.SLEEPING) sin(tick * 0.15f) * 3f else 0f
    val jumpStretch = if (state == PetState.JUMPING) 1.2f else 1.0f
    val jumpSquash = if (state == PetState.JUMPING) 0.8f else 1.0f

    val catWhite = Color(0xFFFBF9F1)
    val catPink = Color(0xFFFFC0D9)
    val catGray = Color(0xFF7F8F9F)
    val catEyeColor = Color(0xFF1E1E1E)

    // Apply translations
    var drawCx = cx
    var drawCy = cy + walkOffset

    if (state == PetState.CLIMBING_LEFT) {
         drawCx = cx - r * 0.3f
    } else if (state == PetState.CLIMBING_RIGHT) {
         drawCx = cx + r * 0.3f
    }

    // Drawing shadow
    if (state != PetState.JUMPING && state != PetState.FALLING) {
        drawOval(
            color = Color(0x33000000),
            topLeft = Offset(drawCx - r, cy + r * 0.6f),
            size = Size(r * 2, r * 0.3f)
        )
    }

    // 1. Left and Right Ears
    val earWidth = r * 0.45f
    val earHeight = r * 0.5f

    // Left Ear
    val leftEarPath = Path().apply {
        moveTo(drawCx - r * 0.7f, drawCy - r * 0.4f)
        lineTo(drawCx - r * 0.8f, drawCy - r * 1.0f)
        lineTo(drawCx - r * 0.2f, drawCy - r * 0.6f)
        close()
    }
    drawPath(leftEarPath, catWhite)
    val leftInnerEarPath = Path().apply {
        moveTo(drawCx - r * 0.65f, drawCy - r * 0.45f)
        lineTo(drawCx - r * 0.73f, drawCy - r * 0.85f)
        lineTo(drawCx - r * 0.33f, drawCy - r * 0.58f)
        close()
    }
    drawPath(leftInnerEarPath, catPink)

    // Right Ear
    val rightEarPath = Path().apply {
        moveTo(drawCx + r * 0.7f, drawCy - r * 0.4f)
        lineTo(drawCx + r * 0.8f, drawCy - r * 1.0f)
        lineTo(drawCx + r * 0.2f, drawCy - r * 0.6f)
        close()
    }
    drawPath(rightEarPath, catWhite)
    val rightInnerEarPath = Path().apply {
        moveTo(drawCx + r * 0.65f, drawCy - r * 0.45f)
        lineTo(drawCx + r * 0.73f, drawCy - r * 0.85f)
        lineTo(drawCx + r * 0.33f, drawCy - r * 0.58f)
        close()
    }
    drawPath(rightInnerEarPath, catPink)

    // 2. Head & Body
    if (state == PetState.SLEEPING) {
        // Sleep curled body
        drawOval(
            color = catWhite,
            topLeft = Offset(drawCx - r * 1.1f, drawCy - r * 0.6f + sleepPulse),
            size = Size(r * 2.2f, r * 1.3f)
        )
    } else {
        // Regular Body Capsule
        drawOval(
            color = catWhite,
            topLeft = Offset(drawCx - r * jumpSquash, drawCy - r * 0.7f * jumpStretch),
            size = Size(r * 2.0f * jumpSquash, r * 1.4f * jumpStretch)
        )
    }

    // 3. Details (Eyes, Whiskers, Face)
    if (state == PetState.SLEEPING) {
        // Closed dreaming eyes
        drawArc(
            color = catGray,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(drawCx - r * 0.45f, drawCy - r * 0.2f + sleepPulse),
            size = Size(r * 0.2f, r * 0.15f),
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
        drawArc(
            color = catGray,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(drawCx + r * 0.25f, drawCy - r * 0.2f + sleepPulse),
            size = Size(r * 0.2f, r * 0.15f),
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
        // Little Zzz
        val zProgress = (tick % 20) / 20f
        drawCircle(
            color = Color(0x992196F3),
            radius = 3f + 4f * zProgress,
            center = Offset(drawCx + r * 0.9f + zProgress * 20f, drawCy - r * 0.8f - zProgress * 30f)
        )
    } else if (state == PetState.FALLING) {
        // Wide shocked eyes
        drawCircle(color = catEyeColor, radius = r * 0.15f, center = Offset(drawCx - r * 0.35f, drawCy - r * 0.1f))
        drawCircle(color = catEyeColor, radius = r * 0.15f, center = Offset(drawCx + r * 0.35f, drawCy - r * 0.1f))
        drawCircle(color = Color.White, radius = r * 0.05f, center = Offset(drawCx - r * 0.38f, drawCy - r * 0.13f))
        drawCircle(color = Color.White, radius = r * 0.05f, center = Offset(drawCx + r * 0.32f, drawCy - r * 0.13f))
    } else {
        // Happy, normal dots
        drawCircle(color = catEyeColor, radius = r * 0.1f, center = Offset(drawCx - r * 0.35f, drawCy - r * 0.1f))
        drawCircle(color = catEyeColor, radius = r * 0.1f, center = Offset(drawCx + r * 0.35f, drawCy - r * 0.1f))
        // Blinks occasionally
        if (tick % 30 == 0) {
            drawCircle(color = catWhite, radius = r * 0.11f, center = Offset(drawCx - r * 0.35f, drawCy - r * 0.1f))
            drawCircle(color = catWhite, radius = r * 0.11f, center = Offset(drawCx + r * 0.35f, drawCy - r * 0.1f))
            drawArc(
                color = catEyeColor, startAngle = 0f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(drawCx - r * 0.45f, drawCy - r * 0.15f), size = Size(r * 0.2f, r * 0.1f),
                style = Stroke(3f)
            )
            drawArc(
                color = catEyeColor, startAngle = 0f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(drawCx + r * 0.25f, drawCy - r * 0.15f), size = Size(r * 0.2f, r * 0.1f),
                style = Stroke(3f)
            )
        }
    }

    // Whiskers
    drawLine(catPink, Offset(drawCx - r * 0.8f, drawCy), Offset(drawCx - r * 1.3f, drawCy - r * 0.05f), strokeWidth = 3f)
    drawLine(catPink, Offset(drawCx - r * 0.8f, drawCy + r * 0.15f), Offset(drawCx - r * 1.25f, drawCy + r * 0.25f), strokeWidth = 3f)

    drawLine(catPink, Offset(drawCx + r * 0.8f, drawCy), Offset(drawCx + r * 1.3f, drawCy - r * 0.05f), strokeWidth = 3f)
    drawLine(catPink, Offset(drawCx + r * 0.8f, drawCy + r * 0.15f), Offset(drawCx + r * 1.25f, drawCy + r * 0.25f), strokeWidth = 3f)

    // Nose & Mouth (Pink triangle/W line)
    drawCircle(Color(0xFFE26EE5), radius = 5f, center = Offset(drawCx, drawCy))
    drawArc(
        color = catGray, startAngle = 0f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(drawCx - r * 0.15f, drawCy + 2f), size = Size(r * 0.15f, r * 0.12f),
        style = Stroke(3f)
    )
    drawArc(
        color = catGray, startAngle = 0f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(drawCx, drawCy + 2f), size = Size(r * 0.15f, r * 0.12f),
        style = Stroke(3f)
    )

    // 4. Tail
    val tailSwing = sin(tick * 0.25f) * 20f
    rotate(degrees = tailSwing, pivot = Offset(drawCx - r * 0.8f, drawCy + r * 0.4f)) {
        drawArc(
            color = catPink,
            startAngle = 135f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(drawCx - r * 1.3f, drawCy + r * 0.1f),
            size = Size(r * 0.8f, r * 0.8f),
            style = Stroke(width = r * 0.18f, cap = StrokeCap.Round)
        )
    }

    // 5. Climbing claws representation
    if (state == PetState.CLIMBING_LEFT) {
        drawCircle(catPink, 8f, Offset(cx - r * 0.85f, drawCy - r * 0.3f))
        drawCircle(catPink, 8f, Offset(cx - r * 0.85f, drawCy + r * 0.3f))
    } else if (state == PetState.CLIMBING_RIGHT) {
        drawCircle(catPink, 8f, Offset(cx + r * 0.85f, drawCy - r * 0.3f))
        drawCircle(catPink, 8f, Offset(cx + r * 0.85f, drawCy + r * 0.3f))
    }
}

// -------------------------------------------------------------
// 2. ASTRO THE ASTRONAUT (DRAWING LOGIC)
// -------------------------------------------------------------
private fun DrawScope.drawAstro(
    cx: Float,
    cy: Float,
    r: Float,
    state: PetState,
    tick: Int
) {
    // Jetpack engine fires when climbing/jumping
    val fuelFlicker = (tick % 4 == 0)
    val floatOffset = sin(tick * 0.15f) * 8f
    val currentCy = cy + floatOffset

    // Suit Palette
    val suitWhite = Color(0xFFEEEEEE)
    val suitBlue = Color(0xFF1E2022)
    val backpackRed = Color(0xFFFF5F5F)
    val visorColor = Color(0xFF2196F3)
    val reflectionColor = Color(0xCCFFFFFF)

    // Jetpack flame rendering
    if (state == PetState.JUMPING || state == PetState.CLIMBING_LEFT || state == PetState.CLIMBING_RIGHT) {
        drawOval(
            color = if (fuelFlicker) Color(0xFFFFC107) else Color(0xFFFF5722),
            topLeft = Offset(cx - r * 0.3f, currentCy + r * 0.6f),
            size = Size(r * 0.6f, r * if (fuelFlicker) 0.8f else 0.5f)
        )
        drawCircle(
            color = Color(0xFFFFEB3B),
            radius = r * 0.15f,
            center = Offset(cx, currentCy + r * 0.7f)
        )
    }

    // Shadow
    drawOval(
        color = Color(0x22000000),
        topLeft = Offset(cx - r * 0.8f, cy + r * 0.8f),
        size = Size(r * 1.6f, r * 0.25f)
    )

    // Red Oxygen Backpack
    drawRoundRect(
        color = backpackRed,
        topLeft = Offset(cx - r * 0.9f, currentCy - r * 0.4f),
        size = Size(r * 0.4f, r * 0.9f),
        cornerRadius = CornerRadius(10f, 10f)
    )

    // Body suit
    drawRoundRect(
        color = suitWhite,
        topLeft = Offset(cx - r * 0.7f, currentCy - r * 0.6f),
        size = Size(r * 1.4f, r * 1.2f),
        cornerRadius = CornerRadius(r * 0.4f, r * 0.4f)
    )

    // Little details on suit (buttons / lines)
    drawCircle(Color(0xFF26A69A), radius = 6f, center = Offset(cx - r * 0.35f, currentCy + r * 0.3f))
    drawCircle(Color(0xFFFFB74D), radius = 6f, center = Offset(cx - r * 0.1f, currentCy + r * 0.3f))

    // Visor border (Outer edge)
    drawRoundRect(
        color = suitBlue,
        topLeft = Offset(cx - r * 0.5f, currentCy - r * 0.4f),
        size = Size(r * 1.0f, r * 0.6f),
        cornerRadius = CornerRadius(20f, 20f)
    )

    // Inner Visor Glass
    drawRoundRect(
        color = if (state == PetState.SLEEPING) Color(0xFF151922) else visorColor,
        topLeft = Offset(cx - r * 0.45f, currentCy - r * 0.35f),
        size = Size(r * 0.9f, r * 0.5f),
        cornerRadius = CornerRadius(15f, 15f)
    )

    // Glass reflection curve
    if (state != PetState.SLEEPING) {
        val path = Path().apply {
            moveTo(cx - r * 0.35f, currentCy - r * 0.3f)
            quadraticTo(cx - r * 0.1f, currentCy - r * 0.3f, cx - r * 0.05f, currentCy - r * 0.15f)
            lineTo(cx - r * 0.15f, currentCy - r * 0.15f)
            quadraticTo(cx - r * 0.2f, currentCy - r * 0.25f, cx - r * 0.35f, currentCy - r * 0.25f)
            close()
        }
        drawPath(path, reflectionColor)
    }

    // Floating animation
    if (state == PetState.FALLING) {
        drawArc(
            color = suitWhite,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(cx - r * 0.6f, currentCy - r * 0.9f),
            size = Size(r * 0.4f, r * 0.4f),
            style = Stroke(6f)
        )
    }
}

// -------------------------------------------------------------
// 3. KAGE THE NINJA (DRAWING LOGIC)
// -------------------------------------------------------------
private fun DrawScope.drawKage(
    cx: Float,
    cy: Float,
    r: Float,
    state: PetState,
    tick: Int
) {
    val darkBlue = Color(0xFF1E252B)
    val faceSkin = Color(0xFFFFD1A9)
    val maskColor = Color(0xFF14191E)
    val accentRed = Color(0xFFE53935)

    val bounce = if (state == PetState.WALKING) sin(tick * 0.45f) * 4f else 0f
    val currentCy = cy + bounce

    // Spin or rotate matrix during ninja flips!
    var angle = 0f
    if (state == PetState.JUMPING) {
        angle = (tick % 360).toFloat() * 10f
    }

    rotate(degrees = angle, pivot = Offset(cx, currentCy)) {
        // Ninja shadow
        drawOval(
            color = Color(0x44000000),
            topLeft = Offset(cx - r * 0.8f, cy + r * 0.6f),
            size = Size(r * 1.6f, r * 0.25f)
        )

        // Trailing red headband scarf
        val waveOne = sin(tick * 0.3f) * 12f
        val waveTwo = cos(tick * 0.3f) * 12f
        val scarfPath = Path().apply {
            moveTo(cx - r * 0.6f, currentCy - r * 0.3f)
            quadraticTo(cx - r * 1.3f, currentCy - r * 0.5f + waveOne, cx - r * 1.6f, currentCy - r * 0.2f + waveTwo)
            lineTo(cx - r * 1.5f, currentCy - r * 0.1f + waveTwo)
            quadraticTo(cx - r * 1.2f, currentCy - r * 0.35f + waveOne, cx - r * 0.6f, currentCy - r * 0.15f)
            close()
        }
        drawPath(scarfPath, accentRed)

        // Body robe
        drawRoundRect(
            color = darkBlue,
            topLeft = Offset(cx - r * 0.7f, currentCy - r * 0.5f),
            size = Size(r * 1.4f, r * 1.1f),
            cornerRadius = CornerRadius(20f, 20f)
        )

        // Red sash belt
        drawRect(
            color = accentRed,
            topLeft = Offset(cx - r * 0.7f, currentCy + r * 0.2f),
            size = Size(r * 1.4f, r * 0.15f)
        )

        // Ninja Mask / Hood
        drawCircle(
            color = maskColor,
            radius = r * 0.55f,
            center = Offset(cx, currentCy - r * 0.25f)
        )

        // Cutout for eyes
        drawRoundRect(
            color = faceSkin,
            topLeft = Offset(cx - r * 0.35f, currentCy - r * 0.38f),
            size = Size(r * 0.7f, r * 0.22f),
            cornerRadius = CornerRadius(10f, 10f)
        )

        if (state == PetState.SLEEPING) {
            // Sleeping eyes slits
            drawLine(maskColor, Offset(cx - r * 0.25f, currentCy - r * 0.3f), Offset(cx - r * 0.1f, currentCy - r * 0.28f), strokeWidth = 3f)
            drawLine(maskColor, Offset(cx + r * 0.1f, currentCy - r * 0.28f), Offset(cx + r * 0.25f, currentCy - r * 0.3f), strokeWidth = 3f)
        } else {
            // Fierce slash glowing red eyes!
            drawOval(
                color = accentRed,
                topLeft = Offset(cx - r * 0.25f, currentCy - r * 0.35f),
                size = Size(r * 0.15f, r * 0.08f)
            )
            drawOval(
                color = accentRed,
                topLeft = Offset(cx + r * 0.1f, currentCy - r * 0.35f),
                size = Size(r * 0.15f, r * 0.08f)
            )
            // Little highlight
            drawCircle(Color.White, radius = 3f, center = Offset(cx - r * 0.18f, currentCy - r * 0.32f))
            drawCircle(Color.White, radius = 3f, center = Offset(cx + r * 0.18f, currentCy - r * 0.32f))
        }

        // Crossed Katana hilt on the back
        if (state != PetState.SLEEPING) {
            drawLine(
                color = Color(0xFFC0C2C3),
                start = Offset(cx + r * 0.3f, currentCy - r * 0.8f),
                end = Offset(cx + r * 0.7f, currentCy - r * 1.2f),
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
            drawCircle(accentRed, radius = 6f, center = Offset(cx + r * 0.7f, currentCy - r * 1.2f))
        }
    }
}

// -------------------------------------------------------------
// 4. REX THE DINO (DRAWING LOGIC)
// -------------------------------------------------------------
private fun DrawScope.drawRex(
    cx: Float,
    cy: Float,
    r: Float,
    state: PetState,
    tick: Int
) {
    val dinoGreen = Color(0xFF81C784)
    val scaleOrange = Color(0xFFFF8A65)
    val paleYellow = Color(0xFFFFF9C4)
    val eyesColor = Color(0xFF2E3D30)

    val walkWaddle = if (state == PetState.WALKING) sin(tick * 0.5f) * 6f else 0f
    val currentCy = cy + if (state == PetState.SLEEPING) r * 0.2f else walkWaddle

    // Shadow
    drawOval(
        color = Color(0x33000000),
        topLeft = Offset(cx - r * 1.0f, cy + r * 0.7f),
        size = Size(r * 2.0f, r * 0.3f)
    )

    // Tail representing dinosaur
    val tailSwing = if (state == PetState.SLEEPING) 0f else sin(tick * 0.2f) * 15f
    rotate(degrees = tailSwing, pivot = Offset(cx - r * 0.6f, currentCy + r * 0.2f)) {
        val tailPath = Path().apply {
            moveTo(cx - r * 0.5f, currentCy + r * 0.1f)
            quadraticTo(cx - r * 1.3f, currentCy + r * 0.3f, cx - r * 1.2f, currentCy - r * 0.1f)
            quadraticTo(cx - r * 0.5f, currentCy - r * 0.1f, cx - r * 0.4f, currentCy)
        }
        drawPath(tailPath, dinoGreen)
        // Scaly tail tips
        drawCircle(scaleOrange, radius = r * 0.12f, center = Offset(cx - r * 1.2f, currentCy - r * 0.1f))
    }

    // Back spikes (3 scales along the spine)
    drawCircle(scaleOrange, radius = r * 0.15f, center = Offset(cx - r * 0.5f, currentCy - r * 0.5f))
    drawCircle(scaleOrange, radius = r * 0.15f, center = Offset(cx - r * 0.1f, currentCy - r * 0.65f))
    drawCircle(scaleOrange, radius = r * 0.15f, center = Offset(cx + r * 0.3f, currentCy - r * 0.6f))

    // Rounded Dino Body
    drawRoundRect(
        color = dinoGreen,
        topLeft = Offset(cx - r * 0.7f, currentCy - r * 0.5f),
        size = Size(r * 1.5f, r * 1.1f),
        cornerRadius = CornerRadius(r * 0.45f, r * 0.45f)
    )

    // Dino pale underbelly patch
    drawOval(
        color = paleYellow,
        topLeft = Offset(cx - r * 0.2f, currentCy - r * 0.1f),
        size = Size(r * 0.6f, r * 0.5f)
    )

    // Cute Dino Eyes
    if (state == PetState.SLEEPING) {
        // Sleep curve eyes
        drawArc(
            color = eyesColor, startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(cx + r * 0.25f, currentCy - r * 0.25f), size = Size(r * 0.2f, r * 0.12f),
            style = Stroke(4f)
        )
    } else {
        // Normal wide friendly eyes
        drawCircle(eyesColor, radius = r * 0.14f, center = Offset(cx + r * 0.35f, currentCy - r * 0.2f))
        drawCircle(Color.White, radius = r * 0.05f, center = Offset(cx + r * 0.30f, currentCy - r * 0.24f))
    }

    // Little dino nose nostrils list and open roaring mouth!
    if (state == PetState.IDLE && (tick % 24 < 6)) {
        // Roar with open mouth! (Draw a cute red semicircle)
        drawArc(
            color = Color(0xFFE57373),
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(cx + r * 0.3f, currentCy - r * 0.05f),
            size = Size(r * 0.35f, r * 0.3f)
        )
    } else {
        // Tiny dark smile line
        drawArc(
            color = eyesColor, startAngle = 0f, sweepAngle = 100f, useCenter = false,
            topLeft = Offset(cx + r * 0.25f, currentCy - r * 0.05f), size = Size(r * 0.25f, r * 0.15f),
            style = Stroke(3f)
        )
    }

    // Little dino arms (Rex has famously tiny arms waving!)
    val armOffset = if (state == PetState.FALLING) sin(tick * 0.6f) * 12f else 0f
    rotate(degrees = armOffset, pivot = Offset(cx + r * 0.2f, currentCy - r * 0.05f)) {
        drawRoundRect(
            color = dinoGreen,
            topLeft = Offset(cx + r * 0.15f, currentCy - r * 0.08f),
            size = Size(r * 0.25f, r * 0.12f),
            cornerRadius = CornerRadius(5f, 5f)
        )
    }
}

// -------------------------------------------------------------
// 5. PANDA (DRAWING LOGIC)
// -------------------------------------------------------------
private fun DrawScope.drawPanda(
    cx: Float,
    cy: Float,
    r: Float,
    state: PetState,
    tick: Int
) {
    val pandaWhite = Color(0xFFFFFFFF)
    val pandaBlack = Color(0xFF1E1E1E)
    val pandaPink = Color(0xFFFFB6C1)

    val walkWaddle = if (state == PetState.WALKING) sin(tick * 0.5f) * 6f else 0f
    val currentCy = cy + if (state == PetState.SLEEPING) r * 0.15f else walkWaddle

    // 1. Shadow underneath
    drawOval(
        color = Color(0x33000000),
        topLeft = Offset(cx - r * 1.0f, cy + r * 0.7f),
        size = Size(r * 2.0f, r * 0.3f)
    )

    // 2. Ears (Panda black ears on top corners of the head)
    drawCircle(pandaBlack, radius = r * 0.3f, center = Offset(cx - r * 0.65f, currentCy - r * 0.65f))
    drawCircle(pandaBlack, radius = r * 0.3f, center = Offset(cx + r * 0.65f, currentCy - r * 0.65f))

    // 3. Body (Panda body black and white parts)
    drawRoundRect(
        color = pandaBlack,
        topLeft = Offset(cx - r * 0.7f, currentCy - r * 0.1f),
        size = Size(r * 1.4f, r * 0.85f),
        cornerRadius = CornerRadius(r * 0.4f, r * 0.4f)
    )
    drawCircle(
        color = pandaWhite,
        radius = r * 0.5f,
        center = Offset(cx, currentCy + r * 0.35f)
    )

    // 4. Head (Big white round cute face)
    drawCircle(
        color = pandaWhite,
        radius = r * 0.75f,
        center = Offset(cx, currentCy - r * 0.1f)
    )

    // 5. Black eye patches (Distinct panda shape!)
    rotate(degrees = -10f, pivot = Offset(cx - r * 0.35f, currentCy - r * 0.15f)) {
        drawOval(
            color = pandaBlack,
            topLeft = Offset(cx - r * 0.55f, currentCy - r * 0.3f),
            size = Size(r * 0.4f, r * 0.3f)
        )
    }
    rotate(degrees = 10f, pivot = Offset(cx + r * 0.35f, currentCy - r * 0.15f)) {
        drawOval(
            color = pandaBlack,
            topLeft = Offset(cx + r * 0.15f, currentCy - r * 0.3f),
            size = Size(r * 0.4f, r * 0.3f)
        )
    }

    // 6. Eyes (Shining white or sleeping curves inside the black patches)
    if (state == PetState.SLEEPING) {
        drawArc(
            color = pandaWhite, startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(cx - r * 0.45f, currentCy - r * 0.22f), size = Size(r * 0.2f, r * 0.12f),
            style = Stroke(3f)
        )
        drawArc(
            color = pandaWhite, startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(cx + r * 0.25f, currentCy - r * 0.22f), size = Size(r * 0.2f, r * 0.12f),
            style = Stroke(3f)
        )
    } else {
        drawCircle(Color.White, radius = r * 0.09f, center = Offset(cx - r * 0.35f, currentCy - r * 0.18f))
        drawCircle(Color.White, radius = r * 0.09f, center = Offset(cx + r * 0.35f, currentCy - r * 0.18f))
        drawCircle(Color.White, radius = r * 0.04f, center = Offset(cx - r * 0.32f, currentCy - r * 0.21f))
        drawCircle(Color.White, radius = r * 0.04f, center = Offset(cx + r * 0.38f, currentCy - r * 0.21f))
    }

    // 7. Small black triangular nose
    val nosePath = Path().apply {
        moveTo(cx, currentCy - r * 0.05f)
        lineTo(cx - r * 0.08f, currentCy - r * 0.11f)
        lineTo(cx + r * 0.08f, currentCy - r * 0.11f)
        close()
    }
    drawPath(nosePath, pandaBlack)

    // 8. Pink cheeks
    drawCircle(pandaPink.copy(alpha = 0.7f), radius = r * 0.12f, center = Offset(cx - r * 0.55f, currentCy - r * 0.02f))
    drawCircle(pandaPink.copy(alpha = 0.7f), radius = r * 0.12f, center = Offset(cx + r * 0.55f, currentCy - r * 0.02f))

    // 9. Mouth
    drawArc(
        color = pandaBlack, startAngle = 0f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(cx - r * 0.08f, currentCy - r * 0.04f), size = Size(r * 0.16f, r * 0.1f),
        style = Stroke(3f)
    )
}
