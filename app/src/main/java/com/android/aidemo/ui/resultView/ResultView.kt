package com.android.aidemo.ui.resultView

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import com.android.aidemo.R
import com.android.aidemo.login.ObjectDetectionProcessor
import com.android.aidemo.login.ObjectDetectionResult


/**
 * TODO: document your custom view class.
 */
class ResultView : View {
    private var mResults: ArrayList<ObjectDetectionResult>? = null

    fun setResults(results: ArrayList<ObjectDetectionResult>) {
        mResults = results
    }

    private val TEXT_X = 40
    private val TEXT_Y = 35
    private val TEXT_WIDTH = 200
    private val TEXT_HEIGHT = 50


    constructor(context: Context) : super(context) {
        init(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        init(attrs, defStyle)
    }

    private fun init(attrs: AttributeSet?, defStyle: Int) {}


    @SuppressLint("ResourceAsColor", "DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (mResults == null) return

        val mPaintRectangle = Paint()
        mPaintRectangle.color = context.getColor(R.color.teal_200)
        mPaintRectangle.strokeWidth = 5F

        val mPaintText = TextPaint()
        mPaintText.color = Color.WHITE
        mPaintText.style = Paint.Style.FILL
        mPaintText.strokeWidth = 0F
        mPaintText.textSize = 32F
        mPaintText.textAlign = Paint.Align.LEFT
        for ((classIndex, score, rect) in mResults!!) {
            // 绘制检测框
            mPaintRectangle.style = Paint.Style.STROKE
            canvas.drawRect(rect, mPaintRectangle)

            // 绘制文本框
            mPaintRectangle.style = Paint.Style.FILL
            canvas.drawRect(
                rect.left.toFloat() - 5F,
                rect.top.toFloat() - TEXT_HEIGHT,
                rect.left.toFloat() + TEXT_WIDTH,
                rect.top.toFloat() + 0F,
                mPaintRectangle)
        }

        for ((classIndex, score, rect) in mResults!!) {
            canvas.drawText(
                ObjectDetectionProcessor.mClasses[classIndex],
                rect.left.toFloat() + 5F,
                rect.top.toFloat() - 10F,
                mPaintText)
        }




    }


}