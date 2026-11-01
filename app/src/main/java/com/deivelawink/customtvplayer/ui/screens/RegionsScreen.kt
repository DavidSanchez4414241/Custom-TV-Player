package com.deivelawink.customtvplayer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deivelawink.customtvplayer.data.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionsScreen(countries: List<Country>, onSave: (List<Country>) -> Unit) {
    var local by remember(countries) { mutableStateOf(countries) }
    var selectedCode by remember(countries) { mutableStateOf(countries.firstOrNull()?.code.orEmpty()) }
    var newCountryName by remember { mutableStateOf("") }
    var newCountryCode by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("") }
    var parentId by remember { mutableStateOf<String?>(null) }
    var parentExpanded by remember { mutableStateOf(false) }
    val selected = local.firstOrNull { it.code == selectedCode }

    fun save(updated: List<Country>) {
        local = updated
        onSave(updated)
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Países y categorías", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Eliminar un país o categoría también elimina sus canales.", style = MaterialTheme.typography.bodyMedium) }
        item {
            Button(onClick = {
                if (selected == null) {
                    val defaults = listOf(Country("ES", "España", DefaultCategories.spain(), true))
                    save(defaults)
                    selectedCode = "ES"
                } else {
                    val restored = if (selected.code == "ES") {
                        DefaultCategories.spain()
                    } else {
                        DefaultCategories.forCountry(selected.code)
                    }
                    save(local.map {
                        if (it.code == selected.code) it.copy(categories = restored) else it
                    })
                }
            }) { Text("Restaurar parámetros por defecto") }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                local.forEach { country ->
                    Row {
                        FilterChip(
                            selected = country.code == selectedCode,
                            onClick = { selectedCode = country.code },
                            label = { Text(country.name) }
                        )
                        if (!country.builtIn) {
                            TextButton(onClick = {
                                save(local.filterNot { it.code == country.code })
                                selectedCode = local.firstOrNull { it.code != country.code }?.code.orEmpty()
                            }) { Text("Borrar") }
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(newCountryCode, { newCountryCode = it.uppercase() }, Modifier.weight(0.3f), label = { Text("Código") })
                OutlinedTextField(newCountryName, { newCountryName = it }, Modifier.weight(0.7f), label = { Text("Nuevo país") })
                Button(onClick = {
                    if (newCountryCode.trim().length == 2 && newCountryName.isNotBlank() &&
                        local.none { it.code == newCountryCode.trim() }) {
                        val country = Country(newCountryCode.trim(), newCountryName.trim())
                        save(local + country)
                        selectedCode = country.code
                        newCountryCode = ""; newCountryName = ""
                    }
                }) { Text("Añadir") }
            }
        }
        if (selected != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Categorías de ${selected.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
            }
            items(selected.categories.filter { it.parentId == null }, key = { it.id }) { category ->
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(category.name, Modifier.weight(1f))
                        if (!category.builtIn) {
                            TextButton(onClick = {
                                val ids = selected.categories.filter { it.id == category.id || it.parentId == category.id }.map { it.id }.toSet()
                                save(local.map { country ->
                                    if (country.code == selectedCode) country.copy(categories = country.categories.filterNot { it.id in ids }) else country
                                })
                            }) { Text("Borrar") }
                        }
                    }
                    selected.categories.filter { it.parentId == category.id }.forEach { child ->
                        Row(Modifier.padding(start = 24.dp).fillMaxWidth()) {
                            Text(child.name, Modifier.weight(1f))
                            if (!child.builtIn) TextButton(onClick = {
                                save(local.map { country ->
                                    if (country.code == selectedCode) country.copy(categories = country.categories - child) else country
                                })
                            }) { Text("Borrar") }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(parentExpanded, { parentExpanded = !parentExpanded }, Modifier.weight(0.4f)) {
                        OutlinedTextField(
                            selected.categories.firstOrNull { it.id == parentId }?.name ?: "Categoría raíz",
                            {}, Modifier.menuAnchor(), readOnly = true
                        )
                        ExposedDropdownMenu(parentExpanded, { parentExpanded = false }) {
                            DropdownMenuItem({ Text("Categoría raíz") }, { parentId = null; parentExpanded = false })
                            selected.categories.filter { it.parentId == null }.forEach {
                                DropdownMenuItem({ Text(it.name) }, { parentId = it.id; parentExpanded = false })
                            }
                        }
                    }
                    OutlinedTextField(newCategory, { newCategory = it }, Modifier.weight(0.6f), label = { Text("Nueva categoría") })
                    Button(onClick = {
                        if (newCategory.isNotBlank()) {
                            val category = Category(
                                id = DefaultCategories.slug("${selectedCode}-${newCategory}"),
                                name = newCategory.trim(),
                                parentId = parentId,
                                countryCode = selectedCode
                            )
                            save(local.map { country ->
                                if (country.code == selectedCode && country.categories.none { it.id == category.id }) {
                                    country.copy(categories = country.categories + category)
                                } else country
                            })
                            newCategory = ""
                        }
                    }) { Text("Añadir") }
                }
            }
        }
    }
}
