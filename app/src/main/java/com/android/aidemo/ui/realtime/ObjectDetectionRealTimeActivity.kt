package com.android.aidemo.ui.realtime

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.media.Image
import android.os.Bundle
import android.util.Log
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.aidemo.ImageAnalyzer
import com.android.aidemo.databinding.ActivityObjectDetectionRealTimeBinding
import com.android.aidemo.login.ObjectDetectionProcessor
import com.android.aidemo.login.ObjectDetectionResult
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.io.*
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class ObjectDetectionRealTimeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityObjectDetectionRealTimeBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var mModule: Module

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityObjectDetectionRealTimeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.insetsController?.setSystemBarsAppearance(
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        )

        //请求相机权限
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
        cameraExecutor = Executors.newSingleThreadExecutor()

        try {
            mModule = LiteModuleLoader.load(assetFilePath(applicationContext, "objectDetection.ptl"))
        } catch (e: IOException) {
            Log.e("Object Detection", "Error reading assets", e)
        }
    }


    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            Runnable {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                // 设置预览相关的状态
                val preview = Preview.Builder()
                    .build()
                    .also { it.setSurfaceProvider(binding.viewFinder.surfaceProvider) }


                // 设置分析相关得状态
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, ImageAnalyzer { image ->
                            val rotationDegrees = image.imageInfo.rotationDegrees
                            val results = analyzeImage(image, rotationDegrees)
                            if (results != null) {
                                runOnUiThread {
                                    binding.resultView.setResults(results)
                                    binding.resultView.invalidate()
                                }
                            }
                        })
                    }

                // 选择后置摄像头
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // 绑定
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
                } catch(exc: Exception) {
                    Log.e(TAG, "Use case binding failed", exc)
                }
            },
            ContextCompat.getMainExecutor(this))
    }

    /**
     * 检查所需的权限是否都被批准了。
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    fun assetFilePath(context: Context?, asset: String): String {
        val file = File(context?.filesDir, asset)

        try {
            val inpStream: InputStream? = context?.assets?.open(asset)
            try {
                val outStream = FileOutputStream(file, false)
                val buffer = ByteArray(4 * 1024)
                var read: Int

                while (true) {
                    read = inpStream?.read(buffer)!!
                    if (read == -1) { break }
                    outStream.write(buffer, 0, read)
                }
                outStream.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun analyzeImage(image: ImageProxy, rotationDegrees: Int): ArrayList<ObjectDetectionResult> {

        var bitmap: Bitmap = imgToBitmap(image.image!!)
        val matrix = Matrix()
        matrix.postRotate(90.0f)
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, ObjectDetectionProcessor.mInputWidth, ObjectDetectionProcessor.mInputHeight, true)
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(resizedBitmap, ObjectDetectionProcessor.NO_MEAN_RGB, ObjectDetectionProcessor.NO_STD_RGB)

        val outputTuple = mModule.forward(IValue.from(inputTensor)).toTuple()
        val outputTensor = outputTuple[0].toTensor()
        val outputs = outputTensor.dataAsFloatArray

        val imgScaleX: Float = bitmap.width.toFloat() / ObjectDetectionProcessor.mInputWidth
        val imgScaleY: Float = bitmap.height.toFloat() / ObjectDetectionProcessor.mInputHeight
        val ivScaleX = binding.resultView.width.toFloat() / bitmap.width
        val ivScaleY = binding.resultView.height.toFloat() / bitmap.height

        val results = ObjectDetectionProcessor.outputsToNMSPredictions(outputs, imgScaleX, imgScaleY, ivScaleX, ivScaleY, 0F, 0F)
        return results
    }

    private fun imgToBitmap(image: Image): Bitmap {
        val planes: Array<Image.Plane> = image.planes
        val yBuffer: ByteBuffer = planes[0].buffer
        val uBuffer: ByteBuffer = planes[1].buffer
        val vBuffer: ByteBuffer = planes[2].buffer
        val ySize: Int = yBuffer.remaining()
        val uSize: Int = uBuffer.remaining()
        val vSize: Int = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 75, out)
        val imageBytes: ByteArray = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }


}