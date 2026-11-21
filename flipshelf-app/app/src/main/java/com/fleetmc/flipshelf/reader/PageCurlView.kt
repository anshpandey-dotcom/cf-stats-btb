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
import android.graphics.RectF
import android.graphics.Shader
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

    var pageCount = 0
        private set
    var index = 0
        private set
    var pageWidth = 0
        private set
    var pageHeight = 0
        private set

    private val density = resources.displayMetrics.density
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val geo = CurlGeometry()
    private var pageLeft = 0f
    private var pageTop = 0f

    private var mode = Mode.IDLE
    private var forward = true
    private var flipA = 0
    private var flipB = 0
    private var animator: ValueAnimator? = null
    private var tipX = 0f
    private var tipY = 0f

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
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
    private val tmp = FloatArray(2)

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
        val filter = if (night) nightFilter else null
        pagePaint.colorFilter = filter
        fillPaint.color = if (night) paperDark else paperLight
    }

    fun setPageCount(count: Int, startIndex: Int) {
        cancelAnimation()
        pageCount = count
        index = startIndex.coerceIn(0, max(0, count - 1))
        mode = Mode.IDLE
        resetZoom()
        prefetch()
        invalidate()
    }

    fun goTo(target: Int) {
        if (pageCount == 0) return
        cancelAnimation()
        val t = target.coerceIn(0, pageCount - 1)
        mode = Mode.IDLE
        resetZoom()
        if (t != index) {
            index = t
            host?.onPageChanged(index)
        }
        prefetch()
        invalidate()
    }

    fun flipNext() {
        if (mode == Mode.ANIMATE) finishAnimation()
        if (mode != Mode.IDLE || zoom > 1.02f || index >= pageCount - 1) return
        beginFlip(true, true)
        animateTap(true)
    }

    fun flipPrevious() {
        if (mode == Mode.ANIMATE) finishAnimation()
        if (mode != Mode.IDLE || zoom > 1.02f || index <= 0) return
        beginFlip(false, true)
        animateTap(false)
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
        c.request(index + 1)
        c.request(index - 1)
        c.request(index + 2)
    }

    fun isWanted(i: Int) = abs(i - index) <= 3 || (mode != Mode.IDLE && (i == flipA || i == flipB))

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computePageRect()
    }

    private fun computePageRect() {
        if (width == 0 || height == 0) return
        val availW = width - 2 * 12f * density
        val availH = height - 2 * 28f * density
        val pw = min(availW, availH * aspect)
        val ph = pw / aspect
        pageLeft = (width - pw) / 2f
        pageTop = (height - ph) / 2f
        val newW = pw.toInt()
        val newH = ph.toInt()
        geo.setSize(pw, ph)
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
        val limX = max(0f, (pageWidth * zoom - width) / 2f)
        val limY = max(0f, (pageHeight * zoom - height) / 2f)
        panX = panX.coerceIn(-limX, limX)
        panY = panY.coerceIn(-limY, limY)
    }

    private fun requestHighRes() {
        if (zoom > 1.2f) {
            val longest = max(pageWidth, pageHeight).toFloat()
            val scale = min(2.6f, 3200f / longest).coerceAtLeast(1f)
            cache?.requestHi(index, scale)
        }
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
            val target = 2.4f
            val cx = width / 2f
            val cy = height / 2f
            val px = (fx - cx) * (1f - target)
            val py = (fy - cy) * (1f - target)
            val limX = max(0f, (pageWidth * target - width) / 2f)
            val limY = max(0f, (pageHeight * target - height) / 2f)
            animateZoomTo(target, px.coerceIn(-limX, limX), py.coerceIn(-limY, limY))
            requestHighRes()
            postDelayed({ requestHighRes() }, 60)
        }
    }

    private fun beginFlip(isForward: Boolean, fromTap: Boolean) {
        forward = isForward
        flipA = if (isForward) index else index - 1
        flipB = if (isForward) index + 1 else index
        geo.setCorner(cornerBottomFor(fromTap))
        mode = Mode.DRAG
        val c = cache
        c?.request(flipA)
        c?.request(flipB)
        if (isForward) {
            tipX = geo.restX()
            tipY = geo.cornerY
        } else {
            tipX = geo.goneX()
            tipY = geo.cornerY
        }
        geo.update(tipX, tipY)
    }

    private fun cornerBottomFor(fromTap: Boolean): Boolean {
        if (fromTap) return true
        return downY - pageTop > pageHeight * 0.5f
    }

    private fun dragTo(x: Float, y: Float) {
        val w = geo.width
        val dx = x - downX
        val dy = y - downY
        val fold: Float
        if (forward) {
            val reach = max(w * 0.15f, downX - pageLeft)
            val gain = (w / reach).coerceIn(1f, 3f)
            fold = (w + gain * dx).coerceIn(0f, w)
        } else {
            val reach = max(w * 0.15f, w - (downX - pageLeft))
            val gain = (w / reach).coerceIn(1f, 3f)
            fold = (gain * dx).coerceIn(0f, w)
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
        val newIndex = if (toGone) flipB else flipA
        mode = Mode.IDLE
        val changed = newIndex != index
        index = newIndex
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

    private fun animateTap(isForward: Boolean) {
        if (soundEnabled) sound?.play()
        val bump = geo.height * 0.2f
        animateRelease(isForward, 0f, bump, 620L)
    }

    private fun finishAnimation() {
        animator?.end()
    }

    private fun cancelAnimation() {
        animator?.cancel()
        animator = null
        zoomAnimator?.cancel()
    }

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
                downTime = e.eventTime
                dragging = false
                dragStarted = false
                ignoreGesture = false
                tracker?.recycle()
                tracker = VelocityTracker.obtain()
                tracker?.addMovement(e)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (mode == Mode.DRAG) {
                    animateRelease(!forward, 0f)
                }
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
                if (!dragStarted) {
                    if (moved) {
                        dragStarted = true
                        if (abs(dx) >= abs(dy) * 0.6f) {
                            val wantsForward = dx < 0
                            val ok = if (wantsForward) index < pageCount - 1 else index > 0
                            if (ok && mode == Mode.IDLE) {
                                beginFlip(wantsForward, false)
                                dragging = true
                            } else {
                                ignoreGesture = true
                            }
                        } else {
                            ignoreGesture = true
                        }
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
                    val complete = if (forward) {
                        if (vx < -fling) true else if (vx > fling) false else fold < w * 0.62f
                    } else {
                        if (vx > fling) true else if (vx < -fling) false else fold > w * 0.38f
                    }
                    val toGone = if (forward) complete else !complete
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
                    animateRelease(!forward, 0f)
                }
                tracker?.recycle()
                tracker = null
            }
        }
        return true
    }

    private fun handleTap(e: MotionEvent) {
        val rel = (e.x - pageLeft) / max(1f, pageWidth.toFloat())
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
            drawZoomed(canvas)
            return
        }
        drawPageShadow(canvas)
        canvas.save()
        canvas.translate(pageLeft, pageTop)
        if (mode == Mode.IDLE) {
            drawFlat(canvas, cache?.get(index), pw, ph)
            drawSpine(canvas, pw, ph)
        } else {
            drawCurl(canvas, cache?.get(flipA), cache?.get(flipB), pw, ph)
        }
        canvas.restore()
    }

    private fun drawZoomed(canvas: Canvas) {
        val bmp = cache?.hi(index) ?: cache?.get(index)
        canvas.save()
        canvas.translate(width / 2f + panX, height / 2f + panY)
        canvas.scale(zoom, zoom)
        canvas.translate(-width / 2f + pageLeft, -height / 2f + pageTop)
        rectF.set(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat())
        if (bmp != null) canvas.drawBitmap(bmp, null, rectF, pagePaint) else canvas.drawRect(rectF, fillPaint)
        canvas.restore()
    }

    private fun drawPageShadow(canvas: Canvas) {
        if (night) return
        strokePaint.shader = null
        strokePaint.strokeJoin = Paint.Join.ROUND
        val l = pageLeft
        val t = pageTop
        val r = pageLeft + pageWidth
        val b = pageTop + pageHeight
        for (k in 1..6) {
            strokePaint.strokeWidth = k * 3.2f * density
            strokePaint.color = Color.argb(14, 0, 0, 0)
            canvas.drawRect(l, t, r, b, strokePaint)
        }
    }

    private fun drawFlat(c: Canvas, bmp: Bitmap?, pw: Float, ph: Float) {
        if (bmp == null) {
            c.drawRect(0f, 0f, pw, ph, fillPaint)
            return
        }
        rectF.set(0f, 0f, pw, ph)
        c.drawBitmap(bmp, null, rectF, pagePaint)
    }

    private fun drawSpine(c: Canvas, pw: Float, ph: Float) {
        val w = pw * 0.05f
        shadowPaint.shader = LinearGradient(0f, 0f, w, 0f, Color.argb(46, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, ph, shadowPaint)
        shadowPaint.shader = null
    }

    private fun fillWithBitmap(c: Canvas, bmp: Bitmap?, shape: Path, pw: Float, ph: Float, extra: Matrix?) {
        if (bmp == null) {
            c.drawPath(shape, fillPaint)
            return
        }
        val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        matrix.setScale(pw / bmp.width, ph / bmp.height)
        if (extra != null) matrix.postConcat(extra)
        shader.setLocalMatrix(matrix)
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

    private fun drawCurl(c: Canvas, a: Bitmap?, b: Bitmap?, pw: Float, ph: Float) {
        val g = geo
        if (!g.valid) {
            if (forward) {
                drawFlat(c, a, pw, ph)
                drawSpine(c, pw, ph)
            } else {
                drawFlat(c, b, pw, ph)
                drawSpine(c, pw, ph)
            }
            return
        }

        drawFlat(c, b, pw, ph)
        drawSpine(c, pw, ph)

        val dist = hypot(g.tipX - g.cornerX, g.tipY - g.cornerY)
        val sizeFade = min(1f, dist / (pw * 0.12f))
        val spineFade = min(1f, g.midX / (pw * 0.2f)).coerceAtLeast(0f)
        val fade = sizeFade * spineFade

        toPath(g.folded, flapPath)
        val revealWidth = min(pw * 0.3f, g.flapDepth * 0.9f + 12f * density)
        shadowPaint.shader = LinearGradient(
            g.midX, g.midY, g.midX - g.dirX * revealWidth, g.midY - g.dirY * revealWidth,
            Color.argb((120 * fade).toInt(), 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        c.drawPath(flapPath, shadowPaint)
        shadowPaint.shader = null

        if (g.unfolded.size >= 3) {
            toPath(g.unfolded, path)
            fillWithBitmap(c, a, path, pw, ph, null)
            c.save()
            c.clipPath(path)
            drawSpine(c, pw, ph)
            c.restore()
        }

        toPath(g.flap, flapPath)

        c.save()
        c.clipRect(0f, 0f, pw, ph)
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
        if (a != null) {
            val shader = BitmapShader(a, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            matrix.setScale(pw / a.width, ph / a.height)
            matrix.postConcat(reflect)
            shader.setLocalMatrix(matrix)
            pagePaint.shader = shader
            pagePaint.alpha = 78
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
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val h = min(bottom - top, (200 * density).toInt())
            val mid = (bottom - top) / 2
            val edge = (28 * density).toInt()
            val w = right - left
            systemGestureExclusionRects = listOf(
                android.graphics.Rect(0, mid - h / 2, edge, mid + h / 2),
                android.graphics.Rect(w - edge, mid - h / 2, w, mid + h / 2)
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
