package com.android.aidemo.login

import java.util.ArrayList

object ImageRecognitionProcessor {
    var mClasses: MutableList<String> = ArrayList()

    // model input image size
    var mInputWidth = 256
    var mInputHeight = 256

    fun outputsToPredictions(outputs: FloatArray): Int {
        var maxScore = -Float.MAX_VALUE
        var maxScoreIdx = -1
        for (i in outputs.indices) {
            if (outputs[i] > maxScore) {
                maxScore = outputs[i]
                maxScoreIdx = i
            }
        }
        return maxScoreIdx
    }
}