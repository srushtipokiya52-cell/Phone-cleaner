package com.phone.cleanerapp.storage.mediafiles.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.phone.cleanerapp.storage.mediafiles.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Fragment that owns a ViewBinding between onCreateView and onDestroyView. */
abstract class VBFragment<VB : ViewBinding>(
    private val inflate: (LayoutInflater, ViewGroup?, Boolean) -> VB,
) : Fragment() {
    private var _b: VB? = null
    protected val b: VB get() = _b!!
    protected val bOrNull: VB? get() = _b

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflate(inflater, container, false).also { _b = it }.root

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }

    /** Runs [block] while the view exists; cancelled automatically when it is destroyed. */
    protected fun ui(block: suspend CoroutineScope.() -> Unit): Job =
        viewLifecycleOwner.lifecycleScope.launch { block() }

    /** Runs [block] every time the screen comes back to the foreground (e.g. after a Settings trip). */
    protected fun onEachResume(block: suspend CoroutineScope.() -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) { block() }
        }
    }
}

/** Simple adapter: one layout, one bind lambda. */
class RowAdapter<T, VB : ViewBinding>(
    private val inflate: (LayoutInflater, ViewGroup, Boolean) -> VB,
    private val bind: (VB, T, Int) -> Unit,
) : RecyclerView.Adapter<RowAdapter.VH<VB>>() {

    class VH<VB : ViewBinding>(val b: VB) : RecyclerView.ViewHolder(b.root)

    var items: List<T> = emptyList()
        set(v) {
            field = v
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: VH<VB>, position: Int) = bind(holder.b, items[position], position)
    override fun getItemCount() = items.size
}

/** Colour pairs used for the little rounded icon tiles all over the design. */
enum class Tone(@ColorRes val bg: Int, @ColorRes val fg: Int) {
    GREEN(R.color.brand_tint, R.color.brand),
    AMBER(R.color.amber_tint, R.color.amber_dark),
    CYAN(R.color.cyan_tint, R.color.cyan),
    VIOLET(R.color.violet_tint, R.color.violet),
    PINK(R.color.pink_tint, R.color.pink),
    GRAY(R.color.divider, R.color.muted),
}

fun ImageView.tile(@DrawableRes icon: Int, tone: Tone) {
    setImageResource(icon)
    imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, tone.fg))
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, tone.bg))
}

fun View.setTint(@ColorRes color: Int) {
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, color))
}

/** A rounded filter chip like the tabs in the design. */
fun chipView(ctx: Context, text: String, on: Boolean, click: () -> Unit): TextView = TextView(ctx).apply {
    this.text = text
    textSize = 13f
    setTypeface(typeface, android.graphics.Typeface.BOLD)
    gravity = android.view.Gravity.CENTER
    minHeight = ctx.resources.displayMetrics.density.let { (40 * it).toInt() }
    val pad = (16 * ctx.resources.displayMetrics.density).toInt()
    setPadding(pad, 0, pad, 0)
    setTextColor(ContextCompat.getColor(ctx, if (on) R.color.white else R.color.ink))
    setBackgroundResource(if (on) R.drawable.bg_chip_on else R.drawable.bg_chip)
    layoutParams = android.widget.LinearLayout.LayoutParams(
        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { marginEnd = (8 * ctx.resources.displayMetrics.density).toInt() }
    setOnClickListener { click() }
}

fun View.show(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}

/** A donut made of coloured arcs. Segment fractions are of the full circle. */
class RingView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    private class Seg(val fraction: Float, val color: Int)

    private val segs = mutableListOf<Seg>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val rect = RectF()
    private var stroke = 14f * resources.displayMetrics.density
    private var track = ContextCompat.getColor(ctx, R.color.border)
    private var fill = 0

    init {
        context.obtainStyledAttributes(attrs, R.styleable.RingView).apply {
            stroke = getDimension(R.styleable.RingView_ringStroke, stroke)
            track = getColor(R.styleable.RingView_ringTrack, track)
            fill = getColor(R.styleable.RingView_ringFill, 0)
            recycle()
        }
    }

    fun setSegments(vararg s: Pair<Float, Int>) {
        segs.clear()
        s.forEach { segs.add(Seg(it.first.coerceIn(0f, 1f), ContextCompat.getColor(context, it.second))) }
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val half = stroke / 2
        rect.set(half, half, width - half, height - half)
        if (fill != 0) {
            paint.style = Paint.Style.FILL; paint.color = fill
            c.drawCircle(width / 2f, height / 2f, width / 2f - stroke, paint)
            paint.style = Paint.Style.STROKE
        }
        paint.strokeWidth = stroke
        paint.color = track
        c.drawArc(rect, 0f, 360f, false, paint)
        var start = -90f
        for (s in segs) {
            if (s.fraction <= 0.002f) continue
            paint.color = s.color
            // Leave a little room so rounded caps of neighbouring arcs don't overlap.
            val sweep = (s.fraction * 360f - 3f).coerceAtLeast(1f)
            c.drawArc(rect, start + 1.5f, sweep, false, paint)
            start += s.fraction * 360f
        }
    }
}

/** A TextView that takes no space while it has no text (so rows without a subtitle stay vertically centred). */
class AutoHideTextView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : androidx.appcompat.widget.AppCompatTextView(ctx, attrs) {
    override fun setText(text: CharSequence?, type: BufferType?) {
        super.setText(text, type)
        visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }
}

/** Skeleton container: draws its (grey) children with a soft light band sweeping across them. */
class ShimmerLayout @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : android.widget.FrameLayout(ctx, attrs) {
    private val paint = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP) }
    private val matrix = android.graphics.Matrix()
    private var shader: android.graphics.LinearGradient? = null
    private var progress = 0f
    private var animator: android.animation.ValueAnimator? = null

    /** Turn the sweep off (the children then draw normally). */
    var active = true
        set(v) {
            field = v
            invalidate()
        }

    init {
        setWillNotDraw(false)
    }

    private fun bandWidth() = width * 0.6f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shader = android.graphics.LinearGradient(
            0f, 0f, bandWidth().coerceAtLeast(1f), 0f,
            intArrayOf(0x00FFFFFF, 0xB3FFFFFF.toInt(), 0x00FFFFFF), null, android.graphics.Shader.TileMode.CLAMP,
        )
        paint.shader = shader
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200
            repeatCount = android.animation.ValueAnimator.INFINITE
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun dispatchDraw(canvas: Canvas) {
        val save = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.dispatchDraw(canvas)
        if (active && shader != null) {
            matrix.setTranslate(-bandWidth() + progress * (width + bandWidth()), 0f)
            shader!!.setLocalMatrix(matrix)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
        canvas.restoreToCount(save)
    }
}

/** A LinearLayout that disappears while it has no children and appears as soon as one is added. */
class AutoHideLinearLayout @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : android.widget.LinearLayout(ctx, attrs) {
    override fun onViewAdded(child: View?) {
        super.onViewAdded(child)
        visibility = View.VISIBLE
    }

    override fun onViewRemoved(child: View?) {
        super.onViewRemoved(child)
        if (childCount == 0) visibility = View.GONE
    }
}

/**
 * Turns any block of UI into a shimmering skeleton while its data loads: every text becomes a grey bar,
 * icons fade and a light band sweeps across. [stop] puts everything back exactly as it was.
 */
object Skeleton {
    private class Saved(val textColor: Int?, val bg: android.graphics.drawable.Drawable?, val bgTint: android.content.res.ColorStateList?, val imgAlpha: Int?, val alpha: Float)

    fun start(root: View) {
        val shimmer = wrap(root)
        shimmer.active = true
        walk(root) { v ->
            if (v.getTag(R.id.skeleton_saved) != null) return@walk
            v.setTag(
                R.id.skeleton_saved,
                Saved(
                    (v as? TextView)?.currentTextColor,
                    v.background,
                    v.backgroundTintList,
                    (v as? ImageView)?.imageAlpha,
                    v.alpha,
                ),
            )
            when (v) {
                is TextView -> {
                    if (v.text.isNullOrEmpty()) v.text = "00000000"
                    v.setTextColor(android.graphics.Color.TRANSPARENT)
                    v.setBackgroundResource(R.drawable.bg_skel)
                    v.backgroundTintList = null
                }
                is ImageView -> {
                    v.imageAlpha = 0
                    if (v.background != null) v.backgroundTintList = ColorStateList.valueOf(0xFFE2E8F0.toInt())
                }
                is RingView -> v.alpha = 0.25f
            }
        }
    }

    fun stop(root: View) {
        (root.parent as? ShimmerLayout)?.active = false
        walk(root) { v ->
            val s = v.getTag(R.id.skeleton_saved) as? Saved ?: return@walk
            v.setTag(R.id.skeleton_saved, null)
            if (v is TextView && s.textColor != null) v.setTextColor(s.textColor)
            if (v is TextView || v is ImageView) {
                v.background = s.bg
                v.backgroundTintList = s.bgTint
            }
            if (v is ImageView && s.imgAlpha != null) v.imageAlpha = s.imgAlpha
            v.alpha = s.alpha
        }
    }

    private fun walk(v: View, f: (View) -> Unit) {
        f(v)
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), f)
    }

    private fun wrap(v: View): ShimmerLayout {
        (v.parent as? ShimmerLayout)?.let { return it }
        val parent = v.parent as android.view.ViewGroup
        val idx = parent.indexOfChild(v)
        val lp = v.layoutParams
        parent.removeViewAt(idx)
        val shimmer = ShimmerLayout(v.context)
        shimmer.addView(v, android.widget.FrameLayout.LayoutParams(if (lp.width == 0) -1 else lp.width, lp.height))
        parent.addView(shimmer, idx, lp)
        return shimmer
    }
}
