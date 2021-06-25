package com.android.aidemo

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
typealias ImageListener = (image: ImageProxy) -> Unit

class ImageAnalyzer(private val listener: ImageListener) : ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy) {
        listener(image)
        image.close()
    }
}