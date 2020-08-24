package com.video.edit.ext;

import com.video.edit.demo.R;
import com.video.edit.view.BottomDialogFragment;

import java.util.Arrays;
import java.util.List;

public class MusicConfigs {

    public static List<BottomDialogFragment.Option> createMusicOptions() {
        return Arrays.asList(new BottomDialogFragment.Option[] {
                new BottomDialogFragment.Option(R.drawable.filter_daqiang, "不选", 0),
                new BottomDialogFragment.Option(R.drawable.filter_daqiang, "静音", 1),
                new BottomDialogFragment.Option(R.drawable.filter_daqiang, "故乡", 2),
                new BottomDialogFragment.Option(R.drawable.filter_daqiang, "婚礼", 3),
                new BottomDialogFragment.Option(R.drawable.filter_daqiang, "天空", 4)});
    }

    public static String getMusicByType(String option) {
        switch (option) {
            case "故乡":
                return "country.aac";
            case "天空":
                return "sky_music.mp3";
            case "婚礼":
                return "marriage.mp3";
            default:
                return "None";
        }
    }
}
