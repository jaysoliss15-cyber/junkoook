package com.example.render

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.model.ActorExpression
import com.example.model.AnimationStyle
import com.example.model.MetaphorVisual
import com.example.model.SceneScript
import com.example.model.StatusPill
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object MetaphorVisualRenderer {

    fun resolveMetaphorVisual(scene: SceneScript, style: AnimationStyle): MetaphorVisual {
        if (scene.metaphorVisual != MetaphorVisual.NONE) return scene.metaphorVisual
        if (style != AnimationStyle.VIRAL_2D_EXPLAINER) return MetaphorVisual.NONE
        val text = "${scene.tagalogNarration} ${scene.topicPillLabel} ${scene.calloutBanner}".lowercase()
        when {
            text.contains("itlog") || text.contains("egg") || text.contains("sisiw") || text.contains("chick") || text.contains("basag") ->
                return MetaphorVisual.CRACKING_EGG_CHICK
            text.contains("money tree") || text.contains("puno ng pera") || text.contains("tanim") || text.contains("bunga") ->
                return MetaphorVisual.GROWING_MONEY_TREE
            text.contains("utak") || text.contains("doomscroll") || text.contains("lowbat") || text.contains("baterya") || text.contains("battery") ->
                return MetaphorVisual.BRAIN_BATTERY_DRAIN
            text.contains("kumpara") || text.contains("timbang") || text.contains("balance") || text.contains(" vs ") ->
                return MetaphorVisual.SCALE_BALANCE_COMPARE
            text.contains("rule of 72") || text.contains("rocket") || text.contains("chart") || text.contains("compound") || text.contains("₱100,000") ->
                return MetaphorVisual.ROCKET_GROWTH_CHART
            text.contains("sikreto") || text.contains("secret") || text.contains("susi") || text.contains("unlock") ->
                return MetaphorVisual.LOCK_AND_KEY_SECRET
            text.contains("shield") || text.contains("protekta") || text.contains("emergency fund") || text.contains("panangga") ->
                return MetaphorVisual.SHIELD_BLOCKING_ARROWS
            text.contains("domino") || text.contains("sunod-sunod") || text.contains("chain reaction") ->
                return MetaphorVisual.DOMINO_CHAIN_REACTION
        }
        return MetaphorVisual.NONE
    }

    fun isDiagramAlongsideCharacter(metaphor: MetaphorVisual): Boolean {
        return metaphor != MetaphorVisual.NONE && metaphor != MetaphorVisual.SUNBURST_SPOTLIGHT
    }

    /**
     * Draws the rotating 16-ray radial sunburst spotlight behind the hero character/prop.
     */
    fun drawSunburstBackground(
        canvas: Canvas,
        w: Float,
        h: Float,
        scene: SceneScript,
        globalTimeSec: Float
    ) {
        val u = w / 1080f
        val isDanger = scene.statusPill == StatusPill.PROBLEM ||
            scene.expression == ActorExpression.PANIC_SWEAT_FIRE ||
            scene.expression == ActorExpression.ANGRY_EVIL ||
            scene.expression == ActorExpression.TONGUE_STING

        val baseColor = if (isDanger) 0xFFDC2626.toInt() else 0xFF0284C7.toInt()
        val rayColorA = if (isDanger) 0xFFFB923C.toInt() else 0xFF22D3EE.toInt()
        val rayColorB = if (isDanger) 0xFFEA580C.toInt() else 0xFF0EA5E9.toInt()
        val coreGlowColor = if (isDanger) 0xFFFDE047.toInt() else 0xFFE0F2FE.toInt()

        canvas.drawColor(baseColor)

        val cx = w * 0.5f
        val cy = h * 0.44f
        val radius = h * 0.95f
        val rotDeg = globalTimeSec * 14f

        canvas.save()
        canvas.rotate(rotDeg, cx, cy)
        val rayCount = 16
        val stepDeg = 360f / rayCount
        val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until rayCount) {
            rayPaint.color = if (i % 2 == 0) rayColorA else rayColorB
            val a0 = Math.toRadians((i * stepDeg).toDouble())
            val a1 = Math.toRadians(((i + 0.56f) * stepDeg).toDouble())
            val path = Path().apply {
                moveTo(cx, cy)
                lineTo(cx + cos(a0).toFloat() * radius, cy + sin(a0).toFloat() * radius)
                lineTo(cx + cos(a1).toFloat() * radius, cy + sin(a1).toFloat() * radius)
                close()
            }
            canvas.drawPath(path, rayPaint)
        }
        canvas.restore()

        // Bright center spotlight halo behind character/prop
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, 460f * u,
                intArrayOf(coreGlowColor, (coreGlowColor and 0x00FFFFFF) or 0x55000000, 0x00000000),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, 460f * u, haloPaint)
    }

    /**
     * Draws animated 2D visual metaphor diagrams in the upper-right / upper-center explainer stage.
     */
    fun drawDiagram(
        canvas: Canvas,
        w: Float,
        h: Float,
        metaphor: MetaphorVisual,
        scene: SceneScript,
        sceneElapsedSec: Float,
        sceneDurationSec: Float,
        globalTimeSec: Float
    ) {
        if (!isDiagramAlongsideCharacter(metaphor)) return
        val u = w / 1080f

        val cardRect = RectF(w * 0.38f, h * 0.145f, w * 0.95f, h * 0.455f)
        val enterScale = Easing.easeOutBack((sceneElapsedSec / 0.42f).coerceIn(0f, 1f)).coerceAtLeast(0.05f)
        val cx = cardRect.centerX()
        val cy = cardRect.centerY()

        canvas.save()
        canvas.scale(enterScale, enterScale, cx, cy)

        // Soft drop shadow + crisp dark 6px cartoon frame
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x55000000
            maskFilter = BlurMaskFilter((16f * u).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(
            RectF(cardRect.left + 8f * u, cardRect.top + 12f * u, cardRect.right + 8f * u, cardRect.bottom + 12f * u),
            28f * u, 28f * u, shadowPaint
        )
        canvas.drawRoundRect(
            cardRect, 28f * u, 28f * u,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF5F8FAFC.toInt() }
        )
        val frameStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0E1018.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = 6f * u
        }
        canvas.drawRoundRect(cardRect, 28f * u, 28f * u, frameStroke)

        when (metaphor) {
            MetaphorVisual.CHOMP_CHAIN -> drawChompChain(canvas, cardRect, u, scene, sceneElapsedSec, globalTimeSec, frameStroke)
            MetaphorVisual.TISSUE_CROSS_SECTION -> drawTissueCrossSection(canvas, cardRect, u, sceneElapsedSec, sceneDurationSec, globalTimeSec, frameStroke)
            MetaphorVisual.TIME_LAPSE_CLOCK -> drawTimeLapseClock(canvas, cardRect, u, sceneElapsedSec, globalTimeSec, frameStroke)
            MetaphorVisual.BOILING_POT_PROCESS -> drawBoilingPotProcess(canvas, cardRect, u, globalTimeSec, frameStroke)
            MetaphorVisual.CRACKING_EGG_CHICK -> drawCrackingEggChick(canvas, cardRect, u, sceneElapsedSec, globalTimeSec, frameStroke)
            MetaphorVisual.GROWING_MONEY_TREE -> drawGrowingMoneyTree(canvas, cardRect, u, sceneElapsedSec, globalTimeSec, frameStroke)
            MetaphorVisual.BRAIN_BATTERY_DRAIN -> drawBrainBatteryDrain(canvas, cardRect, u, scene, sceneElapsedSec, globalTimeSec, frameStroke)
            MetaphorVisual.SCALE_BALANCE_COMPARE -> drawScaleBalanceCompare(canvas, cardRect, u, globalTimeSec, frameStroke)
            MetaphorVisual.ROCKET_GROWTH_CHART -> drawRocketGrowthChart(canvas, cardRect, u, sceneElapsedSec, globalTimeSec, frameStroke)
            MetaphorVisual.LOCK_AND_KEY_SECRET -> drawLockAndKeySecret(canvas, cardRect, u, sceneElapsedSec, globalTimeSec, frameStroke)
            MetaphorVisual.SHIELD_BLOCKING_ARROWS -> drawShieldBlockingArrows(canvas, cardRect, u, globalTimeSec, frameStroke)
            MetaphorVisual.DOMINO_CHAIN_REACTION -> drawDominoChainReaction(canvas, cardRect, u, sceneElapsedSec, frameStroke)
            MetaphorVisual.SUNBURST_SPOTLIGHT, MetaphorVisual.NONE -> Unit
        }

        canvas.restore()
    }

    private fun drawChompChain(
        canvas: Canvas,
        box: RectF,
        u: Float,
        scene: SceneScript,
        sceneElapsedSec: Float,
        globalTimeSec: Float,
        outline: Paint
    ) {
        val text = "${scene.tagalogNarration} ${scene.topicPillLabel} ${scene.calloutBanner}"
        val label = when {
            text.contains("pera", ignoreCase = true) ||
                text.contains("ipon", ignoreCase = true) -> "MONEY CHAIN"
            text.contains("data", ignoreCase = true) ||
                text.contains("wifi", ignoreCase = true) -> "DATA CHAIN"
            text.contains("protein", ignoreCase = true) ||
                text.contains("amino", ignoreCase = true) ||
                text.contains("digest", ignoreCase = true) ||
                text.contains("tiyan", ignoreCase = true) -> "PROTEIN CHAIN"
            else -> scene.topicPillLabel.trim().uppercase().take(16).ifBlank { "PROCESS CHAIN" }
        }
        drawDiagramHeader(canvas, box, u, label, 0xFF2563EB.toInt())

        val beadColors = intArrayOf(
            0xFFEF4444.toInt(), 0xFFF59E0B.toInt(), 0xFF10B981.toInt(),
            0xFF3B82F6.toInt(), 0xFFA855F7.toInt(), 0xFFEC4899.toInt()
        )
        val beadCount = 6
        val startX = box.left + 72f * u
        val endX = box.right - 72f * u
        val baseY = box.centerY() + 36f * u
        val chompProg = ((sceneElapsedSec * 0.42f) % 1.15f).coerceIn(0f, 1.05f)
        val chompX = startX + (endX - startX) * chompProg

        // Connecting bonds
        val bondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF334155.toInt()
            strokeWidth = 10f * u
            strokeCap = Paint.Cap.ROUND
        }
        val beadPoints = Array(beadCount) { idx ->
            val frac = idx / (beadCount - 1f)
            val bx = startX + (endX - startX) * frac
            val by = baseY + sin(frac * PI.toFloat() * 2f + globalTimeSec * 2.5f) * 34f * u
            bx to by
        }
        for (i in 0 until beadCount - 1) {
            val (x0, y0) = beadPoints[i]
            val (x1, y1) = beadPoints[i + 1]
            if (x1 > chompX - 18f * u) {
                canvas.drawLine(x0, y0, x1, y1, bondPaint)
            }
        }

        // Multi-colored beads (pop off as Pac-Man enzymes pass)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88FFFFFF.toInt() }
        for (i in 0 until beadCount) {
            val (bx, by) = beadPoints[i]
            if (bx <= chompX - 12f * u) {
                // Eaten sparkle pop
                val dist = chompX - bx
                if (dist < 75f * u) {
                    fillPaint.color = beadColors[i % beadColors.size]
                    canvas.drawCircle(bx - 12f * u, by - dist * 0.4f, 6f * u, fillPaint)
                    canvas.drawCircle(bx + 10f * u, by + dist * 0.3f, 5f * u, fillPaint)
                }
                continue
            }
            val r = 29f * u
            fillPaint.color = beadColors[i % beadColors.size]
            canvas.drawCircle(bx, by, r, fillPaint)
            canvas.drawCircle(bx - 8f * u, by - 9f * u, 8f * u, shinePaint)
            canvas.drawCircle(bx, by, r, outline)
        }

        // Two Pac-Man enzyme spheres chomping forward
        val chompMouth = 12f + abs(sin(globalTimeSec * 14f)) * 34f
        fun drawEnzyme(ex: Float, ey: Float, color: Int, radius: Float) {
            val rect = RectF(ex - radius, ey - radius, ex + radius, ey + radius)
            fillPaint.color = color
            canvas.drawArc(rect, chompMouth, 360f - chompMouth * 2f, true, fillPaint)
            canvas.drawArc(rect, chompMouth, 360f - chompMouth * 2f, true, outline)
            canvas.drawCircle(ex + radius * 0.12f, ey - radius * 0.44f, 6.5f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.BLACK })
        }

        val chompY = baseY + sin((chompProg * PI.toFloat() * 2f) + globalTimeSec * 2.5f) * 34f * u
        drawEnzyme(chompX, chompY, 0xFFFFD600.toInt(), 42f * u)
        drawEnzyme((chompX - 78f * u).coerceAtLeast(box.left + 44f * u), chompY - 46f * u, 0xFF22D3EE.toInt(), 28f * u)
    }

    private fun drawTissueCrossSection(
        canvas: Canvas,
        box: RectF,
        u: Float,
        sceneElapsedSec: Float,
        sceneDurationSec: Float,
        globalTimeSec: Float,
        outline: Paint
    ) {
        drawDiagramHeader(canvas, box, u, "PROTECTIVE BARRIER", 0xFFDC2626.toInt())

        val blockL = box.left + 46f * u
        val blockR = box.right - 46f * u
        val blockTop = box.top + 116f * u
        val blockBot = box.bottom - 38f * u
        val totalH = blockBot - blockTop

        val dissolve = (sceneElapsedSec / (sceneDurationSec * 0.72f).coerceAtLeast(0.8f)).coerceIn(0f, 1f)

        // Deep tissue base + nerve layer
        val deepRect = RectF(blockL, blockTop + totalH * 0.45f, blockR, blockBot)
        canvas.drawRoundRect(deepRect, 14f * u, 14f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFCA5A5.toInt() })
        canvas.drawRoundRect(deepRect, 14f * u, 14f * u, outline)

        // Cellular middle bricks
        val cellTop = blockTop + totalH * 0.22f
        val cellBot = blockTop + totalH * 0.48f
        val cellCount = 5
        val cellW = (blockR - blockL) / cellCount
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF43F5E.toInt() }
        for (i in 0 until cellCount) {
            val cr = RectF(blockL + i * cellW + 3f * u, cellTop, blockL + (i + 1) * cellW - 3f * u, cellBot)
            canvas.drawRoundRect(cr, 8f * u, 8f * u, cellPaint)
            canvas.drawRoundRect(cr, 8f * u, 8f * u, outline)
        }

        // Top protective mucus/shield barrier dissolving into gaps
        val shieldH = totalH * 0.20f * (1f - dissolve * 0.72f)
        val shieldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF38BDF8.toInt() }
        for (i in 0 until cellCount) {
            if (dissolve > 0.35f && (i == 1 || i == 3)) continue // dissolved breach gaps!
            val sr = RectF(blockL + i * cellW + 2f * u, blockTop, blockL + (i + 1) * cellW - 2f * u, blockTop + shieldH.coerceAtLeast(6f * u))
            canvas.drawRoundRect(sr, 7f * u, 7f * u, shieldPaint)
            canvas.drawRoundRect(sr, 7f * u, 7f * u, outline)
        }

        // Falling yellow acid/enzyme droplets penetrating breaches
        val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFACC15.toInt() }
        for (idx in listOf(1, 3)) {
            val dx = blockL + (idx + 0.5f) * cellW
            val fallFrac = ((globalTimeSec * 0.9f + idx * 0.35f) % 1f)
            val dy = (blockTop - 32f * u) + fallFrac * (totalH * 0.55f)
            val drop = Path().apply {
                moveTo(dx, dy - 16f * u)
                quadTo(dx + 14f * u, dy + 8f * u, dx, dy + 16f * u)
                quadTo(dx - 14f * u, dy + 8f * u, dx, dy - 16f * u)
            }
            canvas.drawPath(drop, dropPaint)
            canvas.drawPath(drop, outline)
        }

        // Glowing lightning-bolt nerve sparks pulsing underneath
        val sparkAlpha = (140 + (115 * abs(sin(globalTimeSec * 12f)))).toInt().coerceIn(80, 255)
        val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(sparkAlpha, 250, 204, 21)
            setStyle(Paint.Style.STROKE)
            strokeWidth = 7f * u
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val nerveY = deepRect.centerY()
        for (idx in listOf(1, 3)) {
            val sx = blockL + (idx + 0.5f) * cellW
            val bolt = Path().apply {
                moveTo(sx - 10f * u, nerveY - 36f * u)
                lineTo(sx + 14f * u, nerveY - 8f * u)
                lineTo(sx - 8f * u, nerveY - 2f * u)
                lineTo(sx + 18f * u, nerveY + 34f * u)
            }
            canvas.drawPath(bolt, boltPaint)
        }
    }

    private fun drawTimeLapseClock(
        canvas: Canvas,
        box: RectF,
        u: Float,
        sceneElapsedSec: Float,
        globalTimeSec: Float,
        outline: Paint
    ) {
        drawDiagramHeader(canvas, box, u, "TIME-LAPSE", 0xFF0284C7.toInt())

        val cx = box.centerX()
        val cy = box.centerY() + 28f * u
        val r = minOf(box.width(), box.height()) * 0.30f

        // Swept speed arc behind hands
        val sweepDeg = (sceneElapsedSec * 360f) % 360f
        canvas.drawArc(
            RectF(cx - r * 1.18f, cy - r * 1.18f, cx + r * 1.18f, cy + r * 1.18f),
            sweepDeg - 100f, 100f, false,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x6638BDF8
                setStyle(Paint.Style.STROKE)
                strokeWidth = 14f * u
                strokeCap = Paint.Cap.ROUND
            }
        )

        // Clock casing & face
        canvas.drawCircle(cx, cy, r + 14f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0EA5E9.toInt() })
        canvas.drawCircle(cx, cy, r + 14f * u, outline)
        canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawCircle(cx, cy, r, outline)

        // 12 hour tick marks
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1E293B.toInt()
            strokeWidth = 5f * u
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0 until 12) {
            val a = Math.toRadians((i * 30.0))
            canvas.drawLine(
                cx + cos(a).toFloat() * (r * 0.78f),
                cy + sin(a).toFloat() * (r * 0.78f),
                cx + cos(a).toFloat() * (r * 0.90f),
                cy + sin(a).toFloat() * (r * 0.90f),
                tickPaint
            )
        }

        // Rapidly spinning hour & minute hands
        val minAngle = Math.toRadians((globalTimeSec * 420.0 - 90.0))
        val hourAngle = Math.toRadians((globalTimeSec * 70.0 - 90.0))
        val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0E1018.toInt()
            strokeWidth = 9f * u
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(cx, cy, cx + cos(hourAngle).toFloat() * (r * 0.48f), cy + sin(hourAngle).toFloat() * (r * 0.48f), handPaint)
        handPaint.color = 0xFFEF4444.toInt()
        handPaint.strokeWidth = 6f * u
        canvas.drawLine(cx, cy, cx + cos(minAngle).toFloat() * (r * 0.72f), cy + sin(minAngle).toFloat() * (r * 0.72f), handPaint)
        canvas.drawCircle(cx, cy, 10f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0E1018.toInt() })

        // Radial sparkle bursts around clock rim
        val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD600.toInt() }
        for (i in 0 until 4) {
            val pulse = 0.75f + 0.35f * abs(sin(globalTimeSec * 7f + i))
            val ang = Math.toRadians((45.0 + i * 90.0))
            val sx = cx + cos(ang).toFloat() * (r + 42f * u)
            val sy = cy + sin(ang).toFloat() * (r + 42f * u)
            val s = 18f * u * pulse
            val star = Path().apply {
                moveTo(sx, sy - s)
                lineTo(sx + s * 0.32f, sy - s * 0.32f)
                lineTo(sx + s, sy)
                lineTo(sx + s * 0.32f, sy + s * 0.32f)
                lineTo(sx, sy + s)
                lineTo(sx - s * 0.32f, sy + s * 0.32f)
                lineTo(sx - s, sy)
                lineTo(sx - s * 0.32f, sy - s * 0.32f)
                close()
            }
            canvas.drawPath(star, sparkPaint)
            canvas.drawPath(star, Paint(outline).apply { strokeWidth = 3f * u })
        }
    }

    private fun drawBoilingPotProcess(
        canvas: Canvas,
        box: RectF,
        u: Float,
        globalTimeSec: Float,
        outline: Paint
    ) {
        drawDiagramHeader(canvas, box, u, "ACTIVE PROCESS", 0xFFEA580C.toInt())

        val cx = box.centerX()
        val stoveY = box.bottom - 38f * u
        val potW = box.width() * 0.56f
        val potH = box.height() * 0.34f
        val potBot = stoveY - 32f * u
        val potTop = potBot - potH

        // Animated stove flames under pot
        val flameOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF5722.toInt() }
        val flameInner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD600.toInt() }
        for (i in -2..2) {
            val fx = cx + i * 38f * u
            val flicker = 1f + 0.25f * sin(globalTimeSec * 16f + i * 1.4f)
            val fPath = Path().apply {
                moveTo(fx - 18f * u, stoveY)
                quadTo(fx - 12f * u, stoveY - 34f * u * flicker, fx, stoveY - 46f * u * flicker)
                quadTo(fx + 12f * u, stoveY - 34f * u * flicker, fx + 18f * u, stoveY)
                close()
            }
            canvas.drawPath(fPath, flameOuter)
            canvas.drawCircle(fx, stoveY - 12f * u, 9f * u * flicker, flameInner)
        }
        // Stove burner bar
        canvas.drawRoundRect(
            RectF(cx - potW * 0.56f, stoveY, cx + potW * 0.56f, stoveY + 14f * u),
            6f * u, 6f * u,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF334155.toInt() }
        )

        // Animated wavy rising steam paths
        val steamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99CBD5E1.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = 7f * u
            strokeCap = Paint.Cap.ROUND
        }
        for (i in -1..1) {
            val sx = cx + i * 54f * u
            val phase = globalTimeSec * 4.5f + i * 1.2f
            val steam = Path().apply {
                moveTo(sx, potTop - 16f * u)
                cubicTo(
                    sx - 20f * u * sin(phase), potTop - 48f * u,
                    sx + 20f * u * sin(phase), potTop - 82f * u,
                    sx + sin(phase) * 10f * u, potTop - 112f * u
                )
            }
            canvas.drawPath(steam, steamPaint)
        }

        // Bubbling particles popping from liquid surface
        val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFDE047.toInt() }
        for (i in 0 until 6) {
            val prog = ((globalTimeSec * (0.8f + (i % 3) * 0.25f) + i * 0.19f) % 1f)
            val bx = cx + (i - 2.5f) * 44f * u + sin(globalTimeSec * 5f + i) * 8f * u
            val by = potTop - prog * 68f * u
            val br = (14f - prog * 6f) * u
            canvas.drawCircle(bx, by, br, bubblePaint)
            canvas.drawCircle(bx, by, br, Paint(outline).apply { strokeWidth = 3f * u })
        }

        // Steel pot body + handles + shimmering broth rim
        val potRect = RectF(cx - potW * 0.5f, potTop, cx + potW * 0.5f, potBot)
        val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF475569.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = 10f * u
        }
        canvas.drawArc(RectF(potRect.left - 24f * u, potTop + 26f * u, potRect.left + 8f * u, potTop + 66f * u), 90f, 180f, false, handlePaint)
        canvas.drawArc(RectF(potRect.right - 8f * u, potTop + 26f * u, potRect.right + 24f * u, potTop + 66f * u), -90f, 180f, false, handlePaint)

        val potFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                potRect.left, potRect.top, potRect.right, potRect.top,
                intArrayOf(0xFFE2E8F0.toInt(), 0xFF94A3B8.toInt(), 0xFF64748B.toInt()),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(potRect, 22f * u, 22f * u, potFill)
        canvas.drawRoundRect(potRect, 22f * u, 22f * u, outline)

        // Simmering liquid oval at top rim
        val liquidRect = RectF(potRect.left + 10f * u, potTop - 14f * u, potRect.right - 10f * u, potTop + 16f * u)
        canvas.drawOval(liquidRect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF59E0B.toInt() })
        canvas.drawOval(liquidRect, outline)
    }

    private fun drawCrackingEggChick(
        canvas: Canvas,
        box: RectF,
        u: Float,
        sceneElapsedSec: Float,
        globalTimeSec: Float,
        outline: Paint
    ) {
        drawDiagramHeader(canvas, box, u, "BREAKTHROUGH", 0xFFF59E0B.toInt())
        val cx = box.centerX()
        val cy = box.centerY() + 36f * u
        val hatch = ((sceneElapsedSec / 2.2f) % 1.15f).coerceIn(0f, 1f)
        val wobble = if (hatch < 0.45f) sin(globalTimeSec * 16f) * 6f else 0f

        canvas.save()
        canvas.rotate(wobble, cx, cy + 50f * u)

        // Emerging yellow chick when hatch > 0.45
        if (hatch > 0.40f) {
            val pop = Easing.easeOutBack(((hatch - 0.40f) / 0.45f).coerceIn(0f, 1f))
            val chickY = cy - 18f * u - pop * 48f * u
            canvas.drawCircle(cx, chickY, 44f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFACC15.toInt() })
            canvas.drawCircle(cx, chickY, 44f * u, outline)
            val eyeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
            canvas.drawCircle(cx - 15f * u, chickY - 8f * u, 6f * u, eyeP)
            canvas.drawCircle(cx + 15f * u, chickY - 8f * u, 6f * u, eyeP)
            val beak = Path().apply {
                moveTo(cx - 10f * u, chickY + 4f * u)
                lineTo(cx + 10f * u, chickY + 4f * u)
                lineTo(cx, chickY + 18f * u)
                close()
            }
            canvas.drawPath(beak, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEA580C.toInt() })
        }

        // Bottom half of egg shell with jagged zig-zag crack rim
        val shellRect = RectF(cx - 72f * u, cy - 84f * u, cx + 72f * u, cy + 92f * u)
        val botShell = Path().apply {
            arcTo(shellRect, 0f, 180f, true)
            lineTo(cx - 46f * u, cy - 8f * u)
            lineTo(cx - 18f * u, cy + 12f * u)
            lineTo(cx + 8f * u, cy - 10f * u)
            lineTo(cx + 38f * u, cy + 12f * u)
            close()
        }
        canvas.drawPath(botShell, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF8E7.toInt() })
        canvas.drawPath(botShell, outline)

        // Top shell cap lifting & tilting
        val lift = if (hatch > 0.35f) ((hatch - 0.35f) / 0.65f) * 58f * u else 0f
        canvas.save()
        canvas.translate(lift * 0.35f, -lift)
        canvas.rotate(lift * 0.35f, cx, cy - 20f * u)
        val topShell = Path().apply {
            arcTo(shellRect, 180f, 180f, true)
            lineTo(cx + 38f * u, cy + 12f * u)
            lineTo(cx + 8f * u, cy - 10f * u)
            lineTo(cx - 18f * u, cy + 12f * u)
            lineTo(cx - 46f * u, cy - 8f * u)
            close()
        }
        canvas.drawPath(topShell, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF8E7.toInt() })
        canvas.drawPath(topShell, outline)
        canvas.restore()
        canvas.restore()
    }

    private fun drawGrowingMoneyTree(
        canvas: Canvas,
        box: RectF,
        u: Float,
        sceneElapsedSec: Float,
        globalTimeSec: Float,
        outline: Paint
    ) {
        drawDiagramHeader(canvas, box, u, "COMPOUND TREE", 0xFF16A34A.toInt())
        val cx = box.centerX()
        val groundY = box.bottom - 38f * u
        val grow = Easing.smoothstep((sceneElapsedSec / 1.4f).coerceIn(0.25f, 1f))

        // Ground pot/soil mound
        canvas.drawOval(
            RectF(cx - 95f * u, groundY - 14f * u, cx + 95f * u, groundY + 18f * u),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF78350F.toInt() }
        )

        // Trunk + branches
        val trunkTop = groundY - 170f * u * grow
        val trunk = Path().apply {
            moveTo(cx - 22f * u, groundY)
            quadTo(cx - 12f * u, (groundY + trunkTop) * 0.5f, cx - 10f * u, trunkTop)
            lineTo(cx + 10f * u, trunkTop)
            quadTo(cx + 12f * u, (groundY + trunkTop) * 0.5f, cx + 22f * u, groundY)
            close()
        }
        canvas.drawPath(trunk, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF92400E.toInt() })
        canvas.drawPath(trunk, outline)

        // Lush green foliage clusters
        val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF22C55E.toInt() }
        val canopySpots = listOf(
            Triple(cx - 64f * u, trunkTop + 18f * u, 52f * u * grow),
            Triple(cx + 64f * u, trunkTop + 18f * u, 52f * u * grow),
            Triple(cx, trunkTop - 22f * u, 64f * u * grow)
        )
        canopySpots.forEach { (lx, ly, lr) ->
            canvas.drawCircle(lx, ly, lr, leafPaint)
            canvas.drawCircle(lx, ly, lr, outline)
        }

        // Blooming gold Peso coins
        val coinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFACC15.toInt() }
        val pesoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF92400E.toInt()
            textSize = 24f * u
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val coins = listOf(
            cx - 72f * u to trunkTop + 16f * u,
            cx + 70f * u to trunkTop + 12f * u,
            cx to trunkTop - 32f * u,
            cx - 28f * u to trunkTop + 34f * u,
            cx + 30f * u to trunkTop + 38f * u
        )
        coins.forEachIndexed { idx, (px, py) ->
            val bob = sin(globalTimeSec * 3.5f + idx) * 4f * u
            val r = 20f * u * grow
            canvas.drawCircle(px, py + bob, r, coinPaint)
            canvas.drawCircle(px, py + bob, r, Paint(outline).apply { strokeWidth = 3f * u })
            canvas.drawText("₱", px, py + bob + 8f * u, pesoPaint)
        }
    }

    private fun drawBrainBatteryDrain(
        canvas: Canvas,
        box: RectF,
        u: Float,
        scene: SceneScript,
        sceneElapsedSec: Float,
        globalTimeSec: Float,
        outline: Paint
    ) {
        val isCharging = scene.statusPill == StatusPill.FIX || scene.statusPill == StatusPill.GOAL
        drawDiagramHeader(
            canvas, box, u,
            if (isCharging) "BRAIN RECHARGE" else "ENERGY DRAIN",
            if (isCharging) 0xFF16A34A.toInt() else 0xFFDC2626.toInt()
        )
        val cy = box.centerY() + 30f * u
        val brainX = box.left + box.width() * 0.28f
        val batX = box.left + box.width() * 0.72f

        // Pink cartoon brain blob
        val brainP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF472B6.toInt() }
        canvas.drawCircle(brainX - 24f * u, cy, 42f * u, brainP)
        canvas.drawCircle(brainX + 24f * u, cy, 42f * u, brainP)
        canvas.drawCircle(brainX, cy - 22f * u, 44f * u, brainP)
        canvas.drawCircle(brainX - 24f * u, cy, 42f * u, outline)
        canvas.drawCircle(brainX + 24f * u, cy, 42f * u, outline)

        // Pulsing energy wire connecting brain -> battery
        val wirePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isCharging) 0xFF4ADE80.toInt() else 0xFFFACC15.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = 7f * u
            strokeCap = Paint.Cap.ROUND
        }
        val wire = Path().apply {
            moveTo(brainX + 58f * u, cy)
            lineTo(brainX + 86f * u, cy - 18f * u)
            lineTo(batX - 78f * u, cy + 16f * u)
            lineTo(batX - 54f * u, cy)
        }
        canvas.drawPath(wire, wirePaint)

        // Battery housing
        val batW = 88f * u
        val batH = 148f * u
        val batRect = RectF(batX - batW * 0.5f, cy - batH * 0.5f, batX + batW * 0.5f, cy + batH * 0.5f)
        canvas.drawRoundRect(
            RectF(batX - 22f * u, batRect.top - 16f * u, batX + 22f * u, batRect.top + 4f * u),
            6f * u, 6f * u,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF64748B.toInt() }
        )
        canvas.drawRoundRect(batRect, 14f * u, 14f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0F172A.toInt() })

        val cycle = (sceneElapsedSec / 2.0f).coerceIn(0f, 1f)
        val level = if (isCharging) (0.20f + cycle * 0.78f) else (0.88f - cycle * 0.70f)
        val fillColor = when {
            level > 0.55f -> 0xFF22C55E.toInt()
            level > 0.28f -> 0xFFFACC15.toInt()
            else -> 0xFFEF4444.toInt()
        }
        val innerH = (batH - 18f * u) * level.coerceIn(0.08f, 1f)
        val barRect = RectF(batRect.left + 9f * u, batRect.bottom - 9f * u - innerH, batRect.right - 9f * u, batRect.bottom - 9f * u)
        val blinkAlpha = if (!isCharging && level < 0.25f && sin(globalTimeSec * 12f) < 0f) 110 else 255
        canvas.drawRoundRect(barRect, 8f * u, 8f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
            alpha = blinkAlpha
        })
        canvas.drawRoundRect(batRect, 14f * u, 14f * u, outline)
    }

    private fun drawScaleBalanceCompare(
        canvas: Canvas,
        box: RectF,
        u: Float,
        globalTimeSec: Float,
        outline: Paint
    ) {
        drawDiagramHeader(canvas, box, u, "SMART CHOICE", 0xFF0284C7.toInt())
        val cx = box.centerX()
        val baseY = box.bottom - 42f * u
        val pivotY = box.centerY() - 6f * u

        // Stand & triangle fulcrum
        canvas.drawLine(cx, pivotY, cx, baseY, Paint(outline).apply { strokeWidth = 10f * u })
        canvas.drawRoundRect(
            RectF(cx - 72f * u, baseY - 12f * u, cx + 72f * u, baseY + 6f * u),
            8f * u, 8f * u,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF334155.toInt() }
        )

        val tiltDeg = -14f + sin(globalTimeSec * 2.2f) * 6f
        val beamHalf = box.width() * 0.34f
        val rad = Math.toRadians(tiltDeg.toDouble())
        val lx = cx - cos(rad).toFloat() * beamHalf
        val ly = pivotY - sin(rad).toFloat() * beamHalf
        val rx = cx + cos(rad).toFloat() * beamHalf
        val ry = pivotY + sin(rad).toFloat() * beamHalf

        canvas.drawLine(lx, ly, rx, ry, Paint(outline).apply { strokeWidth = 11f * u })
        canvas.drawCircle(cx, pivotY, 14f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFACC15.toInt() })
        canvas.drawCircle(cx, pivotY, 14f * u, outline)

        // Left pan (Green Savings Coin) & Right pan (Red Impulse Tag)
        val cordP = Paint(outline).apply { strokeWidth = 4f * u }
        for ((px, py, col, sym) in listOf(
            Tuple4(lx, ly, 0xFF16A34A.toInt(), "₱"),
            Tuple4(rx, ry, 0xFFEF4444.toInt(), "!")
        )) {
            val panY = py + 76f * u
            canvas.drawLine(px, py, px - 32f * u, panY, cordP)
            canvas.drawLine(px, py, px + 32f * u, panY, cordP)
            canvas.drawArc(RectF(px - 44f * u, panY - 12f * u, px + 44f * u, panY + 26f * u), 0f, 180f, true,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF64748B.toInt() })
            canvas.drawCircle(px, panY - 22f * u, 26f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
            canvas.drawCircle(px, panY - 22f * u, 26f * u, Paint(outline).apply { strokeWidth = 3.5f * u })
            canvas.drawText(sym, px, panY - 13f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = 28f * u; textAlign = Paint.Align.CENTER; isFakeBoldText = true
            })
        }
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    private fun drawRocketGrowthChart(
        canvas: Canvas,
        box: RectF,
        u: Float,
        sceneElapsedSec: Float,
        globalTimeSec: Float,
        outline: Paint
    ) {
        drawDiagramHeader(canvas, box, u, "EXPONENTIAL GROWTH", 0xFF7C3AED.toInt())
        val chartL = box.left + 52f * u
        val chartR = box.right - 56f * u
        val chartT = box.top + 118f * u
        val chartB = box.bottom - 48f * u

        // Subtle chart grid
        val gridP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x3364748B; strokeWidth = 3f * u }
        for (i in 0..3) {
            val gy = chartT + (chartB - chartT) * (i / 3f)
            canvas.drawLine(chartL, gy, chartR, gy, gridP)
        }
        canvas.drawLine(chartL, chartT, chartL, chartB, Paint(outline).apply { strokeWidth = 5f * u })
        canvas.drawLine(chartL, chartB, chartR, chartB, Paint(outline).apply { strokeWidth = 5f * u })

        // Rising exponential curve + rocket at tip
        val prog = Easing.smoothstep((sceneElapsedSec / 1.5f).coerceIn(0.15f, 1f))
        val tipX = chartL + (chartR - chartL) * prog
        val tipY = chartB - (chartB - chartT) * (prog * prog)

        val curve = Path().apply {
            moveTo(chartL, chartB)
            quadTo(chartL + (tipX - chartL) * 0.65f, chartB - (chartB - tipY) * 0.12f, tipX, tipY)
        }
        canvas.drawPath(curve, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF10B981.toInt(); setStyle(Paint.Style.STROKE); strokeWidth = 11f * u; strokeCap = Paint.Cap.ROUND
        })

        // Animated rocket riding curve tip
        canvas.save()
        canvas.translate(tipX, tipY)
        canvas.rotate(42f)
        val flicker = 1f + 0.25f * sin(globalTimeSec * 18f)
        canvas.drawOval(RectF(-10f * u, 16f * u, 10f * u, (42f * flicker) * u),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF97316.toInt() })
        val body = Path().apply {
            moveTo(0f, -34f * u)
            lineTo(18f * u, 18f * u)
            lineTo(-18f * u, 18f * u)
            close()
        }
        canvas.drawPath(body, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF38BDF8.toInt() })
        canvas.drawPath(body, Paint(outline).apply { strokeWidth = 3.5f * u })
        canvas.restore()
    }

    private fun drawLockAndKeySecret(
        canvas: Canvas,
        box: RectF,
        u: Float,
        sceneElapsedSec: Float,
        globalTimeSec: Float,
        outline: Paint
    ) {
        drawDiagramHeader(canvas, box, u, "HIDDEN SECRET", 0xFFD97706.toInt())
        val cx = box.centerX()
        val cy = box.centerY() + 42f * u
        val unlock = Easing.smoothstep(((sceneElapsedSec - 0.35f) / 0.85f).coerceIn(0f, 1f))
        val shackleLift = unlock * 28f * u

        // Padlock shackle rising as it unlocks
        val shackleP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF94A3B8.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = 18f * u
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(
            RectF(cx - 48f * u, cy - 118f * u - shackleLift, cx + 48f * u, cy - 16f * u - shackleLift),
            180f, 180f, false, shackleP
        )

        // Gold padlock body
        val lockRect = RectF(cx - 68f * u, cy - 42f * u, cx + 68f * u, cy + 78f * u)
        canvas.drawRoundRect(lockRect, 18f * u, 18f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFACC15.toInt() })
        canvas.drawRoundRect(lockRect, 18f * u, 18f * u, outline)
        canvas.drawCircle(cx, cy + 12f * u, 14f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0F172A.toInt() })
        canvas.drawRoundRect(RectF(cx - 6f * u, cy + 16f * u, cx + 6f * u, cy + 44f * u), 4f * u, 4f * u,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0F172A.toInt() })

        // Turning golden key sliding into center
        val keyShift = (1f - unlock) * 52f * u + sin(globalTimeSec * 4f) * 3f * u
        val kx = cx + 78f * u + keyShift
        val ky = cy + 16f * u
        val keyP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF38BDF8.toInt() }
        canvas.drawCircle(kx + 28f * u, ky, 18f * u, keyP)
        canvas.drawCircle(kx + 28f * u, ky, 18f * u, Paint(outline).apply { strokeWidth = 3.5f * u })
        canvas.drawRoundRect(RectF(kx - 36f * u, ky - 6f * u, kx + 14f * u, ky + 6f * u), 4f * u, 4f * u, keyP)
    }

    private fun drawShieldBlockingArrows(
        canvas: Canvas,
        box: RectF,
        u: Float,
        globalTimeSec: Float,
        outline: Paint
    ) {
        drawDiagramHeader(canvas, box, u, "PROTECTION", 0xFF0284C7.toInt())
        val cx = box.centerX() - 26f * u
        val cy = box.centerY() + 34f * u

        // Shield body
        val shield = Path().apply {
            moveTo(cx, cy - 88f * u)
            lineTo(cx + 76f * u, cy - 60f * u)
            quadTo(cx + 70f * u, cy + 48f * u, cx, cy + 92f * u)
            quadTo(cx - 70f * u, cy + 48f * u, cx - 76f * u, cy - 60f * u)
            close()
        }
        canvas.drawPath(shield, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0EA5E9.toInt() })
        canvas.drawPath(shield, outline)
        canvas.drawCircle(cx, cy, 24f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFBAE6FD.toInt() })

        // Incoming red arrows bouncing off shield edge
        val arrowP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEF4444.toInt(); strokeWidth = 8f * u; strokeCap = Paint.Cap.ROUND }
        for (i in -1..1) {
            val cycle = (globalTimeSec * 1.4f + (i + 1) * 0.3f) % 1f
            val ay = cy + i * 44f * u
            val ax = box.right - 22f * u - cycle * (box.right - cx - 86f * u)
            canvas.drawLine(ax, ay, ax + 48f * u, ay, arrowP)
            val tip = Path().apply {
                moveTo(ax - 10f * u, ay)
                lineTo(ax + 10f * u, ay - 12f * u)
                lineTo(ax + 10f * u, ay + 12f * u)
                close()
            }
            canvas.drawPath(tip, arrowP)
        }
    }

    private fun drawDominoChainReaction(
        canvas: Canvas,
        box: RectF,
        u: Float,
        sceneElapsedSec: Float,
        outline: Paint
    ) {
        drawDiagramHeader(canvas, box, u, "CHAIN REACTION", 0xFF9333EA.toInt())
        val groundY = box.bottom - 48f * u
        val count = 5
        val spacing = (box.width() - 120f * u) / (count - 1)
        val startX = box.left + 64f * u
        val wave = (sceneElapsedSec * 1.3f) % 2.4f

        val domColors = intArrayOf(0xFFEF4444.toInt(), 0xFFF59E0B.toInt(), 0xFF10B981.toInt(), 0xFF3B82F6.toInt(), 0xFFA855F7.toInt())
        canvas.drawLine(box.left + 28f * u, groundY, box.right - 28f * u, groundY, Paint(outline).apply { strokeWidth = 5f * u })

        for (i in 0 until count) {
            val dx = startX + i * spacing
            val trigger = (wave - i * 0.30f).coerceIn(0f, 1f)
            val maxAngle = if (i == count - 1) 68f else 56f
            val angle = Easing.smoothstep(trigger) * maxAngle

            canvas.save()
            canvas.rotate(angle, dx, groundY)
            val rect = RectF(dx - 16f * u, groundY - 124f * u, dx + 16f * u, groundY)
            canvas.drawRoundRect(rect, 6f * u, 6f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = domColors[i] })
            canvas.drawRoundRect(rect, 6f * u, 6f * u, outline)
            canvas.drawCircle(dx, groundY - 94f * u, 6f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            canvas.drawCircle(dx, groundY - 30f * u, 6f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            canvas.restore()
        }
    }

    private fun drawDiagramHeader(
        canvas: Canvas,
        box: RectF,
        u: Float,
        text: String,
        pillColor: Int
    ) {
        val tPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f * u
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.06f
        }
        val tw = tPaint.measureText(text)
        val pw = (tw + 48f * u).coerceAtMost(box.width() - 32f * u)
        val ph = 46f * u
        val cx = box.centerX()
        val cy = box.top + 48f * u
        val rect = RectF(cx - pw * 0.5f, cy - ph * 0.5f, cx + pw * 0.5f, cy + ph * 0.5f)
        canvas.drawRoundRect(rect, ph * 0.5f, ph * 0.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pillColor })
        canvas.drawRoundRect(rect, ph * 0.5f, ph * 0.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0E1018.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = 4f * u
        })
        val fm = tPaint.fontMetrics
        canvas.drawText(text, cx, cy - (fm.ascent + fm.descent) * 0.5f, tPaint)
    }
}
