package com.android.aidemo.login

import android.graphics.Rect

data class ObjectDetectionResult(var classIndex: Int, var score: Float, val rect: Rect) {
}
