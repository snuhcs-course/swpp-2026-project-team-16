package com.example.runtime.ui.route

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.data.remote.Place
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.theme.Spacing

private const val MAX_VISIBLE_SUGGESTIONS = 5

@Composable
fun PlaceSearchField(
    field: PlaceField,
    label: String,
    onInputChange: (String) -> Unit,
    onSelect: (Place) -> Unit,
    modifier: Modifier = Modifier,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = field.input,
            onValueChange = onInputChange,
            label = { Text(label) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            supportingText = field.selected?.let { place -> { Text(place.address, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            trailingIcon = when {
                field.isSearching -> {
                    { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                }
                else -> trailingAction
            },
            modifier = Modifier.fillMaxWidth()
        )
        when {
            field.suggestions.isNotEmpty() -> Card(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                field.suggestions.take(MAX_VISIBLE_SUGGESTIONS).forEach { place ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(place) }
                            .padding(horizontal = Spacing.m, vertical = 10.dp)
                    ) {
                        Text(
                            place.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            place.address,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            field.noResults -> Text(
                stringResource(R.string.no_places_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs, start = Spacing.xs)
            )
            field.searchError != null -> Text(
                field.searchError!!.asString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.xs, start = Spacing.xs)
            )
        }
    }
}
