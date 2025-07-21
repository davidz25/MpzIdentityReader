package org.multipaz.identityreader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import multipazidentityreader.composeapp.generated.resources.Res
import multipazidentityreader.composeapp.generated.resources.about_screen_title
import org.jetbrains.compose.resources.stringResource
import org.multipaz.compose.webview.MarkdownText

@Composable
fun AboutScreen(
    onBackPressed: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = AnnotatedString(stringResource(Res.string.about_screen_title)),
                onBackPressed = onBackPressed,
            )
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                MarkdownText(
                    content = """
                        Identity Reader version **${BuildConfig.VERSION}**
                        
                        This software is provided as-is and does not come with any guarantees
                        or promises about its performance, functionality, or fitness for a
                        particular purpose.
                        
                        This is not a supported Google product.

                        Visit [https://apps.multipaz.org](https://apps.multipaz.org) for more
                        information and updates.
                    """.trimIndent()
                )
            }
        }
    }
}
