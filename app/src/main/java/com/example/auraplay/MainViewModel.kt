package com.example.auraplay

import android.app.Application
import android.content.ContentUris
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.auraplay.data.Playlist
import com.example.auraplay.data.PlaylistDao
import com.example.auraplay.data.PlaylistSongCrossRef
import com.example.auraplay.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortOrder {
    TITLE, ARTIST, DATE_ADDED
}

class MainViewModel(application: Application, private val playlistDao: PlaylistDao) : AndroidViewModel(application) {

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_ADDED)
    val sortOrder = _sortOrder.asStateFlow()

    private val _darkTheme = MutableStateFlow(true)
    val darkTheme = _darkTheme.asStateFlow()

    val songs: StateFlow<List<Song>> =
        combine(_allSongs, _searchQuery, _sortOrder) { songs, query, order ->
            val filteredSongs = if (query.isBlank()) {
                songs
            } else {
                songs.filter {
                    it.title.contains(query, ignoreCase = true) ||
                            it.artist.contains(query, ignoreCase = true)
                }
            }
            when (order) {
                SortOrder.TITLE -> filteredSongs.sortedBy { it.title }
                SortOrder.ARTIST -> filteredSongs.sortedBy { it.artist }
                SortOrder.DATE_ADDED -> filteredSongs.sortedByDescending { it.id }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Playlist states
    val playlists = playlistDao.getAllPlaylists().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun getPlaylistWithSongs(playlistId: Long) = playlistDao.getPlaylistWithSongs(playlistId)

    val showPlaylistDialog = mutableStateOf<Song?>(null)

    init {
        loadSongs()
    }

    fun toggleTheme() {
        _darkTheme.value = !_darkTheme.value
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun changeSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    private fun loadSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            val songsList = mutableListOf<Song>()
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            val context = getApplication<Application>().applicationContext

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)


                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn)
                    val artist = cursor.getString(artistColumn)
                    val albumId = cursor.getLong(albumIdColumn)
                    val duration = cursor.getLong(durationColumn)
                    val data = cursor.getString(dataColumn)
                    val albumArtUri = ContentUris.withAppendedId(
                        android.net.Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()

                    songsList.add(Song(id, title, artist, albumArtUri, duration, data))
                }
            }
            withContext(Dispatchers.Main) {
                _allSongs.value = songsList
            }
            playlistDao.insertSongs(songsList) // Cache songs in DB
        }
    }

    // Playlist Management
    fun createPlaylist(name: String) = viewModelScope.launch(Dispatchers.IO) {
        playlistDao.insertPlaylist(Playlist(name = name))
    }

    fun deletePlaylist(playlist: Playlist) = viewModelScope.launch(Dispatchers.IO) {
        playlistDao.deletePlaylist(playlist)
    }

    fun renamePlaylist(playlist: Playlist, newName: String) = viewModelScope.launch(Dispatchers.IO) {
        playlistDao.updatePlaylist(playlist.copy(name = newName))
    }

    fun addSongToPlaylist(song: Song, playlist: Playlist) = viewModelScope.launch(Dispatchers.IO) {
        playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlist.playlistId, song.id))
    }

    fun addMultipleSongsToPlaylist(playlistId: Long, songIds: List<Long>) = viewModelScope.launch(Dispatchers.IO) {
        val crossRefs = songIds.map { songId ->
            PlaylistSongCrossRef(playlistId = playlistId, id = songId)
        }
        playlistDao.addSongsToPlaylist(crossRefs)
    }

    fun removeSongFromPlaylist(song: Song, playlist: Playlist) = viewModelScope.launch(Dispatchers.IO) {
        playlistDao.removeSongFromPlaylist(PlaylistSongCrossRef(playlist.playlistId, song.id))
    }

}

class MainViewModelFactory(private val application: Application, private val playlistDao: PlaylistDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, playlistDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}