package com.example.runtime.ui.route

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runtime.R
import com.example.runtime.data.remote.Place
import com.example.runtime.ui.common.asString

private const val MAX_VISIBLE_SUGGESTIONS = 5

@Composable
fun PlaceSearchField(
    field: PlaceField,
    label: String,
    onInputChange: (String) -> Unit,
    onSelect: (Place) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = field.input,
            onValueChange = onInputChange,
            label = { Text(label) },
            singleLine = true,
            supportingText = field.selected?.let { place -> { Text(place.address) } },
            trailingIcon = if (field.isSearching) {
                { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth()
        )
        when {
            field.suggestions.isNotEmpty() -> Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                field.suggestions.take(MAX_VISIBLE_SUGGESTIONS).forEach { place ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(place) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            place.name,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            place.address,
                            color = Color.Gray,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            field.noResults -> Text(
                stringResource(R.string.no_places_found),
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
            field.searchError != null -> Text(
                field.searchError!!.asString(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }
    }
}
