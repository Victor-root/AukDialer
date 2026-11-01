package auk.dialer.vroot.controller;

import android.os.ParcelFileDescriptor;

interface IPrivilegedRecorder {

    // Transaction code Shizuku calls to stop the user service.
    void destroy() = 16777114;

    // Starts capturing from the given MediaRecorder.AudioSource into output, as an AAC m4a file.
    boolean start(in ParcelFileDescriptor output, int audioSource) = 1;

    // The highest sample level since the previous call, like MediaRecorder.getMaxAmplitude().
    int maxAmplitude() = 2;

    // Ends the capture. False when nothing playable was written.
    boolean stop() = 3;
}
