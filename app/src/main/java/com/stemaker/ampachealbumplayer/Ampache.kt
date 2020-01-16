package com.stemaker.ampachealbumplayer

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
//import okhttp3.logging.HttpLoggingInterceptor
import org.simpleframework.xml.Attribute
import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException
import java.security.MessageDigest
import java.util.*

/* We need to use the constructor to define the elements */
@Root(name="root", strict=false)
data class AmpacheHandshake (
    @field:Element(name="error", required = false)
    @param:Element(name="error", required = false)
    val error: String? = null,

    @field:Element(name="auth", required = false)
    @param:Element(name="auth", required = false)
    var token: String? = null,

    @field:Element(name="api", required = false)
    @param:Element(name="api", required = false)
    var version: String? = null,

    @field:Element(name="update", required = false)
    @param:Element(name="update", required = false)
    var update: String? = null,

    @field:Element(name="add", required = false)
    @param:Element(name="add", required = false)
    var add: String? = null,

    @field:Element(name="clean", required = false)
    @param:Element(name="clean", required = false)
    var clean: String? = null,

    @field:Element(name="songs", required = false)
    @param:Element(name="songs", required = false)
    var songs: String? = null,

    @field:Element(name="artists", required = false)
    @param:Element(name="artists", required = false)
    var artists: String? = null,

    @field:Element(name="albums", required = false)
    @param:Element(name="albums", required = false)
    var albums: String? = null,

    @field:Element(name="playlists", required = false)
    @param:Element(name="playlists", required = false)
    var playlists: String? = null,

    @field:Element(name="videos", required = false)
    @param:Element(name="videos", required = false)
    var videos: String? = null
)

@Root(name="album", strict = false)
data class AmpacheAlbum(
    @field:Element(name="name", required = false)
    @param:Element(name="name", required = false)
    var name: String? = null,

    @field:Element(name="art", required = false)
    @param:Element(name="art", required = false)
    var cover: String? = null,

    @field:Element(name="artist", required = false)
    @param:Element(name="artist", required = false)
    var artist: String? = null,

    @field:Element(name="tracks", required = false)
    @param:Element(name="tracks", required = false)
    var numTracks: String? = null,

    @field:Attribute(name="id", required = false)
    @param:Attribute(name="id", required = false)
    val id: String? = null
)

@Root(name="root", strict=false)
data class AmpacheAlbumsList(
    @field:Element(name="total_count", required = false)
    @param:Element(name="total_count", required = false)
    var total_count: String? = null,

    @field:ElementList(required = false, name="album", entry="album", inline=true)
    @param:ElementList(required = false, name="album", entry="album", inline=true)
    val albums: MutableList<AmpacheAlbum>? = null
)

@Root(name="song", strict = false)
data class AmpacheSong(
    @field:Element(name="title", required = true)
    @param:Element(name="title", required = true)
    var title: String? = null,

    @field:Element(name="url", required = false)
    @param:Element(name="url", required = false)
    var url: String? = null,

    @field:Element(name="track", required = true)
    @param:Element(name="track", required = true)
    var track: Int? = null,

    @field:Attribute(name="id", required = true)
    @param:Attribute(name="id", required = true)
    val id: Int? = null,

    @field:Element(name="time", required = false)
    @param:Element(name="time", required = false)
    val time: Int? = null
): Comparable<AmpacheSong> {
    override fun compareTo(other: AmpacheSong): Int {
        val track = this.track?:0
        val other = other.track?:0
        if(track > other)
            return track - other
        else if(track < other)
            return other - track
        else
            return 0
    }
}

@Root(name="root", strict=false)
data class AmpacheAlbumSongsList(
    @field:ElementList(required = false, name="song", entry="song", inline=true)
    @param:ElementList(required = false, name="song", entry="song", inline=true)
    val songs: MutableList<AmpacheSong>? = null
)

interface AmpacheService {
    @GET("?action=handshake")
    suspend fun connect(@Query("auth") passphrase: String,
        @Query("timestamp") time: String,
        @Query("user") user: String) :
            AmpacheHandshake

    @GET("?action=albums")
    suspend fun getAlbums(@Query("auth") token: String,
                        @Query("offset") offset: String,
                        @Query("limit") limit: String):
            AmpacheAlbumsList

    @GET("?action=album")
    suspend fun getAlbum(@Query("auth") token: String,
                          @Query("filter") uid: String) :
            AmpacheAlbumsList

    @GET("?action=album_songs")
    suspend fun getAlbumSongs(@Query("auth") token: String,
                              @Query("filter") albumId: String,
                              @Query("offset") offset: String = "0",
                              @Query("limit") limit: String = "5000") :
            AmpacheAlbumSongsList

    @GET("?action=song")
    suspend fun getSong(@Query("auth") token: String,
                          @Query("filter") albumId: String) :
            AmpacheAlbumSongsList
}

class Ampache(serverUrl: String) {
    enum class SortOrder {NAME, ARTIST}
    var service: AmpacheService? = null
    var token: String? = null
    val url = serverUrl

    var numAlbums: Int = 0
    var albumIDsByName = mutableListOf<Int>()
    var albumIDsByArtist = mutableListOf<Int>()
    var sortOrder: SortOrder = SortOrder.ARTIST
    val albumIDs: List<Int>
        get() = when(sortOrder) {
            SortOrder.ARTIST -> albumIDsByArtist.toList()
            else -> albumIDsByName.toList()
        }
    var numArtists: Int = 0
    val interceptor: HttpLoggingInterceptor = HttpLoggingInterceptor().apply {
        this.level = HttpLoggingInterceptor.Level.BODY
    }
    val client = OkHttpClient.Builder().apply {
        this.addInterceptor(interceptor)
    }.build()

    val retrofit = Retrofit.Builder()
        .baseUrl("${url}/server/xml.server.php/")
        .addConverterFactory(SimpleXmlConverterFactory.create())
        .client(client)
        .build()

    suspend fun connect(user: String, key: String) {
        service = retrofit.create(AmpacheService::class.java)
        val md = MessageDigest.getInstance("SHA-256")
        val time = ((Date().getTime()) / 1000L).toString()
        md.update((time + key).toByteArray())
        val pass = md.digest().toHexString()
        Log.d("MainActivity::connectAmpache", "triggering handshake")
        lateinit var handshakeResult: AmpacheHandshake
        try {
            handshakeResult = service!!.connect(pass, time, user)
        } catch(e: Exception) {
            Log.d("MainActivity::connectAmpache", "Unknown error")
            throw IOException("Unknown error")
        }
        if(handshakeResult.error != null) {
            val msg = "Connect to Ampache server not successful. Server error message: ${handshakeResult.error}"
            Log.d("MainActivity::connectAmpache", "xml error, $msg")
            throw IOException(msg)
        } else if(handshakeResult.token == null) {
            val msg = "No error reported from Ampache server, but no token received"
            Log.d("MainActivity::connectAmpache", msg)
            throw IOException(msg)
        } else {
            Log.d( "MainActivity::connectAmpache", "token received. Ampache server lists ${handshakeResult.albums?:0} albums")
            token = handshakeResult.token
            numAlbums = handshakeResult.albums?.toInt() ?: 0
            numArtists = handshakeResult.artists?.toInt() ?: 0
        }
    }

    suspend fun initialize() {
        val albums = mutableListOf<AmpacheAlbum>()
        while(albums.size < numAlbums) {
            /* TODO: Catch exceptions */
            val albumList = service!!.getAlbums(token!!, albums.size.toString(), limit = "10")
            albums.addAll(albumList.albums?: mutableListOf<AmpacheAlbum>())
        }
        albums.sortBy { it.name }
        for(album in albums)
            albumIDsByName.add(album.id?.toInt() ?: 0)
        albums.sortBy { it.artist}
        for(album in albums)
            albumIDsByArtist.add(album.id?.toInt() ?: 0)
    }

    suspend fun getAlbum(uid: Int): AmpacheAlbum {
        val albums = service!!.getAlbum(token!!, uid.toString())
        return albums!!.albums!![0]
    }

    suspend fun getSong(uid: Int): AmpacheSong {
        val songs = service!!.getSong(token!!, uid.toString())
        return songs.songs!![0]
    }

    suspend fun getAlbumSongs(uid: Int): List<AmpacheSong>? {
        return service!!.getAlbumSongs(token!!, uid.toString()).songs
    }
}
