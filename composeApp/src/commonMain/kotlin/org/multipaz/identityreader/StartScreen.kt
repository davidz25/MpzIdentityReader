package org.multipaz.identityreader

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import multipazidentityreader.composeapp.generated.resources.Res
import multipazidentityreader.composeapp.generated.resources.about_screen_title
import multipazidentityreader.composeapp.generated.resources.nfc_icon
import multipazidentityreader.composeapp.generated.resources.qr_icon
import multipazidentityreader.composeapp.generated.resources.settings_screen_title
import multipazidentityreader.composeapp.generated.resources.start_screen_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.multipaz.cbor.DataItem
import org.multipaz.compose.permissions.rememberBluetoothPermissionState
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodBle
import org.multipaz.mdoc.nfc.scanNfcMdocReader
import org.multipaz.mdoc.transport.MdocTransport
import org.multipaz.mdoc.transport.MdocTransportFactory
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.nfc.nfcTagScanningSupported
import org.multipaz.nfc.nfcTagScanningSupportedWithoutDialog
import org.multipaz.prompt.PromptModel
import org.multipaz.util.Logger
import org.multipaz.util.UUID
import org.multipaz.util.toBase64Url

private const val TAG = "StartScreen"

private suspend fun signIn(
    explicitSignIn: Boolean,
    settingsModel: SettingsModel,
    readerBackendClient: ReaderBackendClient
) {
    Logger.i(TAG, "signIn, explicitSignIn = $explicitSignIn")
    val nonce = readerBackendClient.signInGetNonce()
    val (googleIdTokenString, signInData) = signInWithGoogle(
        explicitSignIn = explicitSignIn,
        serverClientId = BuildConfig.IDENTITY_READER_BACKEND_CLIENT_ID,
        nonce = nonce.toByteArray().toBase64Url(),
        httpClientEngineFactory = platformHttpClientEngineFactory(),
    )
    readerBackendClient.signIn(nonce, googleIdTokenString)
    settingsModel.signedIn.value = signInData
}

private suspend fun signOut(
    settingsModel: SettingsModel,
    readerBackendClient: ReaderBackendClient
) {
    Logger.i(TAG, "signOut()")
    settingsModel.explicitlySignedOut.value = true
    settingsModel.signedIn.value = null
    signInWithGoogleSignedOut()
    readerBackendClient.signOut()
    if (settingsModel.readerAuthMethod.value == ReaderAuthMethod.IDENTITY_FROM_GOOGLE_ACCOUNT ||
        settingsModel.readerAuthMethod.value == ReaderAuthMethod.STANDARD_READER_AUTH_WITH_GOOGLE_ACCOUNT_DETAILS
    ) {
        settingsModel.readerAuthMethod.value = ReaderAuthMethod.STANDARD_READER_AUTH
        settingsModel.readerAuthMethodGoogleIdentity.value = null
        try {
            readerBackendClient.getKey()
        } catch (e: Throwable) {
            Logger.w(TAG, "Error priming cache for standard reader auth", e)
        }
    }
}

@Composable
fun StartScreen(
    settingsModel: SettingsModel,
    readerBackendClient: ReaderBackendClient,
    promptModel: PromptModel,
    mdocTransportOptionsForNfcEngagement: MdocTransportOptions,
    onScanQrClicked: () -> Unit,
    onNfcHandover: suspend (
        transport: MdocTransport,
        encodedDeviceEngagement: ByteString,
        handover: DataItem,
        updateMessage: ((message: String) -> Unit)?
    ) -> Unit,
    onSettingsClicked: () -> Unit,
    onAboutClicked: () -> Unit,
    onReaderIdentitiesClicked: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val blePermissionState = rememberBluetoothPermissionState()
    val showAccountDialog = remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val showSignInErrorDialog = remember { mutableStateOf<Throwable?>(null) }

    showSignInErrorDialog.value?.let {
        AlertDialog(
            onDismissRequest = { showSignInErrorDialog.value = null },
            confirmButton = {
                TextButton(
                    onClick = { showSignInErrorDialog.value = null }
                ) {
                    Text(text = "Close")
                }
            },
            title = {
                Text(text = "Error signing in")
            },
            text = {
                Text(text = it.toString())
            }
        )
    }

    if (showAccountDialog.value) {
        AccountDialog(
            settingsModel = settingsModel,
            onDismissed = { showAccountDialog.value = false },
            onUseWithoutGoogleAccountClicked = {
                coroutineScope.launch {
                    try {
                        showAccountDialog.value = false
                        signOut(
                            settingsModel = settingsModel,
                            readerBackendClient = readerBackendClient
                        )
                    } catch (e: Throwable) {
                        Logger.w(TAG, "Error signing out", e)
                    }
                }
            },
            onSignInToGoogleClicked = {
                coroutineScope.launch {
                    try {
                        showAccountDialog.value = false
                        signIn(
                            explicitSignIn = true,
                            settingsModel = settingsModel,
                            readerBackendClient = readerBackendClient
                        )
                    } catch (_: SignInWithGoogleDismissedException) {
                        // Do nothing
                    } catch (e: Throwable) {
                        showSignInErrorDialog.value = e
                    }
                }
            },
        )
    }

    // We can't do anything at all without Bluetooth permissions so make request those
    // upfront if they're not there...
    if (!blePermissionState.isGranted) {
        RequestBluetoothPermission(blePermissionState)
        return
    }

    ModalNavigationDrawer(
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Multipaz Identity Reader",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider()

                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = null
                            )
                        },
                        label = { Text(text = stringResource(Res.string.settings_screen_title)) },
                        selected = false,
                        onClick = {
                            onSettingsClicked()
                            coroutineScope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Help,
                                contentDescription = null
                            )
                        },
                        label = { Text(text = stringResource(Res.string.about_screen_title)) },
                        selected = false,
                        onClick = {
                            onAboutClicked()
                            coroutineScope.launch { drawerState.close() }
                        }
                    )
                }
            }
        },
        drawerState = drawerState
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(stringResource(Res.string.start_screen_title))
                        }
                    },
                    onMenuPressed = {
                        coroutineScope.launch {
                            drawerState.open()
                        }
                    },
                    onAccountPressed = {
                        showAccountDialog.value = true
                    },
                    settingsModel = settingsModel,
                )
            },
        ) { innerPadding ->
            Surface(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                color = MaterialTheme.colorScheme.background
            ) {
                StartScreenWithPermissions(
                    settingsModel = settingsModel,
                    promptModel = promptModel,
                    mdocTransportOptionsForNfcEngagement = mdocTransportOptionsForNfcEngagement,
                    onScanQrClicked = onScanQrClicked,
                    onNfcHandover = onNfcHandover,
                    onOpportunisticSignInToGoogle = {
                        // Only opportunistically try to sign in the user except
                        //  - they explicitly signed out
                        //  - they dismissed the dialog for an opportunistic sign-in attempt
                        if (settingsModel.signedIn.value == null && !settingsModel.explicitlySignedOut.value) {
                            coroutineScope.launch {
                                try {
                                    signIn(
                                        explicitSignIn = false,
                                        settingsModel = settingsModel,
                                        readerBackendClient = readerBackendClient
                                    )
                                } catch (e: SignInWithGoogleDismissedException) {
                                    // If the user explicitly dismissed this, don't try to sign them in again
                                    settingsModel.explicitlySignedOut.value = true
                                } catch (e: Throwable) {
                                    Logger.e(TAG, "Error signing into Google", e)
                                }
                            }
                        }
                    },
                    onReaderIdentitiesClicked = onReaderIdentitiesClicked
                )
            }
        }
    }
}

@Composable
private fun StartScreenWithPermissions(
    settingsModel: SettingsModel,
    promptModel: PromptModel,
    mdocTransportOptionsForNfcEngagement: MdocTransportOptions,
    onScanQrClicked: () -> Unit,
    onNfcHandover: suspend (
        transport: MdocTransport,
        encodedDeviceEngagement: ByteString,
        handover: DataItem,
        updateMessage: ((message: String) -> Unit)?
    ) -> Unit,
    onOpportunisticSignInToGoogle: () -> Unit,
    onReaderIdentitiesClicked: () -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    val selectedQueryName = remember { mutableStateOf(
        ReaderQuery.valueOf(settingsModel.selectedQueryName.value).name
    )}
    val coroutineScope = rememberCoroutineScope { promptModel }
    val nfcComposition by rememberLottieComposition {
        LottieCompositionSpec.JsonString(
            if (isDarkTheme) {
                Res.readBytes("files/nfc_animation_dark.json").decodeToString()
            } else {
                Res.readBytes("files/nfc_animation.json").decodeToString()
            }
        )
    }
    val nfcProgress by animateLottieCompositionAsState(
        composition = nfcComposition,
        iterations = Compottie.IterateForever
    )

    // On Platforms that support NFC scanning without a dialog, start scanning as soon
    // as we enter this screen. We'll get canceled when switched away because `coroutineScope`
    // will get canceled.
    //
    LaunchedEffect(Unit) {
        if (nfcTagScanningSupportedWithoutDialog) {
            coroutineScope.launch {
                scanNfcMdocReader(
                    message = null,
                    options = mdocTransportOptionsForNfcEngagement,
                    transportFactory = MdocTransportFactory.Default,
                    // TODO: maybe do UI
                    selectConnectionMethod = { connectionMethods -> connectionMethods.first() },
                    negotiatedHandoverConnectionMethods = listOf(
                        MdocConnectionMethodBle(
                            supportsPeripheralServerMode = false,
                            supportsCentralClientMode = true,
                            peripheralServerModeUuid = null,
                            centralClientModeUuid = UUID.randomUUID(),
                        )
                    ),
                    onHandover = onNfcHandover
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        onOpportunisticSignInToGoogle()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppUpdateCard()

        Spacer(modifier = Modifier.weight(0.1f))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(
                16.dp,
                alignment = Alignment.CenterHorizontally
            )
        ) {
            for (query in ReaderQuery.entries) {
                FilterChip(
                    selected = query.name == selectedQueryName.value,
                    onClick = {
                        selectedQueryName.value = query.name
                        settingsModel.selectedQueryName.value = query.name
                    },
                    label = { Text(text = query.displayName) },
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.2f))

        if (!nfcTagScanningSupported) {
            // This is for phones that don't support NFC scanning
            Text(
                text = "Scan QR code from Wallet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        } else {
            Image(
                painter = rememberLottiePainter(
                    composition = nfcComposition,
                    progress = { nfcProgress },
                ),
                contentDescription = null,
                modifier = Modifier.size(200.dp)
            )
            if (nfcTagScanningSupportedWithoutDialog) {
                // This is for phones that support NFC scanning w/o dialog (Android)
                Text(
                    text = "Hold to Wallet",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            } else {
                // This is for phones that requires a dialog for NFC scanning (iOS)
                Text(
                    text = "Scan NFC or QR from Wallet",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.2f))
        RequestingData(
            settingsModel = settingsModel,
            onClicked = onReaderIdentitiesClicked
        )
        Spacer(modifier = Modifier.weight(0.3f))

        // Only show the "Scan NFC" button on platforms which require the system NFC Scan dialog (iOS)
        // and if the device actually supports NFC scanning functionality.
        if (nfcTagScanningSupported && !nfcTagScanningSupportedWithoutDialog) {
            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        scanNfcMdocReader(
                            message = "Hold to Wallet",
                            options = MdocTransportOptions(bleUseL2CAP = true),
                            transportFactory = MdocTransportFactory.Default,
                            // TODO: maybe do UI
                            selectConnectionMethod = { connectionMethods -> connectionMethods.first() },
                            negotiatedHandoverConnectionMethods = listOf(
                                MdocConnectionMethodBle(
                                    supportsPeripheralServerMode = false,
                                    supportsCentralClientMode = true,
                                    peripheralServerModeUuid = null,
                                    centralClientModeUuid = UUID.randomUUID(),
                                )
                            ),
                            onHandover = onNfcHandover
                        )
                    }
                }
            ) {
                Icon(
                    painter = painterResource(Res.drawable.nfc_icon),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                Text(
                    text = "Scan NFC"
                )
            }
        }

        OutlinedButton(
            onClick = { onScanQrClicked() }
        ) {
            Icon(
                painter = painterResource(Res.drawable.qr_icon),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Text(
                text = "Scan QR Code"
            )
        }
    }
}
