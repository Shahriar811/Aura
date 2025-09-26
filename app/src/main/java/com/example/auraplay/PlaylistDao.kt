package com.example.auraplay.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    // Playlist operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("SELECT * FROM playlist ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Transaction
    @Query("SELECT * FROM playlist WHERE playlistId = :playlistId")
    fun getPlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongs>

    // Song operations
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongs(songs: List<Song>)

    // Playlist-Song relationship operations
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongsToPlaylist(crossRefs: List<PlaylistSongCrossRef>)

    @Delete
    suspend fun removeSongFromPlaylist(crossRef: PlaylistSongCrossRef)
}