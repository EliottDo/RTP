package com.dev.rptsoc

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodec.CodecException
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaFormat
import android.os.*
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.*

@RequiresApi(Build.VERSION_CODES.M)
class NetworkedVirtualDisplay internal constructor(context: Context, width: Int, height: Int, dpi: Int) {
    private val mUniqueId = UUID.randomUUID().toString()
    private val mDisplayManager: DisplayManager = context.getSystemService(DisplayManager::class.java)
    private val mWidth: Int = width
    private val mHeight: Int = height
    private val mDpi: Int = dpi
    private var mVirtualDisplay: VirtualDisplay? = null
    private var mVideoEncoder: MediaCodec? = null
    private val mThread = HandlerThread("NetworkThread")
    private var mHandler: Handler? = null
    private var mServerSocket: Socket? = null
    private var mOutputStream: OutputStream? = null
    private var mBuffer: ByteArray? = null
    private var mLastFrameLength = 0
    private val mCounter = DebugCounter()


    fun start(): String {
        Log.d(TAG, "CALL START")
        check(!mThread.isAlive) { "Already started" }
        mThread.start()
        mHandler = NetworkThreadHandler(mThread.looper)
        (mHandler as NetworkThreadHandler).sendMessage(Message.obtain(mHandler, MSG_START))
        Log.d(TAG, "CALL START THEN msg = ${Message.obtain(mHandler, MSG_START).data.toString()}")
        return displayName
    }

    fun release() {
        stopCasting()
        if (mVirtualDisplay != null) {
            mVirtualDisplay?.release()
            mVirtualDisplay = null
        }
        mThread.quit()
    }

    private val displayName: String get() = "Cluster-$mUniqueId"

    private fun createVirtualDisplay(): VirtualDisplay {
        Log.i(TAG, "createVirtualDisplay " + mWidth + "x" + mHeight + "@" + mDpi)
        return mDisplayManager.createVirtualDisplay(displayName, mWidth, mHeight, mDpi, null, 0 /* flags */, null, null)
    }

    private fun onVirtualDisplayReady(display: Display) {
        Log.i(TAG, "onVirtualDisplayReady, display: $display")
    }

    private fun startCasting(handler: Handler) {
        Log.i(TAG, "Start casting...")
        mVideoEncoder = createVideoStream(handler)
        if (mVirtualDisplay == null) {
            mVirtualDisplay = createVirtualDisplay()
        }
        mVirtualDisplay?.surface = mVideoEncoder?.createInputSurface()
        mVideoEncoder?.start()
        Log.i(TAG, "Video encoder started")
    }

    private fun createVideoStream(handler: Handler): MediaCodec? {
        val encoder: MediaCodec
        try {
            encoder = MediaCodec.createEncoderByType(MEDIA_FORMAT_MIMETYPE)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to create video encoder for $MEDIA_FORMAT_MIMETYPE", e)
            return null
        }
        encoder.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                Log.i(TAG, "onInputBufferAvailable, index: $index")
            }

            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                Log.i(TAG, "onOutputBufferAvailable, index: $index")
                mCounter.outputBuffers++
                doOutputBufferAvailable(index, info)
            }

            override fun onError(codec: MediaCodec, e: CodecException) {
                Log.e(TAG, "onError, codec: $codec", e)
                mCounter.bufferErrors++
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                Log.i(TAG, "onOutputFormatChanged, codec: $codec, format: $format")
            }
        }, handler)
        configureVideoEncoder(encoder, mWidth, mHeight)
        return encoder
    }

    private fun doOutputBufferAvailable(index: Int, info: MediaCodec.BufferInfo) {
        mHandler?.removeMessages(MSG_RESUBMIT_FRAME)

        val encodedData = mVideoEncoder?.getOutputBuffer(index)
            ?: throw RuntimeException("couldn't fetch buffer at index $index")

        if (info.size != 0) {
            encodedData.position(info.offset)
            encodedData.limit(info.offset + info.size)
            mLastFrameLength = encodedData.remaining()
            if (mBuffer == null || mBuffer?.size!! < mLastFrameLength) {
                Log.i(TAG, "Allocating new buffer: $mLastFrameLength")
                mBuffer = ByteArray(mLastFrameLength)
            }
            encodedData[mBuffer, 0, mLastFrameLength]
            mVideoEncoder?.releaseOutputBuffer(index, false)
            sendFrame(mBuffer, mLastFrameLength)

            // If nothing happens in Virtual Display we won't receive new frames. If we won't keep
            // sending frames it could be a problem for the receiver because it needs certain
            // number of frames in order to start decoding.
            scheduleResendingLastFrame(1000 / FPS.toLong())
        } else {
            Log.e(TAG, "Skipping empty buffer")
            mVideoEncoder?.releaseOutputBuffer(index, false)
        }
    }

    private fun scheduleResendingLastFrame(delayMs: Long) {
        val msg = mHandler?.obtainMessage(MSG_RESUBMIT_FRAME)
        msg?.let { mHandler?.sendMessageDelayed(it, delayMs) }
    }

    private fun sendFrame(buf: ByteArray?, len: Int) {
        try {
            mOutputStream?.write(buf, 0, len)
            Log.i(TAG, "Bytes written: $len")
        } catch (e: IOException) {
            mCounter.clientsDisconnected++
            mOutputStream = null
            Log.e(TAG, "Failed to write data to socket, restart casting", e)
            restart()
        }
    }

    private fun stopCasting() {
        Log.i(TAG, "Stopping casting...")
        if (mServerSocket != null) {
            try {
                mServerSocket?.close()
            } catch (e: IOException) {
                Log.w(TAG, "Failed to close server socket, ignoring", e)
            }
            mServerSocket = null
        }
        if (mVirtualDisplay != null) {
            // We do not want to destroy virtual display (as it will also destroy all the
            // activities on that display, instead we will turn off the display by setting
            // a null surface.
            val surface = mVirtualDisplay?.surface
            surface?.release()
            mVirtualDisplay?.surface = null
        }
        if (mVideoEncoder != null) {
            // Releasing encoder as stop/start didn't work well (couldn't create or reuse input
            // surface).
            mVideoEncoder?.stop()
            mVideoEncoder?.release()
            mVideoEncoder = null
        }
        Log.i(TAG, "Casting stopped")
    }

    @Synchronized
    private fun restart() {
        // This method could be called from different threads when receiver has disconnected.
        if (mHandler?.hasMessages(MSG_START)!!) return
        mHandler?.sendMessage(Message.obtain(mHandler, MSG_STOP))
        mHandler?.sendMessage(Message.obtain(mHandler, MSG_START))
    }

    private inner class NetworkThreadHandler internal constructor(looper: Looper?) :
        Handler(looper!!) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_START -> {
                    if (mServerSocket == null) {
                        Log.i(TAG, "openServerSocket")
                        mServerSocket = openServerSocket()
                        Log.i(TAG, "Server socket opened")
                    }

                    mOutputStream = waitForReceiver(mServerSocket)
                    if (mOutputStream == null) {
                        Log.d(TAG, "----- " + Message.obtain(this, MSG_START).data)
                        sendMessage(Message.obtain(this, MSG_START))
                        return
                    }
                    mCounter.clientsConnected++
                    startCasting(this)
                }
                MSG_STOP -> stopCasting()
                MSG_RESUBMIT_FRAME -> {
                    if (mServerSocket != null && mOutputStream != null) {
                        Log.i(TAG, "Resending the last frame again. Buffer: $mLastFrameLength")
                        sendFrame(mBuffer, mLastFrameLength)
                    }
                    // We will keep sending last frame every second as a heartbeat.
                    scheduleResendingLastFrame(1000L)
                }
            }
        }
    }

    private fun waitForReceiver(sockettt: Socket?): OutputStream? {
        return try {
            Log.i(TAG, "Listening for incoming connections on port: $PORT")
            Log.i(TAG, "Receiver connected: $sockettt")
            sockettt?.getInputStream()?.let {
                Log.d(TAG, "Eliottttt  $it")
                listenReceiverDisconnected(it)
            }

            Log.d(TAG, "leiot + ${sockettt?.getOutputStream()}")
            sockettt?.getOutputStream()

        } catch (e: IOException) {
            Log.e(TAG, "Failed to accept connection")
            null
        }
    }

    private fun listenReceiverDisconnected(inputStream: InputStream) {
        Thread(Runnable {
            try {
                if (inputStream.read() == -1) throw IOException()
            } catch (e: IOException) {
                Log.w(TAG, "Receiver has disconnected", e)
            }
            //restart()
        }).start()
    }

    private class DebugCounter {
        var outputBuffers: Long = 0
        var bufferErrors: Long = 0
        var clientsConnected: Long = 0
        var clientsDisconnected: Long = 0
        override fun toString(): String {
            return (javaClass.simpleName + "{"
                    + "outputBuffers=" + outputBuffers
                    + ", bufferErrors=" + bufferErrors
                    + ", clientsConnected=" + clientsConnected
                    + ", clientsDisconnected= " + clientsDisconnected
                    + "}")
        }
    }

    companion object {
        private val TAG = "NETWORK_VIRTUAL_DISPLAY"
        private const val PORT = 5555
        private const val FPS = 25
        private const val BITRATE = 6144000
        private const val MEDIA_FORMAT_MIMETYPE = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val MSG_START = 0
        private const val MSG_STOP = 1
        private const val MSG_RESUBMIT_FRAME = 2

        private fun configureVideoEncoder(codec: MediaCodec, width: Int, height: Int) {
            val format = MediaFormat.createVideoFormat(MEDIA_FORMAT_MIMETYPE, width, height)
            format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            format.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            format.setInteger(MediaFormat.KEY_CAPTURE_RATE, FPS)
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
            format.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, 1f) // 1 second between I-frames
            format.setInteger(MediaFormat.KEY_LEVEL, CodecProfileLevel.AVCLevel31)
            format.setInteger(MediaFormat.KEY_PROFILE, CodecProfileLevel.AVCProfileBaseline)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }

        private fun openServerSocket(): Socket? {
            return try {
                Log.d(TAG,  "START openServerSocket")
                Socket("192.168.98.117", PORT)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to create socket", e)
                throw RuntimeException(e)
            }
        }
    }

    init {
        val displayListener: DisplayManager.DisplayListener = object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(i: Int) {
                    val display = mDisplayManager.getDisplay(i)
                    if (display != null && displayName == display.name) {
                        onVirtualDisplayReady(display)
                    }
                }

                override fun onDisplayRemoved(i: Int) {}
                override fun onDisplayChanged(i: Int) {}
            }
        mDisplayManager.registerDisplayListener(displayListener, Handler())
    }
}
