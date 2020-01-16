package com.stemaker.ampachealbumplayer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.stemaker.ampachealbumplayer.musicdb.MusicDb
import com.stemaker.ampachealbumplayer.player.AmpachePlayerService
import com.stemaker.ampachealbumplayer.player.PlaylistElementDesc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

private const val TAG = "AlbumViewActivity"
/* TODO Need to block the back button towards main activity or modify main activity to
 jump to album view if connection established and settings unchanged */
class AlbumViewActivity() : AppCompatActivity(), AlbumRecycleViewAdapter.PlayerRequestListener,
    Parcelable {
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: RecyclerView.Adapter<*>
    private lateinit var viewManager: RecyclerView.LayoutManager

    inner class ScrollListener(): RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            // TODO: Haven't found a better solution. Without delay update is happening often too early with an outdated letter
            Handler().postDelayed({
                val man = recyclerView.layoutManager as LinearLayoutManager
                val firstIdx = man.findFirstCompletelyVisibleItemPosition()
                val album = MusicDb.getAlbumByIndexIfCached(firstIdx)
                if(album != null)
                {
                    var letter = ' '
                    when (MusicDb.getAlbumSortOrder()) {
                        Ampache.SortOrder.NAME -> letter = album.name?.get(0) ?: ' '
                        Ampache.SortOrder.ARTIST -> letter = album.artist?.get(0) ?: ' '
                    }
                    findViewById<TextView>(R.id.first_letter)?.text = letter.toString()
                }
            }, 200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_album_view)
        viewManager = LinearLayoutManager(this)
        viewAdapter = AlbumRecycleViewAdapter(this)

        recyclerView = findViewById<RecyclerView>(R.id.album_recycler_view).apply {
            // use this setting to improve performance if you know that changes
            // in content do not change the layout size of the RecyclerView
            setHasFixedSize(true)

            // use the linear layout manager
            layoutManager = viewManager

            // specify the viewAdapter
            adapter = viewAdapter

        }
        recyclerView.addOnScrollListener(ScrollListener())

        GlobalScope.launch(Dispatchers.Main) {
            val album = MusicDb.getAlbumByIndex(0)
            var letter = ' '
            when (MusicDb.getAlbumSortOrder()) {
                Ampache.SortOrder.NAME -> letter = album.name?.get(0) ?: ' '
                Ampache.SortOrder.ARTIST -> letter = album.artist?.get(0) ?: ' '
            }
            findViewById<TextView>(R.id.first_letter)?.text = letter.toString()
        }

        MusicDb.setAlbumSortOrderTitle()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.album_view_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.goto_player_activity -> {
               showPlayerActivity()
                true
            }
            R.id.toggle_sort -> {
                MusicDb.toggleAlbumSortOrder()
                viewAdapter.notifyDataSetChanged()
                recyclerView.scrollToPosition(0)
                true
            }
            R.id.settings-> {
                showSettingsActivity()
                true
            }
            R.id.about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    /* Interfacing with the AmpachePlayerService */
    private lateinit var playerService: AmpachePlayerService
    private var playerServiceBound: Boolean = false

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as AmpachePlayerService.PlayerServiceBinder
            playerService = binder.getService()
            playerServiceBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            playerServiceBound = false
        }
    }

    constructor(parcel: Parcel) : this() {
        playerServiceBound = parcel.readByte() != 0.toByte()
    }

    override fun onStart() {
        super.onStart()
        /* Bind to the AmpachePlayerService */
        Intent(this, AmpachePlayerService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        playerServiceBound = false
    }

    override fun playAlbum(uid: Int) {
        GlobalScope.launch(Dispatchers.Main) {
            val songs = MusicDb.getAlbumSongs(uid)
            songs.sorted() // sort the songs by track number
            val songList = mutableListOf<PlaylistElementDesc>()
            for ( (index, song) in songs.withIndex()) {
                val elem = PlaylistElementDesc(uid, song.id?:0, index, song.url?:"", song.time?:0)
                songList.add(elem)
            }
            songList.sortBy { it.trackIndex }
            playerService.clear()
            playerService.addTracks(songList.toTypedArray(), false)
            playerService.play()
            showPlayerActivity()
        }
    }

    private fun showPlayerActivity() {
        val intent = Intent(AmpacheAlbumPlayerApp.appContext, PlayerActivity::class.java)
        startActivity(intent)
    }

    private fun showSettingsActivity() {
        val intent = Intent(this, ConfigurationActivity::class.java).apply {}
        startActivity(intent)
    }

    private fun showAboutDialog() {
        val aboutDialog = AboutDialogFragment()
        aboutDialog.show(supportFragmentManager, "AboutDialog")
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeByte(if (playerServiceBound) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<AlbumViewActivity> {
        override fun createFromParcel(parcel: Parcel): AlbumViewActivity {
            return AlbumViewActivity(parcel)
        }

        override fun newArray(size: Int): Array<AlbumViewActivity?> {
            return arrayOfNulls(size)
        }
    }
}
