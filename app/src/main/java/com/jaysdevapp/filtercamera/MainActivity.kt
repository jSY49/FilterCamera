package com.jaysdevapp.filtercamera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.AnimationUtils
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.jaysdevapp.filtercamera.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var mCameraFacing = 0
    private lateinit var cameraAnimationListener : AnimationListener
    private lateinit var cameraController : CameraControl
    private var MyFilterAdpater = FilterAdpater(arrayListOf())
    val datas = mutableListOf<FilterList>()
    var filterFlag = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setCameraAnimationListener()

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera(mCameraFacing)
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Set up the listeners for take photo and video capture buttons
        binding.cameraButton.setOnClickListener { takePhoto() }
        binding.turnButton.setOnClickListener{ turnCamera() }
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.filterRecycler.apply {
            layoutManager = LinearLayoutManager(context).also {
                it.orientation = LinearLayoutManager.HORIZONTAL
            }
            adapter = MyFilterAdpater
        }

        datas.apply {
            add(FilterList(img = "file:///assets/Filters/jejubear.png", title = "none"))
            add(FilterList(img = "file:///assets/Filters/hanla.png", title = "hanla"))
        }
        MyFilterAdpater.update(datas)

    }


    private fun turnCamera(){
        if(mCameraFacing==0){
            mCameraFacing=1
            startCamera(mCameraFacing)
        }else{
            mCameraFacing=0
            startCamera(mCameraFacing)
        }
    }
    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.KOREA)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FilCam")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo capture succeeded: ${output.savedUri}"

                    val animation = AnimationUtils.loadAnimation(this@MainActivity, R.anim.shutter_animation)
                    animation.setAnimationListener(cameraAnimationListener)
                    binding.frameLayout.animation = animation
                    binding.frameLayout.visibility=View.VISIBLE
                    binding.frameLayout.startAnimation(animation)

//                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }


    private fun startCamera(mCameraFacing: Int) {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }


            imageCapture = ImageCapture.Builder().build()

            // Select back camera as a default
            var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            when(mCameraFacing){
                0 -> cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                1-> cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            }

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
                // Bind use cases to camera
                var camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)

                //seekBar 리스너
                binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
//                        cameraController.setZoomRatio(progress.toFloat())
                        camera.cameraControl.setLinearZoom(progress / 100.toFloat())
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) { }
                    override fun onStopTrackingTouch(seekBar: SeekBar?) { }
                })

                cameraController = camera.cameraControl


            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera(mCameraFacing)
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }


    private fun setCameraAnimationListener() {
        cameraAnimationListener = object : AnimationListener {
            override fun onAnimationStart(animation: Animation?) {
//                binding.frameLayout.visibility=View.VISIBLE
            }

            override fun onAnimationEnd(animation: Animation?) {
                binding.frameLayout.visibility=View.GONE
            }

            override fun onAnimationRepeat(animation: Animation?) {

            }

        }
    }

    fun filterOnoff(view: View) {
        if(filterFlag){
            binding.filterRecycler.visibility=View.VISIBLE
        }else{
            binding.filterRecycler.visibility=View.GONE
        }
        filterFlag=!filterFlag
    }

}