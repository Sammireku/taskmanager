package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SavedLocation
import com.example.places.PlaceSuggestion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SavedPlacesSection(
    viewModel: TaskViewModel,
    modifier: Modifier = Modifier
) {
    val savedLocations by viewModel.savedLocations.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingLocation by remember { mutableStateOf<SavedLocation?>(null) }
    var presetCategoryToOpen by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier.fillMaxWidth().testTag("saved_places_section_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Frequent Locations",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Anchors for search bias and spatial geofences",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FilledTonalButton(
                    onClick = {
                        presetCategoryToOpen = null
                        editingLocation = null
                        showAddDialog = true
                    },
                    modifier = Modifier.testTag("add_saved_place_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (savedLocations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No frequent locations saved yet.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Quick Presets:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "Home" to "HOME",
                                "Work" to "WORK",
                                "School" to "SCHOOL",
                                "Market" to "MARKET",
                                "Gym" to "GYM"
                            ).forEach { (label, cat) ->
                                OutlinedButton(
                                    onClick = {
                                        presetCategoryToOpen = cat
                                        editingLocation = null
                                        showAddDialog = true
                                    },
                                    modifier = Modifier.height(34.dp).testTag("quick_preset_$cat")
                                ) {
                                    Text("+ $label", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    savedLocations.forEach { loc ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth().testTag("saved_location_item_${loc.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = loc.displayIcon, fontSize = 18.sp)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = loc.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "${loc.radiusMeters.toInt()}m",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (loc.address.isNotBlank()) {
                                            Text(
                                                text = loc.address,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.deleteSavedLocationById(loc.id) },
                                    modifier = Modifier.testTag("delete_saved_place_${loc.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete ${loc.name}",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        SavePlaceDialog(
            initialCategory = presetCategoryToOpen ?: "HOME",
            initialLocation = editingLocation,
            viewModel = viewModel,
            onDismiss = {
                showAddDialog = false
                editingLocation = null
                presetCategoryToOpen = null
            },
            onSave = { newLocation ->
                viewModel.insertSavedLocation(newLocation)
                showAddDialog = false
                editingLocation = null
                presetCategoryToOpen = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SavePlaceDialog(
    initialCategory: String,
    initialLocation: SavedLocation?,
    viewModel: TaskViewModel,
    onDismiss: () -> Unit,
    onSave: (SavedLocation) -> Unit
) {
    var name by remember {
        mutableStateOf(initialLocation?.name ?: when (initialCategory.uppercase()) {
            "HOME" -> "Home"
            "WORK" -> "Work"
            "SCHOOL" -> "School"
            "MARKET" -> "Market"
            "GYM" -> "Gym"
            else -> ""
        })
    }
    var category by remember { mutableStateOf(initialLocation?.category ?: initialCategory) }
    var address by remember { mutableStateOf(initialLocation?.address ?: "") }
    var latitude by remember { mutableStateOf(initialLocation?.latitude ?: 0.0) }
    var longitude by remember { mutableStateOf(initialLocation?.longitude ?: 0.0) }
    var radiusMeters by remember { mutableFloatStateOf(initialLocation?.radiusMeters ?: 150f) }

    var addressQuery by remember { mutableStateOf(initialLocation?.address ?: "") }
    var suggestions by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isLocating by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialLocation != null) "Edit Frequent Location" else "Add Frequent Location",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Preset Category", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        "HOME" to "🏠 Home",
                        "WORK" to "💼 Work",
                        "SCHOOL" to "🏫 School",
                        "MARKET" to "🛒 Market",
                        "GYM" to "🏋️ Gym",
                        "CUSTOM" to "📍 Custom"
                    )
                    presets.forEach { (catKey, label) ->
                        FilterChip(
                            selected = category == catKey,
                            onClick = {
                                category = catKey
                                if (name.isBlank() || name in listOf("Home", "Work", "School", "Market", "Gym")) {
                                    name = label.substringAfter(" ").trim()
                                }
                            },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Place Name (e.g. Home, Trader Joe's, Dentist)") },
                    modifier = Modifier.fillMaxWidth().testTag("saved_place_name_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = addressQuery,
                    onValueChange = { query ->
                        addressQuery = query
                        searchJob?.cancel()
                        if (query.isNotBlank()) {
                            searchJob = coroutineScope.launch {
                                delay(300)
                                isSearching = true
                                val bias = viewModel.getLocationBiasCenter()
                                val preds = viewModel.placesService.getAutocompletePredictions(query, biasCenter = bias)
                                suggestions = preds
                                isSearching = false
                            }
                        } else {
                            suggestions = emptyList()
                        }
                    },
                    label = { Text("Search Address or Venue") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("saved_place_address_input"),
                    singleLine = true
                )

                if (suggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 2.dp,
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Column {
                            suggestions.take(3).forEach { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            coroutineScope.launch {
                                                val details = viewModel.placesService.fetchPlaceDetails(suggestion.placeId)
                                                if (details?.latLng != null) {
                                                    address = details.address ?: suggestion.fullText
                                                    addressQuery = address
                                                    latitude = details.latLng.latitude
                                                    longitude = details.latLng.longitude
                                                    if (name.isBlank()) name = details.name
                                                }
                                                suggestions = emptyList()
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(suggestion.primaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(suggestion.secondaryText, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            isLocating = true
                            val loc = viewModel.locationHelper.getCurrentLocation()
                            if (loc != null) {
                                latitude = loc.latitude
                                longitude = loc.longitude
                                val addr = viewModel.locationHelper.getAddressFromCoordinates(loc.latitude, loc.longitude)
                                address = addr
                                addressQuery = addr
                            }
                            isLocating = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("use_current_location_button")
                ) {
                    if (isLocating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Use My Current Location", fontSize = 12.sp)
                }

                if (latitude != 0.0 && longitude != 0.0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Coords: ${String.format(Locale.US, "%.4f, %.4f", latitude, longitude)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Geofence Radius: ${radiusMeters.toInt()} meters",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = radiusMeters,
                    onValueChange = { radiusMeters = it },
                    valueRange = 50f..500f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth().testTag("saved_place_radius_slider")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        coroutineScope.launch {
                            var finalLat = latitude
                            var finalLng = longitude
                            var finalAddress = address.ifBlank { addressQuery }.trim()

                            // Ensure coordinates are never left at 0.0, 0.0
                            if (finalLat == 0.0 && finalLng == 0.0) {
                                val queryToResolve = finalAddress.ifBlank { name }
                                val resolved = viewModel.resolveLocationCoordinates(queryToResolve)
                                if (resolved != null && (resolved.second != 0.0 || resolved.third != 0.0)) {
                                    finalLat = resolved.second
                                    finalLng = resolved.third
                                    if (finalAddress.isBlank()) finalAddress = resolved.first
                                } else {
                                    val cur = viewModel.userLocation.value ?: viewModel.locationHelper.getCurrentLocation()
                                    if (cur != null) {
                                        finalLat = cur.latitude
                                        finalLng = cur.longitude
                                        if (finalAddress.isBlank()) {
                                            finalAddress = viewModel.locationHelper.getAddressFromCoordinates(cur.latitude, cur.longitude)
                                        }
                                    }
                                }
                            }

                            onSave(
                                SavedLocation(
                                    id = initialLocation?.id ?: 0,
                                    name = name.trim(),
                                    address = finalAddress,
                                    latitude = finalLat,
                                    longitude = finalLng,
                                    radiusMeters = radiusMeters,
                                    category = category
                                )
                            )
                        }
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_frequent_place_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
