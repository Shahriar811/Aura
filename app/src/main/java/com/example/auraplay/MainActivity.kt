package com.example.auraplay

import android.Manifest
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.auraplay.data.Song
import com.example.auraplay.service.MusicService
import com.example.auraplay.service.PlayerState
import com.example.auraplay.ui.*
import com.example.auraplay.ui.theme.AuraPlayTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.common.util.concurrent.MoreExecutors

@Suppress("OPT_IN_IS_NOT_ENABLED")
@OptIn(ExperimentalPermissionsApi::class)
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(
            application,
            (application as AuraPlayApplication).database.playlistDao()
        )
    }
    private var mediaController: MediaController? = null

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                mediaController = controllerFuture.get()
            },
            MoreExecutors.directExecutor()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkTheme by viewModel.darkTheme.collectAsState()
            AuraPlayTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
                val currentBackStack by navController.currentBackStackEntryAsState()
                val currentDestination = currentBackStack?.destination

                val fullPlayerState by MusicService.playerState.collectAsState()
                val songs by viewModel.songs.collectAsState()

                // Determine if the bottom bar should be shown
                val showBottomBar = currentDestination?.route in listOf("home", "playlists")

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar {
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                                    label = { Text("Home") },
                                    selected = currentDestination?.route == "home",
                                    onClick = {
                                        navController.navigate("home") {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                                    label = { Text("Playlists") },
                                    selected = currentDestination?.route == "playlists",
                                    onClick = {
                                        navController.navigate("playlists") {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(navController, startDestination = "home", Modifier.padding(innerPadding)) {
                        composable("home") {
                            HomeScreenWithPermission(
                                navController = navController,
                                viewModel = viewModel,
                                playerState = fullPlayerState,
                                songs = songs,
                                mediaController = mediaController
                            )
                        }
                        composable("player") {
                            PlayerScreen(
                                navController = navController,
                                playerState = fullPlayerState,
                                onPlayPause = {
                                    if (fullPlayerState.isPlaying) mediaController?.pause() else mediaController?.play()
                                },
                                onSeek = { mediaController?.seekTo(it.toLong()) },
                                onNext = { mediaController?.seekToNext() },
                                onPrevious = { mediaController?.seekToPrevious() },
                                onToggleShuffle = { mediaController?.shuffleModeEnabled = !fullPlayerState.isShuffleOn },
                                onToggleRepeatMode = {
                                    mediaController?.repeatMode = when (fullPlayerState.repeatMode) {
                                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                        else -> Player.REPEAT_MODE_OFF
                                    }
                                }
                            )
                        }
                        composable("playlists") {
                            PlaylistScreen(navController = navController, viewModel = viewModel)
                        }
                        composable(
                            "playlist_details/{playlistId}",
                            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: 0L
                            PlaylistDetailsScreenWrapper(
                                playlistId = playlistId,
                                navController = navController,
                                viewModel = viewModel,
                                mediaController = mediaController
                            )
                        }
                        composable(
                            "add_songs/{playlistId}",
                            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: 0L
                            AddSongsToPlaylistScreen(
                                playlistId = playlistId,
                                navController = navController,
                                viewModel = viewModel
                            )
                        }
                        composable("settings") {
                            SettingsScreen(navController = navController, viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun HomeScreenWithPermission(
        navController: NavController,
        viewModel: MainViewModel,
        playerState: PlayerState,
        songs: List<Song>,
        mediaController: MediaController?
    ) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val permissionState = rememberPermissionState(permission)

        if (permissionState.status.isGranted) {
            HomeScreen(
                navController = navController,
                viewModel = viewModel,
                playerState = playerState,
                onPlaySong = { song ->
                    val mediaItems = songs.map { s ->
                        MediaItem.Builder()
                            .setUri(s.data)
                            .setMediaId(s.id.toString())
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(s.title)
                                    .setArtist(s.artist)
                                    .setArtworkUri(android.net.Uri.parse(s.albumArtUri))
                                    .build()
                            )
                            .build()
                    }
                    mediaController?.setMediaItems(mediaItems, songs.indexOf(song), 0)
                    mediaController?.prepare()
                    mediaController?.play()
                    navController.navigate("player")
                },
                onTogglePlayPause = {
                    if (playerState.isPlaying) mediaController?.pause() else mediaController?.play()
                }
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Permission required to access music files.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { permissionState.launchPermissionRequest() }) {
                        Text("Grant Permission")
                    }
                }
            }
        }
    }

    @Composable
    fun PlaylistDetailsScreenWrapper(
        playlistId: Long,
        navController: NavController,
        viewModel: MainViewModel,
        mediaController: MediaController?
    ) {
        val playlistWithSongs by viewModel.getPlaylistWithSongs(playlistId).collectAsState(initial = null)
        val playlistSongs = playlistWithSongs?.songs ?: emptyList()

        PlaylistDetailsScreen(
            playlistId = playlistId,
            navController = navController,
            viewModel = viewModel,
            onSongSelected = { song ->
                val mediaItems = playlistSongs.map { s ->
                    MediaItem.Builder()
                        .setUri(s.data)
                        .setMediaId(s.id.toString())
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(s.title)
                                .setArtist(s.artist)
                                .setArtworkUri(android.net.Uri.parse(s.albumArtUri))
                                .build()
                        )
                        .build()
                }
                mediaController?.setMediaItems(mediaItems, playlistSongs.indexOf(song), 0)
                mediaController?.prepare()
                mediaController?.play()
                navController.navigate("player")
            }
        )
    }

    override fun onStop() {
        mediaController?.release()
        super.onStop()
    }
}