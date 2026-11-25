package com.example.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.model.CutawayStage
import com.example.model.CutawaySubject
import com.example.model.FlowDirection
import com.example.model.PointerCallout
import com.example.model.SceneScript
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Authentic 16-bit nearest-neighbor pixel cross-section renderer.
 * Renders all cross-section strata, flowing red blood cells, white blood cells,
 * PathMeasure fibrin strands, rising capillary branches, migrating repair cells,
 * lifting scab, and animated 2px pixel pointer callouts onto an offscreen
 * 270x480 ARGB_8888 Bitmap (isAntiAlias = false, isDither = false), then blits
 * scaled 4x onto the 1080x1920 output Canvas with isFilterBitmap = false.
 */
object PixelCutawayRenderer {

    const val PIXEL_WIDTH = 270
    const val PIXEL_HEIGHT = 480

    private const val BLOCK_LEFT = 12
    private const val BLOCK_RIGHT = 258
    private const val EPIDERMIS_TOP = 108
    private const val EPIDERMIS_BOT = 154
    private const val DERMIS_BOT = 286
    private const val ADIPOSE_BOT = 360

    private class OffscreenBuffer {
        val bitmap: Bitmap = Bitmap.createBitmap(PIXEL_WIDTH, PIXEL_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas: Canvas = Canvas(bitmap)
    }

    private val threadBuffer = object : ThreadLocal<OffscreenBuffer>() {
        override fun initialValue(): OffscreenBuffer = OffscreenBuffer()
    }

    private val srcRect by lazy { Rect(0, 0, PIXEL_WIDTH, PIXEL_HEIGHT) }

    private fun newPixelPaint(): Paint = Paint().apply {
        isAntiAlias = false
        isDither = false
    }

    private val blitPaint by lazy {
        Paint().apply {
            isAntiAlias = false
            isDither = false
            isFilterBitmap = false
        }
    }

    private val checkerShaderCache = HashMap<Long, BitmapShader>()

    private fun getCheckerShader(colorA: Int, colorB: Int, cellPx: Int = 2): BitmapShader {
        val key = (colorA.toLong() shl 32) xor (colorB.toLong() and 0xFFFFFFFFL) xor cellPx.toLong()
        checkerShaderCache[key]?.let { return it }
        val size = cellPx * 2
        val tile = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val checker = ((x / cellPx) + (y / cellPx)) % 2 == 0
                tile.setPixel(x, y, if (checker) colorA else colorB)
            }
        }
        return BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).also {
            checkerShaderCache[key] = it
        }
    }

    fun normalizeCalloutPoint(rawX: Float, rawY: Float): Pair<Float, Float> {
        val px = when {
            rawX in 0f..1f && rawY in 0f..1f -> rawX * PIXEL_WIDTH
            rawX > PIXEL_WIDTH || rawY > PIXEL_HEIGHT -> rawX / 4f
            else -> rawX
        }
        val py = when {
            rawX in 0f..1f && rawY in 0f..1f -> rawY * PIXEL_HEIGHT
            rawX > PIXEL_WIDTH || rawY > PIXEL_HEIGHT -> rawY / 4f
            else -> rawY
        }
        return px.coerceIn(26f, 244f) to py.coerceIn(66f, 344f)
    }

    fun drawPixelCutaway(
        canvas: Canvas,
        outputW: Float,
        outputH: Float,
        scene: SceneScript,
        sceneElapsedSec: Float,
        sceneDurationSec: Float,
        globalTimeSec: Float,
        backdropBitmap: Bitmap? = null
    ) {
        val buf = threadBuffer.get() ?: return
        val pxCanvas = buf.canvas
        val stage = ProceduralPuppetEngine.resolveCutawayStage(scene)
        val subject = ProceduralPuppetEngine.resolveCutawaySubjectOrNull(scene)
        val callouts = ProceduralPuppetEngine.resolvePointerCallouts(scene, stage, subject)
        val progress = (sceneElapsedSec / sceneDurationSec.coerceAtLeast(1f)).coerceIn(0f, 1f)

        pxCanvas.drawColor(0xFF0A1120.toInt())

        // Compute stage camera zoom inside low-res 270x480 space so pixels remain blocky
        val stageScale: Float
        val stageTranslateY: Float
        when (stage) {
            CutawayStage.SURFACE_ZOOM_IN -> {
                val zoomT = Easing.smoothstep(progress)
                stageScale = 1.38f - 0.38f * zoomT
                stageTranslateY = (1f - zoomT) * 28f
            }
            CutawayStage.SURFACE_HEALED_ZOOM_OUT -> {
                val zoomT = Easing.smoothstep(progress)
                stageScale = 1.10f - 0.10f * zoomT
                stageTranslateY = 0f
            }
            else -> {
                stageScale = 1f
                stageTranslateY = 0f
            }
        }

        pxCanvas.save()
        if (stageScale != 1f || stageTranslateY != 0f) {
            pxCanvas.translate(0f, stageTranslateY)
            pxCanvas.scale(stageScale, stageScale, PIXEL_WIDTH * 0.5f, 180f)
        }

        if (backdropBitmap != null && !backdropBitmap.isRecycled) {
            // Downscale topic-specific artwork onto 270x480 with nearest-neighbor pixelization
            val artSrc = Rect(0, 0, backdropBitmap.width, backdropBitmap.height)
            val artDst = Rect(BLOCK_LEFT, 54, BLOCK_RIGHT, ADIPOSE_BOT)
            drawPixelLabBackdrop(pxCanvas, globalTimeSec, subject)
            pxCanvas.drawBitmap(backdropBitmap, artSrc, artDst, blitPaint)
            // Subtle pixel vignette & specimen border frame
            val tintPaint = newPixelPaint().apply { color = 0x22070B14 }
            pxCanvas.drawRect(artDst.left.toFloat(), artDst.top.toFloat(), artDst.right.toFloat(), artDst.bottom.toFloat(), tintPaint)
            val frameBorder = newPixelPaint().apply {
                color = 0xFF38BDF8.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            pxCanvas.drawRect(artDst.left.toFloat(), artDst.top.toFloat(), artDst.right.toFloat(), artDst.bottom.toFloat(), frameBorder)
        } else {
            drawPixelLabBackdrop(pxCanvas, globalTimeSec, subject)

            if (subject == null) {
                drawTopicNeutralPixelBlueprint(
                    c = pxCanvas,
                    scene = scene,
                    stage = stage,
                    progress = progress,
                    sceneElapsedSec = sceneElapsedSec,
                    globalTimeSec = globalTimeSec
                )
            } else if (stage == CutawayStage.SURFACE_ZOOM_IN && progress < 0.48f) {
                drawSurfaceMacroView(pxCanvas, subject, progress / 0.48f, globalTimeSec)
            } else {
                val revealFrac = if (stage == CutawayStage.SURFACE_ZOOM_IN) {
                    Easing.smoothstep(((progress - 0.48f) / 0.32f).coerceIn(0f, 1f))
                } else 1f
                when (subject) {
                    CutawaySubject.SKIN_WOUND_HEALING -> drawCutawaySpecimen(
                        c = pxCanvas,
                        stage = stage,
                        progress = progress,
                        revealFrac = revealFrac,
                        sceneElapsedSec = sceneElapsedSec,
                        globalTimeSec = globalTimeSec
                    )
                    CutawaySubject.BRAIN_NEURON_SIGNAL -> drawBrainNeuronCutaway(
                        c = pxCanvas,
                        stage = stage,
                        progress = progress,
                        sceneElapsedSec = sceneElapsedSec,
                        globalTimeSec = globalTimeSec
                    )
                    CutawaySubject.TOOTH_ENAMEL_DECAY -> drawToothEnamelCutaway(
                        c = pxCanvas,
                        stage = stage,
                        progress = progress,
                        sceneElapsedSec = sceneElapsedSec,
                        globalTimeSec = globalTimeSec
                    )
                    CutawaySubject.HEART_ARTERY_FLOW -> drawHeartArteryCutaway(
                        c = pxCanvas,
                        stage = stage,
                        progress = progress,
                        sceneElapsedSec = sceneElapsedSec,
                        globalTimeSec = globalTimeSec
                    )
                    CutawaySubject.BATTERY_CIRCUIT_CHARGE -> drawBatteryCircuitCutaway(
                        c = pxCanvas,
                        stage = stage,
                        progress = progress,
                        sceneElapsedSec = sceneElapsedSec,
                        globalTimeSec = globalTimeSec
                    )
                    CutawaySubject.SOIL_ROOT_GROWTH -> drawSoilRootCutaway(
                        c = pxCanvas,
                        stage = stage,
                        progress = progress,
                        sceneElapsedSec = sceneElapsedSec,
                        globalTimeSec = globalTimeSec
                    )
                }
            }
        }

        // Animated 16-bit FlowDirection pulse lines & chunky energy particles over the cutaway
        drawFlowDirectionOverlay(
            c = pxCanvas,
            flowDirection = scene.flowDirection,
            sceneElapsedSec = sceneElapsedSec,
            globalTimeSec = globalTimeSec
        )

        pxCanvas.restore()

        // Draw pointer callout labels crisp on top of 270x480 stage
        drawPointerCallouts(
            c = pxCanvas,
            callouts = callouts,
            sceneElapsedSec = sceneElapsedSec,
            globalTimeSec = globalTimeSec
        )

        // Subtle caption-safe pixel dock at bottom (y=364..480)
        drawCaptionSafeFooter(pxCanvas)

        // Blit 270x480 -> 1080x1920 with nearest-neighbor scaling (isFilterBitmap = false)
        val dstRect = Rect(0, 0, outputW.roundToInt(), outputH.roundToInt())
        canvas.drawBitmap(buf.bitmap, srcRect, dstRect, blitPaint)
    }

    private fun drawPixelLabBackdrop(
        c: Canvas,
        globalTimeSec: Float,
        subject: CutawaySubject? = null
    ) {
        val fill = newPixelPaint()
        val (topBg, midBg, gridCol) = when (subject) {
            CutawaySubject.SOIL_ROOT_GROWTH -> Triple(0xFF0C2233.toInt(), 0xFF153447.toInt(), 0x224ADE80)
            CutawaySubject.BATTERY_CIRCUIT_CHARGE -> Triple(0xFF080E18.toInt(), 0xFF0E1B2A.toInt(), 0x22FACC15)
            CutawaySubject.BRAIN_NEURON_SIGNAL -> Triple(0xFF110C22.toInt(), 0xFF1D143B.toInt(), 0x22C084FC)
            else -> Triple(0xFF09101E.toInt(), 0xFF101C33.toInt(), 0x2238BDF8)
        }
        fill.color = topBg
        c.drawRect(0f, 0f, PIXEL_WIDTH.toFloat(), 54f, fill)

        fill.shader = getCheckerShader(topBg, midBg, cellPx = 2)
        c.drawRect(0f, 54f, PIXEL_WIDTH.toFloat(), 74f, fill)
        fill.shader = null

        fill.color = midBg
        c.drawRect(0f, 74f, PIXEL_WIDTH.toFloat(), 116f, fill)

        // Subtle 16px pixel lab grid lines
        val grid = newPixelPaint().apply { color = gridCol }
        var gx = 8
        while (gx < PIXEL_WIDTH) {
            c.drawRect(gx.toFloat(), 12f, (gx + 1).toFloat(), 362f, grid)
            gx += 16
        }
        var gy = 16
        while (gy < 362) {
            c.drawRect(0f, gy.toFloat(), PIXEL_WIDTH.toFloat(), (gy + 1).toFloat(), grid)
            gy += 16
        }

        // Tiny drifting micro-spores / pixel particles in lab air
        val speck = newPixelPaint()
        for (i in 0 until 8) {
            val sx = ((i * 37 + (globalTimeSec * 8f).toInt()) % (PIXEL_WIDTH - 20)) + 10
            val sy = ((i * 23 + (sin((globalTimeSec + i).toDouble()) * 6.0).toInt()) % 86) + 18
            speck.color = if (i % 2 == 0) 0x554ADE80 else 0x5538BDF8
            c.drawRect(sx.toFloat(), sy.toFloat(), (sx + 2).toFloat(), (sy + 2).toFloat(), speck)
        }
    }

    private fun drawSurfaceMacroView(
        c: Canvas,
        subject: CutawaySubject,
        zoomFrac: Float,
        globalTimeSec: Float
    ) {
        val fill = newPixelPaint()
        val tileW = 24
        val tileH = 18
        val startY = 82

        val (c0, c1, c2, mortar) = when (subject) {
            CutawaySubject.BRAIN_NEURON_SIGNAL -> listOf(0xFFF472B6.toInt(), 0xFFDB2777.toInt(), 0xFFF9A8D4.toInt(), 0xFF831843.toInt())
            CutawaySubject.TOOTH_ENAMEL_DECAY -> listOf(0xFFF8FAFC.toInt(), 0xFFE2E8F0.toInt(), 0xFFFFFFFF.toInt(), 0xFF94A3B8.toInt())
            CutawaySubject.HEART_ARTERY_FLOW -> listOf(0xFFDC2626.toInt(), 0xFFB91C1C.toInt(), 0xFFEF4444.toInt(), 0xFF7F1D1D.toInt())
            CutawaySubject.BATTERY_CIRCUIT_CHARGE -> listOf(0xFF475569.toInt(), 0xFF334155.toInt(), 0xFF64748B.toInt(), 0xFF0F172A.toInt())
            CutawaySubject.SOIL_ROOT_GROWTH -> listOf(0xFF78350F.toInt(), 0xFF92400E.toInt(), 0xFF65A30D.toInt(), 0xFF451A03.toInt())
            CutawaySubject.SKIN_WOUND_HEALING -> listOf(0xFFE98A6C.toInt(), 0xFFDF7959.toInt(), 0xFFF29D80.toInt(), 0xFFB8563A.toInt())
        }

        for (row in 0 until 12) {
            val y = startY + row * tileH
            val rowOffset = if (row % 2 == 0) 0 else tileW / 2
            for (col in -1 until 13) {
                val x = col * tileW + rowOffset
                val shade = (row + col) % 3
                fill.color = when (shade) {
                    0 -> c0
                    1 -> c1
                    else -> c2
                }
                c.drawRect(x.toFloat(), y.toFloat(), (x + tileW - 1).toFloat(), (y + tileH - 1).toFloat(), fill)
                fill.color = mortar
                c.drawRect(x.toFloat(), (y + tileH - 1).toFloat(), (x + tileW).toFloat(), (y + tileH).toFloat(), fill)
                c.drawRect((x + tileW - 1).toFloat(), y.toFloat(), (x + tileW).toFloat(), (y + tileH).toFloat(), fill)
            }
        }

        // Scanning pixel reticle crosshair signaling macro zoom into cross-section
        val reticle = newPixelPaint().apply {
            color = 0xFF4ADE80.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val boxHalf = (42f - 18f * zoomFrac).roundToInt()
        val cx = PIXEL_WIDTH / 2
        val cy = 176
        val pulse = ((sin(globalTimeSec * 8f) + 1f) * 2f).roundToInt()
        c.drawRect(
            (cx - boxHalf - pulse).toFloat(),
            (cy - boxHalf - pulse).toFloat(),
            (cx + boxHalf + pulse).toFloat(),
            (cy + boxHalf + pulse).toFloat(),
            reticle
        )
    }

    private fun drawCutawaySpecimen(
        c: Canvas,
        stage: CutawayStage,
        progress: Float,
        revealFrac: Float,
        sceneElapsedSec: Float,
        globalTimeSec: Float
    ) {
        val fill = newPixelPaint()
        val left = BLOCK_LEFT.toFloat()
        val right = BLOCK_RIGHT.toFloat()

        // 1. Cutaway Drop Backing Frame (dark 16-bit specimen box)
        fill.color = 0xFF060913.toInt()
        c.drawRect(left - 2f, (EPIDERMIS_TOP - 2).toFloat(), right + 4f, (ADIPOSE_BOT + 4).toFloat(), fill)

        // Compute V-shaped gap width by stage
        val gapHalfTop = when (stage) {
            CutawayStage.SURFACE_ZOOM_IN -> (6f * revealFrac).roundToInt()
            CutawayStage.CROSS_SECTION_DAMAGE -> (12f + 22f * Easing.smoothstep((progress * 1.4f).coerceIn(0f, 1f))).roundToInt()
            CutawayStage.IMMUNE_CLEANUP -> 34
            CutawayStage.STRAND_AND_VESSEL_REBUILD -> 32
            CutawayStage.CELL_MIGRATION_CLOSE -> (32f * (1f - Easing.smoothstep(progress) * 0.85f)).roundToInt().coerceAtLeast(4)
            CutawayStage.SURFACE_HEALED_ZOOM_OUT -> 0
        }
        val gapDepthY = when (stage) {
            CutawayStage.SURFACE_ZOOM_IN -> EPIDERMIS_TOP + 18
            CutawayStage.CROSS_SECTION_DAMAGE -> EPIDERMIS_BOT + 54
            CutawayStage.IMMUNE_CLEANUP -> EPIDERMIS_BOT + 52
            CutawayStage.STRAND_AND_VESSEL_REBUILD -> EPIDERMIS_BOT + 48
            CutawayStage.CELL_MIGRATION_CLOSE -> (EPIDERMIS_BOT + 44 - (progress * 30f)).roundToInt()
            CutawayStage.SURFACE_HEALED_ZOOM_OUT -> EPIDERMIS_TOP
        }

        val cx = PIXEL_WIDTH / 2

        // 2. Bottom Adipose / Base Layer (golden bubbly pixel fat lobules)
        drawAdiposeLayer(c, left.toInt(), right.toInt(), DERMIS_BOT, ADIPOSE_BOT)

        // 3. Middle Dermis Layer with 2x2 Bayer Dithered Shading Bands
        drawDermisLayer(c, left.toInt(), right.toInt(), EPIDERMIS_BOT, DERMIS_BOT, globalTimeSec)

        // 4. Vascular Channel & Flowing Pixelated Red Blood Cells inside Dermis
        drawVascularChannel(c, left.toInt(), right.toInt(), stage, progress, globalTimeSec)

        // 5. Top Epidermis Surface Brick Grid
        drawEpidermisLayer(
            c = c,
            left = left.toInt(),
            right = right.toInt(),
            top = EPIDERMIS_TOP,
            bot = EPIDERMIS_BOT,
            gapCenterX = cx,
            gapHalfTop = gapHalfTop
        )

        // 6. V-Shaped Breach Cavity + Blood Pool + Stage-Specific Cellular Activity
        if (gapHalfTop > 0) {
            drawBreachCavity(
                c = c,
                cx = cx,
                topY = EPIDERMIS_TOP,
                depthY = gapDepthY,
                halfW = gapHalfTop,
                stage = stage,
                progress = progress,
                globalTimeSec = globalTimeSec
            )
        }

        // Stage-specific overlay mechanics
        when (stage) {
            CutawayStage.CROSS_SECTION_DAMAGE -> {
                drawDamageDebrisAndPulse(c, cx, EPIDERMIS_TOP + 12, gapDepthY, gapHalfTop, globalTimeSec)
            }
            CutawayStage.IMMUNE_CLEANUP -> {
                drawWhiteBloodCellsCleanup(c, cx, EPIDERMIS_TOP + 16, gapDepthY, gapHalfTop, progress, globalTimeSec)
            }
            CutawayStage.STRAND_AND_VESSEL_REBUILD -> {
                drawFibrinMeshStrands(c, cx, EPIDERMIS_TOP + 10, gapDepthY - 4, gapHalfTop, sceneElapsedSec)
                drawRisingCapillaryBranches(c, cx, gapDepthY, progress, globalTimeSec)
            }
            CutawayStage.CELL_MIGRATION_CLOSE -> {
                drawFibrinMeshStrands(c, cx, EPIDERMIS_TOP + 10, gapDepthY, gapHalfTop + 6, 2.0f)
                drawRisingCapillaryBranches(c, cx, EPIDERMIS_BOT + 44, 1.0f, globalTimeSec)
                drawMigratingCells(c, cx, EPIDERMIS_TOP + 8, EPIDERMIS_BOT + 26, gapHalfTop, progress)
            }
            CutawayStage.SURFACE_HEALED_ZOOM_OUT -> {
                drawRisingCapillaryBranches(c, cx, EPIDERMIS_BOT + 36, 1.0f, globalTimeSec)
                drawLiftingCrackedScab(c, cx, EPIDERMIS_TOP, progress)
            }
            CutawayStage.SURFACE_ZOOM_IN -> Unit
        }

        // Outer 2px dark specimen border on cutaway slab
        val border = newPixelPaint().apply {
            color = 0xFF1E293B.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        c.drawRect(left, EPIDERMIS_TOP.toFloat(), right, ADIPOSE_BOT.toFloat(), border)

        // Subtle cutaway stratum divider lines
        val divider = newPixelPaint().apply { color = 0xFF1F1218.toInt() }
        c.drawRect(left, EPIDERMIS_BOT.toFloat(), right, (EPIDERMIS_BOT + 2).toFloat(), divider)
        c.drawRect(left, DERMIS_BOT.toFloat(), right, (DERMIS_BOT + 2).toFloat(), divider)
    }

    private fun drawEpidermisLayer(
        c: Canvas,
        left: Int,
        right: Int,
        top: Int,
        bot: Int,
        gapCenterX: Int,
        gapHalfTop: Int
    ) {
        val fill = newPixelPaint()
        val cellW = 14
        val cellH = 11
        val rows = (bot - top) / cellH + 1

        for (r in 0 until rows) {
            val cy = top + r * cellH
            val yEnd = (cy + cellH).coerceAtMost(bot)
            val stagger = if (r % 2 == 0) 0 else cellW / 2
            var x = left - stagger
            while (x < right) {
                val xStart = x.coerceAtLeast(left)
                val xEnd = (x + cellW - 1).coerceAtMost(right)
                if (xEnd > xStart) {
                    val midX = (xStart + xEnd) / 2
                    val rowGapHalf = (gapHalfTop * (1f - r / rows.toFloat() * 0.4f)).roundToInt()
                    val insideGap = gapHalfTop > 0 && midX in (gapCenterX - rowGapHalf)..(gapCenterX + rowGapHalf)
                    if (!insideGap) {
                        fill.color = when {
                            r == 0 -> 0xFFF4A98B.toInt()
                            r == 1 -> 0xFFE58667.toInt()
                            r == 2 -> 0xFFD96E52.toInt()
                            else -> 0xFFC55A43.toInt()
                        }
                        c.drawRect(xStart.toFloat(), cy.toFloat(), xEnd.toFloat(), yEnd.toFloat(), fill)
                        // Pixel top bevel highlight on top row
                        if (r == 0) {
                            fill.color = 0xFFFFC7AD.toInt()
                            c.drawRect(xStart.toFloat(), cy.toFloat(), xEnd.toFloat(), (cy + 2).toFloat(), fill)
                        }
                        // Tiny pixel nucleus dot inside each epidermal brick
                        if (xEnd - xStart >= 6 && yEnd - cy >= 6) {
                            fill.color = 0xFF9A3412.toInt()
                            c.drawRect((midX - 1).toFloat(), (cy + 4).toFloat(), (midX + 1).toFloat(), (cy + 6).toFloat(), fill)
                        }
                        // Crisp 1px mortar lines
                        fill.color = 0xFF8C3523.toInt()
                        c.drawRect(xEnd.toFloat(), cy.toFloat(), (xEnd + 1).coerceAtMost(right).toFloat(), yEnd.toFloat(), fill)
                        c.drawRect(xStart.toFloat(), (yEnd - 1).toFloat(), xEnd.toFloat(), yEnd.toFloat(), fill)
                    }
                }
                x += cellW
            }
        }
    }

    private fun drawDermisLayer(c: Canvas, left: Int, right: Int, top: Int, bot: Int, globalTimeSec: Float) {
        val fill = newPixelPaint()
        val band1 = top + 34
        val band2 = top + 74

        fill.color = 0xFFD6546C.toInt()
        c.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), band1.toFloat(), fill)

        // Checkerboard dithered transition strip
        fill.shader = getCheckerShader(0xFFD6546C.toInt(), 0xFFB83955.toInt(), cellPx = 2)
        c.drawRect(left.toFloat(), band1.toFloat(), right.toFloat(), (band1 + 14).toFloat(), fill)
        fill.shader = null

        fill.color = 0xFFB83955.toInt()
        c.drawRect(left.toFloat(), (band1 + 14).toFloat(), right.toFloat(), band2.toFloat(), fill)

        fill.shader = getCheckerShader(0xFFB83955.toInt(), 0xFF962843.toInt(), cellPx = 2)
        c.drawRect(left.toFloat(), band2.toFloat(), right.toFloat(), (band2 + 16).toFloat(), fill)
        fill.shader = null

        fill.color = 0xFF962843.toInt()
        c.drawRect(left.toFloat(), (band2 + 16).toFloat(), right.toFloat(), bot.toFloat(), fill)

        // Pixelated collagen fibers & dermal cells
        val fiber = newPixelPaint().apply { color = 0xFFEA7A8E.toInt() }
        for (i in 0 until 14) {
            val fx = left + 10 + (i * 17) % (right - left - 24)
            val fy = top + 10 + ((i * 29) % 52)
            c.drawRect(fx.toFloat(), fy.toFloat(), (fx + 8).toFloat(), (fy + 2).toFloat(), fiber)
            // Occasional tiny dermal fibroblast dot
            if (i % 3 == 0) {
                val shift = (sin((globalTimeSec * 2f + i).toDouble()) * 1.5).roundToInt()
                fiber.color = 0xFFFCA5A5.toInt()
                c.drawRect((fx + 2 + shift).toFloat(), (fy - 2).toFloat(), (fx + 5 + shift).toFloat(), (fy + 1).toFloat(), fiber)
                fiber.color = 0xFFEA7A8E.toInt()
            }
        }
    }

    private fun drawVascularChannel(
        c: Canvas,
        left: Int,
        right: Int,
        stage: CutawayStage,
        progress: Float,
        globalTimeSec: Float
    ) {
        val fill = newPixelPaint()
        val vesselTop = 236
        val vesselBot = 262

        // Outer vessel wall
        fill.color = 0xFF7F1D1D.toInt()
        c.drawRect(left.toFloat(), (vesselTop - 3).toFloat(), right.toFloat(), (vesselBot + 3).toFloat(), fill)

        // Inner dark lumen
        fill.color = 0xFF450A0A.toInt()
        c.drawRect(left.toFloat(), vesselTop.toFloat(), right.toFloat(), vesselBot.toFloat(), fill)

        // Dithered upper blood plasma sheen
        fill.shader = getCheckerShader(0xFF5C1010.toInt(), 0xFF450A0A.toInt(), cellPx = 2)
        c.drawRect(left.toFloat(), vesselTop.toFloat(), right.toFloat(), (vesselTop + 6).toFloat(), fill)
        fill.shader = null

        // Flowing pixelated red blood cells across lumen
        val span = (right - left - 10).coerceAtLeast(100)
        for (i in 0 until 11) {
            val speed = 34f + (i % 3) * 8f
            val rbcX = left + 4 + ((globalTimeSec * speed + i * 24f) % span).roundToInt()
            val wobbleY = (sin((globalTimeSec * 4f + i * 0.9f).toDouble()) * 3.0).roundToInt()
            val rbcY = vesselTop + 8 + (i % 2) * 8 + wobbleY

            // Dark outline + crimson pixel disc + specular pixel
            fill.color = 0xFF280505.toInt()
            c.drawRect((rbcX - 1).toFloat(), rbcY.toFloat(), (rbcX + 9).toFloat(), (rbcY + 5).toFloat(), fill)
            fill.color = 0xFFEF4444.toInt()
            c.drawRect(rbcX.toFloat(), (rbcY + 1).toFloat(), (rbcX + 8).toFloat(), (rbcY + 4).toFloat(), fill)
            fill.color = 0xFFFCA5A5.toInt()
            c.drawRect((rbcX + 2).toFloat(), (rbcY + 1).toFloat(), (rbcX + 5).toFloat(), (rbcY + 2).toFloat(), fill)
        }

        // Subtle sprout base stub on vessel top during rebuild/migration
        if (stage == CutawayStage.STRAND_AND_VESSEL_REBUILD ||
            stage == CutawayStage.CELL_MIGRATION_CLOSE ||
            stage == CutawayStage.SURFACE_HEALED_ZOOM_OUT
        ) {
            val cx = PIXEL_WIDTH / 2
            val stubAlpha = if (stage == CutawayStage.STRAND_AND_VESSEL_REBUILD) {
                (progress * 2f).coerceIn(0f, 1f)
            } else 1f
            if (stubAlpha > 0.05f) {
                fill.color = 0xFFDC2626.toInt()
                c.drawRect((cx - 4).toFloat(), (vesselTop - 5).toFloat(), (cx + 4).toFloat(), vesselTop.toFloat(), fill)
            }
        }
    }

    private fun drawAdiposeLayer(c: Canvas, left: Int, right: Int, top: Int, bot: Int) {
        val fill = newPixelPaint()
        fill.color = 0xFFB45309.toInt()
        c.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bot.toFloat(), fill)

        fill.shader = getCheckerShader(0xFFD97706.toInt(), 0xFFB45309.toInt(), cellPx = 2)
        c.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), (top + 10).toFloat(), fill)
        fill.shader = null

        // Packed golden pixel fat lobules
        val cellSize = 18
        var row = 0
        var y = top + 4
        while (y < bot - 6) {
            val offset = if (row % 2 == 0) 4 else 13
            var x = left + offset
            while (x < right - 16) {
                val w = 15
                val h = (bot - 3 - y).coerceAtMost(14)
                if (h >= 8) {
                    fill.color = 0xFF78350F.toInt()
                    c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), fill)
                    fill.color = 0xFFF59E0B.toInt()
                    c.drawRect((x + 1).toFloat(), (y + 1).toFloat(), (x + w - 1).toFloat(), (y + h - 1).toFloat(), fill)
                    fill.color = 0xFFFBBF24.toInt()
                    c.drawRect((x + 2).toFloat(), (y + 2).toFloat(), (x + w - 3).toFloat(), (y + h - 4).toFloat(), fill)
                    // Specular 2x2 highlight dot
                    fill.color = 0xFFFEF3C7.toInt()
                    c.drawRect((x + 3).toFloat(), (y + 3).toFloat(), (x + 6).toFloat(), (y + 5).toFloat(), fill)
                }
                x += cellSize
            }
            y += 16
            row++
        }
    }

    private fun drawBreachCavity(
        c: Canvas,
        cx: Int,
        topY: Int,
        depthY: Int,
        halfW: Int,
        stage: CutawayStage,
        progress: Float,
        globalTimeSec: Float
    ) {
        val fill = newPixelPaint()
        // Stepped 16-bit V-shaped cavity carved downwards into dermis
        val steps = 16
        for (s in 0 until steps) {
            val frac = s / steps.toFloat()
            val y0 = (topY + frac * (depthY - topY)).roundToInt()
            val y1 = (topY + ((s + 1) / steps.toFloat()) * (depthY - topY)).roundToInt().coerceAtLeast(y0 + 1)
            val span = ((1f - frac * 0.85f) * halfW).roundToInt().coerceAtLeast(3)

            fill.color = 0xFF2B0A14.toInt()
            c.drawRect((cx - span - 2).toFloat(), y0.toFloat(), (cx + span + 2).toFloat(), y1.toFloat(), fill)

            val cavityColor = when {
                stage == CutawayStage.CROSS_SECTION_DAMAGE && frac > 0.45f -> 0xFF991B1B.toInt()
                stage == CutawayStage.IMMUNE_CLEANUP && frac > 0.55f -> 0xFF7F1D1D.toInt()
                stage == CutawayStage.STRAND_AND_VESSEL_REBUILD -> 0xFF4C1120.toInt()
                else -> 0xFF3A0D19.toInt()
            }
            fill.color = cavityColor
            c.drawRect((cx - span).toFloat(), y0.toFloat(), (cx + span).toFloat(), y1.toFloat(), fill)
        }

        // Pulsing blood pool meniscus at base of V-gap during early stages
        if (stage == CutawayStage.CROSS_SECTION_DAMAGE || stage == CutawayStage.IMMUNE_CLEANUP) {
            val wave = (sin(globalTimeSec * 6f) * 2f).roundToInt()
            fill.color = 0xFFDC2626.toInt()
            c.drawRect((cx - 12).toFloat(), (depthY - 14 + wave).toFloat(), (cx + 12).toFloat(), (depthY - 4).toFloat(), fill)
            fill.color = 0xFFFCA5A5.toInt()
            c.drawRect((cx - 6).toFloat(), (depthY - 12 + wave).toFloat(), (cx + 2).toFloat(), (depthY - 10 + wave).toFloat(), fill)
        }
    }

    private fun drawDamageDebrisAndPulse(
        c: Canvas,
        cx: Int,
        topY: Int,
        depthY: Int,
        halfW: Int,
        globalTimeSec: Float
    ) {
        val fill = newPixelPaint()
        // Floating bacteria / dark debris specks & leaking crimson droplets
        for (i in 0 until 9) {
            val side = if (i % 2 == 0) 1 else -1
            val dx = cx + side * ((i * 5) % (halfW - 4).coerceAtLeast(6))
            val dy = topY + 12 + ((globalTimeSec * 14f + i * 11f) % (depthY - topY - 16).coerceAtLeast(16)).roundToInt()
            fill.color = if (i % 3 == 0) 0xFFA3E635.toInt() else 0xFFEF4444.toInt()
            c.drawRect(dx.toFloat(), dy.toFloat(), (dx + 3).toFloat(), (dy + 3).toFloat(), fill)
        }
    }

    private fun drawWhiteBloodCellsCleanup(
        c: Canvas,
        cx: Int,
        topY: Int,
        depthY: Int,
        halfW: Int,
        progress: Float,
        globalTimeSec: Float
    ) {
        val fill = newPixelPaint()
        val midY = (topY + depthY) / 2
        // 3 chunky 16-bit white blood cells (macrophages) gliding into the breach
        val wbcTargets = listOf(
            Triple(cx - 14, midY - 6, 0f),
            Triple(cx + 10, midY + 8, 1.4f),
            Triple(cx - 2, midY + 18, 2.7f)
        )
        wbcTargets.forEachIndexed { idx, (tx, ty, phase) ->
            val enterT = Easing.smoothstep(((progress * 1.3f) - idx * 0.15f).coerceIn(0f, 1f))
            val startX = if (idx % 2 == 0) cx - 44 else cx + 44
            val wx = (startX + (tx - startX) * enterT + sin((globalTimeSec * 4f + phase).toDouble()) * 3.0).roundToInt()
            val wy = (ty + cos((globalTimeSec * 3.5f + phase).toDouble()) * 2.5).roundToInt()

            drawPixelMacrophage(c, wx, wy, fill)
        }

        // Remaining debris shrinks as cleanup progresses
        val debrisCount = ((1f - progress) * 7f).roundToInt().coerceAtLeast(1)
        for (i in 0 until debrisCount) {
            val dx = cx - halfW / 2 + (i * 7) % halfW.coerceAtLeast(8)
            val dy = topY + 16 + (i * 11) % (depthY - topY - 20).coerceAtLeast(12)
            fill.color = 0xFFA3E635.toInt()
            c.drawRect(dx.toFloat(), dy.toFloat(), (dx + 2).toFloat(), (dy + 2).toFloat(), fill)
        }
    }

    private fun drawPixelMacrophage(c: Canvas, cx: Int, cy: Int, fill: Paint) {
        // Dark pixel outline cross shape (rounded 16-bit blob)
        fill.color = 0xFF0F172A.toInt()
        c.drawRect((cx - 6).toFloat(), (cy - 5).toFloat(), (cx + 6).toFloat(), (cy + 5).toFloat(), fill)
        c.drawRect((cx - 5).toFloat(), (cy - 6).toFloat(), (cx + 5).toFloat(), (cy + 6).toFloat(), fill)

        // Bright white/ice-blue body
        fill.color = 0xFFF8FAFC.toInt()
        c.drawRect((cx - 5).toFloat(), (cy - 4).toFloat(), (cx + 5).toFloat(), (cy + 4).toFloat(), fill)
        c.drawRect((cx - 4).toFloat(), (cy - 5).toFloat(), (cx + 4).toFloat(), (cy + 5).toFloat(), fill)

        // Shaded bottom edge
        fill.color = 0xFFCBD5E1.toInt()
        c.drawRect((cx - 4).toFloat(), (cy + 2).toFloat(), (cx + 4).toFloat(), (cy + 4).toFloat(), fill)

        // Lobed indigo pixel nucleus
        fill.color = 0xFF6366F1.toInt()
        c.drawRect((cx - 3).toFloat(), (cy - 2).toFloat(), cx.toFloat(), (cy + 1).toFloat(), fill)
        c.drawRect((cx + 1).toFloat(), (cy - 1).toFloat(), (cx + 3).toFloat(), (cy + 2).toFloat(), fill)
    }

    private fun drawFibrinMeshStrands(
        c: Canvas,
        cx: Int,
        topY: Int,
        botY: Int,
        halfW: Int,
        sceneElapsedSec: Float
    ) {
        val strandProgress = (sceneElapsedSec / 1.45f).coerceIn(0f, 1f)
        if (strandProgress <= 0.02f) return

        val strandPath = Path().apply {
            moveTo((cx - halfW).toFloat(), (topY + 8).toFloat())
            lineTo((cx + halfW - 4).toFloat(), (topY + 26).toFloat())
            moveTo((cx + halfW).toFloat(), (topY + 10).toFloat())
            lineTo((cx - halfW + 4).toFloat(), (topY + 30).toFloat())
            moveTo((cx - halfW + 6).toFloat(), (topY + 28).toFloat())
            lineTo((cx + halfW - 6).toFloat(), (botY - 6).toFloat())
            moveTo((cx + halfW - 6).toFloat(), (topY + 28).toFloat())
            lineTo((cx - halfW + 6).toFloat(), (botY - 6).toFloat())
            moveTo((cx - halfW + 2).toFloat(), ((topY + botY) / 2).toFloat())
            lineTo((cx + halfW - 2).toFloat(), ((topY + botY) / 2).toFloat())
        }

        val partial = Path()
        val pm = PathMeasure(strandPath, false)
        var totalLength = 0f
        run {
            val counter = PathMeasure(strandPath, false)
            do {
                totalLength += counter.length
            } while (counter.nextContour())
        }

        var remaining = totalLength * strandProgress
        do {
            val len = pm.length
            if (remaining <= len) {
                pm.getSegment(0f, remaining, partial, true)
                break
            } else {
                pm.getSegment(0f, len, partial, true)
                remaining -= len
            }
        } while (pm.nextContour())

        val darkEdge = newPixelPaint().apply {
            color = 0xFF451A03.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 3f
            strokeCap = Paint.Cap.SQUARE
            strokeJoin = Paint.Join.MITER
        }
        val strandPaint = newPixelPaint().apply {
            color = 0xFFFDE68A.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            strokeCap = Paint.Cap.SQUARE
            strokeJoin = Paint.Join.MITER
        }
        c.drawPath(partial, darkEdge)
        c.drawPath(partial, strandPaint)
    }

    private fun drawRisingCapillaryBranches(
        c: Canvas,
        cx: Int,
        targetTopY: Int,
        progress: Float,
        globalTimeSec: Float
    ) {
        val grow = Easing.smoothstep(progress.coerceIn(0f, 1f))
        val rootY = 236f
        val branchPath = Path().apply {
            // Left rising branch
            moveTo((cx - 4).toFloat(), rootY)
            lineTo((cx - 4).toFloat(), rootY - 22f)
            lineTo((cx - 16).toFloat(), rootY - 40f)
            lineTo((cx - 12).toFloat(), (targetTopY + 8).toFloat())

            // Right rising branch
            moveTo((cx + 3).toFloat(), rootY)
            lineTo((cx + 3).toFloat(), rootY - 18f)
            lineTo((cx + 16).toFloat(), rootY - 36f)
            lineTo((cx + 10).toFloat(), (targetTopY + 4).toFloat())

            // Center capillary sprout
            moveTo(cx.toFloat(), rootY - 18f)
            lineTo(cx.toFloat(), (targetTopY + 12).toFloat())
        }

        val partial = Path()
        val pm = PathMeasure(branchPath, false)
        var totalLen = 0f
        run {
            val counter = PathMeasure(branchPath, false)
            do {
                totalLen += counter.length
            } while (counter.nextContour())
        }
        var rem = totalLen * grow
        do {
            val len = pm.length
            if (rem <= len) {
                pm.getSegment(0f, rem, partial, true)
                break
            } else {
                pm.getSegment(0f, len, partial, true)
                rem -= len
            }
        } while (pm.nextContour())

        val wallPaint = newPixelPaint().apply {
            color = 0xFF7F1D1D.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 4f
            strokeCap = Paint.Cap.SQUARE
        }
        val corePaint = newPixelPaint().apply {
            color = if ((sin(globalTimeSec * 6f) > 0f)) 0xFFEF4444.toInt() else 0xFFF87171.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f
            strokeCap = Paint.Cap.SQUARE
        }
        c.drawPath(partial, wallPaint)
        c.drawPath(partial, corePaint)
    }

    private fun drawMigratingCells(
        c: Canvas,
        cx: Int,
        topY: Int,
        botY: Int,
        currentHalfGap: Int,
        progress: Float
    ) {
        val fill = newPixelPaint()
        val rows = 4
        val march = Easing.smoothstep(progress)
        for (r in 0 until rows) {
            val cy = topY + 6 + r * 14
            if (cy > botY) break
            // Left and right marching cells converging toward cx
            for (side in listOf(-1, 1)) {
                val distFromCenter = (currentHalfGap + 4 - (march * 12f).roundToInt()).coerceAtLeast(3)
                val cellX = cx + side * distFromCenter
                // Crisp square-rounded repair cell
                fill.color = 0xFF7C2D12.toInt()
                c.drawRect((cellX - 5).toFloat(), (cy - 5).toFloat(), (cellX + 5).toFloat(), (cy + 5).toFloat(), fill)
                fill.color = 0xFFFDBA74.toInt()
                c.drawRect((cellX - 4).toFloat(), (cy - 4).toFloat(), (cellX + 4).toFloat(), (cy + 4).toFloat(), fill)
                fill.color = 0xFFFED7AA.toInt()
                c.drawRect((cellX - 3).toFloat(), (cy - 3).toFloat(), (cellX + 1).toFloat(), (cy - 1).toFloat(), fill)
                fill.color = 0xFF9A3412.toInt()
                c.drawRect((cellX - 1).toFloat(), (cy - 1).toFloat(), (cellX + 2).toFloat(), (cy + 2).toFloat(), fill)
            }
        }
    }

    private fun drawLiftingCrackedScab(c: Canvas, cx: Int, epidermisTop: Int, progress: Float) {
        val liftT = Easing.smoothstep(((progress - 0.15f) / 0.70f).coerceIn(0f, 1f))
        val liftY = (liftT * 38f).roundToInt()
        val driftX = (liftT * 10f).roundToInt()
        if (liftT >= 0.96f) return

        val fill = newPixelPaint()
        val sx = cx - 26 + driftX
        val sy = epidermisTop - 8 - liftY
        val w = 52
        val h = 11

        // Dark cracked scab cap lifting up from newly healed epidermis
        fill.color = 0xFF1F0D09.toInt()
        c.drawRect((sx - 1).toFloat(), (sy - 1).toFloat(), (sx + w + 1).toFloat(), (sy + h + 1).toFloat(), fill)
        fill.color = 0xFF5C2B20.toInt()
        c.drawRect(sx.toFloat(), sy.toFloat(), (sx + w).toFloat(), (sy + h).toFloat(), fill)

        // Pixelated crack lines on scab
        fill.color = 0xFF2D120C.toInt()
        c.drawRect((sx + 14).toFloat(), sy.toFloat(), (sx + 16).toFloat(), (sy + 6).toFloat(), fill)
        c.drawRect((sx + 16).toFloat(), (sy + 5).toFloat(), (sx + 20).toFloat(), (sy + 11).toFloat(), fill)
        c.drawRect((sx + 34).toFloat(), (sy + 2).toFloat(), (sx + 36).toFloat(), (sy + 11).toFloat(), fill)
    }

    private fun drawPointerCallouts(
        c: Canvas,
        callouts: List<PointerCallout>,
        sceneElapsedSec: Float,
        globalTimeSec: Float
    ) {
        if (callouts.isEmpty()) return

        val lineBgPaint = newPixelPaint().apply {
            color = 0xFF070B14.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 4f
            strokeCap = Paint.Cap.SQUARE
            strokeJoin = Paint.Join.MITER
        }
        val linePaint = newPixelPaint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f
            strokeCap = Paint.Cap.SQUARE
            strokeJoin = Paint.Join.MITER
        }
        val dotOutline = newPixelPaint().apply { color = 0xFF070B14.toInt() }
        val dotFill = newPixelPaint().apply { color = Color.WHITE }

        val textOutline = newPixelPaint().apply {
            color = 0xFF070B14.toInt()
            textSize = 11f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.04f
        }
        val textFill = newPixelPaint().apply {
            color = Color.WHITE
            textSize = 11f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.04f
        }

        val usedShelves = mutableListOf<Pair<Float, Float>>()

        callouts.take(2).forEachIndexed { idx, rawCallout ->
            val delay = idx * 0.25f
            val animT = ((sceneElapsedSec - delay) / 0.42f).coerceIn(0f, 1f)
            if (animT <= 0.02f) return@forEachIndexed

            val label = rawCallout.label.trim().uppercase().take(24)
            if (label.isEmpty()) return@forEachIndexed

            val (tx, ty) = normalizeCalloutPoint(rawCallout.x, rawCallout.y)
            val labelW = textFill.measureText(label)

            // Route pointer upward-left or upward-right, avoiding overlap with first label
            val goRight = if (idx == 0) {
                tx < PIXEL_WIDTH * 0.55f
            } else {
                usedShelves.firstOrNull()?.let { (prevX, _) -> prevX < PIXEL_WIDTH * 0.5f } ?: (tx < PIXEL_WIDTH * 0.5f)
            }
            val elbowX = (tx + (if (goRight) 18f else -18f)).coerceIn(20f, PIXEL_WIDTH - 20f)
            var shelfY = if (idx == 0) {
                (ty - 44f).coerceIn(30f, 76f)
            } else {
                (ty - 26f).coerceIn(56f, 96f)
            }
            if (idx == 1 && usedShelves.any { (_, prevY) -> kotlin.math.abs(prevY - shelfY) < 18f }) {
                shelfY = if (shelfY < 70f) shelfY + 22f else shelfY - 22f
            }
            usedShelves.add(elbowX to shelfY)

            val shelfLen = (labelW * 0.72f).coerceIn(26f, 84f)
            val endX = if (goRight) {
                (elbowX + shelfLen).coerceAtMost(PIXEL_WIDTH - 10f)
            } else {
                (elbowX - shelfLen).coerceAtLeast(10f)
            }

            // Animated PathMeasure white 2px pointer line
            val pointerPath = Path().apply {
                moveTo(tx.roundToInt().toFloat(), ty.roundToInt().toFloat())
                lineTo(elbowX.roundToInt().toFloat(), shelfY.roundToInt().toFloat())
                lineTo(endX.roundToInt().toFloat(), shelfY.roundToInt().toFloat())
            }
            val pm = PathMeasure(pointerPath, false)
            val partial = Path()
            pm.getSegment(0f, pm.length * animT, partial, true)
            c.drawPath(partial, lineBgPaint)
            c.drawPath(partial, linePaint)

            // Small square white anchor dot at target point with dark pixel border
            val pulsePad = if (sin(globalTimeSec * 6f + idx) > 0.5f) 1 else 0
            val ix = tx.roundToInt()
            val iy = ty.roundToInt()
            c.drawRect(
                (ix - 3 - pulsePad).toFloat(),
                (iy - 3 - pulsePad).toFloat(),
                (ix + 3 + pulsePad).toFloat(),
                (iy + 3 + pulsePad).toFloat(),
                dotOutline
            )
            c.drawRect((ix - 2).toFloat(), (iy - 2).toFloat(), (ix + 2).toFloat(), (iy + 2).toFloat(), dotFill)

            // Crisp dark-outlined uppercase text above horizontal pointer shelf
            if (animT > 0.55f) {
                val rawTextX = minOf(elbowX, endX)
                val textX = rawTextX.coerceIn(10f, (PIXEL_WIDTH - 10f - labelW).coerceAtLeast(10f))
                val textY = (shelfY - 5f).roundToInt().toFloat()
                textFill.color = if (label.contains("HEALED")) 0xFF4ADE80.toInt() else Color.WHITE

                // 8-direction integer pixel outline for authentic crisp 16-bit readability
                for (oy in -1..1) {
                    for (ox in -1..1) {
                        if (ox == 0 && oy == 0) continue
                        c.drawText(label, textX.roundToInt() + ox.toFloat(), textY + oy.toFloat(), textOutline)
                    }
                }
                c.drawText(label, textX.roundToInt().toFloat(), textY, textFill)
            }
        }
    }

    private fun drawSpecimenFrameBorder(c: Canvas) {
        val left = BLOCK_LEFT.toFloat()
        val right = BLOCK_RIGHT.toFloat()
        val border = newPixelPaint().apply {
            color = 0xFF1E293B.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        c.drawRect(left, EPIDERMIS_TOP.toFloat(), right, ADIPOSE_BOT.toFloat(), border)
    }

    private fun drawPathProgress(c: Canvas, path: Path, frac: Float, darkEdge: Paint, brightCore: Paint) {
        val f = frac.coerceIn(0f, 1f)
        if (f <= 0.02f) return
        val partial = Path()
        val counter = PathMeasure(path, false)
        var totalLen = 0f
        do {
            totalLen += counter.length
        } while (counter.nextContour())
        val pm = PathMeasure(path, false)
        var rem = totalLen * f
        do {
            val len = pm.length
            if (rem <= len) {
                pm.getSegment(0f, rem, partial, true)
                break
            } else {
                pm.getSegment(0f, len, partial, true)
                rem -= len
            }
        } while (pm.nextContour())
        c.drawPath(partial, darkEdge)
        c.drawPath(partial, brightCore)
    }

    private fun drawBrainNeuronCutaway(
        c: Canvas,
        stage: CutawayStage,
        progress: Float,
        sceneElapsedSec: Float,
        globalTimeSec: Float
    ) {
        val fill = newPixelPaint()
        val left = BLOCK_LEFT
        val right = BLOCK_RIGHT
        val cx = PIXEL_WIDTH / 2

        // Backdrop slab
        fill.color = 0xFF060913.toInt()
        c.drawRect((left - 2).toFloat(), (EPIDERMIS_TOP - 2).toFloat(), (right + 4).toFloat(), (ADIPOSE_BOT + 4).toFloat(), fill)

        // Bottom Thalamus / Synaptic base (deep purple with dither)
        fill.color = 0xFF3B0764.toInt()
        c.drawRect(left.toFloat(), DERMIS_BOT.toFloat(), right.toFloat(), ADIPOSE_BOT.toFloat(), fill)
        fill.shader = getCheckerShader(0xFF581C87.toInt(), 0xFF3B0764.toInt(), 2)
        c.drawRect(left.toFloat(), DERMIS_BOT.toFloat(), right.toFloat(), (DERMIS_BOT + 18).toFloat(), fill)
        fill.shader = null

        // Middle White Matter tract (indigo-violet with myelinated fiber bands)
        fill.color = 0xFF1E1B4B.toInt()
        c.drawRect(left.toFloat(), EPIDERMIS_BOT.toFloat(), right.toFloat(), DERMIS_BOT.toFloat(), fill)
        fill.shader = getCheckerShader(0xFF312E81.toInt(), 0xFF1E1B4B.toInt(), 2)
        c.drawRect(left.toFloat(), EPIDERMIS_BOT.toFloat(), right.toFloat(), (EPIDERMIS_BOT + 28).toFloat(), fill)
        fill.shader = null

        // Top Cerebral Cortex folded gyri blocks
        for (i in 0 until 9) {
            val bx = left + i * ((right - left) / 9)
            val bw = (right - left) / 9 - 2
            val ridgeY = EPIDERMIS_TOP + (if (i % 2 == 0) 0 else 6)
            fill.color = 0xFFBE185D.toInt()
            c.drawRect(bx.toFloat(), ridgeY.toFloat(), (bx + bw).toFloat(), EPIDERMIS_BOT.toFloat(), fill)
            fill.color = 0xFFF472B6.toInt()
            c.drawRect(bx.toFloat(), ridgeY.toFloat(), (bx + bw).toFloat(), (ridgeY + 14).toFloat(), fill)
            fill.color = 0xFFFBCFE8.toInt()
            c.drawRect((bx + 2).toFloat(), (ridgeY + 2).toFloat(), (bx + bw - 4).toFloat(), (ridgeY + 5).toFloat(), fill)
        }

        // Left & Right Neuron cell somas + horizontal axon channel
        val axonY = 228
        fill.color = 0xFF4338CA.toInt()
        c.drawRect((left + 8).toFloat(), (axonY - 12).toFloat(), (right - 8).toFloat(), (axonY + 12).toFloat(), fill)
        fill.color = 0xFF1E1B4B.toInt()
        c.drawRect((left + 8).toFloat(), (axonY - 7).toFloat(), (right - 8).toFloat(), (axonY + 7).toFloat(), fill)

        // Left & right star somas
        for (sx in listOf(left + 32, right - 32)) {
            fill.color = 0xFF818CF8.toInt()
            c.drawRect((sx - 12).toFloat(), (axonY - 12).toFloat(), (sx + 12).toFloat(), (axonY + 12).toFloat(), fill)
            fill.color = 0xFFC7D2FE.toInt()
            c.drawRect((sx - 6).toFloat(), (axonY - 6).toFloat(), (sx + 6).toFloat(), (axonY + 6).toFloat(), fill)
        }

        // Stage mechanics
        val isOverload = stage == CutawayStage.CROSS_SECTION_DAMAGE
        val pulseColor = if (isOverload) 0xFFEF4444.toInt() else 0xFF22D3EE.toInt()
        val speed = if (isOverload) 90f else 48f
        for (i in 0 until 7) {
            val px = left + 36 + ((globalTimeSec * speed + i * 26f) % 176f).roundToInt()
            val py = axonY - 2 + (if (isOverload) (sin((globalTimeSec * 16f + i).toDouble()) * 5.0).roundToInt() else 0)
            fill.color = if (i % 2 == 0) pulseColor else 0xFFFACC15.toInt()
            c.drawRect(px.toFloat(), py.toFloat(), (px + 7).toFloat(), (py + 4).toFloat(), fill)
        }

        when (stage) {
            CutawayStage.CROSS_SECTION_DAMAGE -> {
                // Static overload glitch blocks + jagged dopamine spikes
                for (i in 0 until 8) {
                    val gx = cx - 44 + (i * 13) % 88
                    val gy = 152 + ((globalTimeSec * 24f + i * 19f) % 90f).roundToInt()
                    fill.color = if (i % 2 == 0) 0xFFEF4444.toInt() else 0xFFFACC15.toInt()
                    c.drawRect(gx.toFloat(), gy.toFloat(), (gx + 6).toFloat(), (gy + 3).toFloat(), fill)
                }
            }
            CutawayStage.IMMUNE_CLEANUP -> {
                // Cyan microglia orbs clearing noise specks
                for (i in 0 until 3) {
                    val mx = (cx - 28 + i * 28 + sin((globalTimeSec * 3f + i).toDouble()) * 8.0).roundToInt()
                    val my = 186 + (i % 2) * 18
                    fill.color = 0xFF0F172A.toInt()
                    c.drawRect((mx - 6).toFloat(), (my - 6).toFloat(), (mx + 6).toFloat(), (my + 6).toFloat(), fill)
                    fill.color = 0xFF22D3EE.toInt()
                    c.drawRect((mx - 4).toFloat(), (my - 4).toFloat(), (mx + 4).toFloat(), (my + 4).toFloat(), fill)
                    fill.color = 0xFFFFFFFF.toInt()
                    c.drawRect((mx - 1).toFloat(), (my - 1).toFloat(), (mx + 2).toFloat(), (my + 2).toFloat(), fill)
                }
            }
            CutawayStage.STRAND_AND_VESSEL_REBUILD, CutawayStage.CELL_MIGRATION_CLOSE -> {
                val synPath = Path().apply {
                    moveTo((cx - 48).toFloat(), 178f)
                    lineTo((cx - 12).toFloat(), 164f)
                    lineTo((cx + 18).toFloat(), 190f)
                    lineTo((cx + 48).toFloat(), 174f)
                    moveTo((cx - 36).toFloat(), 202f)
                    lineTo(cx.toFloat(), 176f)
                    lineTo((cx + 38).toFloat(), 204f)
                }
                val edge = newPixelPaint().apply { color = 0xFF0E7490.toInt(); style = Paint.Style.STROKE; strokeWidth = 4f }
                val core = newPixelPaint().apply { color = 0xFF67E8F9.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f }
                val frac = if (stage == CutawayStage.STRAND_AND_VESSEL_REBUILD) (sceneElapsedSec / 1.4f).coerceIn(0.1f, 1f) else 1f
                drawPathProgress(c, synPath, frac, edge, core)

                if (stage == CutawayStage.CELL_MIGRATION_CLOSE) {
                    // Golden myelin sheath segments closing along axon
                    for (m in 0 until 4) {
                        val sx = left + 56 + m * 36
                        val w = (26f * Easing.smoothstep(progress)).roundToInt().coerceAtLeast(8)
                        fill.color = 0xFFFACC15.toInt()
                        c.drawRect(sx.toFloat(), (axonY - 14).toFloat(), (sx + w).toFloat(), (axonY - 8).toFloat(), fill)
                        c.drawRect(sx.toFloat(), (axonY + 8).toFloat(), (sx + w).toFloat(), (axonY + 14).toFloat(), fill)
                    }
                }
            }
            CutawayStage.SURFACE_HEALED_ZOOM_OUT -> {
                // Smooth green balanced alpha wave + healed glow
                val wavePath = Path().apply {
                    moveTo((left + 20).toFloat(), 196f)
                    var wx = left + 20
                    while (wx <= right - 20) {
                        val wy = 196f + sin((wx * 0.14f + globalTimeSec * 4f).toDouble()).toFloat() * 14f
                        lineTo(wx.toFloat(), wy)
                        wx += 8
                    }
                }
                val edge = newPixelPaint().apply { color = 0xFF065F46.toInt(); style = Paint.Style.STROKE; strokeWidth = 4f }
                val core = newPixelPaint().apply { color = 0xFF4ADE80.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f }
                drawPathProgress(c, wavePath, 1f, edge, core)
            }
            CutawayStage.SURFACE_ZOOM_IN -> Unit
        }
        drawSpecimenFrameBorder(c)
    }

    private fun drawToothEnamelCutaway(
        c: Canvas,
        stage: CutawayStage,
        progress: Float,
        sceneElapsedSec: Float,
        globalTimeSec: Float
    ) {
        val fill = newPixelPaint()
        val left = BLOCK_LEFT
        val right = BLOCK_RIGHT
        val cx = PIXEL_WIDTH / 2

        fill.color = 0xFF060913.toInt()
        c.drawRect((left - 2).toFloat(), (EPIDERMIS_TOP - 2).toFloat(), (right + 4).toFloat(), (ADIPOSE_BOT + 4).toFloat(), fill)

        // Bottom Pink Gum / Root Base
        fill.color = 0xFF9D174D.toInt()
        c.drawRect(left.toFloat(), DERMIS_BOT.toFloat(), right.toFloat(), ADIPOSE_BOT.toFloat(), fill)
        // Central Crimson Pulp Chamber & Root Nerve
        fill.color = 0xFFDC2626.toInt()
        c.drawRect((cx - 46).toFloat(), 248f, (cx + 46).toFloat(), ADIPOSE_BOT.toFloat(), fill)
        fill.color = 0xFFFCA5A5.toInt()
        c.drawRect((cx - 14).toFloat(), 262f, (cx + 14).toFloat(), ADIPOSE_BOT.toFloat(), fill)

        // Middle Warm Yellow Dentin Layer with vertical pixel tubules
        fill.color = 0xFFFDE68A.toInt()
        c.drawRect(left.toFloat(), EPIDERMIS_BOT.toFloat(), right.toFloat(), DERMIS_BOT.toFloat(), fill)
        fill.shader = getCheckerShader(0xFFFCD34D.toInt(), 0xFFFDE68A.toInt(), 2)
        c.drawRect(left.toFloat(), (EPIDERMIS_BOT + 54).toFloat(), right.toFloat(), DERMIS_BOT.toFloat(), fill)
        fill.shader = null
        fill.color = 0xFFD97706.toInt()
        var tx = left + 16
        while (tx < right - 12) {
            c.drawRect(tx.toFloat(), (EPIDERMIS_BOT + 8).toFloat(), (tx + 2).toFloat(), (DERMIS_BOT - 12).toFloat(), fill)
            tx += 18
        }

        // Top White Enamel Crown bricks with gap for decay
        val gapHalf = when (stage) {
            CutawayStage.CROSS_SECTION_DAMAGE -> (18f + 22f * progress).roundToInt()
            CutawayStage.IMMUNE_CLEANUP -> 36
            CutawayStage.STRAND_AND_VESSEL_REBUILD -> 28
            CutawayStage.CELL_MIGRATION_CLOSE -> (26f * (1f - progress * 0.85f)).roundToInt().coerceAtLeast(4)
            else -> 0
        }
        drawCustomBrickLayer(c, left, right, EPIDERMIS_TOP, EPIDERMIS_BOT, cx, gapHalf,
            topCol = 0xFFFFFFFF.toInt(), midCol = 0xFFE2E8F0.toInt(), botCol = 0xFFCBD5E1.toInt(), mortarCol = 0xFF94A3B8.toInt())

        if (gapHalf > 0) {
            val cavDepth = when (stage) {
                CutawayStage.CROSS_SECTION_DAMAGE -> EPIDERMIS_BOT + 46
                CutawayStage.IMMUNE_CLEANUP -> EPIDERMIS_BOT + 40
                else -> EPIDERMIS_BOT + 22
            }
            fill.color = 0xFF451A03.toInt()
            c.drawRect((cx - gapHalf).toFloat(), EPIDERMIS_TOP.toFloat(), (cx + gapHalf).toFloat(), cavDepth.toFloat(), fill)
        }

        when (stage) {
            CutawayStage.CROSS_SECTION_DAMAGE -> {
                // Falling neon green acid droplets
                for (i in 0 until 6) {
                    val ax = cx - 22 + (i * 9) % 44
                    val ay = EPIDERMIS_TOP + ((globalTimeSec * 28f + i * 16f) % 72f).roundToInt()
                    fill.color = 0xFFA3E635.toInt()
                    c.drawRect(ax.toFloat(), ay.toFloat(), (ax + 4).toFloat(), (ay + 6).toFloat(), fill)
                }
            }
            CutawayStage.IMMUNE_CLEANUP -> {
                // Mint-cyan fluoride shield particles
                for (i in 0 until 6) {
                    val fx = cx - 32 + i * 12
                    val fy = EPIDERMIS_TOP + 22 + (sin((globalTimeSec * 5f + i).toDouble()) * 6.0).roundToInt()
                    fill.color = 0xFF2DD4BF.toInt()
                    c.drawRect(fx.toFloat(), fy.toFloat(), (fx + 6).toFloat(), (fy + 6).toFloat(), fill)
                }
            }
            CutawayStage.STRAND_AND_VESSEL_REBUILD, CutawayStage.CELL_MIGRATION_CLOSE -> {
                val lattice = Path().apply {
                    moveTo((cx - 26).toFloat(), (EPIDERMIS_TOP + 18).toFloat())
                    lineTo((cx + 26).toFloat(), (EPIDERMIS_TOP + 42).toFloat())
                    moveTo((cx + 26).toFloat(), (EPIDERMIS_TOP + 18).toFloat())
                    lineTo((cx - 26).toFloat(), (EPIDERMIS_TOP + 42).toFloat())
                    moveTo((cx - 28).toFloat(), (EPIDERMIS_TOP + 30).toFloat())
                    lineTo((cx + 28).toFloat(), (EPIDERMIS_TOP + 30).toFloat())
                }
                val edge = newPixelPaint().apply { color = 0xFF0284C7.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f }
                val core = newPixelPaint().apply { color = 0xFFE0F2FE.toInt(); style = Paint.Style.STROKE; strokeWidth = 1.5f }
                drawPathProgress(c, lattice, (sceneElapsedSec / 1.3f).coerceIn(0.15f, 1f), edge, core)
            }
            CutawayStage.SURFACE_HEALED_ZOOM_OUT -> {
                // Sparkle star pixel glint on sealed pearl enamel
                val pulse = if (sin(globalTimeSec * 6f) > 0f) 2 else 0
                fill.color = 0xFF38BDF8.toInt()
                c.drawRect((cx - 6 - pulse).toFloat(), (EPIDERMIS_TOP + 12).toFloat(), (cx + 6 + pulse).toFloat(), (EPIDERMIS_TOP + 15).toFloat(), fill)
                c.drawRect((cx - 1).toFloat(), (EPIDERMIS_TOP + 6 - pulse).toFloat(), (cx + 2).toFloat(), (EPIDERMIS_TOP + 21 + pulse).toFloat(), fill)
            }
            CutawayStage.SURFACE_ZOOM_IN -> Unit
        }
        drawSpecimenFrameBorder(c)
    }

    private fun drawHeartArteryCutaway(
        c: Canvas,
        stage: CutawayStage,
        progress: Float,
        sceneElapsedSec: Float,
        globalTimeSec: Float
    ) {
        val fill = newPixelPaint()
        val left = BLOCK_LEFT
        val right = BLOCK_RIGHT
        val cx = PIXEL_WIDTH / 2

        fill.color = 0xFF060913.toInt()
        c.drawRect((left - 2).toFloat(), (EPIDERMIS_TOP - 2).toFloat(), (right + 4).toFloat(), (ADIPOSE_BOT + 4).toFloat(), fill)

        // Top & Bottom Cardiac Muscle Walls
        fill.color = 0xFF991B1B.toInt()
        c.drawRect(left.toFloat(), EPIDERMIS_TOP.toFloat(), right.toFloat(), 168f, fill)
        c.drawRect(left.toFloat(), 274f, right.toFloat(), ADIPOSE_BOT.toFloat(), fill)
        fill.shader = getCheckerShader(0xFFB91C1C.toInt(), 0xFF7F1D1D.toInt(), 2)
        c.drawRect(left.toFloat(), 150f, right.toFloat(), 168f, fill)
        c.drawRect(left.toFloat(), 274f, right.toFloat(), 296f, fill)
        fill.shader = null

        // Central Wide Artery Lumen
        fill.color = 0xFF450A0A.toInt()
        c.drawRect(left.toFloat(), 168f, right.toFloat(), 274f, fill)

        // Cholesterol Plaque mound constricting lumen
        val plaqueThickness = when (stage) {
            CutawayStage.SURFACE_ZOOM_IN -> 6
            CutawayStage.CROSS_SECTION_DAMAGE -> (18f + 18f * progress).roundToInt()
            CutawayStage.IMMUNE_CLEANUP -> (36f * (1f - progress * 0.45f)).roundToInt()
            CutawayStage.STRAND_AND_VESSEL_REBUILD -> (20f * (1f - progress * 0.65f)).roundToInt()
            CutawayStage.CELL_MIGRATION_CLOSE -> 4
            CutawayStage.SURFACE_HEALED_ZOOM_OUT -> 0
        }
        if (plaqueThickness > 0) {
            fill.color = 0xFFD97706.toInt()
            c.drawRect((cx - 56).toFloat(), 168f, (cx + 56).toFloat(), (168 + plaqueThickness).toFloat(), fill)
            c.drawRect((cx - 52).toFloat(), (274 - plaqueThickness).toFloat(), (cx + 52).toFloat(), 274f, fill)
            fill.color = 0xFFFACC15.toInt()
            c.drawRect((cx - 42).toFloat(), (170 + plaqueThickness / 2).toFloat(), (cx + 42).toFloat(), (170 + plaqueThickness).toFloat(), fill)
        }

        // Flowing Red Blood Cells + O2 bubbles through artery lumen
        val span = (right - left - 16).coerceAtLeast(100)
        val flowSpeed = if (plaqueThickness > 22) 22f else 54f
        for (i in 0 until 12) {
            val rx = left + 6 + ((globalTimeSec * flowSpeed + i * 21f) % span).roundToInt()
            val bottleneck = if (kotlin.math.abs(rx - cx) < 50 && plaqueThickness > 16) 0.4f else 1f
            val ry = 221 + (((i % 5) - 2) * 14f * bottleneck).roundToInt()
            fill.color = 0xFFEF4444.toInt()
            c.drawRect(rx.toFloat(), (ry - 3).toFloat(), (rx + 9).toFloat(), (ry + 3).toFloat(), fill)
            fill.color = 0xFFFCA5A5.toInt()
            c.drawRect((rx + 2).toFloat(), (ry - 2).toFloat(), (rx + 5).toFloat(), ry.toFloat(), fill)
            if (i % 3 == 0 && stage.ordinal >= CutawayStage.STRAND_AND_VESSEL_REBUILD.ordinal) {
                fill.color = 0xFF38BDF8.toInt()
                c.drawRect((rx + 3).toFloat(), (ry + 5).toFloat(), (rx + 6).toFloat(), (ry + 8).toFloat(), fill)
            }
        }

        // Stage accents
        if (stage == CutawayStage.IMMUNE_CLEANUP) {
            for (i in 0 until 4) {
                val hx = (cx - 36 + i * 24 + sin((globalTimeSec * 4f + i).toDouble()) * 5.0).roundToInt()
                val hy = 198 + (i % 2) * 40
                fill.color = 0xFF4ADE80.toInt()
                c.drawRect((hx - 5).toFloat(), (hy - 5).toFloat(), (hx + 5).toFloat(), (hy + 5).toFloat(), fill)
            }
        } else if (stage == CutawayStage.STRAND_AND_VESSEL_REBUILD) {
            val wallPath = Path().apply {
                moveTo((cx - 64).toFloat(), 170f)
                lineTo((cx + 64).toFloat(), 170f)
                moveTo((cx - 64).toFloat(), 272f)
                lineTo((cx + 64).toFloat(), 272f)
            }
            val edge = newPixelPaint().apply { color = 0xFF0F172A.toInt(); style = Paint.Style.STROKE; strokeWidth = 4f }
            val core = newPixelPaint().apply { color = 0xFFFDA4AF.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f }
            drawPathProgress(c, wallPath, (sceneElapsedSec / 1.2f).coerceIn(0.2f, 1f), edge, core)
        }
        drawSpecimenFrameBorder(c)
    }

    private fun drawBatteryCircuitCutaway(
        c: Canvas,
        stage: CutawayStage,
        progress: Float,
        sceneElapsedSec: Float,
        globalTimeSec: Float
    ) {
        val fill = newPixelPaint()
        val left = BLOCK_LEFT
        val right = BLOCK_RIGHT
        val cx = PIXEL_WIDTH / 2

        fill.color = 0xFF060913.toInt()
        c.drawRect((left - 2).toFloat(), (EPIDERMIS_TOP - 2).toFloat(), (right + 4).toFloat(), (ADIPOSE_BOT + 4).toFloat(), fill)

        // Top Aluminum Casing + Terminal Tab
        fill.color = 0xFF64748B.toInt()
        c.drawRect(left.toFloat(), EPIDERMIS_TOP.toFloat(), right.toFloat(), EPIDERMIS_BOT.toFloat(), fill)
        fill.color = 0xFF94A3B8.toInt()
        c.drawRect((left + 4).toFloat(), (EPIDERMIS_TOP + 4).toFloat(), (right - 4).toFloat(), (EPIDERMIS_TOP + 14).toFloat(), fill)
        fill.color = 0xFFCBD5E1.toInt()
        c.drawRect((cx - 28).toFloat(), (EPIDERMIS_TOP - 10).toFloat(), (cx + 28).toFloat(), EPIDERMIS_TOP.toFloat(), fill)

        // Middle Electrolyte Tank (Anode left, Separator center, Cathode right)
        fill.color = 0xFF0F172A.toInt()
        c.drawRect(left.toFloat(), EPIDERMIS_BOT.toFloat(), right.toFloat(), DERMIS_BOT.toFloat(), fill)
        // Left Anode block (cobalt blue) & Right Cathode block (emerald/amber)
        fill.color = 0xFF1D4ED8.toInt()
        c.drawRect((left + 12).toFloat(), (EPIDERMIS_BOT + 14).toFloat(), (cx - 18).toFloat(), (DERMIS_BOT - 14).toFloat(), fill)
        fill.color = 0xFF047857.toInt()
        c.drawRect((cx + 18).toFloat(), (EPIDERMIS_BOT + 14).toFloat(), (right - 12).toFloat(), (DERMIS_BOT - 14).toFloat(), fill)

        // Polymer separator grid down center
        fill.shader = getCheckerShader(0xFF475569.toInt(), 0xFF1E293B.toInt(), 2)
        c.drawRect((cx - 8).toFloat(), (EPIDERMIS_BOT + 8).toFloat(), (cx + 8).toFloat(), (DERMIS_BOT - 8).toFloat(), fill)
        fill.shader = null

        // Bottom Copper Bus PCB Traces
        fill.color = 0xFF064E3B.toInt()
        c.drawRect(left.toFloat(), DERMIS_BOT.toFloat(), right.toFloat(), ADIPOSE_BOT.toFloat(), fill)
        fill.color = 0xFFF59E0B.toInt()
        c.drawRect((left + 14).toFloat(), (DERMIS_BOT + 22).toFloat(), (right - 14).toFloat(), (DERMIS_BOT + 28).toFloat(), fill)
        c.drawRect((left + 26).toFloat(), (DERMIS_BOT + 44).toFloat(), (right - 26).toFloat(), (DERMIS_BOT + 50).toFloat(), fill)

        when (stage) {
            CutawayStage.CROSS_SECTION_DAMAGE -> {
                // Jagged metallic dendrite short piercing separator + sparks
                val dendrite = Path().apply {
                    moveTo((cx - 18).toFloat(), 210f)
                    lineTo(cx.toFloat(), 198f)
                    lineTo((cx + 18).toFloat(), 214f)
                    lineTo(cx.toFloat(), 234f)
                }
                val edge = newPixelPaint().apply { color = 0xFF991B1B.toInt(); style = Paint.Style.STROKE; strokeWidth = 5f }
                val core = newPixelPaint().apply { color = 0xFFEF4444.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f }
                drawPathProgress(c, dendrite, progress.coerceAtLeast(0.35f), edge, core)
                if (sin(globalTimeSec * 16f) > 0f) {
                    fill.color = 0xFFFACC15.toInt()
                    c.drawRect((cx - 6).toFloat(), 202f, (cx + 6).toFloat(), 214f, fill)
                }
            }
            CutawayStage.IMMUNE_CLEANUP -> {
                for (i in 0 until 6) {
                    val ix = left + 40 + (i * 32)
                    val iy = 216 + (sin((globalTimeSec * 5f + i).toDouble()) * 14.0).roundToInt()
                    fill.color = 0xFF38BDF8.toInt()
                    c.drawRect(ix.toFloat(), iy.toFloat(), (ix + 7).toFloat(), (iy + 7).toFloat(), fill)
                }
            }
            CutawayStage.STRAND_AND_VESSEL_REBUILD, CutawayStage.CELL_MIGRATION_CLOSE, CutawayStage.SURFACE_HEALED_ZOOM_OUT -> {
                // Flowing golden/cyan electron charge packets across separator into cathode
                for (i in 0 until 8) {
                    val ex = left + 26 + ((globalTimeSec * 64f + i * 24f) % 192f).roundToInt()
                    val ey = 186 + (i % 4) * 20
                    fill.color = 0xFF4ADE80.toInt()
                    c.drawRect(ex.toFloat(), ey.toFloat(), (ex + 8).toFloat(), (ey + 4).toFloat(), fill)
                }
                // Charge bars lighting up inside Cathode bank
                val bars = if (stage == CutawayStage.SURFACE_HEALED_ZOOM_OUT) 5 else (2 + (progress * 3f).roundToInt()).coerceAtMost(5)
                for (b in 0 until bars) {
                    val by = DERMIS_BOT - 28 - b * 18
                    fill.color = 0xFF4ADE80.toInt()
                    c.drawRect((cx + 26).toFloat(), by.toFloat(), (right - 22).toFloat(), (by + 10).toFloat(), fill)
                }
            }
            CutawayStage.SURFACE_ZOOM_IN -> Unit
        }
        drawSpecimenFrameBorder(c)
    }

    private fun drawSoilRootCutaway(
        c: Canvas,
        stage: CutawayStage,
        progress: Float,
        sceneElapsedSec: Float,
        globalTimeSec: Float
    ) {
        val fill = newPixelPaint()
        val left = BLOCK_LEFT
        val right = BLOCK_RIGHT
        val cx = PIXEL_WIDTH / 2

        fill.color = 0xFF060913.toInt()
        c.drawRect((left - 2).toFloat(), (EPIDERMIS_TOP - 2).toFloat(), (right + 4).toFloat(), (ADIPOSE_BOT + 4).toFloat(), fill)

        // Bottom Mineral Bedrock Subsoil
        fill.color = 0xFF451A03.toInt()
        c.drawRect(left.toFloat(), DERMIS_BOT.toFloat(), right.toFloat(), ADIPOSE_BOT.toFloat(), fill)
        fill.shader = getCheckerShader(0xFF57534E.toInt(), 0xFF451A03.toInt(), 2)
        c.drawRect(left.toFloat(), DERMIS_BOT.toFloat(), right.toFloat(), (DERMIS_BOT + 18).toFloat(), fill)
        fill.shader = null

        // Middle Warm Clay & Loam Soil
        fill.color = 0xFF78350F.toInt()
        c.drawRect(left.toFloat(), EPIDERMIS_BOT.toFloat(), right.toFloat(), DERMIS_BOT.toFloat(), fill)
        fill.shader = getCheckerShader(0xFF92400E.toInt(), 0xFF78350F.toInt(), 2)
        c.drawRect(left.toFloat(), EPIDERMIS_BOT.toFloat(), right.toFloat(), (EPIDERMIS_BOT + 26).toFloat(), fill)
        fill.shader = null

        // Top Humus & Grass Crust bricks
        val gapHalf = if (stage == CutawayStage.CROSS_SECTION_DAMAGE) 22 else 0
        drawCustomBrickLayer(c, left, right, EPIDERMIS_TOP, EPIDERMIS_BOT, cx, gapHalf,
            topCol = 0xFF65A30D.toInt(), midCol = 0xFF4D7C0F.toInt(), botCol = 0xFF78350F.toInt(), mortarCol = 0xFF451A03.toInt())

        // Golden Seed Embryo resting below surface
        fill.color = 0xFF451A03.toInt()
        c.drawRect((cx - 9).toFloat(), 198f, (cx + 9).toFloat(), 216f, fill)
        fill.color = 0xFFFACC15.toInt()
        c.drawRect((cx - 7).toFloat(), 200f, (cx + 7).toFloat(), 214f, fill)

        when (stage) {
            CutawayStage.CROSS_SECTION_DAMAGE -> {
                // Dry brown fissure below topsoil
                fill.color = 0xFF292524.toInt()
                c.drawRect((cx - 10).toFloat(), EPIDERMIS_TOP.toFloat(), (cx + 10).toFloat(), 190f, fill)
            }
            CutawayStage.IMMUNE_CLEANUP -> {
                // Blue water droplets percolating down + green mineral specks
                for (i in 0 until 7) {
                    val wx = cx - 48 + i * 16
                    val wy = EPIDERMIS_TOP + 12 + ((globalTimeSec * 26f + i * 19f) % 130f).roundToInt()
                    fill.color = if (i % 2 == 0) 0xFF38BDF8.toInt() else 0xFF4ADE80.toInt()
                    c.drawRect(wx.toFloat(), wy.toFloat(), (wx + 5).toFloat(), (wy + 7).toFloat(), fill)
                }
            }
            CutawayStage.STRAND_AND_VESSEL_REBUILD, CutawayStage.CELL_MIGRATION_CLOSE, CutawayStage.SURFACE_HEALED_ZOOM_OUT -> {
                // Branching roots growing downward via PathMeasure
                val rootPath = Path().apply {
                    moveTo(cx.toFloat(), 216f)
                    lineTo(cx.toFloat(), 294f)
                    moveTo(cx.toFloat(), 236f)
                    lineTo((cx - 42).toFloat(), 274f)
                    lineTo((cx - 64).toFloat(), 304f)
                    moveTo(cx.toFloat(), 246f)
                    lineTo((cx + 46).toFloat(), 282f)
                    lineTo((cx + 68).toFloat(), 312f)
                }
                val edge = newPixelPaint().apply { color = 0xFF451A03.toInt(); style = Paint.Style.STROKE; strokeWidth = 5f }
                val core = newPixelPaint().apply { color = 0xFFFDE68A.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f }
                val rootFrac = if (stage == CutawayStage.STRAND_AND_VESSEL_REBUILD) (sceneElapsedSec / 1.4f).coerceIn(0.2f, 1f) else 1f
                drawPathProgress(c, rootPath, rootFrac, edge, core)

                // Green pixel sprout pushing upward above topsoil
                val sproutH = if (stage == CutawayStage.SURFACE_HEALED_ZOOM_OUT) 42 else 28
                fill.color = 0xFF14532D.toInt()
                c.drawRect((cx - 3).toFloat(), (EPIDERMIS_TOP - sproutH).toFloat(), (cx + 3).toFloat(), 200f, fill)
                fill.color = 0xFF4ADE80.toInt()
                c.drawRect((cx - 2).toFloat(), (EPIDERMIS_TOP - sproutH).toFloat(), (cx + 2).toFloat(), EPIDERMIS_TOP.toFloat(), fill)
                // Sprout leaves
                c.drawRect((cx - 18).toFloat(), (EPIDERMIS_TOP - sproutH + 4).toFloat(), cx.toFloat(), (EPIDERMIS_TOP - sproutH + 14).toFloat(), fill)
                c.drawRect(cx.toFloat(), (EPIDERMIS_TOP - sproutH + 2).toFloat(), (cx + 18).toFloat(), (EPIDERMIS_TOP - sproutH + 12).toFloat(), fill)
                if (stage == CutawayStage.SURFACE_HEALED_ZOOM_OUT) {
                    fill.color = 0xFFFACC15.toInt()
                    c.drawRect((cx - 5).toFloat(), (EPIDERMIS_TOP - sproutH - 8).toFloat(), (cx + 5).toFloat(), (EPIDERMIS_TOP - sproutH + 2).toFloat(), fill)
                }
            }
            CutawayStage.SURFACE_ZOOM_IN -> Unit
        }
        drawSpecimenFrameBorder(c)
    }

    private fun drawCustomBrickLayer(
        c: Canvas,
        left: Int,
        right: Int,
        top: Int,
        bot: Int,
        gapCenterX: Int,
        gapHalfTop: Int,
        topCol: Int,
        midCol: Int,
        botCol: Int,
        mortarCol: Int
    ) {
        val fill = newPixelPaint()
        val cellW = 14
        val cellH = 11
        val rows = (bot - top) / cellH + 1
        for (r in 0 until rows) {
            val cy = top + r * cellH
            val yEnd = (cy + cellH).coerceAtMost(bot)
            val stagger = if (r % 2 == 0) 0 else cellW / 2
            var x = left - stagger
            while (x < right) {
                val xStart = x.coerceAtLeast(left)
                val xEnd = (x + cellW - 1).coerceAtMost(right)
                if (xEnd > xStart) {
                    val midX = (xStart + xEnd) / 2
                    val rowGapHalf = (gapHalfTop * (1f - r / rows.toFloat() * 0.4f)).roundToInt()
                    val insideGap = gapHalfTop > 0 && midX in (gapCenterX - rowGapHalf)..(gapCenterX + rowGapHalf)
                    if (!insideGap) {
                        fill.color = when (r) {
                            0 -> topCol
                            1 -> midCol
                            else -> botCol
                        }
                        c.drawRect(xStart.toFloat(), cy.toFloat(), xEnd.toFloat(), yEnd.toFloat(), fill)
                        fill.color = mortarCol
                        c.drawRect(xEnd.toFloat(), cy.toFloat(), (xEnd + 1).coerceAtMost(right).toFloat(), yEnd.toFloat(), fill)
                        c.drawRect(xStart.toFloat(), (yEnd - 1).toFloat(), xEnd.toFloat(), yEnd.toFloat(), fill)
                    }
                }
                x += cellW
            }
        }
    }

    private fun drawTopicNeutralPixelBlueprint(
        c: Canvas,
        scene: SceneScript,
        stage: CutawayStage,
        progress: Float,
        sceneElapsedSec: Float,
        globalTimeSec: Float
    ) {
        val fill = newPixelPaint()
        val left = BLOCK_LEFT.toFloat()
        val right = BLOCK_RIGHT.toFloat()

        // Dark 16-bit blueprint chassis block (replaces forced skin wound when topic is non-biological)
        fill.color = 0xFF060B16.toInt()
        c.drawRect(left - 2f, (EPIDERMIS_TOP - 2).toFloat(), right + 4f, (ADIPOSE_BOT + 4).toFloat(), fill)

        // Layered schematic strata: Top input layer, Middle active core, Bottom foundation layer
        fill.color = 0xFF1E3A5F.toInt()
        c.drawRect(left, EPIDERMIS_TOP.toFloat(), right, EPIDERMIS_BOT.toFloat(), fill)

        fill.shader = getCheckerShader(0xFF152A47.toInt(), 0xFF0F2038.toInt(), cellPx = 2)
        c.drawRect(left, EPIDERMIS_BOT.toFloat(), right, DERMIS_BOT.toFloat(), fill)
        fill.shader = null

        fill.color = 0xFF0B1526.toInt()
        c.drawRect(left, DERMIS_BOT.toFloat(), right, ADIPOSE_BOT.toFloat(), fill)

        // Crisp 2px pixel dividers
        fill.color = 0xFF38BDF8.toInt()
        c.drawRect(left, EPIDERMIS_BOT.toFloat(), right, (EPIDERMIS_BOT + 2).toFloat(), fill)
        c.drawRect(left, DERMIS_BOT.toFloat(), right, (DERMIS_BOT + 2).toFloat(), fill)

        // Interactive 3-module schematic nodes inside middle chamber
        val nodes = listOf(
            scene.step1.ifBlank { scene.leftLabel }.ifBlank { "INPUT" }.uppercase().take(10),
            scene.topicPillLabel.ifBlank { scene.step2 }.ifBlank { "CORE" }.uppercase().take(10),
            scene.step3.ifBlank { scene.rightLabel }.ifBlank { "OUTPUT" }.uppercase().take(10)
        )
        val lblPaint = newPixelPaint().apply {
            color = Color.WHITE
            textSize = 9f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val activeIdx = ((globalTimeSec * 1.6f).toInt() % 3)
        for (i in 0 until 3) {
            val nx = 34 + i * 74
            val ny = 186
            val nw = 54
            val nh = 44
            fill.color = if (i == activeIdx) 0xFF0284C7.toInt() else 0xFF1E293B.toInt()
            c.drawRect(nx.toFloat(), ny.toFloat(), (nx + nw).toFloat(), (ny + nh).toFloat(), fill)
            val borderP = newPixelPaint().apply {
                color = if (i == activeIdx) 0xFF4ADE80.toInt() else 0xFF64748B.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            c.drawRect(nx.toFloat(), ny.toFloat(), (nx + nw).toFloat(), (ny + nh).toFloat(), borderP)
            c.drawText(nodes[i], (nx + nw * 0.5f).roundToInt().toFloat(), (ny + 26).toFloat(), lblPaint)
        }

        // Animated progress gauge bar inside bottom layer
        val barLeft = left + 16f
        val barRight = right - 16f
        val barTop = (DERMIS_BOT + 26).toFloat()
        val barBot = (DERMIS_BOT + 42).toFloat()
        fill.color = 0xFF1E293B.toInt()
        c.drawRect(barLeft, barTop, barRight, barBot, fill)
        val stageFrac = when (stage) {
            CutawayStage.SURFACE_ZOOM_IN -> 0.20f
            CutawayStage.CROSS_SECTION_DAMAGE -> 0.35f
            CutawayStage.IMMUNE_CLEANUP -> 0.55f
            CutawayStage.STRAND_AND_VESSEL_REBUILD -> 0.72f
            CutawayStage.CELL_MIGRATION_CLOSE -> 0.88f
            CutawayStage.SURFACE_HEALED_ZOOM_OUT -> 1.0f
        } * Easing.smoothstep((sceneElapsedSec / 0.8f).coerceIn(0.25f, 1f))
        fill.color = if (progress > 0.7f || stage == CutawayStage.SURFACE_HEALED_ZOOM_OUT) 0xFF4ADE80.toInt() else 0xFF38BDF8.toInt()
        c.drawRect(barLeft + 2f, barTop + 2f, barLeft + 2f + (barRight - barLeft - 4f) * stageFrac, barBot - 2f, fill)

        val border = newPixelPaint().apply {
            color = 0xFF38BDF8.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        c.drawRect(left, EPIDERMIS_TOP.toFloat(), right, ADIPOSE_BOT.toFloat(), border)
    }

    private fun drawFlowDirectionOverlay(
        c: Canvas,
        flowDirection: FlowDirection,
        sceneElapsedSec: Float,
        globalTimeSec: Float
    ) {
        val cx = PIXEL_WIDTH * 0.5f
        val cy = (EPIDERMIS_TOP + ADIPOSE_BOT) * 0.5f
        val path = when (flowDirection) {
            FlowDirection.UP -> Path().apply {
                moveTo(cx - 48f, (ADIPOSE_BOT - 24).toFloat())
                lineTo(cx - 48f, (EPIDERMIS_TOP + 16).toFloat())
                moveTo(cx + 48f, (ADIPOSE_BOT - 24).toFloat())
                lineTo(cx + 48f, (EPIDERMIS_TOP + 16).toFloat())
            }
            FlowDirection.DOWN -> Path().apply {
                moveTo(cx - 48f, (EPIDERMIS_TOP + 16).toFloat())
                lineTo(cx - 48f, (ADIPOSE_BOT - 24).toFloat())
                moveTo(cx + 48f, (EPIDERMIS_TOP + 16).toFloat())
                lineTo(cx + 48f, (ADIPOSE_BOT - 24).toFloat())
            }
            FlowDirection.LEFT_TO_RIGHT -> Path().apply {
                moveTo((BLOCK_LEFT + 14).toFloat(), cy - 28f)
                lineTo((BLOCK_RIGHT - 14).toFloat(), cy - 28f)
                moveTo((BLOCK_LEFT + 14).toFloat(), cy + 28f)
                lineTo((BLOCK_RIGHT - 14).toFloat(), cy + 28f)
            }
            FlowDirection.RADIAL_BURST -> Path().apply {
                moveTo(cx, cy)
                lineTo(cx - 72f, cy - 64f)
                moveTo(cx, cy)
                lineTo(cx + 72f, cy - 64f)
                moveTo(cx, cy)
                lineTo(cx - 72f, cy + 64f)
                moveTo(cx, cy)
                lineTo(cx + 72f, cy + 64f)
            }
        }

        val reveal = (sceneElapsedSec / 0.85f).coerceIn(0.15f, 1f)
        val outerPaint = newPixelPaint().apply {
            color = 0x550F172A
            style = Paint.Style.STROKE
            strokeWidth = 4f
            strokeCap = Paint.Cap.SQUARE
        }
        val glowPaint = newPixelPaint().apply {
            color = 0xAA38BDF8.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f
            strokeCap = Paint.Cap.SQUARE
        }
        val pm = PathMeasure(path, false)
        val dotFill = newPixelPaint().apply { color = 0xFF4ADE80.toInt() }
        val dotOutline = newPixelPaint().apply { color = 0xFF070B14.toInt() }
        val pos = FloatArray(2)
        var contourIdx = 0
        do {
            val len = pm.length
            if (len > 2f) {
                val seg = Path()
                pm.getSegment(0f, len * reveal, seg, true)
                c.drawPath(seg, outerPaint)
                c.drawPath(seg, glowPaint)

                for (p in 0 until 2) {
                    val t = ((globalTimeSec * 0.72f + p * 0.5f + contourIdx * 0.22f) % 1f)
                    if (t <= reveal) {
                        pm.getPosTan(len * t, pos, null)
                        val px = pos[0].roundToInt()
                        val py = pos[1].roundToInt()
                        c.drawRect((px - 3).toFloat(), (py - 3).toFloat(), (px + 3).toFloat(), (py + 3).toFloat(), dotOutline)
                        c.drawRect((px - 2).toFloat(), (py - 2).toFloat(), (px + 2).toFloat(), (py + 2).toFloat(), dotFill)
                    }
                }
            }
            contourIdx++
        } while (pm.nextContour())
    }

    private fun drawCaptionSafeFooter(c: Canvas) {
        val fill = newPixelPaint()
        fill.color = 0xFF070B14.toInt()
        c.drawRect(0f, (ADIPOSE_BOT + 4).toFloat(), PIXEL_WIDTH.toFloat(), PIXEL_HEIGHT.toFloat(), fill)

        // Subtle pixel scanline accents in caption footer
        fill.color = 0x2238BDF8
        var sy = ADIPOSE_BOT + 12
        while (sy < PIXEL_HEIGHT) {
            c.drawRect(0f, sy.toFloat(), PIXEL_WIDTH.toFloat(), (sy + 1).toFloat(), fill)
            sy += 8
        }
    }
}
