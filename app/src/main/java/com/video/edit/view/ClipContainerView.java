package com.video.edit.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.video.edit.demo.R;
import com.video.edit.ext.FileUtils;
import com.video.edit.ext.SdkConfig;

import java.util.ArrayList;
import java.util.List;

public class ClipContainerView extends FrameLayout {

    private static final String TAG = "ClipContainerView";
    private static final int DELTA = 6;
    private static final int SHADOW_DELTA = 0;

    private RecyclerView mRecyclerView;
    private Paint mShadowPaint;
    private int mFramebarHeight = 0;
    private int mRecyclerViewPadding = 0;
    private int mItemCount = 0;
    private int mItemWidth = 0;
    private int mTotalItemsWidth = 0;
    private int mItemCountInFrame = 10;
    private int mDuration = 0;
    private int mFrameWidth = 900;
    private int mRealProgressBarWidth = 6;
    private int mMinProgressBarX = 120;
    private int mMaxProgressBarX = 900;

    private Paint mPaint = null;
    private Paint mProgressPaint = null;
    private View mLeftFrameBar;
    private View mRightFrameBar;
    private View mPlayProgressBar;
    private View mLeftFrameBarIv;
    private View mRightFrameBarIv;
    public List<String> mList = new ArrayList<>();
    private float mStartMillSec = 0f;
    private float mEndMillSec = 0f;
    private int mLeftShadowStart = 0;
    private int mLeftShadowEnd = 0;
    private int mRightShadowStart = 0;
    private int mRightShadowEnd = 0;
    private int mLeftFrameLeft = 0;
    private int mRightFrameLeft = 0;
    private int mProgressStart = 0;
    private int mProgressWidth = 10;

    private int mFramebarPadding = 80;
    private int mFramebarImageWidth = 42;

    private float minDistance = 120f;
    private long mMillSecInFrame = SdkConfig.maxSelection;

    public ResolveCallback mCallback;
    private MyAdapter mAdapter;

    public ClipContainerView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public ClipContainerView(@NonNull Context context, AttributeSet set) {
        super(context, set);
        init(context);
    }

    public ClipContainerView(@NonNull Context context, AttributeSet set, int style) {
        super(context, set, style);
        init(context);
    }

    private void init(Context context) {
        setWillNotDraw(false);
        mShadowPaint = new Paint();
        mShadowPaint.setColor(getContext().getResources().getColor(R.color.clip_shadow_color));
        mShadowPaint.setStyle(Paint.Style.FILL);

        mPaint = new Paint();
        mPaint.setColor(getContext().getResources().getColor(R.color.frame_bar_color));
        mPaint.setStyle(Paint.Style.FILL);

        mProgressPaint = new Paint();
        mProgressPaint.setColor(getContext().getResources().getColor(R.color.video_clip_progress_color));
        mProgressPaint.setStyle(Paint.Style.FILL);

        minDistance = getContext().getResources().getDimensionPixelSize(R.dimen.video_clip_min_length);
        mProgressWidth = getContext().getResources().getDimensionPixelSize(R.dimen.video_clip_progressbar_width);

        mRecyclerViewPadding = getContext().getResources().getDimensionPixelSize(R.dimen.clip_recyclerview_paddingleft);
        mFramebarHeight = getContext().getResources().getDimensionPixelSize(R.dimen.clip_frame_bar_height);
        mItemWidth = getContext().getResources().getDimensionPixelSize(R.dimen.clip_frame_item_width);
        mFramebarPadding = getContext().getResources().getDimensionPixelSize(R.dimen.clip_frame_bar_width_outer) - getContext().getResources().getDimensionPixelSize(R.dimen.clip_frame_bar_width);
        mFramebarImageWidth = getContext().getResources().getDimensionPixelSize(R.dimen.clip_frame_bar_width);
        mRealProgressBarWidth = getContext().getResources().getDimensionPixelSize(R.dimen.clip_frame_progressbar_width);
        mMinProgressBarX = getContext().getResources().getDimensionPixelSize(R.dimen.clip_recyclerview_paddingleft);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mRecyclerView = findViewById(R.id.recyclerview);

        mLeftFrameBar = findViewById(R.id.frame_left);
        mRightFrameBar = findViewById(R.id.frame_right);
        mPlayProgressBar = findViewById(R.id.clip_play_progress_ll);
        mLeftFrameBarIv = findViewById(R.id.frame_left_iv);
        mRightFrameBarIv = findViewById(R.id.frame_right_iv);

        mLeftFrameBar.setOnTouchListener(mLeftTouchListener);
        mRightFrameBar.setOnTouchListener(mRightTouchListener);
        mPlayProgressBar.setOnTouchListener(mProgressBarTouchListener);

        mAdapter = new MyAdapter();
        mRecyclerView.setAdapter(mAdapter);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        mRecyclerView.setLayoutManager(layoutManager);

        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dx != 0) {
                    updateSelection();
                }
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus && mRightFrameLeft == 0f) {
            initUiValues();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        // 绘制阴影
        if (mLeftShadowEnd > mLeftShadowStart) {
            canvas.drawRect(new Rect(mLeftShadowStart, 0, mLeftShadowEnd+2, getHeight()), mShadowPaint);
        }

        if (mRightShadowEnd > mRightShadowStart) {
            canvas.drawRect(new Rect(mRightShadowStart-2, 0, mRightShadowEnd, getHeight()), mShadowPaint);
        }


        // 绘制上下边框矩形
        canvas.drawRect(new Rect((mLeftFrameLeft + mLeftFrameBar.getWidth()),
                0, (mRightFrameLeft + DELTA), mFramebarHeight), mPaint);
        canvas.drawRect(new Rect((mLeftFrameLeft + mLeftFrameBar.getWidth()),
                getHeight() - mFramebarHeight, (mRightFrameLeft + DELTA), getHeight()),mPaint);
    }

    private void updateSelection() {
        onFrameMoved(true);
    }

    public void updateInfo(long mediaDutaion, int itemCount) {
        this.mItemCount = itemCount;
        this.mDuration = (int)mediaDutaion;

        mPlayProgressBar.setVisibility(VISIBLE);

        if (mRightFrameLeft == 0f) {
            initUiValues();
        }

        mFrameWidth = getWidth() - mLeftFrameBar.getWidth() - mRightFrameBar.getWidth();
        mItemWidth = (int)(mFrameWidth * 1f / mItemCountInFrame);
        mTotalItemsWidth = itemCount * mItemWidth;

        long selection = Math.min(SdkConfig.maxSelection, mediaDutaion);

        minDistance = mFrameWidth * (SdkConfig.minSelection * 1f / selection);

        mMillSecInFrame = Math.min(mediaDutaion, SdkConfig.maxSelection);

        mAdapter.notifyDataSetChanged();

        adjustProgressBar(mPlayProgressBar, mPlayProgressBar.getTranslationX());

        if (mediaDutaion > SdkConfig.maxSelection) {
            mRightShadowStart = (mRightFrameLeft + mFramebarImageWidth) - SHADOW_DELTA;
            mRightShadowEnd = getFrameFixLeftX() + mTotalItemsWidth;
            if (mRightShadowEnd > getWidth()) {
                mRightShadowEnd = getWidth();
            }
        }
        updateFramebarBg();
        invalidate();
    }

    private void initUiValues() {
        mRightFrameLeft = (getWidth() - mRightFrameBar.getWidth());
        mProgressStart = (mLeftFrameLeft + mLeftFrameBar.getWidth());

        mMaxProgressBarX = getWidth() - getResources().getDimensionPixelSize(R.dimen.clip_recyclerview_paddingleft);
        mFrameWidth = getWidth() - mLeftFrameBar.getWidth() - mRightFrameBar.getWidth();
    }

    public void setProgress(long currentPosition, long frozonTime) {
        if (mDuration <= SdkConfig.maxSelection) {
            float ratio = currentPosition * 1f / mDuration;
            mProgressStart = (int)(getFrameFixLeftX() + ratio * mFrameWidth);
        } else {
            float millsecs = currentPosition - mStartMillSec;
            if (millsecs < 0) {
                millsecs = 0f;
            }
            if (millsecs > SdkConfig.maxSelection) {
                millsecs = SdkConfig.maxSelection;
            }
            float ratio = millsecs * 1f / SdkConfig.maxSelection;
            mProgressStart = (int)(getCutLeftX() + ratio * mFrameWidth);
        }

        if (mProgressStart < getCutLeftX()) {
            mProgressStart = getCutLeftX();
        }
        if (mProgressStart > getCutRightX()) {
            mProgressStart = getCutRightX();
        }
        adjustProgressBar(mPlayProgressBar, (float) mProgressStart);

        invalidate();
    }

    public void addThumbnail(int index, String bitmapPath) {
        mList.set(index, bitmapPath);
        mAdapter.notifyDataSetChanged();
    }

    public interface ResolveCallback {
        void onSelectionChange(int totalCount, long startMillSec, long endMillSec, boolean finished);
        void onPreviewChange(long startMillSec, boolean finished);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        public TextView mTitle;
        public ImageView mImage;

        public ViewHolder(View itemView) {
            super(itemView);
            mTitle = itemView.findViewById(R.id.title);
            mImage = itemView.findViewById(R.id.image);
        }
    }

    class MyAdapter extends RecyclerView.Adapter<ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(getContext()).inflate(R.layout.item_layout, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
            layoutParams.width = mItemWidth;
            holder.itemView.setLayoutParams(layoutParams);
            if (!TextUtils.isEmpty(mList.get(position))) {
                holder.mImage.setImageBitmap(FileUtils.decodeFile(mList.get(position)));
            } else {
                holder.mImage.setImageResource(R.drawable.ic_launcher_background);
            }
        }

        @Override
        public int getItemCount() {
            return mList.size();
        }
    }

    private View.OnTouchListener mLeftTouchListener = new View.OnTouchListener() {
        private float downX = 0f;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            android.util.Log.e("litianpeng", "onTouch action="+event.getAction());
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
//                    break;
                case MotionEvent.ACTION_MOVE:
                    float xDistance = event.getX() - downX;
                    if (xDistance != 0f) {
                        float newTransX = v.getTranslationX() + xDistance;
                        if (newTransX < 0) {
                            newTransX = 0f;
                        }
                        if (newTransX + v.getWidth() > mRightFrameLeft - minDistance) {
                            newTransX = mRightFrameLeft - minDistance - v.getWidth();
                        }
                        v.setTranslationX(newTransX);
                        mLeftFrameLeft = (int)(newTransX + mLeftFrameBar.getLeft());
                        mProgressStart = mLeftFrameLeft + v.getWidth();
                        onFrameMoved(false);
                        invalidate();
                    }
//                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    onFrameMoved(true);
//                    break;
            }
            return false;
        }
    };

    private View.OnTouchListener mRightTouchListener = new View.OnTouchListener() {
        private float downX = 0f;
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    break;
                case MotionEvent.ACTION_MOVE:
                    float xDistance = event.getX() - downX;
                    if (xDistance != 0f) {
                        float newTransX = v.getTranslationX() + xDistance;
                        if (newTransX < 0) {
                            newTransX = 0f;
                        }
                        if (getWidth() - v.getWidth() + newTransX < mLeftFrameLeft + mLeftFrameBar.getWidth() + minDistance) {
                            newTransX = -(getWidth() - (mLeftFrameLeft + mLeftFrameBar.getWidth() + minDistance)) - v.getWidth();
                        }
                        v.setTranslationX(newTransX);
                        mRightFrameLeft = (int)(newTransX + v.getLeft());
                        onFrameMoved(false);
                        invalidate();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    onFrameMoved(true);
                    break;
            }
            return false;
        }
    };

    private View.OnTouchListener mProgressBarTouchListener = new View.OnTouchListener() {
        private float downX = 0f;
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    break;
                case MotionEvent.ACTION_MOVE:
                    float xDistance = event.getX() - downX;
                    if (xDistance != 0f) {
                        float newTransX = v.getTranslationX() + xDistance;
                        adjustProgressBar(v, newTransX);
                        onPreviewChange(false);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    onPreviewChange(true);
                    break;
            }
            return false;
        }
    };

    private void onFrameMoved(boolean finished) {
        adjustProgressBar(mPlayProgressBar, mPlayProgressBar.getTranslationX());

        mStartMillSec = (getCutLeftX() - getFrameFixLeftX()) * 1f / mFrameWidth * mMillSecInFrame;
        mEndMillSec = (getCutRightX() - getFrameFixLeftX()) * 1f / mFrameWidth * mMillSecInFrame;

        if (mDuration <= SdkConfig.maxSelection) {

            mLeftShadowStart = getFrameFixLeftX();
            if (mLeftShadowStart < 0) {
                mLeftShadowStart = 0;
            }
            mLeftShadowEnd = mLeftFrameLeft + mFramebarPadding + SHADOW_DELTA;

            mRightShadowStart = mRightFrameLeft + mFramebarImageWidth + SHADOW_DELTA;
            mRightShadowEnd = getFrameFixLeftX() + mTotalItemsWidth;
            if (mRightShadowEnd > getWidth()) {
                mRightShadowEnd = getWidth();
            }
            updateFramebarBg();
            Log.d(TAG, "onFrameMoved: rightShadowStart:$rightShadowStart, rightShadowEnd:$rightShadowEnd");

            if (mCallback != null) {
                mCallback.onSelectionChange(mItemCount, (long)mStartMillSec, (long)mEndMillSec, finished);
            }
            invalidate();
            return;
        }
        int[] res = getScollXDistance();
        Log.d(TAG, "onFrameMoved: position:$position, itemLeft:$itemLeft,  scrollX:$scrollX");

        int scrollXTotal = res[2] + getFrameFixLeftX();

        mLeftShadowStart = getFrameFixLeftX() - scrollXTotal;
        if (mLeftShadowStart < 0) {
            mLeftShadowStart = 0;
        }

        mLeftShadowEnd = mLeftFrameLeft + mFramebarPadding + SHADOW_DELTA;

        mRightShadowStart = mRightFrameLeft + mFramebarImageWidth - SHADOW_DELTA;
        mRightShadowEnd = getFrameFixLeftX() + mTotalItemsWidth - scrollXTotal;
        if (mRightShadowEnd > getWidth()) {
            mRightShadowEnd = getWidth();
        }

        updateFramebarBg();

        Log.d(TAG, "onFrameMoved: rightShadowStart:$rightShadowStart, rightShadowEnd:$rightShadowEnd");

        float scrollMillSec = scrollXTotal * 1f / mTotalItemsWidth * mDuration;

        mStartMillSec += scrollMillSec;
        mEndMillSec += scrollMillSec;

        if (mCallback != null) {
            mCallback.onSelectionChange(mItemCount, (long) mStartMillSec, (long) mEndMillSec, finished);
        }
        invalidate();
    }

    public int[] getScollXDistance() {
        LinearLayoutManager layoutManager = (LinearLayoutManager)mRecyclerView.getLayoutManager();
        int position = layoutManager.findFirstVisibleItemPosition();
        View firstVisiableChildView = layoutManager.findViewByPosition(position);
        if (firstVisiableChildView != null) {
            int itemWidth = getWidth();
            return new int[]{position, -getLeft(), position * itemWidth - getLeft()};
        }
        return new int[]{position, 0, 0};
    }

    private void updateFramebarBg(){
        if (mRightShadowEnd > mRightShadowStart) {
            mRightFrameBarIv.setBackgroundResource(R.color.clip_shadow_color);
        } else {
            mRightFrameBarIv.setBackgroundColor(Color.TRANSPARENT);
        }

        if (mLeftShadowEnd > mLeftShadowStart) {
            mLeftFrameBarIv.setBackgroundResource(R.color.clip_shadow_color);
        } else {
            mLeftFrameBarIv.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private void onPreviewChange(boolean finished) {
        float previewPosition = mPlayProgressBar.getTranslationX();

        float previewMillSec = (previewPosition - getFrameFixLeftX()) * 1f / mFrameWidth * mMillSecInFrame;

        if (mDuration > SdkConfig.maxSelection) {

            int[] res = getScollXDistance();

            int scrollXTotal = res[2] + getFrameFixLeftX();
            float scrollMillSec = scrollXTotal * 1f / mTotalItemsWidth * mDuration;

            previewMillSec += scrollMillSec;
        }

        if (mCallback != null) {
            mCallback.onPreviewChange((long)previewMillSec, finished);
        }
        invalidate();
    }

    private void adjustProgressBar(View v, float transX) {
        if (transX + mRealProgressBarWidth > mRightFrameLeft) {
            transX = mRightFrameLeft - mRealProgressBarWidth;
        }

        if (transX < getCutLeftX()) {
            transX = getCutLeftX();
        }

        if (transX < mMinProgressBarX) {
            transX = mMinProgressBarX;
        }
        v.setTranslationX(transX);
    }

    public void initRecyclerList(int count) {
        for (int i = 0; i < count; i++) {
            mList.add(null);
        }
    }

    private int getCutLeftX() {
        return mLeftFrameLeft + mLeftFrameBar.getWidth();
    }

    private int getCutRightX() {
        return mRightFrameLeft;
    }

    private int getFrameFixLeftX() {
        return mLeftFrameBar.getWidth();
    }

}
