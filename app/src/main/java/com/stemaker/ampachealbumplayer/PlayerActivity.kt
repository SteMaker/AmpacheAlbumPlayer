package com.stemaker.ampachealbumplayer

import android.content.*
import android.graphics.Typeface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.*
import android.widget.*
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.stemaker.ampachealbumplayer.musicdb.MusicDb.getAlbumByUid
import com.stemaker.ampachealbumplayer.player.AmpachePlayerService
import com.stemaker.ampachealbumplayer.player.PLAYER_STATUS_MSG
import com.bumptech.glide.Glide
import com.stemaker.ampachealbumplayer.musicdb.MusicDb
import com.stemaker.ampachealbumplayer.musicdb.MusicDb.getAlbumSongs
import com.stemaker.ampachealbumplayer.player.StatusDesc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.floor

private const val TAG = "PlayerActivity"

class PlayerActivity : AppCompatActivity(), Chronometer.OnChronometerTickListener {
    var playing = false
    var activeAlbumId: Int = -1
    var activeTrackIndex: Int = -1
    var chronometerLastPause: Long = -1
    var first = true

    inner class PlayerBroadcastReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if(intent.action == PLAYER_STATUS_MSG) {
                playerStatusUpdate(intent)
            }
        }
    }

    private val playerServiceReceiver = PlayerBroadcastReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        findViewById<Chronometer>(R.id.track_time)?.onChronometerTickListener = this
        PlayerNotificationHandler.initialize()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.player_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.album_view -> {
                showAlbumViewActivity()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onChronometerTick(chronometer: Chronometer) {
        if(playing) {
            val elapsedSeconds = (SystemClock.elapsedRealtime() - chronometer.base) / 1000
            findViewById<ProgressBar>(R.id.track_progressbar)?.progress = elapsedSeconds.toInt()
        }
    }

    fun showAlbumViewActivity() {
        val intent = Intent(this, AlbumViewActivity::class.java).apply {}
        startActivity(intent)
    }

    // This is called whenever the player service sends a status update
    fun playerStatusUpdate(intent: Intent) {
        val status = intent.getParcelableExtra<StatusDesc>("status")?:return

        // Switch the play button between play and pause
        if(first || status.newState != status.oldState) {
            var imgId: Int = R.drawable.ic_play_arrow_black_24dp
            if (status.newState == StatusDesc.States.PLAYING) imgId = R.drawable.ic_pause_black_24dp
            findViewById<ImageButton>(R.id.button_player_play)?.setImageResource(imgId)
        }

        /* Handle the progress bar/time.
           Start the timer if
             A) pause->play (continue)
             B) stopped->play (restart)
             C) track changed (restart)
             D) album changed (restart)
           Stop the timer if play->pause or play->stopped
         */
        if(status.newState == StatusDesc.States.PLAYING && status.oldState == StatusDesc.States.PAUSED)
            startStatusUpdateTimer(false)
        else if(status.newState == StatusDesc.States.PLAYING && status.oldState == StatusDesc.States.STOPPED)
            startStatusUpdateTimer(true)
        else if((status.trackIndex != activeTrackIndex) || (status.albumId != activeAlbumId))
            startStatusUpdateTimer(true)
        else if((status.newState == StatusDesc.States.PAUSED) && (status.oldState == StatusDesc.States.PLAYING))
            stopStatusUpdateTimer()
        else if((status.newState == StatusDesc.States.STOPPED) && (status.oldState == StatusDesc.States.PLAYING)) {
            resetStatusUpdateTimer()
        }

        if(status.trackIndex != activeTrackIndex) {
            val minutes = floor(status.duration.toDouble() / 60.0).toInt()
            val min = minutes.toString().padStart(2,'0')
            val seconds = status.duration % 60
            val sec = seconds.toString().padStart(2,'0')
            findViewById<TextView>(R.id.track_duration)?.text = "$min:$sec"
            // set max of progress bar to amount of seconds of the track
            findViewById<ProgressBar>(R.id.track_progressbar)?.max = status.duration
        }

        if(activeAlbumId != status.albumId) {
            // Update the album cover if the album changed
            updateAlbumCover(status.albumId)
            // Write the complete song list if the album changed
            rewriteSongList(status)
        } else if(status.trackIndex != activeTrackIndex) {
            // update the active song if we didn't change the album but the track
            updateActiveSong(status)
        }

        playing = status.newState == StatusDesc.States.PLAYING
        activeAlbumId = status.albumId ?: -1
        activeTrackIndex = status.trackIndex ?: -1


        val chronometer = findViewById<Chronometer>(R.id.track_time)
        val now = SystemClock.elapsedRealtime()
        val startOfPlayMs =  now - status.progress
        chronometer.base = startOfPlayMs
        if(playing) {
            chronometer.start()
        } else {
            chronometer.stop()
        }
        if(first) {
            val elapsedSeconds = status.progress / 1000
            findViewById<ProgressBar>(R.id.track_progressbar)?.progress = elapsedSeconds.toInt()
        }

        first = false
        PlayerNotificationHandler.statusUpdate(status)
    }

    private fun showNoAlbum() {
        val container = findViewById<LinearLayout>(R.id.song_list_container)
        container.removeAllViews()
        /* TODO: Load some special empty cover in here */
    }

    private fun updateAlbumCover(albumId: Int) {
        GlobalScope.launch(Dispatchers.Main) {
            val album = getAlbumByUid(albumId)
            findViewById<TextView>(R.id.album_title)?.text = album.name
            findViewById<TextView>(R.id.album_artist)?.text = album.artist
            val imgV = findViewById<ImageView>(R.id.album_cover)
            Glide.with(imgV.context).load(album.cover).into(imgV)
        }
    }

    // This writes the complete list of tracks after the album changed and sets the active
    // song to a bold type face
    private fun rewriteSongList(status: StatusDesc) {
        GlobalScope.launch(Dispatchers.Main) {
            /* TODO: Scroll the song list to show the current track */
            val songs = getAlbumSongs(status.albumId)
            val container = findViewById<LinearLayout>(R.id.song_list_container)
            container.removeAllViews()
            val inflater = LayoutInflater.from(container.context)
            for ((index, song) in songs.withIndex()) {
                val view = inflater.inflate(
                    R.layout.song_list_element_layout,
                    null,
                    false
                ) as TextView
                view.text = "${index+1}: ${song.title}"
                view.setOnClickListener(object: View.OnClickListener {
                    override fun onClick(v: View?) {
                        playerService.selectTrack(index)
                    }
                })
                if (status.songId == song.id) {
                    view.setTypeface(view.typeface, Typeface.BOLD)
                }
                container.addView(view)
            }
        }
    }

    // This resets the previously active song to a normal type face and sets the now active
    // song as bold type face
    private fun updateActiveSong(status: StatusDesc) {
        val container = findViewById<LinearLayout>(R.id.song_list_container)
        if(activeTrackIndex != -1) {
            val tv = container.getChildAt(activeTrackIndex) as TextView
            tv.setTypeface(null, Typeface.NORMAL)
        }
        val tv = container.getChildAt(status.trackIndex) as TextView
        tv.setTypeface(tv.typeface, Typeface.BOLD)
    }

    private fun startStatusUpdateTimer(restart: Boolean) {
        val chronometer = findViewById<Chronometer>(R.id.track_time)
        if(restart)
            chronometer.setBase(SystemClock.elapsedRealtime())
        else
            chronometer.setBase(chronometer.getBase() + SystemClock.elapsedRealtime() - chronometerLastPause)
        chronometer?.start()
    }

    private fun stopStatusUpdateTimer() {
        val chronometer = findViewById<Chronometer>(R.id.track_time)
        chronometerLastPause = SystemClock.elapsedRealtime()
        chronometer?.stop()
    }

    private fun resetStatusUpdateTimer() {
        val chronometer = findViewById<Chronometer>(R.id.track_time)
        chronometer.setBase(SystemClock.elapsedRealtime())
        chronometer?.stop()
    }

    fun buttonPrevClicked(bnt: View) {
        playerService.previous()
    }

    fun buttonPlayClicked(bnt: View) {
        if(playing)
            playerService.pause()
        else
            playerService.play()
    }

    fun buttonStopClicked(bnt: View) {
        playerService.stop()
    }

    fun buttonNextClicked(bnt: View) {
        playerService.next()
    }

    /* Interfacing with the AmpachePlayerService */
    private lateinit var playerService: AmpachePlayerService
    private var playerServiceBound: Boolean = false

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as AmpachePlayerService.PlayerServiceBinder
            playerService = binder.getService()
            playerServiceBound = true
            playerService.requestStatus()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            playerServiceBound = false
        }
    }

    override fun onStart() {
        super.onStart()
        /* Bind to the AmpachePlayerService */
        Intent(this, AmpachePlayerService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        val intentFilter = IntentFilter(PLAYER_STATUS_MSG)
        LocalBroadcastManager.getInstance(applicationContext)
            .registerReceiver(playerServiceReceiver, intentFilter)
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        playerServiceBound = false
        LocalBroadcastManager.getInstance(applicationContext)
            .unregisterReceiver(playerServiceReceiver)
    }
}
