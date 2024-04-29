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
import android.util.Log
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
                try{
                    for (landmark in gestureRecognizerResult.landmarks()) {
                        for (i in landmark.indices) {
                            val normalizedLandmark = landmark[i]

                            if (i == 0) { // First normalized landmark
                                val originalBitmap = getCustomImage(context, drawable)

                                // Define the zoom scale factor based on the z-coordinate
                                val zoomFactor =  (normalizedLandmark.z() *  100000000) / 22 // Modify to achieve the desired effect
                                Log.d("zoom factor->","${normalizedLandmark.z() * 100000000}")

                                val scaledBitmap = Bitmap.createScaledBitmap(
                                    originalBitmap,
                                    (originalBitmap.width * zoomFactor).toInt(),
                                    (originalBitmap.height * zoomFactor).toInt(),
                                    true
                                )

                                val x = normalizedLandmark.x() * imageWidth * scaleFactor
                                val y = normalizedLandmark.y() * imageHeight * scaleFactor

                                // Draw the scaled bitmap, centering it on the normalized landmark
                                if(isGestureMode){
                                    canvas.drawBitmap(
                                        originalBitmap,
                                        x - (originalBitmap.width / 2),
                                        y - (originalBitmap.height / 2),
                                        null
                                    )
                                }else{
                                    canvas.drawBitmap(
                                        scaledBitmap,
                                        x - (scaledBitmap.width / 2),
                                        y - (scaledBitmap.height / 2),
                                        null
                                    )
                                }

                            } else {
                                // Draw simple points for other landmarks
                                canvas.drawPoint(
                                    normalizedLandmark.x() * imageWidth * scaleFactor,
                                    normalizedLandmark.y() * imageHeight * scaleFactor,
                                    pointPaint
                                )
                            }
                        }

                        // Draw connections between landmarks (optional, depending on visualization needs)
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
                }catch (e:Exception){
                    e.printStackTrace()
                }


            } else {
                invalidate() // Refresh the view if no landmarks are detected
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
        var drawable :Int = R.drawable.wrist_point_img
        var isGestureMode : Boolean = false
    }
}
