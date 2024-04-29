package com.kroy.gesturerecognition

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.google.mediapipe.examples.gesturerecognizer.GestureRecognizerHelper
import com.google.mediapipe.examples.gesturerecognizer.MainViewModel
import com.google.mediapipe.examples.gesturerecognizer.OverlayView
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.kroy.gesturerecognition.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), GestureRecognizerHelper.GestureRecognizerListener {
    private lateinit var activityMainBinding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var gestureRecognizerHelper: GestureRecognizerHelper
    private  var  isGesture: Boolean  =false
//    private val gestureRecognizerResultAdapter: GestureRecognizerResultsAdapter by lazy {
//        GestureRecognizerResultsAdapter().apply {
//            updateAdapterSize(defaultNumResults)
//        }
//    }
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService

    private var defaultNumResults = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)
        viewModel.changeStatusBarColor(this@MainActivity,R.color.hand_line_color)
        // Initialize our background executor
        backgroundExecutor = Executors.newSingleThreadExecutor()
        checkCameraPermission()
        activityMainBinding.gestureSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            isGesture = isChecked
            val trackColor = if (isChecked) Color.GREEN else Color.RED
            activityMainBinding.gestureSwitch.trackTintList = ColorStateList.valueOf(trackColor)

        }






    }

    private fun checkCameraPermission() {
        // Check camera permission
        if (ContextCompat.checkSelfPermission(
                this,
                 android.Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission not granted, request it
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.CAMERA),
                100
            )
        } else {
            // Wait for the views to be properly laid out
            activityMainBinding.viewFinder.post {
                // Set up the camera and its use cases
                setUpCamera()
                // Create the Hand Gesture Recognition Helper that will handle the
                // inference
               setUpGestureRecognizer()
            }

        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Wait for the views to be properly laid out
                activityMainBinding.viewFinder.post {
                    // Set up the camera and its use cases
                    setUpCamera()
                    // Create the Hand Gesture Recognition Helper that will handle the
                    // inference
                  setUpGestureRecognizer()
                }

            } else {
               Toast.makeText(this , "Camera permission required !!",Toast.LENGTH_SHORT).show()
            }
        }
    }


    override fun onResume() {
        super.onResume()
        setUpGestureRecognizer()

    }
    private fun setUpGestureRecognizer(){
//
//         Start the GestureRecognizerHelper again when users come back
//         to the foreground.
        backgroundExecutor.execute {
            gestureRecognizerHelper = GestureRecognizerHelper(
                context = this,
                runningMode = RunningMode.LIVE_STREAM,
                minHandDetectionConfidence = viewModel.currentMinHandDetectionConfidence,
                minHandTrackingConfidence = viewModel.currentMinHandTrackingConfidence,
                minHandPresenceConfidence = viewModel.currentMinHandPresenceConfidence,
                currentDelegate = viewModel.currentDelegate,
                gestureRecognizerListener = this
            )
        }
    }

    override fun onPause() {
        super.onPause()
        if (this::gestureRecognizerHelper.isInitialized) {
            viewModel.setMinHandDetectionConfidence(gestureRecognizerHelper.minHandDetectionConfidence)
            viewModel.setMinHandTrackingConfidence(gestureRecognizerHelper.minHandTrackingConfidence)
            viewModel.setMinHandPresenceConfidence(gestureRecognizerHelper.minHandPresenceConfidence)
            viewModel.setDelegate(gestureRecognizerHelper.currentDelegate)

            // Close the Gesture Recognizer helper and release resources
            backgroundExecutor.execute { gestureRecognizerHelper.clearGestureRecognizer() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Shut down our background executor
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
    }

    private fun setUpCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this@MainActivity)
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(this@MainActivity)
        )
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(activityMainBinding.viewFinder.display.rotation)
            .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(activityMainBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(backgroundExecutor) { image ->
                        recognizeHand(image)
                    }
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(activityMainBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e("MainActivity", "Use case binding failed", exc)
        }
    }


    private fun recognizeHand(imageProxy: ImageProxy) {
        gestureRecognizerHelper.recognizeLiveStream(
            imageProxy = imageProxy,
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation =
            activityMainBinding.viewFinder.display.rotation
    }




    override fun onError(error: String, errorCode: Int) {
        runOnUiThread {
            Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResults(resultBundle: GestureRecognizerHelper.ResultBundle) {
        runOnUiThread {
            val gestureCategories = resultBundle.results.first().gestures()
            val landmark =   resultBundle.results.first().worldLandmarks()
            if (gestureCategories!=null && landmark.isNotEmpty()) {


                val category = gestureCategories.getOrNull(0)?.getOrNull(0)?.categoryName()

                val wristPoint = landmark.first()[0]
                if(!isGesture){
                    activityMainBinding.gestureText.text = wristPoint.toString()
                    activityMainBinding.gestureImage.visibility = View.GONE
                }else{

                    when(category){
                        "Pointing_Up"->{
                            //activityMainBinding.gestureImage.visibility = View.VISIBLE
                            activityMainBinding.gestureText.text = category
                            activityMainBinding.gestureImage.setImageResource(R.drawable.img_1)
                            OverlayView.drawable = R.drawable.img_1
                        }
                        "Victory"->{
                         //   activityMainBinding.gestureImage.visibility = View.VISIBLE
                            activityMainBinding.gestureText.text = category
                            activityMainBinding.gestureImage.setImageResource(R.drawable.img_2)
                            OverlayView.drawable = R.drawable.img_2
                        }
                        "Thumb_Up"->{
                           // activityMainBinding.gestureImage.visibility = View.VISIBLE
                            activityMainBinding.gestureText.text = category
                            activityMainBinding.gestureImage.setImageResource(R.drawable.img_3)
                            OverlayView.drawable = R.drawable.img_3
                        }
                        "Thumb_Down"-> {
                          //  activityMainBinding.gestureImage.visibility = View.VISIBLE
                            activityMainBinding.gestureText.text = category
                            activityMainBinding.gestureImage.setImageResource(R.drawable.img_4)
                            OverlayView.drawable = R.drawable.img_4
                        }
                    }

                }


                Log.d("Gesture checks -> ","$category")

                Log.d("Gesture landmark-> ","$wristPoint")


            }

            // Pass necessary information to OverlayView for drawing on the canvas
            activityMainBinding.overlay.setResults(
                resultBundle.results.first(),
                resultBundle.inputImageHeight,
                resultBundle.inputImageWidth,
                RunningMode.LIVE_STREAM
            )

            // Force a redraw
            activityMainBinding.overlay.invalidate()
        }
    }


}


