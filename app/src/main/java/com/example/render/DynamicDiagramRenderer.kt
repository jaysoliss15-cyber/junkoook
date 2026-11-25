package com.example.render

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import com.example.model.AnimationStyle
import com.example.model.DynamicDiagramType
import com.example.model.HudWidget
import com.example.model.MetaphorVisual
import com.example.model.SceneScript
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Renders dynamic, data-driven 2D explainer diagrams driven purely by the scene's
 * topic content (no hardcoded fallback subjects).
 */
object DynamicDiagramRenderer {

    fun resolveDiagramType(scene: SceneScript, style: AnimationStyle): DynamicDiagramType {
        if (scene.diagramType != DynamicDiagramType.NONE) return scene.diagramType
        if (scene.metaphorVisual == MetaphorVisual.SUNBURST_SPOTLIGHT) {
            return DynamicDiagramType.SUNBURST_HERO_CALLOUT
        }
        if (style == AnimationStyle.PIXEL_CUTAWAY_EXPLAINER || style == AnimationStyle.CROSS_SECTION) {
            return DynamicDiagramType.NONE
        }
        return when {
            scene.leftLabel.isNotBlank() && scene.rightLabel.isNotBlank() -> DynamicDiagramType.BEFORE_AFTER_SPLIT
            scene.step1.isNotBlank() && scene.step2.isNotBlank() -> DynamicDiagramType.STEP_FLOW_ARROWS
            scene.hudWidget == HudWidget.NONE && scene.hudLabel.isNotBlank() && scene.hudFromValue != scene.hudToValue ->
                DynamicDiagramType.METER_GAUGE_SHIFT
            else -> DynamicDiagramType.NONE
        }
    }

    fun isDiagramAlongsideCharacter(type: DynamicDiagramType): Boolean {
        return type != DynamicDiagramType.NONE && type != DynamicDiagramType.SUNBURST_HERO_CALLOUT
    }

    fun drawSunburstHeroCallout(
        canvas: Canvas,
        w: Float,
        h: Float,
        scene: SceneScript,
        sceneElapsedSec: Float,
        globalTimeSec: Float
    ) {
        MetaphorVisualRenderer.drawSunburstBackground(
            canvas = canvas,
            w = w,
            h = h,
            scene = scene,
            globalTimeSec = globalTimeSec
        )
        val banner = scene.calloutBanner.ifBlank { scene.topicPillLabel }.trim().uppercase()
        if (banner.isEmpty()) return

        val u = w / 1080f
        val pop = Easing.easeOutBack((sceneElapsedSec / 0.35f).coerceIn(0f, 1f)).coerceAtLeast(0.05f)
        val cx = w * 0.5f
        val cy = h * 0.145f

        val tPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0F172A.toInt()
            textSize = 42f * u
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.05f
        }
        val tw = tPaint.measureText(banner)
        val pw = (tw + 76f * u).coerceIn(280f * u, w * 0.86f)
        val ph = 82f * u

        canvas.save()
        canvas.scale(pop, pop, cx, cy)
        canvas.rotate(-2.5f, cx, cy)

        val pillRect = RectF(cx - pw * 0.5f, cy - ph * 0.5f, cx + pw * 0.5f, cy + ph * 0.5f)
        canvas.drawRoundRect(
            RectF(pillRect.left + 6f * u, pillRect.top + 10f * u, pillRect.right + 6f * u, pillRect.bottom + 10f * u),
            24f * u, 24f * u,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 }
        )
        canvas.drawRoundRect(
            pillRect, 24f * u, 24f * u,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFACC15.toInt() }
        )
        canvas.drawRoundRect(
            pillRect, 24f * u, 24f * u,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF0E1018.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 6f * u
            }
        )
        val fm = tPaint.fontMetrics
        canvas.drawText(banner.take(26), cx, cy - (fm.ascent + fm.descent) * 0.5f, tPaint)
        canvas.restore()
    }

    fun drawDiagram(
        canvas: Canvas,
        w: Float,
        h: Float,
        diagramType: DynamicDiagramType,
        scene: SceneScript,
        sceneElapsedSec: Float,
        sceneDurationSec: Float,
        globalTimeSec: Float
    ) {
        if (!isDiagramAlongsideCharacter(diagramType)) return
        val u = w / 1080f

        val cardRect = RectF(w * 0.38f, h * 0.145f, w * 0.95f, h * 0.455f)
        val enterScale = Easing.easeOutBack((sceneElapsedSec / 0.40f).coerceIn(0f, 1f)).coerceAtLeast(0.05f)
        val cx = cardRect.centerX()
        val cy = cardRect.centerY()

        canvas.save()
        canvas.scale(enterScale, enterScale, cx, cy)

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
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF7F8FAFC.toInt() }
        )
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0E1018.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 6f * u
        }
        canvas.drawRoundRect(cardRect, 28f * u, 28f * u, outline)

        when (diagramType) {
            DynamicDiagramType.BEFORE_AFTER_SPLIT -> drawBeforeAfterSplit(canvas, cardRect, u, scene, sceneElapsedSec, globalTimeSec, outline)
            DynamicDiagramType.STEP_FLOW_ARROWS -> drawStepFlowArrows(canvas, cardRect, u, scene, sceneElapsedSec, globalTimeSec, outline)
            DynamicDiagramType.METER_GAUGE_SHIFT -> drawMeterGaugeShift(canvas, cardRect, u, scene, sceneElapsedSec, sceneDurationSec, outline)
            DynamicDiagramType.SCALE_WEIGH_OFF -> drawScaleWeighOff(canvas, cardRect, u, scene, sceneElapsedSec, globalTimeSec, outline)
            DynamicDiagramType.SUNBURST_HERO_CALLOUT, DynamicDiagramType.NONE -> Unit
        }

        canvas.restore()
    }

    private fun drawHeader(canvas: Canvas, box: RectF, u: Float, title: String, pillColor: Int) {
        val clean = title.trim().uppercase().ifBlank { "EXPLAINER" }.take(22)
        val tPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 25f * u
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.05f
        }
        val tw = tPaint.measureText(clean)
        val pw = (tw + 44f * u).coerceAtMost(box.width() - 32f * u)
        val ph = 46f * u
        val cx = box.centerX()
        val cy = box.top + 44f * u
        val rect = RectF(cx - pw * 0.5f, cy - ph * 0.5f, cx + pw * 0.5f, cy + ph * 0.5f)
        canvas.drawRoundRect(rect, ph * 0.5f, ph * 0.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pillColor })
        canvas.drawRoundRect(rect, ph * 0.5f, ph * 0.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0E1018.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 4f * u
        })
        val fm = tPaint.fontMetrics
        canvas.drawText(clean, cx, cy - (fm.ascent + fm.descent) * 0.5f, tPaint)
    }

    private fun drawBeforeAfterSplit(
        canvas: Canvas,
        box: RectF,
        u: Float,
        scene: SceneScript,
        sceneElapsedSec: Float,
        globalTimeSec: Float,
        outline: Paint
    ) {
        val title = scene.topicPillLabel.ifBlank { scene.calloutBanner }.ifBlank { "COMPARISON" }
        drawHeader(canvas, box, u, title, 0xFF2563EB.toInt())

        val leftTxt = scene.leftLabel.trim().uppercase().ifBlank { "BEFORE" }.take(18)
        val rightTxt = scene.rightLabel.trim().uppercase().ifBlank { "AFTER" }.take(18)

        val panelTop = box.top + 88f * u
        val panelBot = box.bottom - 34f * u
        val midX = box.centerX()

        val leftBox = RectF(box.left + 22f * u, panelTop, midX - 10f * u, panelBot)
        val rightBox = RectF(midX + 10f * u, panelTop, box.right - 22f * u, panelBot)

        canvas.drawRoundRect(leftBox, 18f * u, 18f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFEE2E2.toInt() })
        canvas.drawRoundRect(leftBox, 18f * u, 18f * u, Paint(outline).apply { strokeWidth = 4f * u; color = 0xFFEF4444.toInt() })

        canvas.drawRoundRect(rightBox, 18f * u, 18f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDCFCE7.toInt() })
        canvas.drawRoundRect(rightBox, 18f * u, 18f * u, Paint(outline).apply { strokeWidth = 4f * u; color = 0xFF16A34A.toInt() })

        // Draw dynamic visual bar gauges inside each half
        val pulse = 0.05f * sin(globalTimeSec * 4f)
        val leftFill = (0.34f + pulse).coerceIn(0.2f, 0.5f)
        val rightFill = (0.82f + pulse * 0.5f).coerceIn(0.6f, 0.95f)
        val barW = 48f * u
        val barBot = panelBot - 28f * u
        val barMaxH = (panelBot - panelTop) * 0.52f

        val lBarTop = barBot - barMaxH * leftFill
        canvas.drawRoundRect(
            RectF(leftBox.centerX() - barW * 0.5f, lBarTop, leftBox.centerX() + barW * 0.5f, barBot),
            10f * u, 10f * u,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEF4444.toInt() }
        )

        val rBarTop = barBot - barMaxH * rightFill
        canvas.drawRoundRect(
            RectF(rightBox.centerX() - barW * 0.5f, rBarTop, rightBox.centerX() + barW * 0.5f, barBot),
            10f * u, 10f * u,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF16A34A.toInt() }
        )

        val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 23f * u
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        lblPaint.color = 0xFF991B1B.toInt()
        canvas.drawText(leftTxt, leftBox.centerX(), panelTop + 44f * u, lblPaint)
        lblPaint.color = 0xFF166534.toInt()
        canvas.drawText(rightTxt, rightBox.centerX(), panelTop + 44f * u, lblPaint)

        // Animated VS / Transition Arrow badge in center
        val shift = sin(sceneElapsedSec * 3.5f) * 4f * u
        val badgeCx = midX + shift
        val badgeCy = (panelTop + panelBot) * 0.5f
        canvas.drawCircle(badgeCx, badgeCy, 24f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0F172A.toInt() })
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFACC15.toInt()
            textSize = 24f * u
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val fm = arrowPaint.fontMetrics
        canvas.drawText("→", badgeCx, badgeCy - (fm.ascent + fm.descent) * 0.5f, arrowPaint)
    }

    private fun drawStepFlowArrows(
        canvas: Canvas,
        box: RectF,
        u: Float,
        scene: SceneScript,
        sceneElapsedSec: Float,
        globalTimeSec: Float,
        outline: Paint
    ) {
        val title = scene.topicPillLabel.ifBlank { scene.calloutBanner }.ifBlank { "STEP BY STEP" }
        drawHeader(canvas, box, u, title, 0xFF0EA5E9.toInt())

        val steps = listOf(
            scene.step1.trim().ifBlank { "Step 1" }.take(26),
            scene.step2.trim().ifBlank { "Step 2" }.take(26),
            scene.step3.trim().ifBlank { "Step 3" }.take(26)
        )
        val stepColors = intArrayOf(0xFF3B82F6.toInt(), 0xFF8B5CF6.toInt(), 0xFF10B981.toInt())

        val topY = box.top + 96f * u
        val rowH = 88f * u
        val gapY = 34f * u
        val leftX = box.left + 28f * u
        val rightX = box.right - 28f * u

        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f * u
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0F172A.toInt()
            textSize = 24f * u
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }

        steps.forEachIndexed { idx, label ->
            val reveal = Easing.smoothstep(((sceneElapsedSec - idx * 0.25f) / 0.35f).coerceIn(0f, 1f))
            if (reveal <= 0.02f) return@forEachIndexed

            val y0 = topY + idx * (rowH + gapY)
            val card = RectF(leftX, y0, rightX, y0 + rowH)
            val isHighlighted = ((globalTimeSec * 1.4f).toInt() % 3) == idx

            canvas.save()
            canvas.translate((1f - reveal) * 28f * u, 0f)
            canvas.drawRoundRect(
                card, 18f * u, 18f * u,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (isHighlighted) 0xFFE0F2FE.toInt() else Color.WHITE
                }
            )
            canvas.drawRoundRect(
                card, 18f * u, 18f * u,
                Paint(outline).apply {
                    strokeWidth = if (isHighlighted) 5f * u else 3.5f * u
                    color = if (isHighlighted) stepColors[idx] else 0xFF334155.toInt()
                }
            )

            val badgeCx = card.left + 38f * u
            val badgeCy = card.centerY()
            canvas.drawCircle(badgeCx, badgeCy, 22f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = stepColors[idx] })
            val nfm = numPaint.fontMetrics
            canvas.drawText("${idx + 1}", badgeCx, badgeCy - (nfm.ascent + nfm.descent) * 0.5f, numPaint)

            val tfm = txtPaint.fontMetrics
            canvas.drawText(label, card.left + 76f * u, badgeCy - (tfm.ascent + tfm.descent) * 0.5f, txtPaint)
            canvas.restore()

            if (idx < steps.lastIndex) {
                val arrowPath = Path().apply {
                    moveTo(box.centerX(), y0 + rowH + 2f * u)
                    lineTo(box.centerX(), y0 + rowH + gapY - 4f * u)
                }
                val pm = PathMeasure(arrowPath, false)
                val partial = Path()
                pm.getSegment(0f, pm.length * reveal, partial, true)
                canvas.drawPath(
                    partial,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = stepColors[idx]
                        style = Paint.Style.STROKE
                        strokeWidth = 6f * u
                        strokeCap = Paint.Cap.ROUND
                    }
                )
            }
        }
    }

    private fun drawMeterGaugeShift(
        canvas: Canvas,
        box: RectF,
        u: Float,
        scene: SceneScript,
        sceneElapsedSec: Float,
        sceneDurationSec: Float,
        outline: Paint
    ) {
        val title = scene.hudLabel.ifBlank { scene.topicPillLabel }.ifBlank { "METER SHIFT" }
        drawHeader(canvas, box, u, title, 0xFF7C3AED.toInt())

        val animFrac = Easing.smoothstep((sceneElapsedSec / (sceneDurationSec * 0.65f).coerceAtLeast(1f)).coerceIn(0f, 1f))
        val fromV = scene.hudFromValue.coerceIn(0, 100)
        val toV = scene.hudToValue.coerceIn(0, 100)
        val currentV = (fromV + (toV - fromV) * animFrac).roundToInt()

        val gaugeCx = box.centerX()
        val gaugeCy = box.centerY() + 54f * u
        val radius = 148f * u
        val arcRect = RectF(gaugeCx - radius, gaugeCy - radius, gaugeCx + radius, gaugeCy + radius)

        // Background track arc
        canvas.drawArc(
            arcRect, 180f, 180f, false,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFE2E8F0.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 34f * u
                strokeCap = Paint.Cap.ROUND
            }
        )

        val sweep = 180f * (currentV / 100f)
        val activeColor = when {
            currentV >= 70 -> 0xFF10B981.toInt()
            currentV >= 40 -> 0xFFF59E0B.toInt()
            else -> 0xFFEF4444.toInt()
        }
        canvas.drawArc(
            arcRect, 180f, sweep, false,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = activeColor
                style = Paint.Style.STROKE
                strokeWidth = 34f * u
                strokeCap = Paint.Cap.ROUND
            }
        )

        // Needle
        val needleRad = Math.toRadians(180.0 + sweep.toDouble())
        val nx = gaugeCx + cos(needleRad).toFloat() * (radius - 16f * u)
        val ny = gaugeCy + sin(needleRad).toFloat() * (radius - 16f * u)
        canvas.drawLine(
            gaugeCx, gaugeCy, nx, ny,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF0F172A.toInt()
                strokeWidth = 8f * u
                strokeCap = Paint.Cap.ROUND
            }
        )
        canvas.drawCircle(gaugeCx, gaugeCy, 16f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0F172A.toInt() })

        val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0F172A.toInt()
            textSize = 54f * u
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("$currentV%", gaugeCx, gaugeCy + 78f * u, valPaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF475569.toInt()
            textSize = 24f * u
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("$fromV% → $toV%", gaugeCx, box.bottom - 26f * u, subPaint)
    }

    private fun drawScaleWeighOff(
        canvas: Canvas,
        box: RectF,
        u: Float,
        scene: SceneScript,
        sceneElapsedSec: Float,
        globalTimeSec: Float,
        outline: Paint
    ) {
        val title = scene.topicPillLabel.ifBlank { scene.calloutBanner }.ifBlank { "WEIGH OFF" }
        drawHeader(canvas, box, u, title, 0xFFD97706.toInt())

        val leftTxt = scene.leftLabel.trim().uppercase().ifBlank { "OPTION A" }.take(14)
        val rightTxt = scene.rightLabel.trim().uppercase().ifBlank { "OPTION B" }.take(14)

        val cx = box.centerX()
        val baseY = box.bottom - 48f * u
        val pivotY = box.centerY() - 28f * u

        // Stand & pivot
        canvas.drawLine(cx, baseY, cx, pivotY, Paint(outline).apply { strokeWidth = 10f * u })
        canvas.drawLine(cx - 88f * u, baseY, cx + 88f * u, baseY, Paint(outline).apply { strokeWidth = 10f * u; strokeCap = Paint.Cap.ROUND })

        val tiltDeg = 12f * Easing.smoothstep((sceneElapsedSec / 0.8f).coerceIn(0f, 1f)) +
            sin(globalTimeSec * 2.5f) * 2.2f
        val rad = tiltDeg * (PI.toFloat() / 180f)
        val armLen = 176f * u

        val lx = cx - cos(rad) * armLen
        val ly = pivotY - sin(rad) * armLen
        val rx = cx + cos(rad) * armLen
        val ry = pivotY + sin(rad) * armLen

        canvas.drawLine(lx, ly, rx, ry, Paint(outline).apply { strokeWidth = 9f * u; strokeCap = Paint.Cap.ROUND })
        canvas.drawCircle(cx, pivotY, 14f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF59E0B.toInt() })

        val panW = 148f * u
        val panH = 54f * u
        val ropePaint = Paint(outline).apply { strokeWidth = 4f * u }
        val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 21f * u
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val fm = lblPaint.fontMetrics

        // Left pan (higher)
        val lPanY = ly + 72f * u
        canvas.drawLine(lx, ly, lx - 36f * u, lPanY, ropePaint)
        canvas.drawLine(lx, ly, lx + 36f * u, lPanY, ropePaint)
        val lRect = RectF(lx - panW * 0.5f, lPanY, lx + panW * 0.5f, lPanY + panH)
        canvas.drawRoundRect(lRect, 14f * u, 14f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEF4444.toInt() })
        canvas.drawRoundRect(lRect, 14f * u, 14f * u, Paint(outline).apply { strokeWidth = 4f * u })
        canvas.drawText(leftTxt, lx, lRect.centerY() - (fm.ascent + fm.descent) * 0.5f, lblPaint)

        // Right pan (lower / heavier winner)
        val rPanY = ry + 72f * u
        canvas.drawLine(rx, ry, rx - 36f * u, rPanY, ropePaint)
        canvas.drawLine(rx, ry, rx + 36f * u, rPanY, ropePaint)
        val rRect = RectF(rx - panW * 0.5f, rPanY, rx + panW * 0.5f, rPanY + panH)
        canvas.drawRoundRect(rRect, 14f * u, 14f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF10B981.toInt() })
        canvas.drawRoundRect(rRect, 14f * u, 14f * u, Paint(outline).apply { strokeWidth = 4f * u })
        canvas.drawText(rightTxt, rx, rRect.centerY() - (fm.ascent + fm.descent) * 0.5f, lblPaint)
    }
}
