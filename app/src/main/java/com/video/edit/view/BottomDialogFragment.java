package com.video.edit.view;

import android.app.Dialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.video.edit.demo.R;
import com.video.edit.ext.PreferenceUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class BottomDialogFragment extends DialogFragment {
    private static final String TAG = "BottomDialogFragment";
    private int mType = 0;
    private String mTitle = null;
    private int mSelectionIndex = 0;
    private List<Option> mOptions = new ArrayList<>();
    private LinearLayout mContainerLayout;

    private SelectionCallback mCallback;

    public static class Option implements Serializable {

        public int mIconResId;
        public String mOptionName;
        public int mIndex;

        public Option(int iconResId, String optionName, int index) {
            mIconResId = iconResId;
            mOptionName = optionName;
            mIndex = index;
        }
    }

    public static BottomDialogFragment getInstance(int type, int selection, String title, List<Option> optionList) {
        BottomDialogFragment dialogFragment = new BottomDialogFragment();
        Bundle bundle = new Bundle();
        bundle.putInt("type", type);
        bundle.putInt("selection", selection);
        bundle.putString("title", title);
        bundle.putSerializable("options", (Serializable) optionList);
        dialogFragment.setArguments(bundle);
        return dialogFragment;
    }

    public interface SelectionCallback {
        void onSelected(int select, Option option);
    }

    public void setSelectionCallback(SelectionCallback callback) {
        mCallback = callback;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(androidx.fragment.app.DialogFragment.STYLE_NORMAL, R.style.recordBeautyDialogStyle);
        Bundle bundle = this.getArguments();
        mType = bundle.getInt("type");
        mSelectionIndex = bundle.getInt("selection");
        mTitle = bundle.getString("title");
        mOptions = (List<Option>)bundle.getSerializable("options");
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = this.getDialog();
        Window window = dialog.getWindow();
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.gravity = Gravity.BOTTOM; //底部
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        window.setWindowAnimations(R.style.dialogAnim);
        window.setAttributes(lp);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        getDialog().requestWindowFeature(Window.FEATURE_NO_TITLE);
        return inflater.inflate(R.layout.bottom_dialog_fragment_layout, null);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
    }

    private void initViews(View rootView) {
        mContainerLayout = rootView.findViewById(R.id.ll_container);
        TextView tv_operate_title = rootView.findViewById(R.id.tv_operate_title);
        tv_operate_title.setText(mTitle);

        for(Option option : mOptions) {
            View itemView = LayoutInflater.from(getContext()).inflate(R.layout.item_record_beauty, null);
            ImageView beautyImageView = itemView.findViewById(R.id.iv_beauty_image);
            beautyImageView.setImageResource(option.mIconResId);
            TextView beautyTextView = itemView.findViewById(R.id.tv_beauty_text);
            beautyTextView.setText(option.mOptionName);
            itemView.setTag(option);
            mContainerLayout.addView(itemView);
            if (option.mIndex == mSelectionIndex) {
                beautyImageView.setVisibility(View.VISIBLE);
            }
            itemView.setOnClickListener(mClickListener);
        }
        ImageView closeView = rootView.findViewById(R.id.iv_close);
        closeView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismissDialog();
            }
        });
    }

    private void dismissDialog() {
        dismiss();
    }

    private View.OnClickListener mClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            ImageView beautyCircleView = v.findViewById(R.id.iv_beauty_circle);
            beautyCircleView.setVisibility(View.VISIBLE);
            Option option = (Option) v.getTag();
            int selection = option.mIndex;
            PreferenceUtils.putInt(getContext(), "filter_selection", selection);
            int childCount = mContainerLayout.getChildCount();
            String name = "";
            for (int i = 0; i < childCount; i++) {
                View view = mContainerLayout.getChildAt(i);
                ImageView imageView = view.findViewById(R.id.iv_beauty_circle);
                if (i == selection) {
                    imageView.setVisibility(View.VISIBLE);
                } else {
                    imageView.setVisibility(View.INVISIBLE);
                }
            }
            mCallback.onSelected(selection, option);
        }
    };
}
