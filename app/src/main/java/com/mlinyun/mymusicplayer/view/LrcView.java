package com.mlinyun.mymusicplayer.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Scroller;

import androidx.annotation.Nullable;

import com.mlinyun.mymusicplayer.R;
import com.mlinyun.mymusicplayer.model.LyricLine;
import com.mlinyun.mymusicplayer.model.Lyrics;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义歌词显示控件
 * 支持歌词滚动、高亮、平滑动画等
 */
public class LrcView extends View {

    private static final String TAG = "LrcView";

    // 歌词对象
    private Lyrics lyrics;
    private List<LyricLine> lrcLines = new ArrayList<>();

    // 当前播放位置
    private int currentLine = 0;

    // 字体相关
    private float normalTextSize;
    private float highlightTextSize;
    private int normalTextColor;
    private int highlightTextColor;
    private Typeface typeface = Typeface.DEFAULT;

    // 绘图工具
    private Paint normalPaint;
    private Paint highlightPaint;

    // 布局相关
    private float lineSpacing;
    private float paddingTop;
    private float paddingBottom;

    // 歌词偏移量
    private float offset;

    // 动画相关
    private Scroller scroller;
    private ValueAnimator animator;
    private long animationDuration = 300;

    // 手势相关
    private GestureDetector gestureDetector;
    private boolean userScrolling = false;
    private boolean enableUserScroll = true;
    private static final int RESET_DURATION = 3000; // 用户滑动后自动恢复的时间（毫秒）
    private Runnable resetRunnable = new Runnable() {
        @Override
        public void run() {
            if (userScrolling) {
                userScrolling = false;
                scrollToCurrentLine(true);
            }
        }
    };

    // 默认文本
    private String emptyLrcText = "暂无歌词";

    // 回调接口
    private LrcViewListener lrcViewListener;

    /**
     * 构造函数
     */
    public LrcView(Context context) {
        this(context, null);
    }

    public LrcView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LrcView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    /**
     * 初始化
     */
    private void init(Context context, AttributeSet attrs) {
        // 获取自定义属性
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.LrcView);
        normalTextSize = ta.getDimension(R.styleable.LrcView_normalTextSize, sp2px(context, 14));
        highlightTextSize = ta.getDimension(R.styleable.LrcView_highlightTextSize, sp2px(context, 16));
        normalTextColor = ta.getColor(R.styleable.LrcView_normalTextColor, Color.LTGRAY);
        highlightTextColor = ta.getColor(R.styleable.LrcView_highlightTextColor, Color.WHITE);
        lineSpacing = ta.getDimension(R.styleable.LrcView_lineSpacing, dp2px(context, 20));
        paddingTop = ta.getDimension(R.styleable.LrcView_lrcPaddingTop, getHeight() / 3f);
        paddingBottom = ta.getDimension(R.styleable.LrcView_lrcPaddingBottom, getHeight() / 3f);
        enableUserScroll = ta.getBoolean(R.styleable.LrcView_enableUserScroll, true);
        if (ta.hasValue(R.styleable.LrcView_emptyLrcText)) {
            emptyLrcText = ta.getString(R.styleable.LrcView_emptyLrcText);
        }
        ta.recycle();

        // 初始化普通文本画笔
        normalPaint = new Paint();
        normalPaint.setAntiAlias(true);
        normalPaint.setTextSize(normalTextSize);
        normalPaint.setColor(normalTextColor);
        normalPaint.setTextAlign(Paint.Align.CENTER);

        // 初始化高亮文本画笔
        highlightPaint = new Paint();
        highlightPaint.setAntiAlias(true);
        highlightPaint.setTextSize(highlightTextSize);
        highlightPaint.setColor(highlightTextColor);
        highlightPaint.setTextAlign(Paint.Align.CENTER);
        highlightPaint.setTypeface(Typeface.create(typeface, Typeface.BOLD));

        // 初始化Scroller
        scroller = new Scroller(context);

        // 初始化手势检测器
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                // 如果允许用户滑动，则拦截事件
                return enableUserScroll;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (!enableUserScroll) return false;

                // 设置用户滚动状态
                userScrolling = true;

                // 清除之前的重置任务
                removeCallbacks(resetRunnable);

                // 更新偏移量
                offset += distanceY;

                // 限制滚动范围
                float minOffset = -getContentHeight();
                float maxOffset = getHeight();
                offset = Math.max(Math.min(offset, maxOffset), minOffset);

                // 触发重绘
                invalidate();

                // 安排重置任务
                postDelayed(resetRunnable, RESET_DURATION);

                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (!enableUserScroll) return false;

                // 启动惯性滑动
                userScrolling = true;
                removeCallbacks(resetRunnable);

                scroller.fling(0, (int) offset, 0, (int) -velocityY / 3,
                        0, 0, (int) -getContentHeight(), (int) getHeight());

                // 安排重置任务
                postDelayed(resetRunnable, RESET_DURATION);

                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // 点击事件，可以用于显示/隐藏控件等
                if (lrcViewListener != null) {
                    lrcViewListener.onLrcViewClick();
                }
                return true;
            }
        });
    }

    /**
     * 处理触摸事件
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 如果不允许用户滑动，则不处理触摸事件
        if (!enableUserScroll) {
            return super.onTouchEvent(event);
        }

        boolean result = gestureDetector.onTouchEvent(event);

        // 处理ACTION_UP事件，停止scroller
        if (event.getAction() == MotionEvent.ACTION_UP && !scroller.isFinished()) {
            scroller.forceFinished(true);
        }

        return result || super.onTouchEvent(event);
    }

    /**
     * 计算视图尺寸
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        // 确保paddingTop至少为视图高度的1/3
        if (paddingTop < getMeasuredHeight() / 3f) {
            paddingTop = getMeasuredHeight() / 3f;
        }

        // 确保paddingBottom至少为视图高度的1/3
        if (paddingBottom < getMeasuredHeight() / 3f) {
            paddingBottom = getMeasuredHeight() / 3f;
        }
    }

    /**
     * 计算内容高度
     */
    private float getContentHeight() {
        if (lrcLines.isEmpty()) {
            return 0;
        }

        return lrcLines.size() * (normalTextSize + lineSpacing);
    }

    /**
     * 绘制歌词
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 如果没有歌词，显示默认文本
        if (lrcLines.isEmpty()) {
            drawEmptyText(canvas);
            return;
        }

        // 计算中心Y坐标
        float centerY = getHeight() / 2f;

        // 处理Scroller
        if (scroller.computeScrollOffset()) {
            offset = scroller.getCurrY();
            invalidate();
        }

        // 绘制歌词
        float y;
        for (int i = 0; i < lrcLines.size(); i++) {
            String text = lrcLines.get(i).getText();
            if (TextUtils.isEmpty(text)) {
                continue;
            }

            // 计算y坐标
            y = centerY + i * (normalTextSize + lineSpacing) - offset;

            // 如果在可见范围内才绘制
            if (y > -normalTextSize && y < getHeight() + normalTextSize) {
                // 当前行使用高亮画笔
                if (i == currentLine) {
                    canvas.drawText(text, getWidth() / 2f, y, highlightPaint);
                } else {
                    // 其他行使用普通画笔
                    canvas.drawText(text, getWidth() / 2f, y, normalPaint);
                }
            }
        }
    }

    /**
     * 绘制空歌词提示
     */
    private void drawEmptyText(Canvas canvas) {
        float centerY = getHeight() / 2f;
        canvas.drawText(emptyLrcText, getWidth() / 2f, centerY, normalPaint);
    }

    /**
     * 滚动到当前行
     */
    private void scrollToCurrentLine(boolean animated) {
        if (lrcLines.isEmpty() || currentLine < 0) {
            return;
        }

        // 停止之前的动画
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }

        // 计算目标偏移量
        final float targetOffset = currentLine * (normalTextSize + lineSpacing);

        if (animated) {
            // 创建并启动动画
            animator = ValueAnimator.ofFloat(offset, targetOffset);
            animator.setDuration(animationDuration);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    offset = (float) animation.getAnimatedValue();
                    invalidate();
                }
            });
            animator.start();
        } else {
            // 直接设置偏移量
            offset = targetOffset;
            invalidate();
        }
    }

    /**
     * 更新歌词
     */
    public void setLyrics(Lyrics lyrics) {
        this.lyrics = lyrics;

        // 更新歌词行列表
        if (lyrics != null) {
            lrcLines = lyrics.getLines();
        } else {
            lrcLines = new ArrayList<>();
        }

        // 重置状态
        currentLine = 0;
        offset = 0;
        userScrolling = false;

        // 重绘
        invalidate();
    }

    /**
     * 更新当前播放的时间，用于同步歌词
     */
    public void updateTime(long timeMs) {
        if (userScrolling || lyrics == null || lrcLines.isEmpty()) {
            return;
        }

        // 查找当前时间对应的歌词行索引
        int line = lyrics.getLineIndexByTime(timeMs);

        // 如果行号变化，则更新
        if (line != currentLine) {
            currentLine = line;
            scrollToCurrentLine(true);
        }
    }

    /**
     * 设置当前歌词行
     */
    public void setCurrentLine(int line) {
        if (line < 0 || line >= lrcLines.size()) {
            return;
        }

        currentLine = line;
        scrollToCurrentLine(true);
    }

    /**
     * 设置是否允许用户滑动
     */
    public void setEnableUserScroll(boolean enable) {
        this.enableUserScroll = enable;
    }

    /**
     * 设置动画持续时间
     */
    public void setAnimationDuration(long duration) {
        this.animationDuration = duration;
    }

    /**
     * 设置字体大小
     */
    public void setTextSize(float normalSize, float highlightSize) {
        this.normalTextSize = normalSize;
        this.highlightTextSize = highlightSize;

        normalPaint.setTextSize(normalSize);
        highlightPaint.setTextSize(highlightSize);

        invalidate();
    }

    /**
     * 设置字体颜色
     */
    public void setTextColor(int normalColor, int highlightColor) {
        this.normalTextColor = normalColor;
        this.highlightTextColor = highlightColor;

        normalPaint.setColor(normalColor);
        highlightPaint.setColor(highlightColor);

        invalidate();
    }

    /**
     * 设置字体
     */
    public void setTypeface(Typeface typeface) {
        this.typeface = typeface;

        normalPaint.setTypeface(typeface);
        highlightPaint.setTypeface(Typeface.create(typeface, Typeface.BOLD));

        invalidate();
    }

    /**
     * 设置行间距
     */
    public void setLineSpacing(float lineSpacing) {
        this.lineSpacing = lineSpacing;
        invalidate();
    }

    /**
     * 设置空歌词提示文本
     */
    public void setEmptyLrcText(String emptyLrcText) {
        this.emptyLrcText = emptyLrcText;
        invalidate();
    }

    /**
     * 设置监听器
     */
    public void setLrcViewListener(LrcViewListener listener) {
        this.lrcViewListener = listener;
    }

    /**
     * dp转px
     */
    private float dp2px(Context context, float dpVal) {
        float scale = context.getResources().getDisplayMetrics().density;
        return dpVal * scale;
    }

    /**
     * sp转px
     */
    private float sp2px(Context context, float spVal) {
        float scale = context.getResources().getDisplayMetrics().scaledDensity;
        return spVal * scale;
    }

    /**
     * 歌词视图监听接口
     */
    public interface LrcViewListener {
        void onLrcViewClick();

        void onLrcLineTap(int line, LyricLine lrcLine);
    }
}
