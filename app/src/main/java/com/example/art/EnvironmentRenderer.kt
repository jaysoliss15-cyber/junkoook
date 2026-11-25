package com.example.art

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.example.model.AnimationStyle
import com.example.model.EnvironmentTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Multi-Environment 2.5D Procedural Stage Engine.
 * Eliminates dark-night/brick-wall defaults by providing 10 distinct multi-layered
 * daytime, indoor, finance, lab, studio, bedroom, and sunset stages.
 */
object EnvironmentRenderer {

    fun resolveEnvironmentTheme(
        prompt: String,
        explicitTheme: EnvironmentTheme?,
        style: AnimationStyle
    ): EnvironmentTheme {
        if (explicitTheme != null) return explicitTheme
        val p = prompt.lowercase()
        return when {
            listOf("kitchen", "kusina", "breakfast", "almusal", "morning", "umaga", "luto", "kape", "coffee", "egg", "itlog")
                .any { p.contains(it) } -> EnvironmentTheme.BRIGHT_MORNING_KITCHEN

            listOf("market", "palengke", "tindahan", "store", "vendor", "street", "kalye", "jeep", "sari-sari", "lantern")
                .any { p.contains(it) } -> EnvironmentTheme.SUNNY_MARKET_STREET

            listOf("office", "opisina", "desk", "laptop", "computer", "work", "trabaho", "meeting", "bpo", "corporate")
                .any { p.contains(it) } -> EnvironmentTheme.MODERN_OFFICE_DESK

            listOf("bedroom", "kwarto", "midnight", "hatinggabi", "tulog", "sleep", "doomscroll", "night", "horror", "ghost", "haunted", "alas-tres")
                .any { p.contains(it) } -> EnvironmentTheme.COZY_BEDROOM_MIDNIGHT

            listOf("classroom", "school", "paaralan", "chalkboard", "pisara", "teacher", "guro", "lesson", "lecture")
                .any { p.contains(it) } -> EnvironmentTheme.CLASSROOM_CHALKBOARD

            listOf("bank", "vault", "bangko", "finance", "ipon", "sahod", "pera", "rich", "mansion", "gold", "peso", "wallet")
                .any { p.contains(it) } -> EnvironmentTheme.BANK_VAULT_FINANCE

            listOf("hospital", "clinic", "klinika", "ospital", "doctor", "doktor", "nurse", "nars", "checkup", "pasiente", "medical")
                .any { p.contains(it) } -> EnvironmentTheme.HOSPITAL_CLINIC

            listOf("lab", "science", "agham", "biology", "cell", "brain", "neuron", "dna", "microscope")
                .any { p.contains(it) } -> EnvironmentTheme.SCIENCE_LAB_INTERIOR

            listOf("sunset", "dusk", "takipsilim", "skyline", "city", "manila", "building", "rooftop", "tower")
                .any { p.contains(it) } -> EnvironmentTheme.DRAMATIC_SUNSET_CITY

            listOf("park", "nature", "garden", "tree", "puno", "barrio", "province", "probinsya", "farm", "meadow")
                .any { p.contains(it) } -> EnvironmentTheme.SUNNY_PARK_NATURE

            style == AnimationStyle.DARK_SUSPENSE -> EnvironmentTheme.COZY_BEDROOM_MIDNIGHT
            style == AnimationStyle.TECH_EXPLAINER ||
                style == AnimationStyle.CROSS_SECTION ||
                style == AnimationStyle.FLAT_VECTOR -> EnvironmentTheme.ABSTRACT_STUDIO_GRADIENT
            else -> EnvironmentTheme.SUNNY_PARK_NATURE
        }
    }

    fun render(
        canvas: Canvas,
        width: Int,
        height: Int,
        theme: EnvironmentTheme,
        style: AnimationStyle,
        seed: Int
    ) {
        val w = width.toFloat()
        val h = height.toFloat()
        val u = w / 1080f
        val rng = Random(seed)

        when (theme) {
            EnvironmentTheme.BRIGHT_MORNING_KITCHEN -> drawBrightMorningKitchen(canvas, w, h, u)
            EnvironmentTheme.SUNNY_MARKET_STREET -> drawSunnyMarketStreet(canvas, w, h, u, rng)
            EnvironmentTheme.MODERN_OFFICE_DESK -> drawModernOfficeDesk(canvas, w, h, u)
            EnvironmentTheme.COZY_BEDROOM_MIDNIGHT -> drawCozyBedroomMidnight(canvas, w, h, u, rng)
            EnvironmentTheme.CLASSROOM_CHALKBOARD -> drawClassroomChalkboard(canvas, w, h, u)
            EnvironmentTheme.BANK_VAULT_FINANCE -> drawBankVaultFinance(canvas, w, h, u)
            EnvironmentTheme.SUNNY_PARK_NATURE -> drawSunnyParkNature(canvas, w, h, u, rng)
            EnvironmentTheme.SCIENCE_LAB_INTERIOR -> drawScienceLabInterior(canvas, w, h, u, rng)
            EnvironmentTheme.HOSPITAL_CLINIC -> drawHospitalClinic(canvas, w, h, u)
            EnvironmentTheme.ABSTRACT_STUDIO_GRADIENT -> drawAbstractStudioGradient(canvas, w, h, u, style)
            EnvironmentTheme.DRAMATIC_SUNSET_CITY -> drawDramaticSunsetCity(canvas, w, h, u, rng)
        }

        // Paper Diorama edge-softening / Dark Suspense atmospheric polish without turning daytime scenes black
        if (style == AnimationStyle.DARK_SUSPENSE && theme != EnvironmentTheme.COZY_BEDROOM_MIDNIGHT) {
            val moodScrim = Paint().apply { color = 0x52090C18 }
            canvas.drawRect(0f, 0f, w, h, moodScrim)
        }

        val topVignette = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h * 0.14f,
                intArrayOf(0x30000000, 0x00000000),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h * 0.14f, topVignette)

        val bottomTiltShift = Paint().apply {
            shader = LinearGradient(
                0f, h * 0.80f, 0f, h,
                intArrayOf(0x00000000, 0x6E070A12),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, h * 0.80f, w, h, bottomTiltShift)
    }

    private fun drawBrightMorningKitchen(canvas: Canvas, w: Float, h: Float, u: Float) {
        val groundY = h * 0.675f

        // Warm sunlit buttercream plaster wall
        canvas.drawRect(
            0f, 0f, w, groundY,
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, w, groundY,
                    intArrayOf(0xFFFFF8E7.toInt(), 0xFFFAE3B4.toInt(), 0xFFF3CFA0.toInt()),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        )

        // Subtle subway ceramic tile backsplash above counter
        val tileTop = h * 0.42f
        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        val groutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x2894A3B8
            setStyle(Paint.Style.STROKE)
            strokeWidth = 3f * u
        }
        canvas.drawRect(0f, tileTop, w, groundY, Paint().apply { color = 0xFFF8FAFC.toInt() })
        val tileW = 108f * u
        val tileH = 52f * u
        var ty = tileTop
        var row = 0
        while (ty < groundY) {
            var tx = if (row % 2 == 0) 0f else -tileW * 0.5f
            while (tx < w) {
                val r = RectF(tx + 2f * u, ty + 2f * u, tx + tileW - 2f * u, ty + tileH - 2f * u)
                canvas.drawRect(r, tilePaint)
                canvas.drawRect(r, groutPaint)
                tx += tileW
            }
            ty += tileH
            row++
        }

        // Large bright sunlit window showing morning sky & garden greenery
        val winRect = RectF(w * 0.23f, h * 0.14f, w * 0.77f, h * 0.40f)
        canvas.drawRoundRect(
            RectF(winRect.left - 18f * u, winRect.top - 18f * u, winRect.right + 18f * u, winRect.bottom + 20f * u),
            16f * u, 16f * u,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8B5E3C.toInt() }
        )
        canvas.drawRect(
            winRect,
            Paint().apply {
                shader = LinearGradient(
                    winRect.left, winRect.top, winRect.left, winRect.bottom,
                    intArrayOf(0xFF38BDF8.toInt(), 0xFFBAE6FD.toInt(), 0xFFFEF08A.toInt()),
                    floatArrayOf(0f, 0.65f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        )
        // Garden bushes visible outside window
        canvas.drawCircle(winRect.left + 85f * u, winRect.bottom - 10f * u, 70f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF34D399.toInt() })
        canvas.drawCircle(winRect.right - 95f * u, winRect.bottom, 82f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF10B981.toInt() })
        // Window muntins
        val sashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFAF6EE.toInt(); strokeWidth = 10f * u }
        canvas.drawLine(winRect.centerX(), winRect.top, winRect.centerX(), winRect.bottom, sashPaint)
        canvas.drawLine(winRect.left, winRect.centerY(), winRect.right, winRect.centerY(), sashPaint)

        // Floating wooden shelf & breakfast jars on left/right walls
        val shelfPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9A6B3F.toInt() }
        canvas.drawRoundRect(RectF(w * 0.04f, h * 0.24f, w * 0.19f, h * 0.26f), 6f * u, 6f * u, shelfPaint)
        canvas.drawRoundRect(RectF(w * 0.06f, h * 0.18f, w * 0.12f, h * 0.24f), 8f * u, 8f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF38BDF8.toInt() })
        canvas.drawRoundRect(RectF(w * 0.81f, h * 0.24f, w * 0.96f, h * 0.26f), 6f * u, 6f * u, shelfPaint)
        canvas.drawCircle(w * 0.88f, h * 0.205f, 34f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFBBF24.toInt() })

        // Kitchen countertop silhouette across lower wall
        val counterTop = h * 0.535f
        canvas.drawRect(0f, counterTop, w, groundY, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD97706.toInt() })
        canvas.drawRect(0f, counterTop, w, counterTop + 26f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFDFBF7.toInt() })
        // Morning kettle on left counter edge
        canvas.drawRoundRect(RectF(w * 0.08f, counterTop - 72f * u, w * 0.18f, counterTop), 18f * u, 18f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEF4444.toInt() })

        // Diagonal golden morning sunbeams slicing across kitchen
        val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                winRect.left, winRect.top, w * 0.85f, h * 0.82f,
                intArrayOf(0x55FFFBEB, 0x22FEF08A, 0x00FFFBEB),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val sunbeam = Path().apply {
            moveTo(winRect.left, winRect.top)
            lineTo(winRect.right, winRect.top)
            lineTo(w * 0.96f, h * 0.85f)
            lineTo(w * 0.35f, h * 0.85f)
            close()
        }
        canvas.drawPath(sunbeam, rayPaint)

        // Warm hardwood kitchen floor planks
        drawWoodFloor(canvas, w, h, u, groundY, lightWood = 0xFFD99B58.toInt(), darkWood = 0xFFA46B35.toInt())
    }

    private fun drawSunnyMarketStreet(canvas: Canvas, w: Float, h: Float, u: Float, rng: Random) {
        val groundY = h * 0.675f

        // Crisp bright daytime blue sky
        canvas.drawRect(
            0f, 0f, w, groundY,
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, groundY,
                    intArrayOf(0xFF0284C7.toInt(), 0xFF38BDF8.toInt(), 0xFFBAE6FD.toInt()),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        )

        drawSunOrb(canvas, w * 0.78f, h * 0.16f, w * 0.085f, 0xFFFEF08A.toInt())
        drawCloud(canvas, w * 0.26f, h * 0.14f, w * 0.36f, h * 0.045f, 0xEBFFFFFF.toInt(), 0x220284C7, u)
        drawCloud(canvas, w * 0.64f, h * 0.22f, w * 0.30f, h * 0.038f, 0xE6FFFFFF.toInt(), 0x220284C7, u)

        // Distant rolling green hill horizon
        drawSineHill(canvas, w, h, groundY - 90f * u, 38f * u, 1.8f, 0.6f, 0xFF34D399.toInt(), 0xFF059669.toInt())

        // Left & right sunlit Pinoy shophouses + colorful striped market awnings
        val stallColors = listOf(
            Triple(0xFFFFF1E6.toInt(), 0xFFEF4444.toInt(), 0xFFFCA5A5.toInt()),
            Triple(0xFFFEF9C3.toInt(), 0xFF0EA5E9.toInt(), 0xFFBAE6FD.toInt()),
            Triple(0xFFECFDF5.toInt(), 0xFFF59E0B.toInt(), 0xFFFDE68A.toInt())
        )
        val stallW = w * 0.31f
        val xs = listOf(w * 0.03f, w * 0.345f, w * 0.66f)
        xs.forEachIndexed { idx, sx ->
            val (wall, stripeA, stripeB) = stallColors[idx]
            val bh = h * (0.21f + (idx % 2) * 0.035f)
            val top = groundY - bh
            canvas.drawRoundRect(RectF(sx, top, sx + stallW, groundY), 10f * u, 10f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = wall })
            // Striped awning
            val awnTop = top + bh * 0.30f
            val awnBot = awnTop + 44f * u
            val stripes = 6
            val sw = (stallW + 16f * u) / stripes
            for (s in 0 until stripes) {
                val r = RectF(sx - 8f * u + s * sw, awnTop, sx - 8f * u + (s + 1) * sw, awnBot)
                canvas.drawRect(r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (s % 2 == 0) stripeA else stripeB })
            }
            // Shop doorway & display goods
            canvas.drawRoundRect(
                RectF(sx + stallW * 0.20f, awnBot + 16f * u, sx + stallW * 0.80f, groundY),
                8f * u, 8f * u,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF475569.toInt() }
            )
            // Potted tropical shrub beside stall
            canvas.drawCircle(sx + 16f * u, groundY - 28f * u, 28f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF16A34A.toInt() })
        }

        // Festive fiesta buntings curving across top sky
        val cord = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF334155.toInt(); setStyle(Paint.Style.STROKE); strokeWidth = 3f * u }
        val cordPath = Path().apply {
            moveTo(0f, h * 0.12f)
            quadTo(w * 0.5f, h * 0.21f, w, h * 0.13f)
        }
        canvas.drawPath(cordPath, cord)
        val flagColors = intArrayOf(0xFFEF4444.toInt(), 0xFFFACC15.toInt(), 0xFF22C55E.toInt(), 0xFF3B82F6.toInt())
        for (i in 1..9) {
            val t = i / 10f
            val fx = t * w
            val fy = h * 0.12f * (1 - t) * (1 - t) + 2f * (1 - t) * t * (h * 0.21f) + t * t * (h * 0.13f)
            val tri = Path().apply {
                moveTo(fx - 16f * u, fy)
                lineTo(fx + 16f * u, fy)
                lineTo(fx, fy + 32f * u)
                close()
            }
            canvas.drawPath(tri, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = flagColors[i % flagColors.size] })
        }

        // Clean paved sidewalk & sunlit asphalt street
        canvas.drawRect(0f, groundY, w, groundY + 62f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE2E8F0.toInt() })
        canvas.drawRect(0f, groundY + 62f * u, w, h, Paint().apply {
            shader = LinearGradient(0f, groundY + 62f * u, 0f, h, 0xFF64748B.toInt(), 0xFF334155.toInt(), Shader.TileMode.CLAMP)
        })
        // Dashed sunlit road center line
        val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFACC15.toInt() }
        var dx = 40f * u + (rng.nextInt(40) * u)
        while (dx < w) {
            canvas.drawRoundRect(RectF(dx, groundY + 210f * u, dx + 110f * u, groundY + 228f * u), 6f * u, 6f * u, dashPaint)
            dx += 190f * u
        }
    }

    private fun drawModernOfficeDesk(canvas: Canvas, w: Float, h: Float, u: Float) {
        val groundY = h * 0.675f

        // Crisp architectural slate-white interior wall
        canvas.drawRect(
            0f, 0f, w, groundY,
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, groundY,
                    intArrayOf(0xFFE2E8F0.toInt(), 0xFFF1F5F9.toInt(), 0xFFCBD5E1.toInt()),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                )
            }
        )

        // Floor-to-ceiling panoramic city daylight window
        val winRect = RectF(w * 0.14f, h * 0.12f, w * 0.86f, h * 0.50f)
        canvas.drawRect(
            winRect,
            Paint().apply {
                shader = LinearGradient(
                    0f, winRect.top, 0f, winRect.bottom,
                    intArrayOf(0xFF0EA5E9.toInt(), 0xFF7DD3FC.toInt(), 0xFFE0F2FE.toInt()),
                    floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
                )
            }
        )
        // Distant sunlit skyline silhouettes inside office window
        val bPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF94A3B8.toInt() }
        for (i in 0 until 7) {
            val bx = winRect.left + i * (winRect.width() / 7f) + 6f * u
            val bh = (80f + (i % 3) * 48f) * u
            canvas.drawRect(bx, winRect.bottom - bh, bx + winRect.width() / 8f, winRect.bottom, bPaint)
        }
        val mullion = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF334155.toInt(); setStyle(Paint.Style.STROKE); strokeWidth = 12f * u }
        canvas.drawRect(winRect, mullion)
        canvas.drawLine(winRect.left + winRect.width() * 0.33f, winRect.top, winRect.left + winRect.width() * 0.33f, winRect.bottom, mullion)
        canvas.drawLine(winRect.left + winRect.width() * 0.66f, winRect.top, winRect.left + winRect.width() * 0.66f, winRect.bottom, mullion)

        // Office wooden credenza + glowing monitor + potted plant behind stage
        val deskTop = h * 0.54f
        canvas.drawRoundRect(RectF(w * 0.10f, deskTop, w * 0.90f, groundY), 10f * u, 10f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9A6B3F.toInt() })
        canvas.drawRoundRect(RectF(w * 0.08f, deskTop - 16f * u, w * 0.92f, deskTop + 8f * u), 8f * u, 8f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC59B6D.toInt() })
        // Monitor silhouette
        canvas.drawRect(w * 0.48f, deskTop - 36f * u, w * 0.52f, deskTop - 16f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF475569.toInt() })
        canvas.drawRoundRect(RectF(w * 0.36f, deskTop - 140f * u, w * 0.64f, deskTop - 34f * u), 10f * u, 10f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E293B.toInt() })
        canvas.drawRoundRect(RectF(w * 0.375f, deskTop - 130f * u, w * 0.625f, deskTop - 44f * u), 6f * u, 6f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF38BDF8.toInt() })
        // Monstera pot on right
        canvas.drawRoundRect(RectF(w * 0.77f, deskTop - 52f * u, w * 0.85f, deskTop - 14f * u), 8f * u, 8f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        canvas.drawCircle(w * 0.81f, deskTop - 72f * u, 36f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF16A34A.toInt() })

        // Modern polished warm-oak studio office floor
        drawWoodFloor(canvas, w, h, u, groundY, lightWood = 0xFF94A3B8.toInt(), darkWood = 0xFF475569.toInt())
    }

    private fun drawCozyBedroomMidnight(canvas: Canvas, w: Float, h: Float, u: Float, rng: Random) {
        val groundY = h * 0.675f

        // Deep cozy indigo bedroom wallpaper
        canvas.drawRect(
            0f, 0f, w, groundY,
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, groundY,
                    intArrayOf(0xFF0B1026.toInt(), 0xFF162044.toInt(), 0xFF1F2D5A.toInt()),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                )
            }
        )

        // Moonlight window with starry night & glowing crescent moon
        val winRect = RectF(w * 0.14f, h * 0.14f, w * 0.52f, h * 0.42f)
        canvas.drawRoundRect(RectF(winRect.left - 12f * u, winRect.top - 12f * u, winRect.right + 12f * u, winRect.bottom + 14f * u), 12f * u, 12f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E293B.toInt() })
        canvas.drawRect(winRect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF070B19.toInt() })
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE2E8F0.toInt() }
        for (i in 0 until 18) {
            val sx = winRect.left + 16f * u + rng.nextFloat() * (winRect.width() - 32f * u)
            val sy = winRect.top + 16f * u + rng.nextFloat() * (winRect.height() - 32f * u)
            canvas.drawCircle(sx, sy, (2f + (i % 3)) * u, starPaint)
        }
        val moonCx = winRect.right - 72f * u
        val moonCy = winRect.top + 76f * u
        canvas.drawCircle(moonCx, moonCy, 34f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFEF08A.toInt() })
        canvas.drawCircle(moonCx + 14f * u, moonCy - 8f * u, 30f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF070B19.toInt() })

        // Window bars
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF334155.toInt(); strokeWidth = 8f * u }
        canvas.drawLine(winRect.centerX(), winRect.top, winRect.centerX(), winRect.bottom, barPaint)
        canvas.drawLine(winRect.left, winRect.centerY(), winRect.right, winRect.centerY(), barPaint)

        // Cozy wooden bed headboard & plush blanket on right
        val bedLeft = w * 0.50f
        val bedRight = w * 0.96f
        canvas.drawRoundRect(RectF(bedLeft, h * 0.44f, bedRight, groundY), 16f * u, 16f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF452C1E.toInt() })
        canvas.drawRoundRect(RectF(bedLeft + 26f * u, h * 0.51f, bedLeft + 150f * u, h * 0.57f), 20f * u, 20f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE2E8F0.toInt() })
        canvas.drawRoundRect(RectF(bedLeft + 16f * u, h * 0.56f, bedRight - 10f * u, groundY), 18f * u, 18f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF312E81.toInt() })

        // Nightstand & glowing warm amber bedside lamp casting light pool
        val lampX = w * 0.40f
        val lampY = h * 0.45f
        val lampGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                lampX, lampY, 280f * u,
                intArrayOf(0xAAFEF08A.toInt(), 0x44F59E0B, 0x00F59E0B),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(lampX, lampY, 280f * u, lampGlow)
        canvas.drawRoundRect(RectF(lampX - 54f * u, h * 0.55f, lampX + 54f * u, groundY), 8f * u, 8f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3B2418.toInt() })
        canvas.drawRect(lampX - 6f * u, lampY + 26f * u, lampX + 6f * u, h * 0.55f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFBBF24.toInt() })
        val shade = Path().apply {
            moveTo(lampX - 30f * u, lampY - 20f * u)
            lineTo(lampX + 30f * u, lampY - 20f * u)
            lineTo(lampX + 46f * u, lampY + 28f * u)
            lineTo(lampX - 46f * u, lampY + 28f * u)
            close()
        }
        canvas.drawPath(shade, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFDE68A.toInt() })

        // Dark cozy bedroom hardwood floor + soft woven bedside rug
        drawWoodFloor(canvas, w, h, u, groundY, lightWood = 0xFF3B2A22.toInt(), darkWood = 0xFF1F1612.toInt())
        canvas.drawOval(RectF(w * 0.18f, groundY + 42f * u, w * 0.82f, groundY + 190f * u), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4338CA.toInt() })
    }

    private fun drawClassroomChalkboard(canvas: Canvas, w: Float, h: Float, u: Float) {
        val groundY = h * 0.675f

        // Warm sunlit cream classroom wall + oak wainscoting
        canvas.drawRect(0f, 0f, w, groundY, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFEF9EF.toInt() })
        canvas.drawRect(0f, h * 0.56f, w, groundY, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD4A373.toInt() })
        canvas.drawRect(0f, h * 0.56f, w, h * 0.575f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8B5E3C.toInt() })

        // Large classic forest-green chalkboard with wooden frame & chalk tray
        val boardRect = RectF(w * 0.09f, h * 0.14f, w * 0.91f, h * 0.49f)
        canvas.drawRoundRect(
            RectF(boardRect.left - 18f * u, boardRect.top - 18f * u, boardRect.right + 18f * u, boardRect.bottom + 24f * u),
            12f * u, 12f * u,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF78350F.toInt() }
        )
        canvas.drawRect(
            boardRect,
            Paint().apply {
                shader = LinearGradient(
                    boardRect.left, boardRect.top, boardRect.right, boardRect.bottom,
                    intArrayOf(0xFF14532D.toInt(), 0xFF166534.toInt(), 0xFF14532D.toInt()),
                    null, Shader.TileMode.CLAMP
                )
            }
        )
        // Subtle decorative chalk arc & dashed graph curve on board
        val chalkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x44FFFFFF
            setStyle(Paint.Style.STROKE)
            strokeWidth = 5f * u
            strokeCap = Paint.Cap.ROUND
        }
        val chalkCurve = Path().apply {
            moveTo(boardRect.left + 64f * u, boardRect.bottom - 52f * u)
            quadTo(boardRect.centerX(), boardRect.top + 80f * u, boardRect.right - 72f * u, boardRect.top + 48f * u)
        }
        canvas.drawPath(chalkCurve, chalkPaint)
        canvas.drawCircle(boardRect.left + 120f * u, boardRect.top + 96f * u, 38f * u, chalkPaint)

        // Polished honey classroom parquet floor
        drawWoodFloor(canvas, w, h, u, groundY, lightWood = 0xFFD99B58.toInt(), darkWood = 0xFFB0783E.toInt())
    }

    private fun drawBankVaultFinance(canvas: Canvas, w: Float, h: Float, u: Float) {
        val groundY = h * 0.675f

        // Rich emerald-navy marble architectural hall
        canvas.drawRect(
            0f, 0f, w, groundY,
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, groundY,
                    intArrayOf(0xFF0F292E.toInt(), 0xFF163D42.toInt(), 0xFF1E4D52.toInt()),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                )
            }
        )

        // Classical fluted architectural columns
        val colPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E3A40.toInt() }
        val goldTrim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFBBF24.toInt() }
        for (cx in listOf(w * 0.08f, w * 0.92f)) {
            canvas.drawRect(cx - 48f * u, h * 0.08f, cx + 48f * u, groundY, colPaint)
            canvas.drawRect(cx - 58f * u, h * 0.08f, cx + 58f * u, h * 0.11f, goldTrim)
            canvas.drawRect(cx - 58f * u, groundY - 26f * u, cx + 58f * u, groundY, goldTrim)
        }

        // Monumental circular brushed-steel bank vault door centerpiece
        val vx = w * 0.5f
        val vy = h * 0.37f
        val vr = 235f * u
        canvas.drawCircle(vx, vy, vr + 24f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF475569.toInt() })
        canvas.drawCircle(vx, vy, vr, Paint().apply {
            shader = RadialGradient(
                vx - 40f * u, vy - 40f * u, vr * 1.2f,
                intArrayOf(0xFFE2E8F0.toInt(), 0xFF94A3B8.toInt(), 0xFF475569.toInt()),
                null, Shader.TileMode.CLAMP
            )
        })
        canvas.drawCircle(vx, vy, vr * 0.80f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFBBF24.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = 10f * u
        })
        // Radial vault spokes
        val spokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF334155.toInt(); strokeWidth = 14f * u; strokeCap = Paint.Cap.ROUND }
        for (i in 0 until 6) {
            val a = Math.toRadians(i * 60.0)
            canvas.drawLine(vx, vy, vx + cos(a).toFloat() * vr * 0.65f, vy + sin(a).toFloat() * vr * 0.65f, spokePaint)
        }
        canvas.drawCircle(vx, vy, 38f * u, goldTrim)

        // Polished marble tile floor
        canvas.drawRect(
            0f, groundY, w, h,
            Paint().apply {
                shader = LinearGradient(0f, groundY, 0f, h, 0xFFCBD5E1.toInt(), 0xFF64748B.toInt(), Shader.TileMode.CLAMP)
            }
        )
        val tileEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x330F172A; strokeWidth = 4f * u }
        for (i in -3..3) {
            canvas.drawLine(w * 0.5f + i * 110f * u, groundY, w * 0.5f + i * 240f * u, h, tileEdge)
        }
        canvas.drawLine(0f, groundY + 110f * u, w, groundY + 110f * u, tileEdge)
        canvas.drawLine(0f, groundY + 280f * u, w, groundY + 280f * u, tileEdge)
    }

    private fun drawSunnyParkNature(canvas: Canvas, w: Float, h: Float, u: Float, rng: Random) {
        val groundY = h * 0.675f

        // Crisp sunny daylight sky
        canvas.drawRect(
            0f, 0f, w, groundY,
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, groundY,
                    intArrayOf(0xFF1D4ED8.toInt(), 0xFF38BDF8.toInt(), 0xFFD9F99D.toInt()),
                    floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP
                )
            }
        )

        drawSunOrb(canvas, w * 0.24f, h * 0.16f, w * 0.095f, 0xFFFEF08A.toInt())
        drawCloud(canvas, w * 0.72f, h * 0.15f, w * 0.36f, h * 0.045f, 0xEAFFFFFF.toInt(), 0x220EA5E9, u)
        drawCloud(canvas, w * 0.42f, h * 0.23f, w * 0.28f, h * 0.036f, 0xE2FFFFFF.toInt(), 0x220EA5E9, u)

        // Layered emerald hills
        drawSineHill(canvas, w, h, h * 0.54f, 44f * u, 1.5f, 0.4f, 0xFF4ADE80.toInt(), 0xFF16A34A.toInt())
        drawSineHill(canvas, w, h, h * 0.60f, 34f * u, 2.2f, 2.1f, 0xFF22C55E.toInt(), 0xFF15803D.toInt())

        // Framing lush park trees on left & right edges
        drawTree(canvas, w * 0.11f, groundY, h * 0.24f, u, 0xFF5D4037.toInt(), 0xFF15803D.toInt(), 0xFF4ADE80.toInt())
        drawTree(canvas, w * 0.89f, groundY, h * 0.26f, u, 0xFF5D4037.toInt(), 0xFF166534.toInt(), 0xFF22C55E.toInt())

        // Sunlit green meadow + warm garden stepping-stone walkway
        canvas.drawRect(
            0f, groundY, w, h,
            Paint().apply {
                shader = LinearGradient(0f, groundY, 0f, h, 0xFF4ADE80.toInt(), 0xFF15803D.toInt(), Shader.TileMode.CLAMP)
            }
        )
        val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE7D3B0.toInt() }
        val walkPath = Path().apply {
            moveTo(w * 0.42f, groundY)
            quadTo(w * 0.52f, (groundY + h) * 0.5f, w * 0.30f, h)
            lineTo(w * 0.72f, h)
            quadTo(w * 0.58f, (groundY + h) * 0.5f, w * 0.58f, groundY)
            close()
        }
        canvas.drawPath(walkPath, pathPaint)

        // Wildflowers & grass tufts
        val flowerColors = intArrayOf(0xFFFACC15.toInt(), 0xFFFB7185.toInt(), 0xFFFFFFFF.toInt())
        val flowerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 18) {
            val fx = rng.nextFloat() * w
            if (fx in (w * 0.34f)..(w * 0.66f)) continue
            val fy = groundY + 24f * u + rng.nextFloat() * (h - groundY - 48f * u)
            flowerPaint.color = flowerColors[i % flowerColors.size]
            canvas.drawCircle(fx, fy, 7f * u, flowerPaint)
        }
    }

    private fun drawScienceLabInterior(canvas: Canvas, w: Float, h: Float, u: Float, rng: Random) {
        val groundY = h * 0.675f

        // Crisp luminous cyan-white laboratory wall
        canvas.drawRect(
            0f, 0f, w, groundY,
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, groundY,
                    intArrayOf(0xFF0F172A.toInt(), 0xFF164E63.toInt(), 0xFF0E7490.toInt()),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                )
            }
        )

        // Subtle futuristic lab frosted glass panel + faint molecular hexagons
        val glassRect = RectF(w * 0.12f, h * 0.13f, w * 0.88f, h * 0.48f)
        canvas.drawRoundRect(glassRect, 20f * u, 20f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22E0F2FE })
        canvas.drawRoundRect(glassRect, 20f * u, 20f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x6638BDF8
            setStyle(Paint.Style.STROKE)
            strokeWidth = 4f * u
        })

        // Lab counter with glowing cyan & amber glass flasks/beakers
        val benchY = h * 0.54f
        canvas.drawRect(0f, benchY, w, groundY, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E293B.toInt() })
        canvas.drawRect(0f, benchY, w, benchY + 16f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF94A3B8.toInt() })

        val flaskX = listOf(w * 0.16f, w * 0.26f, w * 0.78f)
        val flaskColors = listOf(0xFF22D3EE.toInt(), 0xFF4ADE80.toInt(), 0xFFFACC15.toInt())
        flaskX.forEachIndexed { idx, fx ->
            canvas.drawRoundRect(RectF(fx - 22f * u, benchY - 72f * u, fx + 22f * u, benchY), 14f * u, 14f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88E0F2FE.toInt() })
            canvas.drawRoundRect(RectF(fx - 18f * u, benchY - 42f * u, fx + 18f * u, benchY - 4f * u), 10f * u, 10f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = flaskColors[idx] })
        }

        // Subtle rising laboratory shimmer particles
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x5538BDF8 }
        for (i in 0 until 12) {
            val px = w * 0.16f + rng.nextFloat() * w * 0.68f
            val py = h * 0.16f + rng.nextFloat() * h * 0.28f
            canvas.drawCircle(px, py, (4f + (i % 4) * 2f) * u, dotPaint)
        }

        // Sleek reflective epoxy lab floor
        canvas.drawRect(
            0f, groundY, w, h,
            Paint().apply {
                shader = LinearGradient(0f, groundY, 0f, h, 0xFF334155.toInt(), 0xFF0F172A.toInt(), Shader.TileMode.CLAMP)
            }
        )
        canvas.drawLine(0f, groundY, w, groundY, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAA38BDF8.toInt(); strokeWidth = 5f * u })
    }

    private fun drawHospitalClinic(canvas: Canvas, w: Float, h: Float, u: Float) {
        val groundY = h * 0.675f

        // Bright sterile mint-white clinic wall
        canvas.drawRect(
            0f, 0f, w, groundY,
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, groundY,
                    intArrayOf(0xFFF0FDFA.toInt(), 0xFFCCFBF1.toInt(), 0xFFE2E8F0.toInt()),
                    floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP
                )
            }
        )
        // Teal dado wainscoting band
        canvas.drawRect(0f, h * 0.50f, w, groundY, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0D9488.toInt() })
        canvas.drawRect(0f, h * 0.49f, w, h * 0.508f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF99F6E4.toInt() })

        // Bright daylight clinic window on left
        val winRect = RectF(w * 0.08f, h * 0.14f, w * 0.42f, h * 0.41f)
        canvas.drawRoundRect(RectF(winRect.left - 10f * u, winRect.top - 10f * u, winRect.right + 10f * u, winRect.bottom + 10f * u),
            12f * u, 12f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF64748B.toInt() })
        canvas.drawRect(winRect, Paint().apply {
            shader = LinearGradient(0f, winRect.top, 0f, winRect.bottom, 0xFF38BDF8.toInt(), 0xFFE0F2FE.toInt(), Shader.TileMode.CLAMP)
        })
        val sash = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 7f * u }
        canvas.drawLine(winRect.centerX(), winRect.top, winRect.centerX(), winRect.bottom, sash)
        canvas.drawLine(winRect.left, winRect.centerY(), winRect.right, winRect.centerY(), sash)

        // Clinic medical cross sign & wall health chart on right
        val badgeCx = w * 0.72f
        val badgeCy = h * 0.25f
        canvas.drawCircle(badgeCx, badgeCy, 64f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawCircle(badgeCx, badgeCy, 64f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0D9488.toInt(); setStyle(Paint.Style.STROKE); strokeWidth = 7f * u
        })
        val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEF4444.toInt() }
        canvas.drawRoundRect(RectF(badgeCx - 12f * u, badgeCy - 36f * u, badgeCx + 12f * u, badgeCy + 36f * u), 5f * u, 5f * u, crossPaint)
        canvas.drawRoundRect(RectF(badgeCx - 36f * u, badgeCy - 12f * u, badgeCx + 36f * u, badgeCy + 12f * u), 5f * u, 5f * u, crossPaint)

        // Clinic supply cabinet & medical bed silhouette
        canvas.drawRoundRect(RectF(w * 0.56f, h * 0.44f, w * 0.92f, groundY), 12f * u, 12f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawRect(w * 0.56f, h * 0.44f, w * 0.92f, h * 0.46f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0EA5E9.toInt() })

        // Clean light-gray clinic tile floor
        canvas.drawRect(
            0f, groundY, w, h,
            Paint().apply { shader = LinearGradient(0f, groundY, 0f, h, 0xFFF1F5F9.toInt(), 0xFFCBD5E1.toInt(), Shader.TileMode.CLAMP) }
        )
        val seam = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22334155; strokeWidth = 3f * u }
        for (i in -3..3) {
            canvas.drawLine(w * 0.5f + i * 115f * u, groundY, w * 0.5f + i * 240f * u, h, seam)
        }
        canvas.drawLine(0f, groundY + 120f * u, w, groundY + 120f * u, seam)
    }

    private fun drawAbstractStudioGradient(canvas: Canvas, w: Float, h: Float, u: Float, style: AnimationStyle) {
        val groundY = h * 0.675f
        val (topC, midC, botC) = when (style) {
            AnimationStyle.TECH_EXPLAINER -> Triple(0xFF0F172A.toInt(), 0xFF1E3A8A.toInt(), 0xFF0284C7.toInt())
            AnimationStyle.CHIBI_STORYTIME -> Triple(0xFF6D28D9.toInt(), 0xFFEC4899.toInt(), 0xFFFDE68A.toInt())
            AnimationStyle.VIRAL_2D_EXPLAINER -> Triple(0xFF0369A1.toInt(), 0xFF0EA5E9.toInt(), 0xFFBAE6FD.toInt())
            else -> Triple(0xFF1E1B4B.toInt(), 0xFF312E81.toInt(), 0xFF0D9488.toInt())
        }

        canvas.drawRect(
            0f, 0f, w, groundY,
            Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, groundY, intArrayOf(topC, midC, botC), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            }
        )

        // Studio upper corner keylights
        val keyGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(w * 0.5f, h * 0.36f, w * 0.62f, intArrayOf(0x55FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(w * 0.5f, h * 0.36f, w * 0.62f, keyGlow)

        // Subtle curved cyclorama studio cove horizon + soft floor gradient
        canvas.drawRect(
            0f, groundY, w, h,
            Paint().apply {
                shader = LinearGradient(0f, groundY, 0f, h, botC, topC, Shader.TileMode.CLAMP)
            }
        )
        canvas.drawOval(
            RectF(w * 0.14f, groundY - 12f * u, w * 0.86f, groundY + 40f * u),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
        )
    }

    private fun drawDramaticSunsetCity(canvas: Canvas, w: Float, h: Float, u: Float, rng: Random) {
        val groundY = h * 0.675f

        // Vivid crimson-orange-gold sunset sky
        canvas.drawRect(
            0f, 0f, w, groundY,
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, groundY,
                    intArrayOf(0xFF311042.toInt(), 0xFFB91C1C.toInt(), 0xFFF59E0B.toInt()),
                    floatArrayOf(0f, 0.52f, 1f), Shader.TileMode.CLAMP
                )
            }
        )

        // Large glowing setting sun
        drawSunOrb(canvas, w * 0.52f, groundY - 120f * u, w * 0.15f, 0xFFFDE047.toInt())

        // Layered skyline skyscrapers with warm lit windows
        val slotW = w / 6f
        for (i in 0 until 6) {
            val left = i * slotW + 6f * u
            val right = (i + 1) * slotW - 8f * u
            val bH = h * (0.16f + (i * 37 % 10) * 0.015f)
            val top = groundY - bH
            canvas.drawRoundRect(RectF(left, top, right, groundY), 6f * u, 6f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E1B4B.toInt() })
            val winPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFBBF24.toInt() }
            var wy = top + 24f * u
            while (wy < groundY - 36f * u) {
                var wx = left + 18f * u
                while (wx < right - 26f * u) {
                    if (rng.nextFloat() > 0.25f) {
                        canvas.drawRect(wx, wy, wx + 18f * u, wy + 22f * u, winPaint)
                    }
                    wx += 32f * u
                }
                wy += 42f * u
            }
        }

        // Dusk city promenade pavement
        canvas.drawRect(
            0f, groundY, w, h,
            Paint().apply {
                shader = LinearGradient(0f, groundY, 0f, h, 0xFF334155.toInt(), 0xFF0F172A.toInt(), Shader.TileMode.CLAMP)
            }
        )
        canvas.drawLine(0f, groundY, w, groundY, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88F59E0B.toInt(); strokeWidth = 5f * u })
    }

    private fun drawSunOrb(canvas: Canvas, cx: Float, cy: Float, radius: Float, coreColor: Int) {
        val bloom = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, radius * 3.4f,
                intArrayOf((coreColor and 0x00FFFFFF) or 0x77000000, 0x00000000),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, radius * 3.4f, bloom)
        canvas.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, radius, intArrayOf(Color.WHITE, coreColor), null, Shader.TileMode.CLAMP)
        })
    }

    private fun drawCloud(canvas: Canvas, cx: Float, cy: Float, width: Float, height: Float, fill: Int, shadow: Int, u: Float) {
        fun buildPath(ox: Float, oy: Float): Path {
            val l = cx - width * 0.5f + ox
            val r = cx + width * 0.5f + ox
            val base = cy + height * 0.45f + oy
            return Path().apply {
                moveTo(l + width * 0.08f, base)
                cubicTo(l - width * 0.02f, base - height * 0.75f, l + width * 0.22f, base - height * 1.15f, l + width * 0.34f, base - height * 0.85f)
                cubicTo(l + width * 0.42f, base - height * 1.55f, l + width * 0.66f, base - height * 1.50f, l + width * 0.72f, base - height * 0.82f)
                cubicTo(l + width * 0.84f, base - height * 1.05f, r + width * 0.02f, base - height * 0.60f, r - width * 0.06f, base)
                close()
            }
        }
        canvas.drawPath(buildPath(4f * u, 8f * u), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = shadow
            maskFilter = BlurMaskFilter((8f * u).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        })
        canvas.drawPath(buildPath(0f, 0f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
    }

    private fun drawSineHill(canvas: Canvas, w: Float, h: Float, baseY: Float, amp: Float, freq: Float, phase: Float, topColor: Int, botColor: Int) {
        val path = Path().apply {
            moveTo(0f, h)
            val steps = 32
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val y = baseY - sin(t * PI.toFloat() * freq + phase) * amp - cos(t * PI.toFloat() * freq * 0.5f) * amp * 0.35f
                lineTo(t * w, y)
            }
            lineTo(w, h)
            close()
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, baseY - amp, 0f, h * 0.72f, topColor, botColor, Shader.TileMode.CLAMP)
        })
    }

    private fun drawTree(canvas: Canvas, baseX: Float, groundY: Float, height: Float, u: Float, trunk: Int, leafDark: Int, leafLight: Int) {
        canvas.drawOval(RectF(baseX - height * 0.26f, groundY - 8f * u, baseX + height * 0.26f, groundY + 12f * u), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 })
        canvas.drawRoundRect(RectF(baseX - height * 0.05f, groundY - height * 0.58f, baseX + height * 0.05f, groundY), 6f * u, 6f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = trunk })
        val cy = groundY - height * 0.66f
        val d = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = leafDark }
        val l = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = leafLight }
        canvas.drawCircle(baseX - height * 0.14f, cy + height * 0.04f, height * 0.22f, d)
        canvas.drawCircle(baseX + height * 0.14f, cy + height * 0.04f, height * 0.20f, d)
        canvas.drawCircle(baseX, cy - height * 0.10f, height * 0.24f, d)
        canvas.drawCircle(baseX - height * 0.04f, cy - height * 0.14f, height * 0.17f, l)
    }

    private fun drawWoodFloor(canvas: Canvas, w: Float, h: Float, u: Float, groundY: Float, lightWood: Int, darkWood: Int) {
        canvas.drawRect(
            0f, groundY, w, h,
            Paint().apply { shader = LinearGradient(0f, groundY, 0f, h, lightWood, darkWood, Shader.TileMode.CLAMP) }
        )
        val seam = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x38000000; strokeWidth = 3.5f * u }
        for (i in -3..3) {
            canvas.drawLine(w * 0.5f + i * 115f * u, groundY, w * 0.5f + i * 240f * u, h, seam)
        }
        canvas.drawLine(0f, groundY, w, groundY, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x44FFFFFF; strokeWidth = 5f * u })
    }
}
