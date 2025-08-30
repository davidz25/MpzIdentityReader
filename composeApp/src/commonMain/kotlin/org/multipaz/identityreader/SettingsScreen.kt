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
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import multipazidentityreader.composeapp.generated.resources.Res
import multipazidentityreader.composeapp.generated.resources.settings_screen_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    settingsModel: SettingsModel,
    onBackPressed: () -> Unit,
    onReaderIdentityPressed: () -> Unit,
    onTrustedIssuersPressed: () -> Unit,
    onDeveloperSettingsPressed: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = AnnotatedString(stringResource(Res.string.settings_screen_title)),
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
                val entries = mutableListOf<@Composable () -> Unit>()
                entries.add {
                    Row(
                        modifier = Modifier.clickable { onReaderIdentityPressed() },
                        horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.Start),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Fingerprint,
                            contentDescription = null
                        )
                        EntryItem(
                            key = "Reader identity",
                            valueText = "Configure how this reader identifies itself to wallets " +
                                    "when requesting identity data"
                        )
                    }
                }
                entries.add {
                    Row(
                        modifier = Modifier.clickable { onTrustedIssuersPressed() },
                        horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.Start),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccountBalance,
                            contentDescription = null
                        )
                        EntryItem(
                            key = "Trusted issuers",
                            valueText = "Manage the list of identity issuers this reader will accept "
                                    + "identity credentials from"
                        )
                    }
                }
                if (settingsModel.devMode.value) {
                    entries.add {
                        Row(
                            modifier = Modifier.clickable { onDeveloperSettingsPressed() },
                            horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.Start),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Science,
                                contentDescription = null
                            )
                            EntryItem(
                                key = "Developer settings",
                                valueText = "Manage developer settings"
                            )
                        }
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