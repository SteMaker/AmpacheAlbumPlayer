package com.stemaker.ampachealbumplayer.musicdb

import android.util.Log
import android.util.LruCache
import com.stemaker.ampachealbumplayer.*

object MusicDb {
    lateinit var ampache: Ampache
    val numAlbums: Int
        get() = ampache.numAlbums
    val albumIDs: List<Int>
        get() = ampache.albumIDs

    /* Caches */
    private val albumCache = LruCache<Int, AmpacheAlbum>(30)
    private val songCache = LruCache<Int, AmpacheSong>(50)

    suspend fun connect() {
        ampache = Ampache(Configuration.serverUrl)
        ampache.connect(Configuration.user, Configuration.passwordHash)
    }

    suspend fun initialize() {
        ampache.initialize()
        Log.d("MusicDb::initialize", "Retrieved ${albumIDs.size} Albums IDs")
    }

    suspend fun getAlbumByIndex(idx: Int): AmpacheAlbum {
        if( (idx < 0) || (idx >= ampache.albumIDs.size)) throw(ArrayIndexOutOfBoundsException())
        return getAlbumByUid(ampache.albumIDs[idx])
    }

    fun getAlbumByIndexIfCached(idx: Int): AmpacheAlbum? {
        if( (idx < 0) || (idx >= ampache.albumIDs.size)) throw(ArrayIndexOutOfBoundsException())
        return getAlbumByUidIfCached(ampache.albumIDs[idx])
    }

    suspend fun getAlbumByUid(uid: Int): AmpacheAlbum {
        Log.d("MusicDb::getAlbumByUid", "called uid=$uid")
        var album: AmpacheAlbum? = albumCache.get(uid)
        /* Check if we have it cached */
        if(album == null) {
            /* Not cached -> get it from Ampache, put it in the cache */
            album = ampache.getAlbum(uid)
            Log.d("MusicDb.getAlbumByUid", "retrieved album with title ${album.name}")
            albumCache.put(uid, album)
        }
        return album
    }

    suspend fun getSongByUid(uid: Int): AmpacheSong {
        Log.d("MusicDb::getSongByUid", "called uid=$uid")
        var song: AmpacheSong? = songCache.get(uid)
        /* Check if we have it cached */
        if(song == null) {
            /* Not cached -> get it from Ampache, put it in the cache */
            song = ampache.getSong(uid)
            Log.d("MusicDb.getSongByUid", "retrieved song with title ${song.title}")
            songCache.put(uid, song)
        }
        return song
    }

    fun getAlbumByUidIfCached(uid: Int): AmpacheAlbum? {
        return albumCache.get(uid)
    }

    suspend fun getAlbumSongs(uid: Int): List<AmpacheSong> {
        try {
            val songs = ampache.getAlbumSongs(uid)
            songs?.let{return songs} ?: return listOf<AmpacheSong>()
        } catch(e: java.lang.Exception) {
            return listOf<AmpacheSong>()
        }
    }

    fun setAlbumSortOrderTitle() {
        ampache.sortOrder = Ampache.SortOrder.NAME
    }

    fun setAlbumSortOrderArtist() {
        ampache.sortOrder = Ampache.SortOrder.ARTIST
    }

    fun toggleAlbumSortOrder() =
        when(ampache.sortOrder) {
            Ampache.SortOrder.NAME -> ampache.sortOrder = Ampache.SortOrder.ARTIST
            Ampache.SortOrder.ARTIST -> ampache.sortOrder = Ampache.SortOrder.NAME
        }

    fun getAlbumSortOrder(): Ampache.SortOrder = ampache.sortOrder

    fun getAlbumSortOrderAsTextId() =
        when(ampache.sortOrder) {
            Ampache.SortOrder.NAME -> R.string.album_name
            Ampache.SortOrder.ARTIST -> R.string.artist
        }
}