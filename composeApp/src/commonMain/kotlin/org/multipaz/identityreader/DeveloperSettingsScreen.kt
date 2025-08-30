package org.multipaz.identityreader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import multipazidentityreader.composeapp.generated.resources.Res
import multipazidentityreader.composeapp.generated.resources.developer_settings_screen_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun DeveloperSettingsScreen(
    settingsModel: SettingsModel,
    onBackPressed: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = AnnotatedString(stringResource(Res.string.developer_settings_screen_title)),
                onBackPressed = onBackPressed,
            )
        },
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        Surface(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                Text(
                    modifier = Modifier.padding(vertical = 16.dp),
                    text = """
This screen contain developer settings. If you are not a developer you should probably not
change any of these
                    """.trimIndent().replace("\n", " ").trim(),
                )

                val entries = mutableListOf<@Composable () -> Unit>()
                entries.add {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.Start),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = settingsModel.bleL2capEnabled.collectAsState().value,
                            onCheckedChange = { value ->
                                settingsModel.bleL2capEnabled.value = value
                            },
                        )
                        EntryItem(
                            key = "Use L2CAP",
                            valueText = "If enabled, L2CAP will be enabled for Bluetooth Low Energy connections"
                        )
                    }
                }

                EntryList(
                    title = null,
                    entries = entries
                )
            }
        }
    }
}
