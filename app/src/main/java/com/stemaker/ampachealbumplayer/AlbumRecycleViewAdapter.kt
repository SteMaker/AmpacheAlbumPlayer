package com.stemaker.ampachealbumplayer

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.stemaker.ampachealbumplayer.musicdb.MusicDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

private const val TAG = "AlbumRecycleViewAdapter"

class AlbumRecycleViewAdapter(val listener: PlayerRequestListener): RecyclerView.Adapter<AlbumRecycleViewAdapter.AlbumViewHolder>() {

    class AlbumViewHolder(val albumView: CardView) : RecyclerView.ViewHolder(albumView)

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup,
                                    viewType: Int): AlbumViewHolder {
        // create a new Album Card View
        val albumView = LayoutInflater.from(parent.context)
                .inflate(R.layout.album_card, parent, false) as CardView
        return AlbumViewHolder(albumView)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: AlbumViewHolder, index: Int) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        GlobalScope.launch(Dispatchers.Main) {
            Log.d("AlbumRecycleViewAdapter::onBindViewHolder", "called for index=$index")
            val album = MusicDb.getAlbumByIndex(index)
            val imgV = holder.albumView.findViewById<ImageView>(R.id.album_cover)
            Glide.with(imgV.context).load(album.cover).into(imgV)
            val titleV = holder.albumView.findViewById<TextView>(R.id.album_title)
            titleV.text = album.name
            val artistV = holder.albumView.findViewById<TextView>(R.id.album_artist)
            artistV.text = album.artist
            Log.d(TAG, "album artist = ${album.artist}")
            val numTracksV = holder.albumView.findViewById<TextView>(R.id.album_num_tracks)
            numTracksV.text = "${album.numTracks} Tracks"
            val btn = holder.albumView.findViewById<ImageButton>(R.id.button_album_play)
            btn.setOnClickListener(object: View.OnClickListener {
                override fun onClick(cV: View) {
                    listener.playAlbum(album.id!!.toInt())
                }
            })

            Log.d("AlbumRecycleViewAdapter::onBindViewHolder", "title=${album.name}, id=${album.id}, cover=${album.cover}")
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = MusicDb.albumIDs.size

    interface PlayerRequestListener {
        fun playAlbum(uid: Int)
    }
}