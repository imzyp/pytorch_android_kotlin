package com.android.aidemo.ui.objectDetection

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.android.aidemo.R
import com.android.aidemo.ui.realtime.ObjectDetectionRealTimeActivity
import com.android.aidemo.databinding.FragmentObjectDetectionBinding
import com.android.aidemo.login.ObjectDetectionProcessor
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialView
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.io.*
import kotlin.concurrent.thread


@Suppress("DEPRECATION")
class ObjectDetectionFragment : Fragment() {

    private lateinit var viewModel: ObjectDetectionViewModel
    private var _binding: FragmentObjectDetectionBinding? = null
    private val binding get() = _binding!!

    private lateinit var imageUri: Uri
    private lateinit var outputImage: File

    private var mImageIndex = 0
    private val mTestImages = arrayOf("test1.png", "test2.jpg", "test3.png")
    private lateinit var mModule: Module
    private lateinit var mBitmap: Bitmap
    private var mImgScaleX = 0f
    private var mImgScaleY = 0f
    private var mIvScaleX = 0f
    private var mIvScaleY = 0f
    private var mStartX = 0f
    private var mStartY = 0f


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentObjectDetectionBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(ObjectDetectionViewModel::class.java)

        // 加载模型
        thread {
            try {
                mModule = LiteModuleLoader.load(assetFilePath(requireActivity().applicationContext, "objectDetection.ptl"))
                val br = BufferedReader(InputStreamReader(requireActivity().assets.open("coco_classes.txt")))
                br.forEachLine {
                        line -> ObjectDetectionProcessor.mClasses.add(line)
                }
                requireActivity().runOnUiThread {
                    binding.load.visibility = View.INVISIBLE
                    binding.main.visibility = View.VISIBLE
                }
            } catch (e: IOException) {
                Log.e("Object Detection", "Error reading assets", e)
            }
        }

        // 绑定 fab 功能
        fabAction()

        // 显示内置图像
        mBitmap = BitmapFactory.decodeStream(requireActivity().assets.open(mTestImages[mImageIndex]))
        binding.imageView.setImageBitmap(mBitmap)

        // 更换内置图像
        binding.testButton.setOnClickListener {
            binding.resultView.visibility = View.INVISIBLE
            mImageIndex = (mImageIndex + 1) % mTestImages.size
            mBitmap = BitmapFactory.decodeStream(requireActivity().assets.open(mTestImages[mImageIndex]))
            viewModel.bitmapShow(mBitmap)
        }

        // view 监听
        viewModel.buttonEnable.observe(requireActivity()) { sign ->
            binding.detectButton.isEnabled = sign
            binding.testButton.isEnabled = sign
            binding.speedDial.isEnabled = sign
            if (sign) {
                binding.progressBar.visibility = View.INVISIBLE
            } else {
                binding.progressBar.visibility = View.VISIBLE
            }
        }

        viewModel.bitmap.observe(requireActivity()) { bitmap ->
            binding.imageView.setImageBitmap(bitmap)
        }


        // 目标检测
        binding.detectButton.setOnClickListener {
            viewModel.allButtonEnable(false)
            mImgScaleX = mBitmap.width.toFloat() / ObjectDetectionProcessor.mInputWidth
            mImgScaleY = mBitmap.height.toFloat() / ObjectDetectionProcessor.mInputHeight

            mIvScaleX =
                if (mBitmap.width > mBitmap.height) binding.imageView.width.toFloat() / mBitmap.width else binding.imageView.height.toFloat() / mBitmap.height
            mIvScaleY =
                if (mBitmap.height > mBitmap.width) binding.imageView.height.toFloat() / mBitmap.height else binding.imageView.width.toFloat() / mBitmap.width

            mStartX = (binding.imageView.width - mIvScaleX * mBitmap.width) / 2
            mStartY = (binding.imageView.height - mIvScaleY * mBitmap.height) / 2

            thread {
                val resizedBitmap = Bitmap.createScaledBitmap(mBitmap, ObjectDetectionProcessor.mInputWidth, ObjectDetectionProcessor.mInputHeight, true)
                val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(resizedBitmap, ObjectDetectionProcessor.NO_MEAN_RGB, ObjectDetectionProcessor.NO_STD_RGB)
                val outputTuple = mModule.forward(IValue.from(inputTensor)).toTuple()
                val outputTensor = outputTuple[0].toTensor()
                val outputs = outputTensor.dataAsFloatArray
                val results = ObjectDetectionProcessor.outputsToNMSPredictions(outputs, mImgScaleX, mImgScaleY, mIvScaleX, mIvScaleY, mStartX, mStartY)

                activity?.runOnUiThread{
                    binding.resultView.setResults(results)
                    binding.resultView.invalidate()
                    binding.resultView.visibility = View.VISIBLE
                    viewModel.allButtonEnable(true)
                }

            }
        }

    }


    private fun fabAction() {
        binding.speedDial.addActionItem(
            SpeedDialActionItem.Builder(R.id.takePhoto, R.drawable.round_photo_camera_white_24)
                .setFabBackgroundColor(ResourcesCompat.getColor(resources, R.color.teal_200, null))
                .setLabel("启动相机")
                .setLabelColor(Color.WHITE)
                .setLabelBackgroundColor(ResourcesCompat.getColor(resources, R.color.teal_200, null))
                .create())
        binding.speedDial.addActionItem(
            SpeedDialActionItem.Builder(R.id.fromAlbum, R.drawable.round_image_white_24)
                .setFabBackgroundColor(ResourcesCompat.getColor(resources, R.color.teal_200, null))
                .setLabel("打开相册")
                .setLabelColor(Color.WHITE)
                .setLabelBackgroundColor(ResourcesCompat.getColor(resources, R.color.teal_200, null))
                .create())
        binding.speedDial.addActionItem(
            SpeedDialActionItem.Builder(R.id.realTime, R.drawable.round_smart_display_white_24)
                .setFabBackgroundColor(ResourcesCompat.getColor(resources, R.color.teal_200, null))
                .setLabel("实时预览")
                .setLabelColor(Color.WHITE)
                .setLabelBackgroundColor(ResourcesCompat.getColor(resources, R.color.teal_200, null))
                .create())

        binding.speedDial.setOnActionSelectedListener(SpeedDialView.OnActionSelectedListener { actionItem ->
            when (actionItem.id) {
                R.id.takePhoto -> {
                    // 创建File对象，用于存储拍照后的图片
                    outputImage = File(requireActivity().externalCacheDir, "output_image.jpg")
                    if (outputImage.exists()) { outputImage.delete() }
                    outputImage.createNewFile()
                    imageUri = FileProvider.getUriForFile(requireActivity(), "com.example.android.fileprovider", outputImage)
                    // 启动相机程序
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
                    startActivityForResult(intent, 0)
                    binding.speedDial.close()
                    return@OnActionSelectedListener true
                }
                R.id.fromAlbum -> {
                    val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
                    startActivityForResult(intent, 1)
                    binding.speedDial.close()
                    return@OnActionSelectedListener true
                }
                R.id.realTime -> {
                    val intent = Intent(requireActivity(), ObjectDetectionRealTimeActivity::class.java)
                    startActivity(intent)
                    binding.speedDial.close()
                    return@OnActionSelectedListener true
                }
            }
            false
        })
    }

    private fun assetFilePath(context: Context?, asset: String): String {
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        binding.resultView.visibility = View.INVISIBLE
        when (requestCode) {
            0 -> {
                if (resultCode == RESULT_OK) {
                    // 将拍摄的照片显示出来
                    mBitmap =  BitmapFactory.decodeStream(requireActivity().contentResolver.openInputStream(imageUri))
                    mBitmap = rotateIfRequired(mBitmap)
                    viewModel.bitmapShow(mBitmap)
                }
            }

            1 -> {
                if (resultCode == RESULT_OK && data != null) {
                    data.data?.let { uri ->
                        // 将选择的图片显示
                        mBitmap = BitmapFactory.decodeFileDescriptor(
                            requireActivity().contentResolver.openFileDescriptor(uri, "r")?.fileDescriptor)
                        viewModel.bitmapShow(mBitmap)
                    }

                }
            }
        }
    }

    private fun rotateIfRequired(bitmap: Bitmap): Bitmap {
        val exif = ExifInterface(outputImage.path)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL)
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270)
            else -> bitmap
        }
    }
    private fun rotateBitmap(bitmap: Bitmap, degree: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree.toFloat())
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        return rotatedBitmap
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}