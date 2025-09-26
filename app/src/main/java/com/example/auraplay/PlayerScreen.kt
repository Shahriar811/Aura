package com.example.auraplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.auraplay.service.PlayerState
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    navController: NavController,
    playerState: PlayerState,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeatMode: () -> Unit
) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 25.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceAround
        ) {
            playerState.currentSong?.let { song ->
                Spacer(modifier = Modifier.height(16.dp))
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(15.dp)),
                    contentScale = ContentScale.Crop
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = song.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = song.artist, fontSize = 16.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = playerState.currentPosition.toFloat(),
                        onValueChange = onSeek,
                        valueRange = 0f..playerState.totalDuration.toFloat().coerceAtLeast(0f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = formatTime(playerState.currentPosition), fontSize = 12.sp, color = Color.Gray)
                        Text(text = formatTime(playerState.totalDuration), fontSize = 12.sp, color = Color.Gray)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onToggleShuffle) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playerState.isShuffleOn) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }

                    IconButton(onClick = onPrevious) {
                        Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
                    }

                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.Black,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    IconButton(onClick = onNext) {
                        Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
                    }

                    IconButton(onClick = onToggleRepeatMode) {
                        val (icon, tint) = when (playerState.repeatMode) {
                            1 -> Icons.Default.RepeatOne to MaterialTheme.colorScheme.primary // REPEAT_MODE_ONE
                            2 -> Icons.Default.Repeat to MaterialTheme.colorScheme.primary   // REPEAT_MODE_ALL
                            else -> Icons.Default.Repeat to Color.Gray                      // REPEAT_MODE_OFF
                        }
                        Icon(imageVector = icon, contentDescription = "Repeat", tint = tint)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

fun formatTime(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
    return String.format("%02d:%02d", minutes, seconds)
}

