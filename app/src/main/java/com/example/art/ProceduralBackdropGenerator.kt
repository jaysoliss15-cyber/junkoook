package com.example.art

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.example.model.AnimationStyle
import com.example.model.EnvironmentTheme
import kotlin.random.Random

object ProceduralBackdropGenerator {

    fun generate(
        prompt: String,
        style: AnimationStyle,
        width: Int = 1080,
        height: Int = 1920,
        seed: Int = prompt.hashCode(),
        environmentTheme: EnvironmentTheme? = null
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (style == AnimationStyle.STICKMAN_WHITEBOARD) {
            drawWhiteboardPaperCanvas(canvas, width, height, seed)
            return bitmap
        }

        val resolvedTheme = EnvironmentRenderer.resolveEnvironmentTheme(
            prompt = prompt,
            explicitTheme = environmentTheme,
            style = style
        )
        EnvironmentRenderer.render(
            canvas = canvas,
            width = width,
            height = height,
            theme = resolvedTheme,
            style = style,
            seed = seed
        )
        return bitmap
    }

    private fun drawWhiteboardPaperCanvas(
        canvas: Canvas,
        width: Int,
        height: Int,
        seed: Int
    ) {
        val w = width.toFloat()
        val h = height.toFloat()
        val rng = Random(seed)

        // Warm off-white textured paper base
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(0xFFFAF6EE.toInt(), 0xFFF3ECE0.toInt(), 0xFFEDE4D4.toInt()),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Subtle graph-paper rules
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x148B95A5
            strokeWidth = (w * 0.002f).coerceAtLeast(1f)
        }
        val step = w * 0.08f
        var x = step
        while (x < w) {
            canvas.drawLine(x, 0f, x, h, gridPaint)
            x += step
        }
        var y = step
        while (y < h) {
            canvas.drawLine(0f, y, w, y, gridPaint)
            y += step
        }

        // Paper grain speckles
        val speckPaint = Paint().apply { isAntiAlias = false }
        for (i in 0 until 650) {
            speckPaint.color = if (i % 2 == 0) 0x12000000 else 0x18FFFFFF
            val sx = rng.nextFloat() * w
            val sy = rng.nextFloat() * h
            val r = (1f + rng.nextFloat() * 2.2f) * (w / 1080f)
            canvas.drawCircle(sx, sy, r, speckPaint)
        }

        val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2B2E3A.toInt()
            strokeWidth = (w * 0.0055f).coerceAtLeast(2f)
            setStyle(Paint.Style.STROKE)
            strokeCap = Paint.Cap.ROUND
        }
        val floorY = h * 0.72f
        canvas.drawLine(w * 0.08f, floorY, w * 0.92f, floorY, floorPaint)

        val vigPaint = Paint().apply {
            shader = RadialGradient(
                w * 0.5f, h * 0.48f, h * 0.65f,
                intArrayOf(0x00000000, 0x229B8E78),
                floatArrayOf(0.65f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, vigPaint)
    }
}
