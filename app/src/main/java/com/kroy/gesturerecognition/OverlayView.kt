/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mediapipe.examples.gesturerecognizer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.kroy.gesturerecognition.R
import kotlin.math.max
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {

    private var results: GestureRecognizerResult? = null
    private var linePaint = Paint()
    private var pointPaint = Paint()

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    init {
        initPaints()
    }

    fun clear() {
        results = null
        linePaint.reset()
        pointPaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        linePaint.color =
            ContextCompat.getColor(context!!,R.color.hand_line_color)
        linePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        linePaint.style = Paint.Style.STROKE

        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        pointPaint.style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        results?.let { gestureRecognizerResult ->
            if (gestureRecognizerResult.landmarks().isNotEmpty()) {
                // Loop through each hand's landmarks
                for (landmark in gestureRecognizerResult.landmarks()) {
                    // Draw for each normalized landmark
                    for (i in landmark.indices) {
                        val normalizedLandmark = landmark[i]

                        if (i == 0) { // First normalized landmark
                            // Draw a custom image (assuming you have a Bitmap or Drawable to draw)
                            val bitmap = getCustomImage(context, drawable) // Function to get your desired image
                            val x = normalizedLandmark.x() * imageWidth * scaleFactor
                            val y = normalizedLandmark.y() * imageHeight * scaleFactor

                            canvas.drawBitmap(
                                bitmap,
                                x - (bitmap.width / 2),
                                y - (bitmap.height / 2),
                                null
                            )
                        } else {
                            // Draw simple points for the other landmarks
                            canvas.drawPoint(
                                normalizedLandmark.x() * imageWidth * scaleFactor,
                                normalizedLandmark.y() * imageHeight * scaleFactor,
                                pointPaint
                            )
                        }
                    }

                    // Draw connections between landmarks (you can still maintain this logic)
                    HandLandmarker.HAND_CONNECTIONS.forEach { connection ->
                        if (connection != null) {
                            canvas.drawLine(
                                landmark[connection.start()].x() * imageWidth * scaleFactor,
                                landmark[connection.start()].y() * imageHeight * scaleFactor,
                                landmark[connection.end()].x() * imageWidth * scaleFactor,
                                landmark[connection.end()].y() * imageHeight * scaleFactor,
                                linePaint
                            )
                        }
                    }
                }
            } else {
                invalidate() // Refresh the view
            }
        }
    }
    fun getCustomImage(context: Context, drawableResId: Int): Bitmap {
        val drawable: Drawable = ContextCompat.getDrawable(context, drawableResId) ?: throw IllegalArgumentException("Drawable not found")

        val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            // Create a bitmap with the dimensions of the drawable
            val width = drawable.intrinsicWidth
            val height = drawable.intrinsicHeight
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                val canvas = Canvas(this)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
            }
        }

        return bitmap
    }


//    override fun draw(canvas: Canvas) {
//        super.draw(canvas)
//        results?.let { gestureRecognizerResult ->
//
//            if(gestureRecognizerResult.landmarks().isNotEmpty()){
//                for(landmark in gestureRecognizerResult.landmarks()) {
//                    for(normalizedLandmark in landmark) {
//                        canvas.drawPoint(
//                            normalizedLandmark.x() * imageWidth * scaleFactor,
//                            normalizedLandmark.y() * imageHeight * scaleFactor,
//                            pointPaint)
//                    }
//
//                    HandLandmarker.HAND_CONNECTIONS.forEach {
//                        canvas.drawLine(
//                            gestureRecognizerResult.landmarks().get(0).get(it!!.start()).x() * imageWidth * scaleFactor,
//                            gestureRecognizerResult.landmarks().get(0).get(it.start()).y() * imageHeight * scaleFactor,
//                            gestureRecognizerResult.landmarks().get(0).get(it.end()).x() * imageWidth * scaleFactor,
//                            gestureRecognizerResult.landmarks().get(0).get(it.end()).y() * imageHeight * scaleFactor,
//                            linePaint)
//                    }
//                }
//            }else{
//                invalidate()
//            }
//
//        }
//    }

    fun setResults(
        gestureRecognizerResult: GestureRecognizerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        results = gestureRecognizerResult

        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        scaleFactor = when (runningMode) {
            RunningMode.IMAGE,
            RunningMode.VIDEO -> {
                min(width * 1f / imageWidth, height * 1f / imageHeight)
            }
            RunningMode.LIVE_STREAM -> {
                // PreviewView is in FILL_START mode. So we need to scale up the
                // landmarks to match with the size that the captured images will be
                // displayed.
                max(width * 1f / imageWidth, height * 1f / imageHeight)
            }
        }
        invalidate()
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 8F
        var drawable :Int = R.drawable.handlogo
    }
}
