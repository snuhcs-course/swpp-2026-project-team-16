package com.example.runtime.ui.mypage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.ui.common.LocalAppMessages
import com.example.runtime.ui.common.UiText
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.doneKeyboardOptions
import com.example.runtime.ui.common.hideKeyboardActions
import com.example.runtime.ui.common.placeholder
import com.example.runtime.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val AVATAR_MAX_SIDE = 512
private const val AVATAR_JPEG_QUALITY = 85
const val MAX_DISPLAY_NAME_LENGTH = 30
const val MAX_STATUS_MESSAGE_LENGTH = 100

suspend fun Context.compressAvatar(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
            val scale = AVATAR_MAX_SIDE.toFloat() / maxOf(info.size.width, info.size.height)
            if (scale < 1f) {
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, AVATAR_JPEG_QUALITY, out)
            out.toByteArray()
        }
    }.getOrNull()
}

@Composable
fun ProfileAvatar(bytes: ByteArray?, initial: String, size: Dp, modifier: Modifier = Modifier) {
    val image = remember(bytes) { bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
    ) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                initial.take(1).uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun ProfileSection(myPageViewModel: MyPageViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = LocalAppMessages.current
    val unreadableMessage = stringResource(R.string.profile_photo_unreadable)
    var showPhotoMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<UiText?>(null) }
    val failureText = failure?.asString()

    LaunchedEffect(failureText) {
        failureText?.let {
            messages?.show(it)
            failure = null
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val jpeg = context.compressAvatar(uri)
            if (jpeg == null) messages?.show(unreadableMessage)
            else myPageViewModel.uploadAvatar(jpeg) { failure = it }
        }
    }

    fun openPicker() {
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val username = myPageViewModel.username
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.m)) {
            Box {
                Box(
                    modifier = Modifier.clickable(enabled = username != null && !myPageViewModel.isUploadingAvatar) {
                        if (myPageViewModel.avatar == null) openPicker() else showPhotoMenu = true
                    }
                ) {
                    if (username == null) {
                        Box(modifier = Modifier.size(76.dp).placeholder(CircleShape))
                    } else {
                        ProfileAvatar(
                            bytes = myPageViewModel.avatar,
                            initial = myPageViewModel.displayName.ifBlank { username },
                            size = 76.dp
                        )
                    }
                    if (myPageViewModel.isUploadingAvatar) {
                        CircularProgressIndicator(modifier = Modifier.size(76.dp), strokeWidth = 3.dp)
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_photo_camera),
                            contentDescription = stringResource(R.string.profile_change_photo),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                DropdownMenu(expanded = showPhotoMenu, onDismissRequest = { showPhotoMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_choose_photo)) },
                        onClick = {
                            showPhotoMenu = false
                            openPicker()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_remove_photo), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showPhotoMenu = false
                            myPageViewModel.removeAvatar { failure = it }
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.width(Spacing.m))
            Column(modifier = Modifier.weight(1f)) {
                if (username == null) {
                    Box(modifier = Modifier.size(width = 120.dp, height = 22.dp).placeholder())
                    Spacer(modifier = Modifier.height(Spacing.s))
                    Box(modifier = Modifier.size(width = 180.dp, height = 16.dp).placeholder())
                } else {
                    Text(
                        myPageViewModel.displayName.ifBlank { username },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "@$username",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    val status = myPageViewModel.statusMessage
                    Text(
                        status.ifBlank { stringResource(R.string.profile_status_empty) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (status.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = { showEditDialog = true }, enabled = username != null) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.profile_edit))
            }
        }
    }

    if (showEditDialog) {
        EditProfileDialog(
            initialName = myPageViewModel.displayName,
            initialStatus = myPageViewModel.statusMessage,
            namePlaceholder = username.orEmpty(),
            state = myPageViewModel.profileState,
            onDismiss = {
                showEditDialog = false
                myPageViewModel.resetProfileEdit()
            },
            onSave = { name, status ->
                myPageViewModel.saveProfile(name, status) { showEditDialog = false }
            }
        )
    }
}

@Composable
private fun EditProfileDialog(
    initialName: String,
    initialStatus: String,
    namePlaceholder: String,
    state: ProfileEditState,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var status by rememberSaveable { mutableStateOf(initialStatus) }
    val isSaving = state == ProfileEditState.Saving

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_edit)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_DISPLAY_NAME_LENGTH).replace("\n", "") },
                    label = { Text(stringResource(R.string.label_display_name)) },
                    placeholder = { Text(namePlaceholder) },
                    singleLine = true,
                    keyboardOptions = doneKeyboardOptions(),
                    keyboardActions = hideKeyboardActions(),
                    enabled = !isSaving,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.s))
                OutlinedTextField(
                    value = status,
                    onValueChange = { status = it.take(MAX_STATUS_MESSAGE_LENGTH).replace("\n", " ") },
                    label = { Text(stringResource(R.string.label_status_message)) },
                    placeholder = { Text(stringResource(R.string.status_message_placeholder)) },
                    supportingText = {
                        Text("${status.length}/$MAX_STATUS_MESSAGE_LENGTH", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                    },
                    minLines = 2,
                    keyboardOptions = doneKeyboardOptions(),
                    keyboardActions = hideKeyboardActions(),
                    enabled = !isSaving,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.profile_name_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                (state as? ProfileEditState.Failed)?.let {
                    Spacer(modifier = Modifier.height(Spacing.s))
                    Text(it.message.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, status) }, enabled = !isSaving) {
                Text(stringResource(if (isSaving) R.string.saving else R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
