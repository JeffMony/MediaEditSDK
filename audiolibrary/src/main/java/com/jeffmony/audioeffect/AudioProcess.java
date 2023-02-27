package com.jeffmony.audioeffect;

////////////////////////////////////////////////////////////////////////////////
///
/// Example class that invokes native SoundTouch routines through the JNI
/// interface.
///
/// Author        : Copyright (c) Olli Parviainen
/// Author e-mail : oparviai 'at' iki.fi
/// WWW           : http://www.surina.net
///
////////////////////////////////////////////////////////////////////////////////

public final class AudioProcess
{
    // Load the native library upon startup
    static {
        System.loadLibrary("audio_effect");
    }

    // Native interface function that returns SoundTouch version string.
    // This invokes the native c++ routine defined in "soundtouch-jni.cpp".
    public native static String getVersionString();

    private native void setTempo(long handle, float tempo);

    private native void setPitchSemiTones(long handle, float pitch);

    private native void setSpeed(long handle, float speed);

    private native int processFile(long handle, String inputFile, String outputFile);

    public native static String getErrorString();

    private native static long newInstance();

    private native void deleteInstance(long handle);

    private long mId = 0;

    public AudioProcess() {
        mId = newInstance();
    }


    public void close() {
        deleteInstance(mId);
        mId = 0;
    }


    public void setTempo(float tempo) {
        setTempo(mId, tempo);
    }


    public void setPitchSemiTones(float pitch) {
        setPitchSemiTones(mId, pitch);
    }


    public void setSpeed(float speed) {
        setSpeed(mId, speed);
    }


    public int processFile(String inputFile, String outputFile) {
        return processFile(mId, inputFile, outputFile);
    }

}

