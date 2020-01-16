package com.stemaker.ampachealbumplayer

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaTimestamp
import android.os.SystemClock
import android.util.Log

private const val TAG = "MediaPlayerCtrl"

object MediaPlayerCtrl: MediaPlayer.OnCompletionListener {
    enum class MPStates {
        IDLE, INITIALIZED, PREPARED, PLAYING, PAUSED, STOPPED, END, ERROR
    }

    private var state: MPStates = MPStates.IDLE
    private var mp: MediaPlayer? = null
    private var playbackStartTime: Long = 0
    private var playbackPauseTime: Long = 0
    val isPlaying: Boolean
        get() = state == MPStates.PLAYING
    val isPaused: Boolean
        get() = state == MPStates.PAUSED
    val isStopped: Boolean
        get() = state == MPStates.STOPPED
    val progress: Int
        get() = calcProgress().toInt()
    var currentUrl: String = ""
    var onPlaybackCompletionListener: OnPlaybackCompletionListener? = null

    fun playUrl(url: String) {
        if(mp == null) createPlayer()
        when(state) {
            MPStates.IDLE -> {
                prepare(url)
                start()
            }
            MPStates.INITIALIZED, MPStates.PREPARED, MPStates.STOPPED -> {
                reset()
                prepare(url)
                start()
            }
            MPStates.PLAYING, MPStates.PAUSED -> {
                stop()
                reset()
                prepare(url)
                start()
            }
        }
    }

    fun pausePlayback(autoStop: Boolean = false) {
        if(state == MPStates.PLAYING) {
            pause()
        }
    }

    fun resumePlayback() {
        if(mp == null) createPlayer()
        if(state == MPStates.PAUSED)
            start()
        else if(currentUrl != "")
            playUrl(currentUrl)
    }

    fun stopPlayback() {
        stop()
        mp = null
    }

    fun getDuration(): Int {
        return mp?.duration ?: 0
    }

    fun setOnCompletionListener(listener: MediaPlayerCtrl.OnPlaybackCompletionListener) {
        onPlaybackCompletionListener = listener
    }

    interface OnPlaybackCompletionListener {
        fun onCompletion()
    }

    private fun createPlayer() {
        mp = MediaPlayer().apply {
            if(android.os.Build.VERSION.SDK_INT >= 21) {
                val attr = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                setAudioAttributes(attr)
            }
            setAudioStreamType(AudioManager.STREAM_MUSIC)
        }
        mp?.setOnCompletionListener(this)
        state = MPStates.IDLE
    }

    override fun onCompletion(__mp: MediaPlayer?) {
        Log.d(TAG, "onCompletion")
        stop()
        reset()
        onPlaybackCompletionListener?.onCompletion()
    }

    private fun stop() {
        mp?.stop()
        playbackPauseTime = 0
        state = MPStates.STOPPED
        currentUrl = ""
    }

    private fun reset() {
        mp?.reset()
        playbackPauseTime = 0
        state = MPStates.IDLE
    }

    private fun prepare(playbackUrl: String) {
        currentUrl = playbackUrl
        mp?.setDataSource(playbackUrl)
        mp?.prepare()
        playbackPauseTime = 0
        state = MPStates.PREPARED
    }

    private fun start() {
        mp?.start()
        if(playbackPauseTime != 0.toLong()) {
            val restartTs = SystemClock.elapsedRealtime()
            playbackStartTime += restartTs - playbackPauseTime
            playbackPauseTime = 0
        } else {
            playbackStartTime = SystemClock.elapsedRealtime()
        }
        state = MPStates.PLAYING
    }

    private fun pause() {
        mp?.pause()
        playbackPauseTime = SystemClock.elapsedRealtime()
        state = MPStates.PAUSED
    }

    private fun calcProgress(): Long {
        if(android.os.Build.VERSION.SDK_INT >= 21) {
            val ts: MediaTimestamp = mp?.timestamp ?: return 0
            val ts2 = ts.anchorMediaTimeUs / 1000
            return ts2
        } else {
            when (state) {
                MPStates.IDLE,MPStates.INITIALIZED, MPStates.PREPARED, MPStates.STOPPED -> return 0
                MPStates.PLAYING -> return SystemClock.elapsedRealtime() - playbackStartTime
                MPStates.PAUSED -> return playbackPauseTime - playbackStartTime
                else -> return 0
            }
        }

    }
}