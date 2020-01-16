package com.stemaker.ampachealbumplayer.player

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.stemaker.ampachealbumplayer.*
import com.stemaker.ampachealbumplayer.musicdb.MusicDb
import kotlinx.android.parcel.Parcelize

private const val TAG = "AmpachePlayerService"
const val PLAYER_STATUS_MSG = "com.stemaker.ampachealbumplayer.playerStatusMsg"

@Parcelize
data class PlaylistElementDesc(
    val albumId: Int = 0,
    val songId: Int = 0,
    val trackIndex: Int = 0,
    val url: String = "",
    val duration: Int = 0): Parcelable

@Parcelize
data class StatusDesc(
    val albumId: Int,
    val trackIndex: Int,
    val songId: Int,
    val url: String,
    val duration: Int,
    val progress: Int,
    val newState: States,
    val oldState: States): Parcelable {
    enum class States {PAUSED, PLAYING, STOPPED, UNDEFINED}
}

@Parcelize
data class Command(val cmd:String): Parcelable

/* addTracksCommand
 * Add a list of tracks to the playlist
 * tracks -> URLs of the tracks to be added
 * back -> false means add to front of playlist (default), true means add to the end of playlist
 */
@Parcelize
data class AddTracksCommand(val playlistElements: Array<PlaylistElementDesc>, val back: Boolean = false): Parcelable

@Parcelize
data class SelectTrackCommand(val track: Int): Parcelable

class AmpachePlayerService: Service(), MediaPlayerCtrl.OnPlaybackCompletionListener {

    private val playlist = Playlist()
    private var serviceLooper: Looper? = null
    private var serviceHandler: AmpachePlayerServiceHandler? = null
    private val binder = PlayerServiceBinder()
    private var state: StatusDesc.States = StatusDesc.States.UNDEFINED

    private inner class AmpachePlayerServiceHandler(looper: Looper) : Handler(looper) {

        override fun handleMessage(msg: Message) {
            val msgData = msg.data as Bundle
            var done = false
            var cnt = 0
            do {
                val cmd = getNextCmd(msgData, cnt)
                when(cmd) {
                    "" -> {done = true}
                    "start" -> handleStartCommand()
                    "play" -> handlePlayCommand()
                    "pause" -> handlePauseCommand()
                    "stop" -> handleStopCommand()
                    "clear" -> handleClearPlaylistCommand()
                    "reqStatus" -> handleGetStatusCommand()
                    "addTracks" -> handleAddTracksCommand(msgData.getParcelable<AddTracksCommand>("data$cnt"))
                    "next" -> handleNextCommand()
                    "prev" -> handlePreviousCommand()
                    "selectTrack" -> handleSelectTrackCommand(msgData.getParcelable<SelectTrackCommand>("data$cnt"))
                }
                cnt++
            } while(!done)
        }

        private fun getNextCmd(msgData: Bundle, cnt: Int): String {
            val cmd = msgData.getParcelable<Command>("cmd$cnt")
            return cmd?.cmd ?: ""
        }
    }

    override fun onCreate() {
        HandlerThread("AmpachePlayerService", Process.THREAD_PRIORITY_BACKGROUND).apply {
            start()
            serviceLooper = looper
            serviceHandler = AmpachePlayerServiceHandler(looper)
        }
        PlayerNotificationHandler.initialize()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, ": OnStartCommand running on thread: ${Process.myTid()}")

        start()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    override fun onCompletion() {
        Log.d(TAG, "onCompletion")
        if(playlist.hasNext()) {
            playlist.next()
            playSong()
        }
        else handleStopCommand()
    }

    /* Methods running in service thread that holds the MediaPlayer */
    private fun playSong() {
        Log.d("AmpachePlayerService", "Requesting MediaPlayer to play ${playlist.current?.url}")
        val current = playlist.current
        if(current != null) {
            MediaPlayerCtrl.playUrl(current.url)
            sendStatus()
        }
    }

    private fun handleStartCommand() {
        Log.d(TAG, ": start() running on thread: ${Process.myTid()}")

        startForeground(1, PlayerNotificationHandler.getIdleNotification())

        MediaPlayerCtrl.setOnCompletionListener(this)
    }

    private fun handleStopCommand() {
        Log.d(TAG, ": handleStopCommand")
        MediaPlayerCtrl.stopPlayback()
        sendStatus()
    }

    private fun handlePlayCommand() {
        Log.d(TAG, ": handlePlayCommand()")
        if(MediaPlayerCtrl.isPaused) {
            MediaPlayerCtrl.resumePlayback()
            sendStatus()
        }
        else if(!playlist.isEmpty) {
            playSong()
        }
    }

    private fun handlePauseCommand() {
        Log.d(TAG, ": handlePauseCommand()")
        MediaPlayerCtrl.pausePlayback()
        sendStatus()
    }

    private fun handleAddTracksCommand(data: AddTracksCommand?) {
        if(data == null) return
        Log.d(TAG, ": handleAddTracksCommand")
        playlist.add(data.playlistElements, false)
    }

    private fun handleClearPlaylistCommand() {
        Log.d(TAG, ": handleClearPlaylistCommand")
        handleStopCommand()
        playlist.clear()
    }

    private fun handleGetStatusCommand() {
        Log.d(TAG, ": handleGetStatusCommand, ${playlist.size}")
        sendStatus()
    }

    private fun handleNextCommand() {
        Log.d(TAG, "handleNextCommand")
        if(playlist.hasNext()) {
            playlist.next()
            playSong()
        }
    }

    private fun handlePreviousCommand() {
        Log.d(TAG, "handlePreviousCommand")
        if(playlist.hasPrevious()) {
            playlist.previous()
            playSong()
        }
    }

    private fun handleSelectTrackCommand(t: SelectTrackCommand?) {
        if(t == null) return
        Log.d(TAG, ": handleSelectTrackCommand ${t.track}")
        if(playlist.hasIndex(t.track)) {
            playlist.select(t.track)
            playSong()
        }
    }

    private fun playerStatus(): StatusDesc.States {
        when {
            MediaPlayerCtrl.isPaused -> return StatusDesc.States.PAUSED
            MediaPlayerCtrl.isPlaying -> return StatusDesc.States.PLAYING
            MediaPlayerCtrl.isStopped -> return StatusDesc.States.STOPPED
            else -> return StatusDesc.States.UNDEFINED
        }
    }

    private fun sendStatus() {
        val current = playlist.current ?: PlaylistElementDesc()
        val newState = playerStatus()
        val desc = StatusDesc(current.albumId, current.trackIndex, current.songId, current.url, current.duration, MediaPlayerCtrl.progress, newState, state)
        state = newState
        val intent = Intent (PLAYER_STATUS_MSG).apply {
            putExtra( "status", desc)
        }
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    /* Interface to send messages/requests to the service */
    inner class PlayerServiceBinder: Binder() {
        fun getService(): AmpachePlayerService = this@AmpachePlayerService
    }

    private fun start() {
        val msg = Message()
        val data = Bundle()
        data.putParcelable("cmd0", Command("start"))
        msg.data = data
        serviceHandler?.sendMessage(msg)
    }

    fun addTracks(tracks: Array<PlaylistElementDesc>, back: Boolean) {
        val msg = Message()
        val data = Bundle()
        data.putParcelable("cmd0", Command("addTracks"))
        data.putParcelable("data0", AddTracksCommand(tracks, back))
        msg.data = data
        serviceHandler?.sendMessage(msg)
    }

    fun clear() {
        val msg = Message()
        val data = Bundle()
        data.putParcelable("cmd0", Command("clear"))
        msg.data = data
        serviceHandler?.sendMessage(msg)
    }

    fun play() {
        val msg = Message()
        val data = Bundle()
        data.putParcelable("cmd0", Command("play"))
        msg.data = data
        serviceHandler?.sendMessage(msg)
    }

    fun pause() {
        val msg = Message()
        val data = Bundle()
        data.putParcelable("cmd0", Command("pause"))
        msg.data = data
        serviceHandler?.sendMessage(msg)
    }

    fun stop() {
        val msg = Message()
        val data = Bundle()
        data.putParcelable("cmd0", Command("stop"))
        msg.data = data
        serviceHandler?.sendMessage(msg)
    }

    fun next() {
        val msg = Message()
        val data = Bundle()
        data.putParcelable("cmd0", Command("next"))
        msg.data = data
        serviceHandler?.sendMessage(msg)
    }

    fun previous() {
        val msg = Message()
        val data = Bundle()
        data.putParcelable("cmd0", Command("prev"))
        msg.data = data
        serviceHandler?.sendMessage(msg)
    }

    fun selectTrack(t: Int) {
        val msg = Message()
        val data = Bundle()
        data.putParcelable("cmd0", Command("selectTrack"))
        data.putParcelable("data0", SelectTrackCommand(t))
        msg.data = data
        serviceHandler?.sendMessage(msg)

    }

    fun requestStatus() {
        val msg = Message()
        val data = Bundle()
        data.putParcelable("cmd0", Command("reqStatus"))
        msg.data = data
        serviceHandler?.sendMessage(msg)
    }
}