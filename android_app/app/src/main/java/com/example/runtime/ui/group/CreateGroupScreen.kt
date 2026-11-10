package com.example.runtime.ui.group

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.ui.auth.PasswordField
import com.example.runtime.ui.common.AccentButton
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.formatDateTime
import com.example.runtime.ui.common.doneKeyboardOptions
import com.example.runtime.ui.common.formatDistance
import com.example.runtime.ui.common.hideKeyboardActions
import com.example.runtime.ui.theme.Spacing
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

data class GroupRouteChoice(val routeId: Int, val title: String, val distance: Int)

private enum class PickingTime { MEET, DEADLINE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    groupsViewModel: GroupsViewModel,
    initialRoute: GroupRouteChoice?,
    onBack: () -> Unit,
    onCreated: (Int) -> Unit,
) {
    val defaultMeet = remember { Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS) }
    var route by remember { mutableStateOf(initialRoute) }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var capacity by rememberSaveable { mutableIntStateOf(4) }
    var meetAt by rememberSaveable { mutableLongStateOf(defaultMeet.toEpochMilli()) }
    var deadline by rememberSaveable { mutableLongStateOf(defaultMeet.minus(Duration.ofHours(2)).toEpochMilli()) }
    var isPrivate by rememberSaveable { mutableStateOf(false) }
    var password by rememberSaveable { mutableStateOf("") }
    var picking by remember { mutableStateOf<PickingTime?>(null) }
    var showRoutePicker by remember { mutableStateOf(false) }
    val isCreating = groupsViewModel.createState == GroupActionState.Working

    LaunchedEffect(Unit) {
        groupsViewModel.resetCreate()
    }
    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
            }
            Text(stringResource(R.string.create_group_title), style = MaterialTheme.typography.titleMedium)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.m)
        ) {
            FieldLabel(stringResource(R.string.label_group_route))
            PickerField(
                text = route?.let { "${it.title} (${formatDistance(it.distance)})" } ?: stringResource(R.string.group_pick_route),
                icon = R.drawable.ic_route,
                onClick = {
                    groupsViewModel.loadRouteChoices()
                    showRoutePicker = true
                }
            )
            Spacer(modifier = Modifier.height(Spacing.m))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(MAX_GROUP_TITLE_LENGTH) },
                label = { Text(stringResource(R.string.label_group_title)) },
                singleLine = true,
                keyboardOptions = doneKeyboardOptions(),
                keyboardActions = hideKeyboardActions(),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(Spacing.s))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(MAX_GROUP_DESCRIPTION_LENGTH) },
                label = { Text(stringResource(R.string.label_group_description)) },
                minLines = 3,
                keyboardOptions = doneKeyboardOptions(),
                keyboardActions = hideKeyboardActions(),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(Spacing.m))
            FieldLabel(stringResource(R.string.label_group_capacity))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedIconButton(onClick = { capacity-- }, enabled = capacity > MIN_GROUP_CAPACITY) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    stringResource(R.string.group_capacity_value, capacity),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = Spacing.l)
                )
                OutlinedIconButton(onClick = { capacity++ }, enabled = capacity < MAX_GROUP_CAPACITY) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.group_capacity_increase))
                }
            }
            Text(
                stringResource(R.string.group_capacity_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.m))
            FieldLabel(stringResource(R.string.label_group_meet_at))
            PickerField(formatDateTime(Instant.ofEpochMilli(meetAt)), R.drawable.ic_schedule) { picking = PickingTime.MEET }
            Spacer(modifier = Modifier.height(Spacing.m))
            FieldLabel(stringResource(R.string.label_group_deadline))
            PickerField(formatDateTime(Instant.ofEpochMilli(deadline)), R.drawable.ic_groups) { picking = PickingTime.DEADLINE }
            if (deadline > meetAt) {
                Text(
                    stringResource(R.string.error_group_deadline_after_run),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.xs)
                )
            }
            Spacer(modifier = Modifier.height(Spacing.m))
            FieldLabel(stringResource(R.string.label_group_visibility))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                FilterChip(
                    selected = !isPrivate,
                    onClick = { isPrivate = false },
                    label = { Text(stringResource(R.string.group_visibility_public)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_groups), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
                    shape = MaterialTheme.shapes.extraLarge
                )
                FilterChip(
                    selected = isPrivate,
                    onClick = { isPrivate = true },
                    label = { Text(stringResource(R.string.group_visibility_private)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_lock), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
                    shape = MaterialTheme.shapes.extraLarge
                )
            }
            Text(
                stringResource(if (isPrivate) R.string.group_visibility_private_hint else R.string.group_visibility_public_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs)
            )
            if (isPrivate) {
                Spacer(modifier = Modifier.height(Spacing.s))
                PasswordField(
                    value = password,
                    onValueChange = { password = it.take(MAX_GROUP_PASSWORD_LENGTH) },
                    label = stringResource(R.string.label_group_password)
                )
                Text(
                    stringResource(R.string.group_password_hint, MIN_GROUP_PASSWORD_LENGTH, MAX_GROUP_PASSWORD_LENGTH),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs)
                )
            }
            (groupsViewModel.createState as? GroupActionState.Failed)?.let {
                Spacer(modifier = Modifier.height(Spacing.s))
                Text(it.message.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(Spacing.l))
        }
        AccentButton(
            text = stringResource(if (isCreating) R.string.creating else R.string.action_create_group),
            icon = R.drawable.ic_groups,
            onClick = {
                val selected = route ?: return@AccentButton
                groupsViewModel.create(
                    routeId = selected.routeId,
                    title = title,
                    description = description,
                    capacity = capacity,
                    meetAt = Instant.ofEpochMilli(meetAt),
                    joinDeadline = Instant.ofEpochMilli(deadline),
                    isPrivate = isPrivate,
                    password = password,
                    onCreated = onCreated
                )
            },
            enabled = route != null && title.isNotBlank() && deadline <= meetAt && !isCreating &&
                (!isPrivate || isValidGroupPassword(password)),
            modifier = Modifier.fillMaxWidth().padding(Spacing.m)
        )
    }

    picking?.let { target ->
        DateTimePickerDialog(
            initial = Instant.ofEpochMilli(if (target == PickingTime.MEET) meetAt else deadline),
            onDismiss = { picking = null },
            onPicked = { picked ->
                picking = null
                if (target == PickingTime.MEET) meetAt = picked.toEpochMilli() else deadline = picked.toEpochMilli()
            }
        )
    }

    if (showRoutePicker) {
        RoutePickerDialog(
            groupsViewModel = groupsViewModel,
            onDismiss = { showRoutePicker = false },
            onPick = {
                route = it
                showRoutePicker = false
            }
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = Spacing.xs))
}

@Composable
private fun PickerField(text: String, icon: Int, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.m)) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(Spacing.s))
            Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePickerDialog(initial: Instant, onDismiss: () -> Unit, onPicked: (Instant) -> Unit) {
    val zone = ZoneId.systemDefault()
    val local = initial.atZone(zone)
    var date by remember { mutableStateOf<LocalDate?>(null) }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = local.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(LocalDate.now(zone))
        }
    )
    val timeState = rememberTimePickerState(initialHour = local.hour, initialMinute = local.minute)

    val pickedDate = date
    if (pickedDate == null) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                }) { Text(stringResource(R.string.action_next)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        ) {
            DatePicker(state = dateState)
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            text = {
                TimePicker(
                    state = timeState,
                    colors = TimePickerDefaults.colors(
                        periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        periodSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onPicked(pickedDate.atTime(LocalTime.of(timeState.hour, timeState.minute)).atZone(zone).toInstant())
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun RoutePickerDialog(groupsViewModel: GroupsViewModel, onDismiss: () -> Unit, onPick: (GroupRouteChoice) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.group_pick_route)) },
        text = {
            val choices = groupsViewModel.routeChoices
            when {
                choices == null -> Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                choices.isEmpty() -> Text(stringResource(R.string.group_no_saved_routes))
                else -> LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(choices, key = { it.routeId }) { choice ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(choice) }
                                .padding(vertical = Spacing.s)
                        ) {
                            Text(choice.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                formatDistance(choice.distance),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
