package com.codeunlock.ui.screens.sprint

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeunlock.data.sprint.SprintParticipant
import com.codeunlock.data.sprint.SprintRoom
import com.codeunlock.data.sprint.SprintRoomStore

@Composable
fun FocusRoomRoute(
    onBack: () -> Unit,
    onOpenProblem: (Int) -> Unit
) {
    FocusRoomScreen(
        onBack = onBack,
        onOpenProblem = onOpenProblem
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusRoomScreen(
    onBack: () -> Unit,
    onOpenProblem: (Int) -> Unit
) {
    val rooms by SprintRoomStore.rooms.collectAsStateWithLifecycle()
    val activeRoom by SprintRoomStore.activeRoom.collectAsStateWithLifecycle()

    var joinCodeInput by remember { mutableStateOf("") }
    var joinError by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = activeRoom?.name ?: "Multiplayer Sprint Rooms",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (activeRoom != null) {
                                SprintRoomStore.leaveRoom()
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (activeRoom == null) {
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Create Room")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val currentRoom = activeRoom
            if (currentRoom != null) {
                ActiveSprintRoomView(
                    room = currentRoom,
                    onOpenProblem = onOpenProblem,
                    onLeaveRoom = { SprintRoomStore.leaveRoom() }
                )
            } else {
                SprintRoomLobbyView(
                    rooms = rooms,
                    joinCodeInput = joinCodeInput,
                    joinError = joinError,
                    onJoinCodeChange = {
                        joinCodeInput = it
                        joinError = null
                    },
                    onJoinClick = {
                        val success = SprintRoomStore.joinByCode(joinCodeInput)
                        if (!success) {
                            joinError = "Room code not found. Check code and try again."
                        }
                    },
                    onSelectRoom = { SprintRoomStore.selectRoom(it.id) }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateRoomDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, topic, duration ->
                SprintRoomStore.createRoom(name, topic, duration)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun ActiveSprintRoomView(
    room: SprintRoom,
    onOpenProblem: (Int) -> Unit,
    onLeaveRoom: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Sprint Hero Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "Code: ${room.code}",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "${room.durationMinutes} min Sprint",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = room.topic,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Target Problem Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Target Problem",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = room.problemTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Button(
                                onClick = { onOpenProblem(room.problemId) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Code, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Solve Now")
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onLeaveRoom,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Leave Sprint Room")
                    }
                }
            }
        }

        item {
            Text(
                text = "Live Co-Workers (${room.participants.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(room.participants) { participant ->
            ParticipantRow(participant)
        }

        item {
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ParticipantRow(participant: SprintParticipant) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = participant.avatarEmoji, fontSize = 22.sp)
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = participant.username,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (participant.isHost) {
                            Text(" 👑", fontSize = 12.sp)
                        }
                    }
                    Text(
                        text = "Working on ${participant.currentProblemTitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when {
                    participant.status.contains("Solved") -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                    participant.status.contains("Break") -> Color(0xFFFF9800).copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
            ) {
                Text(
                    text = participant.status,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SprintRoomLobbyView(
    rooms: List<SprintRoom>,
    joinCodeInput: String,
    joinError: String?,
    onJoinCodeChange: (String) -> Unit,
    onJoinClick: () -> Unit,
    onSelectRoom: (SprintRoom) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Join Code Card
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Join with Room Code",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = joinCodeInput,
                            onValueChange = onJoinCodeChange,
                            placeholder = { Text("e.g. DAILY-25") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true
                        )
                        Button(
                            onClick = onJoinClick,
                            enabled = joinCodeInput.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text("Join")
                        }
                    }
                    if (joinError != null) {
                        Text(
                            text = joinError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Community Sprint Rooms",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(rooms) { room ->
            RoomCard(room = room, onClick = { onSelectRoom(room) })
        }

        item {
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RoomCard(room: SprintRoom, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = room.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${room.durationMinutes}m",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Topic: ${room.topic}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${room.participants.size} co-working live",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Enter Room")
                }
            }
        }
    }
}

@Composable
private fun CreateRoomDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, topic: String, duration: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("Dynamic Programming") }
    var duration by remember { mutableIntStateOf(25) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Sprint Room") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Room Name") },
                    placeholder = { Text("e.g. Study Hall Sprint") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Sprint Topic") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Sprint Duration", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 25, 45, 60).forEach { mins ->
                        FilterChip(
                            selected = duration == mins,
                            onClick = { duration = mins },
                            label = { Text("${mins}m") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, topic, duration) },
                enabled = name.isNotBlank()
            ) {
                Text("Create & Join")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
