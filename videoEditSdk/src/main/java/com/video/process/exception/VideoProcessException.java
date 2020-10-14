package com.video.process.exception;

public class VideoProcessException {

    public static final int ERR_INPUT_OR_OUTPUT_PATH = 100;
    public static final String ERR_STR_INPUT_OR_OUTPUT_PATH = "Input path or output path is null";

    public static final int ERR_EXTRACTOR_SET_DATASOURCE = 101;
    public static final String ERR_STR_EXTRACTOR_SET_DATASOURCE = "MediaExtractor setDataSource failed";

    public static final int ERR_INPUT_VIDEO_NO_DURATION = 102;
    public static final String ERR_STR_INPUT_VIDEO_NO_DURATION = "Input video has no duration";

    public static final int ERR_NO_VIDEO_TRACK = 103;
    public static final String ERR_STR_NO_VIDEO_TRACK = "Input video has no vide track";

    public static final int ERR_NO_AUDIO_TRACK = 104;
    public static final String ERR_STR_NO_AUDIO_TRACK = "Input video has no audio track";

    public static final int ERR_MEDIAMUXER_CREATE_FAILED = 105;
    public static final String ERR_STR_MEDIAMUXER_CREATE_FAILED = "Create MediaMuxer failed";


    private String mErrStr;
    private int mErrorCode;

    public VideoProcessException(String errStr, int error) {
        mErrStr = errStr;
        mErrorCode = error;
    }

    public int getErrorCode() { return mErrorCode; }

    public String getExceptionStr() { return mErrStr; }

    public String toString() {
        return "VideoProcessException[error="+mErrorCode+", Exception="+mErrStr+"]";
    }
}
