package com.video.edit.view;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.video.edit.demo.R;
import com.video.edit.ext.FileUtils;

import java.util.List;

public class BaseThumbnailAdapter extends RecyclerView.Adapter<ViewHolder> {

    private List<String> mList;
    private int mWidth;

    public BaseThumbnailAdapter(List<String> list, int itemWidth) {
        mList = list;
        mWidth = itemWidth;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_layout, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
        layoutParams.width = mWidth;
        holder.itemView.setLayoutParams(layoutParams);
        if (!TextUtils.isEmpty(mList.get(position))) {
            holder.imageView.setImageBitmap(FileUtils.decodeFile(mList.get(position)));
        } else {
            holder.imageView.setImageResource(R.drawable.ic_launcher_background);
        }
    }
}

class ViewHolder extends RecyclerView.ViewHolder {
    TextView titleView;
    ImageView imageView;

    public ViewHolder(View itemView) {
        super(itemView);
        titleView = itemView.findViewById(R.id.title);
        imageView = itemView.findViewById(R.id.image);
    }
}
