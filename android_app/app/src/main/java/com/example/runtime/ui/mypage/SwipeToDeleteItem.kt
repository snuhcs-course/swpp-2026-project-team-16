package com.example.runtime.ui.mypage

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

private val DELETE_BUTTON_WIDTH = 88.dp
private const val FULL_SWIPE_FRACTION = 0.5f
private const val FLING_VELOCITY = 2000f

@Composable
fun SwipeToDeleteItem(
    revealed: Boolean,
    onRevealChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val buttonWidthPx = with(density) { DELETE_BUTTON_WIDTH.toPx() }
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    val deleteLabel = stringResource(R.string.action_delete)

    fun delete() {
        scope.launch {
            offset.animateTo(-widthPx)
            onDelete()
        }
    }

    LaunchedEffect(revealed) {
        if (!revealed && offset.value > -widthPx) offset.animateTo(0f)
    }

    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width.toFloat() }
            .semantics {
                customActions = listOf(CustomAccessibilityAction(deleteLabel) { delete(); true })
            }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CardDefaults.shape),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(with(density) { max(buttonWidthPx, -offset.value).toDp() })
                    .background(MaterialTheme.colorScheme.error)
                    .clickable(enabled = offset.value < 0f, onClick = ::delete),
                contentAlignment = Alignment.Center
            ) {
                Text(deleteLabel, color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(-widthPx, 0f)) }
                    },
                    onDragStarted = { onRevealChange(true) },
                    onDragStopped = { velocity ->
                        when {
                            offset.value < -widthPx * FULL_SWIPE_FRACTION ||
                                (velocity < -FLING_VELOCITY && offset.value < -buttonWidthPx) -> delete()
                            offset.value < -buttonWidthPx / 2 -> offset.animateTo(-buttonWidthPx)
                            else -> {
                                offset.animateTo(0f)
                                onRevealChange(false)
                            }
                        }
                    }
                )
        ) {
            content()
        }
    }
}
