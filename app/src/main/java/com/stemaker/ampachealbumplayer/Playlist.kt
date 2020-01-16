package com.stemaker.ampachealbumplayer

import com.stemaker.ampachealbumplayer.player.PlaylistElementDesc

class Playlist {

    private val playlist = mutableListOf<PlaylistElementDesc>()
    private var playlistIndex = 0

    val current: PlaylistElementDesc?
        get() {
            when(isIndexValid()) {
                true -> return playlist[playlistIndex]
                false -> return null
            }
        }

    val isEmpty: Boolean
        get() = !isIndexValid()
    val size: Int
        get() = playlist.size

    fun add(playlistElements: Array<PlaylistElementDesc>, back: Boolean) {
        playlist.addAll(playlistElements)
    }

    fun next(): PlaylistElementDesc? {
        if(playlistIndex < (playlist.size-1))
            playlistIndex++
        return current
    }

    fun previous(): PlaylistElementDesc? {
        if(playlistIndex > 0)
            playlistIndex--
        return current
    }

    fun select(idx: Int): PlaylistElementDesc? {
        if(isIndexValid(idx))
            playlistIndex = idx
        return current
    }

    fun hasNext(): Boolean {
        return playlistIndex < (playlist.size-1)
    }

    fun hasPrevious(): Boolean {
        return playlistIndex > 0
    }

    fun hasIndex(idx: Int): Boolean {
        return isIndexValid(idx)
    }

    fun clear() {
        playlist.clear()
        playlistIndex = 0
    }

    private fun isIndexValid(idx: Int = playlistIndex): Boolean {
        if(playlistIndex < playlist.size && playlistIndex >= 0) return true
        return false
    }
}