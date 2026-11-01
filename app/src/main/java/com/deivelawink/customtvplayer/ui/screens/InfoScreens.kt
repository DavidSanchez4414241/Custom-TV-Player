package com.deivelawink.customtvplayer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GuideScreen() = InfoScreen("Guía de uso", """
    TV Personalizada no trae canales preinstalados. Añade una URL de reproducción individual o importa una lista M3U/M3U8 desde Importar / exportar.

    1. En Mis canales pulsa Añadir y completa el nombre, la URL, el país y la categoría.
    2. Puedes añadir opcionalmente un logo SVG, PNG o JPG mediante una URL directa.
    3. También puedes importar listas M3U/M3U8; se intentan leer el nombre, logo y grupo de cada canal.
    4. Pulsa Reproducir para abrir el canal. El reproductor admite pantalla completa, giro, segundo plano y PiP cuando el dispositivo lo permite.

       Ejemplo de stream:
       https://servidor.example/canal.m3u8

    En Categorías puedes crear o borrar países, categorías y subcategorías. Al borrar una categoría también se borran sus canales.

    Configuración personal exporta todos tus canales, países y categorías en JSON. Puedes guardarla como copia de seguridad, editarla fuera de la aplicación e importarla después para reemplazar la configuración actual.

    La pantalla de Inicio permite expandir y colapsar cada país o categoría con las flechas ↑ y ↓.
""".trimIndent())

@Composable
fun LegalScreen() = InfoScreen("Aviso legal", """
    TV Personalizada es una herramienta local de reproducción y gestión de listas. No aloja, proporciona ni distribuye canales, listas IPTV o URLs preinstaladas.

    El usuario introduce e importa sus propias fuentes y debe comprobar que tiene los derechos y autorizaciones necesarios. Las fuentes externas están sujetas a sus propias licencias y condiciones. La aplicación no está afiliada a TDTChannels ni a ninguna cadena o plataforma.

    Streamlink es un proyecto de código abierto independiente, disponible en:
    https://github.com/streamlink/streamlink
    Streamlink no es propiedad de TV Personalizada ni está afiliado a esta aplicación.
""".trimIndent())

@Composable
fun SettingsScreen(theme: String, language: String, onThemeChanged: (String) -> Unit, onLanguageChanged: (String) -> Unit, onLegal: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineSmall)
        Text("Idioma", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("es" to "Español", "en" to "English").forEach { (value, label) ->
                FilterChip(language == value, { onLanguageChanged(value) }, label = { Text(label) })
            }
        }
        Text("Tema", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system" to "Dispositivo", "light" to "Claro", "dark" to "Oscuro").forEach { (value, label) ->
                FilterChip(theme == value, { onThemeChanged(value) }, label = { Text(label) })
            }
        }
        Button(onClick = onLegal) { Text("Parámetros y aviso legal") }
        Text("Los canales, regiones y listas importadas permanecen almacenados localmente en el dispositivo.")
    }
}

@Composable
private fun InfoScreen(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}
