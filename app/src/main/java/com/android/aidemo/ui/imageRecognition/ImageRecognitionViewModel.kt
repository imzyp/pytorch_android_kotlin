package com.android.aidemo.ui.imageRecognition

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ImageRecognitionViewModel : ViewModel() {
    val buttonEnable: LiveData<Boolean>
        get() = _buttonEnable
    private val _buttonEnable = MutableLiveData<Boolean>()

    val bitmap: LiveData<Bitmap>
        get() = _bitmap
    private val _bitmap = MutableLiveData<Bitmap>()


    fun allButtonEnable(sign: Boolean) {
        _buttonEnable.value = sign
    }

    fun bitmapShow(bitmap: Bitmap) {
        _bitmap.value = bitmap
    }
}