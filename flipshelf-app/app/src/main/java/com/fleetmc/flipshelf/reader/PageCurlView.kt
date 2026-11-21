package com.fleetmc.flipshelf.reader

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class PageCurlView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    interface Host {
        fun onPageChanged(index: Int)
        fun onCenterTap()
        fun onPageSizeChanged(width: Int, height: Int)
    }

    private enum class Mode { IDLE, DRAG, ANIMATE }

    private enum class Kind { SINGLE_FORWARD, SINGLE_BACK, SPREAD_RIGHT, SPREAD_LEFT }

    var host: Host? = null
    var cache: PageCache? = null
    var sound: FlipSound? = null
    var soundEnabled = true

    var night = false
        set(value) {
            field = value
            applyNight()
            invalidate()
        }

    var aspect = 0.7f
        set(value) {
            field = value
            computePageRect()
            invalidate()
        }

    var fillScreen = false
        set(value) {
            field = value
            computePageRect()
            invalidate()
        }

    var pageCount = 0
        private set
    var index = 0
        private set
    var pageWidth = 0
        private set
    var pageHeight = 0
        private set
    var spreadMode = false
        private set

    private val density = resources.displayMetrics.density
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val geo = CurlGeometry()
    private var contentLeft = 0f
    private var pageTop = 0f
    private var contentWidth = 0f

    private var mode = Mode.IDLE
    private var kind = Kind.SINGLE_FORWARD
    private var pFront = -1
    private var pBack = -1
    private var pUnder = -1
    private var pStatic = -1
    private var doneIndex = 0
    private var completeIsGone = true
    private var animator: ValueAnimator? = null
    private var tipX = 0f
    private var tipY = 0f

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var dragStarted = false
    private var ignoreGesture = false
    private var tracker: VelocityTracker? = null

    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var lastPanX = 0f
    private var lastPanY = 0f
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var zoomAnimator: ValueAnimator? = null
    private var scaleActive = false
    private val centerTapRunnable = Runnable { host?.onCenterTap() }

    private val pagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()
    private val flapPath = Path()
    private val matrix = Matrix()
    private val reflect = Matrix()
    private val reflectValues = FloatArray(9)
    private val rectF = RectF()

    private val paperLight = Color.rgb(246, 244, 238)
    private val paperDark = Color.rgb(28, 28, 30)
    private val nightFilter = ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                -0.86f, 0f, 0f, 0f, 243f,
                0f, -0.86f, 0f, 0f, 243f,
                0f, 0f, -0.86f, 0f, 243f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            if (mode != Mode.IDLE) return false
            zoomAnimator?.cancel()
            scaleActive = true
            dragging = false
            ignoreGesture = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newZoom = (zoom * detector.scaleFactor).coerceIn(0.9f, MAX_ZOOM)
            val s = newZoom / zoom
            val cx = width / 2f
            val cy = height / 2f
            panX = (detector.focusX - cx) * (1f - s) + s * panX
            panY = (detector.focusY - cy) * (1f - s) + s * panY
            zoom = newZoom
            clampPan()
            requestHighRes()
            invalidate()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            scaleActive = false
            if (zoom < 1.05f) animateZoomTo(1f, 0f, 0f)
        }
    })

    init {
        setBackgroundColor(Color.rgb(43, 43, 43))
        applyNight()
    }

    private fun applyNight() {
        pagePaint.colorFilter = if (night) nightFilter else null
        fillPaint.color = if (night) paperDark else paperLight
    }

    private fun spreadOf(i: Int) = (i + 1) / 2

    private fun maxSpread() = pageCount / 2

    private fun normalize(i: Int): Int {
        val clamped = i.coerceIn(0, max(0, pageCount - 1))
        return if (spreadMode) max(0, 2 * spreadOf(clamped) - 1) else clamped
    }

    private fun exists(i: Int) = i in 0 until pageCount

    private fun bitmapOf(i: Int): Bitmap? = if (exists(i)) cache?.get(i) else null

    fun leftPage(): Int = if (spreadMode) 2 * spreadOf(index) - 1 else index

    fun rightPage(): Int = if (spreadMode) 2 * spreadOf(index) else index

    fun setPageCount(count: Int, startIndex: Int) {
        cancelAnimation()
        pageCount = count
        mode = Mode.IDLE
        index = normalize(startIndex)
        resetZoom()
        prefetch()
        invalidate()
    }

    fun goTo(target: Int) {
        if (pageCount == 0) return
        cancelAnimation()
        val t = normalize(target)
        mode = Mode.IDLE
        resetZoom()
        if (t != index) {
            index = t
            host?.onPageChanged(index)
        }
        prefetch()
        invalidate()
    }

    private fun canForward() = if (spreadMode) spreadOf(index) < maxSpread() else index < pageCount - 1

    private fun canBackward() = index > 0

    fun flipNext() {
        if (mode == Mode.ANIMATE) finishAnimation()
        if (mode != Mode.IDLE || zoom > 1.02f || !canForward()) return
        beginFlip(if (spreadMode) Kind.SPREAD_RIGHT else Kind.SINGLE_FORWARD, true)
        animateTap()
    }

    fun flipPrevious() {
        if (mode == Mode.ANIMATE) finishAnimation()
        if (mode != Mode.IDLE || zoom > 1.02f || !canBackward()) return
        beginFlip(if (spreadMode) Kind.SPREAD_LEFT else Kind.SINGLE_BACK, true)
        animateTap()
    }

    private fun resetZoom() {
        zoomAnimator?.cancel()
        zoom = 1f
        panX = 0f
        panY = 0f
        cache?.clearHi()
    }

    private fun prefetch() {
        val c = cache ?: return
        c.request(index)
        if (spreadMode) c.request(index + 1)
        c.request(index + (if (spreadMode) 2 else 1))
        c.request(index - 1)
        c.request(index + (if (spreadMode) 3 else 2))
        c.request(index - 2)
    }

    fun isWanted(i: Int) = abs(i - index) <= 5 ||
        (mode != Mode.IDLE && (i == pFront || i == pBack || i == pUnder || i == pStatic))

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computePageRect()
    }

    private fun computePageRect() {
        if (width == 0 || height == 0) return
        val newSpread = width >= height * 1.15f
        val pw: Float
        val ph: Float
        if (newSpread) {
            if (fillScreen) {
                pw = width / 2f
                ph = height.toFloat()
            } else {
                pw = min(width / 2f, height * aspect)
                ph = pw / aspect
            }
        } else if (fillScreen) {
            pw = width.toFloat()
            ph = height.toFloat()
        } else {
            pw = min(width.toFloat(), height * aspect)
            ph = pw / aspect
        }
        contentWidth = if (newSpread) pw * 2f else pw
        contentLeft = (width - contentWidth) / 2f
        pageTop = (height - ph) / 2f
        geo.setSize(pw, ph)
        if (newSpread != spreadMode) {
            spreadMode = newSpread
            cancelAnimation()
            mode = Mode.IDLE
            if (pageCount > 0) {
                val n = normalize(index)
                if (n != index) {
                    index = n
                    host?.onPageChanged(index)
                }
            }
        }
        val newW = pw.toInt()
        val newH = ph.toInt()
        if (newW != pageWidth || newH != pageHeight) {
            pageWidth = newW
            pageHeight = newH
            cancelAnimation()
            mode = Mode.IDLE
            resetZoom()
            host?.onPageSizeChanged(newW, newH)
        }
    }

    private fun clampPan() {
        val limX = max(0f, (contentWidth * zoom - width) / 2f)
        val limY = max(0f, (pageHeight * zoom - height) / 2f)
        panX = panX.coerceIn(-limX, limX)
        panY = panY.coerceIn(-limY, limY)
    }

    private fun requestHighRes() {
        if (spreadMode || zoom <= 1.2f) return
        val longest = max(pageWidth, pageHeight).toFloat()
        val scale = min(2.6f, 3200f / longest).coerceAtLeast(1f)
        cache?.requestHi(index, scale)
    }

    private fun animateZoomTo(z: Float, px: Float, py: Float) {
        zoomAnimator?.cancel()
        val z0 = zoom
        val x0 = panX
        val y0 = panY
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 240
        a.interpolator = PathInterpolator(0.3f, 0f, 0.2f, 1f)
        a.addUpdateListener {
            val t = it.animatedValue as Float
            zoom = z0 + (z - z0) * t
            panX = x0 + (px - x0) * t
            panY = y0 + (py - y0) * t
            clampPan()
            invalidate()
        }
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (zoom <= 1.02f) resetZoom() else requestHighRes()
            }
        })
        zoomAnimator = a
        a.start()
    }

    private fun toggleZoom(fx: Float, fy: Float) {
        if (zoom > 1.1f) {
            animateZoomTo(1f, 0f, 0f)
        } else {
            val target = if (spreadMode) 1.8f else 2.4f
            val cx = width / 2f
            val cy = height / 2f
            val px = (fx - cx) * (1f - target)
            val py = (fy - cy) * (1f - target)
            val limX = max(0f, (contentWidth * target - width) / 2f)
            val limY = max(0f, (pageHeight * target - height) / 2f)
            animateZoomTo(target, px.coerceIn(-limX, limX), py.coerceIn(-limY, limY))
            requestHighRes()
            postDelayed({ requestHighRes() }, 60)
        }
    }

    private fun spineX(): Float = if (spreadMode) contentLeft + geo.width else contentLeft

    private fun beginFlip(k: Kind, fromTap: Boolean) {
        kind = k
        val s = spreadOf(index)
        when (k) {
            Kind.SINGLE_FORWARD -> {
                pFront = index
                pUnder = index + 1
                pBack = -1
                pStatic = -1
                doneIndex = index + 1
                completeIsGone = true
            }
            Kind.SINGLE_BACK -> {
                pFront = index - 1
                pUnder = index
                pBack = -1
                pStatic = -1
                doneIndex = index - 1
                completeIsGone = false
            }
            Kind.SPREAD_RIGHT -> {
                pFront = 2 * s
                pBack = 2 * s + 1
                pUnder = 2 * s + 2
                pStatic = 2 * s - 1
                doneIndex = 2 * (s + 1) - 1
                completeIsGone = true
            }
            Kind.SPREAD_LEFT -> {
                pFront = 2 * s - 1
                pBack = 2 * s - 2
                pUnder = 2 * s - 3
                pStatic = 2 * s
                doneIndex = max(0, 2 * (s - 1) - 1)
                completeIsGone = true
            }
        }
        geo.setCorner(if (fromTap) true else downY - pageTop > geo.height * 0.5f)
        mode = Mode.DRAG
        val c = cache
        for (p in intArrayOf(pFront, pBack, pUnder, pStatic)) if (exists(p)) c?.request(p)
        if (k == Kind.SINGLE_BACK) {
            tipX = geo.goneX()
        } else {
            tipX = geo.restX()
        }
        tipY = geo.cornerY
        geo.update(tipX, tipY)
    }

    private fun dragTo(x: Float, y: Float) {
        val w = geo.width
        val dy = y - downY
        val fold: Float
        if (kind == Kind.SINGLE_BACK) {
            val reach = max(w * 0.15f, w - (downX - contentLeft))
            val gain = (w / reach).coerceIn(1f, 3f)
            fold = (gain * (x - downX)).coerceIn(0f, w)
        } else {
            val sign = if (kind == Kind.SPREAD_LEFT) -1f else 1f
            val fromSpine = if (kind == Kind.SPREAD_LEFT) spineX() - downX else downX - spineX()
            val reach = max(w * 0.15f, fromSpine.coerceIn(0f, w))
            val gain = (w / reach).coerceIn(1f, 3f)
            fold = (w + gain * sign * (x - downX)).coerceIn(0f, w)
        }
        val lift = min((w - fold) * 0.18f, geo.height * 0.25f)
        val sign = if (geo.cornerBottom) -1f else 1f
        tipX = 2f * fold - w
        tipY = geo.cornerY + sign * lift + dy * 0.5f
        geo.update(tipX, tipY)
        tipX = geo.tipX
        tipY = geo.tipY
        invalidate()
    }

    private fun foldPosition() = (geo.width + tipX) / 2f

    private fun endFlip(toGone: Boolean) {
        mode = Mode.IDLE
        val changed = toGone == completeIsGone && doneIndex != index
        if (toGone == completeIsGone) index = doneIndex
        if (changed) host?.onPageChanged(index)
        prefetch()
        invalidate()
    }

    private fun animateRelease(toGone: Boolean, velocityX: Float, bump: Float = 0f, durationOverride: Long = 0L) {
        val startX = tipX
        val startY = tipY
        val endX = if (toGone) geo.goneX() else geo.restX()
        val endY = geo.cornerY
        val dist = hypot(endX - startX, endY - startY) / (2f * geo.width)
        var duration = if (durationOverride > 0) durationOverride else (170 + 360 * dist).toLong()
        if (abs(velocityX) > 2500f && durationOverride == 0L) duration = (duration * 0.7f).toLong()
        val sign = if (geo.cornerBottom) -1f else 1f
        mode = Mode.ANIMATE
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = duration
        a.interpolator = if (bump > 0f) PathInterpolator(0.35f, 0f, 0.25f, 1f) else DecelerateInterpolator(1.5f)
        a.addUpdateListener {
            val t = it.animatedValue as Float
            val x = startX + (endX - startX) * t
            val y = startY + (endY - startY) * t + sign * bump * sin(Math.PI.toFloat() * t)
            geo.update(x, y)
            tipX = geo.tipX
            tipY = geo.tipY
            invalidate()
        }
        a.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                animator = null
                if (!cancelled) endFlip(toGone)
            }
        })
        animator = a
        a.start()
    }

    private fun animateTap() {
        if (soundEnabled) sound?.play()
        val bump = geo.height * 0.2f
        val toGone = completeIsGone
        if (kind == Kind.SINGLE_BACK) {
            tipX = geo.goneX()
        }
        animateRelease(toGone, 0f, bump, 620L)
    }

    private fun finishAnimation() {
        animator?.end()
    }

    private fun cancelAnimation() {
        animator?.cancel()
        animator = null
        zoomAnimator?.cancel()
    }

    private fun cancelFlipTarget() = if (completeIsGone) false else true

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (pageCount == 0) return true
        scaleDetector.onTouchEvent(e)
        if (scaleActive || scaleDetector.isInProgress) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (mode == Mode.ANIMATE) finishAnimation()
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = e.x
                downY = e.y
                lastPanX = e.x
                lastPanY = e.y
                dragging = false
                dragStarted = false
                ignoreGesture = false
                tracker?.recycle()
                tracker = VelocityTracker.obtain()
                tracker?.addMovement(e)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (mode == Mode.DRAG) animateRelease(cancelFlipTarget(), 0f)
                ignoreGesture = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (ignoreGesture) return true
                tracker?.addMovement(e)
                val dx = e.x - downX
                val dy = e.y - downY
                val moved = hypot(dx, dy) > slop
                if (zoom > 1.02f) {
                    if (moved) dragStarted = true
                    if (dragStarted) {
                        panX += e.x - lastPanX
                        panY += e.y - lastPanY
                        clampPan()
                        invalidate()
                    }
                    lastPanX = e.x
                    lastPanY = e.y
                    return true
                }
                if (!dragStarted && moved) {
                    dragStarted = true
                    if (abs(dx) >= abs(dy) * 0.6f && mode == Mode.IDLE) {
                        val wantsForward = dx < 0
                        val ok = if (wantsForward) canForward() else canBackward()
                        if (ok) {
                            val k = if (spreadMode) {
                                if (wantsForward) Kind.SPREAD_RIGHT else Kind.SPREAD_LEFT
                            } else {
                                if (wantsForward) Kind.SINGLE_FORWARD else Kind.SINGLE_BACK
                            }
                            beginFlip(k, false)
                            dragging = true
                        } else {
                            ignoreGesture = true
                        }
                    } else {
                        ignoreGesture = true
                    }
                }
                if (dragging) dragTo(e.x, e.y)
            }
            MotionEvent.ACTION_UP -> {
                tracker?.addMovement(e)
                tracker?.computeCurrentVelocity(1000)
                val vx = tracker?.xVelocity ?: 0f
                parent?.requestDisallowInterceptTouchEvent(false)
                if (dragging && mode == Mode.DRAG) {
                    dragging = false
                    val w = geo.width
                    val fold = foldPosition()
                    val fling = 600f * density
                    val complete = when (kind) {
                        Kind.SINGLE_BACK -> if (vx > fling) true else if (vx < -fling) false else fold > w * 0.38f
                        Kind.SPREAD_LEFT -> if (vx > fling) true else if (vx < -fling) false else fold < w * 0.62f
                        else -> if (vx < -fling) true else if (vx > fling) false else fold < w * 0.62f
                    }
                    val toGone = if (completeIsGone) complete else !complete
                    if (complete && soundEnabled) sound?.play()
                    animateRelease(toGone, vx)
                } else if (!dragStarted && !ignoreGesture && mode == Mode.IDLE) {
                    handleTap(e)
                }
                tracker?.recycle()
                tracker = null
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging && mode == Mode.DRAG) {
                    dragging = false
                    animateRelease(cancelFlipTarget(), 0f)
                }
                tracker?.recycle()
                tracker = null
            }
        }
        return true
    }

    private fun handleTap(e: MotionEvent) {
        val rel = (e.x - contentLeft) / max(1f, contentWidth)
        val zoomed = zoom > 1.02f
        if (!zoomed && rel > 0.7f) {
            flipNext()
            return
        }
        if (!zoomed && rel < 0.3f) {
            flipPrevious()
            return
        }
        val now = e.eventTime
        val near = hypot(e.x - lastTapX, e.y - lastTapY) < 80f * density
        if (now - lastTapTime < DOUBLE_TAP_MS && near) {
            removeCallbacks(centerTapRunnable)
            lastTapTime = 0L
            toggleZoom(e.x, e.y)
        } else {
            lastTapTime = now
            lastTapX = e.x
            lastTapY = e.y
            postDelayed(centerTapRunnable, DOUBLE_TAP_MS)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pageWidth == 0 || pageCount == 0) return
        val pw = pageWidth.toFloat()
        val ph = pageHeight.toFloat()
        if (zoom > 1.001f || zoom < 0.999f) {
            drawZoomed(canvas, pw, ph)
            return
        }
        drawContentShadow(canvas)
        canvas.save()
        canvas.translate(contentLeft, pageTop)
        if (mode == Mode.IDLE) {
            drawIdle(canvas, pw, ph)
        } else {
            drawFlip(canvas, pw, ph)
        }
        canvas.restore()
    }

    private fun drawIdle(c: Canvas, pw: Float, ph: Float) {
        if (spreadMode) {
            val s = spreadOf(index)
            drawHalf(c, 2 * s - 1, true, pw, ph)
            drawHalf(c, 2 * s, false, pw, ph)
        } else {
            drawFlat(c, bitmapOf(index), pw, ph, false)
            drawSpine(c, pw, ph)
        }
    }

    private fun drawHalf(c: Canvas, page: Int, left: Boolean, pw: Float, ph: Float) {
        if (!exists(page)) return
        c.save()
        c.translate(if (left) 0f else pw, 0f)
        drawFlat(c, bitmapOf(page), pw, ph, false)
        if (left) {
            c.translate(pw, 0f)
            c.scale(-1f, 1f)
        }
        drawSpine(c, pw, ph)
        c.restore()
    }

    private fun drawFlip(c: Canvas, pw: Float, ph: Float) {
        if (!spreadMode) {
            drawCurl(c, false, pw, ph)
            return
        }
        if (kind == Kind.SPREAD_RIGHT) drawHalf(c, pStatic, true, pw, ph) else drawHalf(c, pStatic, false, pw, ph)
        c.save()
        c.translate(pw, 0f)
        if (kind == Kind.SPREAD_LEFT) c.scale(-1f, 1f)
        drawCurl(c, kind == Kind.SPREAD_LEFT, pw, ph)
        c.restore()
    }

    private fun drawZoomed(canvas: Canvas, pw: Float, ph: Float) {
        canvas.save()
        canvas.translate(width / 2f + panX, height / 2f + panY)
        canvas.scale(zoom, zoom)
        canvas.translate(-width / 2f + contentLeft, -height / 2f + pageTop)
        if (spreadMode) {
            drawIdle(canvas, pw, ph)
        } else {
            val bmp = cache?.hi(index) ?: cache?.get(index)
            rectF.set(0f, 0f, pw, ph)
            if (bmp != null) canvas.drawBitmap(bmp, null, rectF, pagePaint) else canvas.drawRect(rectF, fillPaint)
        }
        canvas.restore()
    }

    private fun drawContentShadow(canvas: Canvas) {
        if (night) return
        strokePaint.shader = null
        val pw = pageWidth.toFloat()
        val ph = pageHeight.toFloat()
        var leftVisible = true
        var rightVisible = true
        if (spreadMode && mode == Mode.IDLE) {
            val s = spreadOf(index)
            leftVisible = exists(2 * s - 1)
            rightVisible = exists(2 * s)
        }
        val l = if (spreadMode && !leftVisible) contentLeft + pw else contentLeft
        val r = if (spreadMode && !rightVisible) contentLeft + pw else contentLeft + contentWidth
        for (k in 1..6) {
            strokePaint.strokeWidth = k * 3.2f * density
            strokePaint.color = Color.argb(14, 0, 0, 0)
            canvas.drawRect(l, pageTop, r, pageTop + ph, strokePaint)
        }
    }

    private fun drawFlat(c: Canvas, bmp: Bitmap?, pw: Float, ph: Float, flip: Boolean) {
        if (bmp == null) {
            c.drawRect(0f, 0f, pw, ph, fillPaint)
            return
        }
        c.save()
        if (flip) {
            c.translate(pw, 0f)
            c.scale(-1f, 1f)
        }
        rectF.set(0f, 0f, pw, ph)
        c.drawBitmap(bmp, null, rectF, pagePaint)
        c.restore()
    }

    private fun drawSpine(c: Canvas, pw: Float, ph: Float) {
        val w = pw * (if (spreadMode) 0.07f else 0.05f)
        val alpha = if (spreadMode) 70 else 46
        shadowPaint.shader = LinearGradient(0f, 0f, w, 0f, Color.argb(alpha, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, ph, shadowPaint)
        shadowPaint.shader = null
    }

    private fun textureMatrix(bmp: Bitmap, flip: Boolean, pw: Float, ph: Float): Matrix {
        if (flip) {
            matrix.setScale(-pw / bmp.width, ph / bmp.height)
            matrix.postTranslate(pw, 0f)
        } else {
            matrix.setScale(pw / bmp.width, ph / bmp.height)
        }
        return matrix
    }

    private fun fillWithBitmap(c: Canvas, bmp: Bitmap?, shape: Path, pw: Float, ph: Float, flip: Boolean) {
        if (bmp == null) {
            c.drawPath(shape, fillPaint)
            return
        }
        val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        shader.setLocalMatrix(textureMatrix(bmp, flip, pw, ph))
        pagePaint.shader = shader
        c.drawPath(shape, pagePaint)
        pagePaint.shader = null
    }

    private fun toPath(poly: Poly, out: Path) {
        out.rewind()
        for (i in 0 until poly.size) {
            if (i == 0) out.moveTo(poly.xs[i], poly.ys[i]) else out.lineTo(poly.xs[i], poly.ys[i])
        }
        out.close()
    }

    private fun drawCurl(c: Canvas, mirrored: Boolean, pw: Float, ph: Float) {
        val g = geo
        val front = bitmapOf(pFront)
        val under = bitmapOf(pUnder)
        if (!g.valid) {
            drawFlat(c, front, pw, ph, mirrored)
            drawSpine(c, pw, ph)
            return
        }

        if (exists(pUnder)) {
            drawFlat(c, under, pw, ph, mirrored)
            drawSpine(c, pw, ph)
        }

        val dist = hypot(g.tipX - g.cornerX, g.tipY - g.cornerY)
        val sizeFade = min(1f, dist / (pw * 0.12f))
        val spineFade = min(1f, g.midX / (pw * 0.2f)).coerceAtLeast(0f)
        val fade = sizeFade * spineFade

        toPath(g.folded, flapPath)
        if (exists(pUnder)) {
            val revealWidth = min(pw * 0.3f, g.flapDepth * 0.9f + 12f * density)
            shadowPaint.shader = LinearGradient(
                g.midX, g.midY, g.midX - g.dirX * revealWidth, g.midY - g.dirY * revealWidth,
                Color.argb((120 * fade).toInt(), 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            c.drawPath(flapPath, shadowPaint)
            shadowPaint.shader = null
        }

        if (g.unfolded.size >= 3) {
            toPath(g.unfolded, path)
            fillWithBitmap(c, front, path, pw, ph, mirrored)
            c.save()
            c.clipPath(path)
            drawSpine(c, pw, ph)
            c.restore()
        }

        toPath(g.flap, flapPath)

        c.save()
        c.clipRect(if (spreadMode) -pw else 0f, 0f, pw, ph)
        strokePaint.shader = null
        val spread = 11f * density
        val steps = 8
        for (k in 1..steps) {
            strokePaint.strokeWidth = 2f * spread * k / steps
            strokePaint.color = Color.argb((13 * fade).toInt().coerceAtLeast(0), 0, 0, 0)
            c.drawPath(flapPath, strokePaint)
        }
        c.restore()

        val ux = g.dirX
        val uy = g.dirY
        val dot = 2f * (g.midX * ux + g.midY * uy)
        reflectValues[0] = 1f - 2f * ux * ux
        reflectValues[1] = -2f * ux * uy
        reflectValues[2] = dot * ux
        reflectValues[3] = -2f * ux * uy
        reflectValues[4] = 1f - 2f * uy * uy
        reflectValues[5] = dot * uy
        reflectValues[6] = 0f
        reflectValues[7] = 0f
        reflectValues[8] = 1f
        reflect.setValues(reflectValues)

        c.drawPath(flapPath, fillPaint)
        val realBack = exists(pBack)
        val backBitmap = if (realBack) bitmapOf(pBack) else front
        if (backBitmap != null) {
            val shader = BitmapShader(backBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val m = textureMatrix(backBitmap, if (realBack) !mirrored else mirrored, pw, ph)
            m.postConcat(reflect)
            shader.setLocalMatrix(m)
            pagePaint.shader = shader
            pagePaint.alpha = if (realBack) 255 else 78
            c.drawPath(flapPath, pagePaint)
            pagePaint.alpha = 255
            pagePaint.shader = null
        }

        val depth = max(1f, g.flapDepth)
        shadowPaint.shader = LinearGradient(
            g.midX, g.midY, g.midX + ux * depth, g.midY + uy * depth,
            intArrayOf(
                Color.argb((96 * sizeFade).toInt(), 0, 0, 0),
                Color.argb((26 * sizeFade).toInt(), 0, 0, 0),
                Color.argb(0, 0, 0, 0),
                Color.argb(46, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.16f, 0.62f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawPath(flapPath, shadowPaint)
        shadowPaint.shader = null
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (Build.VERSION.SDK_INT >= 29) {
            val h = min(bottom - top, (200 * density).toInt())
            val mid = (bottom - top) / 2
            val edge = (28 * density).toInt()
            val w = right - left
            systemGestureExclusionRects = listOf(
                Rect(0, mid - h / 2, edge, mid + h / 2),
                Rect(w - edge, mid - h / 2, w, mid + h / 2)
            )
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(centerTapRunnable)
        cancelAnimation()
    }

    companion object {
        private const val MAX_ZOOM = 4f
        private const val DOUBLE_TAP_MS = 280L
    }
}
