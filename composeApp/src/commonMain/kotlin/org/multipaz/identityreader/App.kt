package org.multipaz.identityreader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.bytestring.ByteString
import multipazidentityreader.composeapp.generated.resources.Res
import multipazidentityreader.composeapp.generated.resources.about_screen_title
import multipazidentityreader.composeapp.generated.resources.trusted_issuers_screen_title
import org.jetbrains.compose.resources.stringResource
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.Simple
import org.multipaz.compose.prompt.PromptDialogs
import org.multipaz.crypto.X509Cert
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.multipaz.trustmanagement.CompositeTrustManager
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.trustmanagement.TrustManagerLocal
import org.multipaz.trustmanagement.TrustMetadata
import org.multipaz.util.Platform
import org.multipaz.util.fromBase64Url
import org.multipaz.util.fromHex
import org.multipaz.util.toBase64Url

data class UrlLaunchData(
    val url: String,
    val finish: () -> Unit,
)

/**
 * App instance.
 *
 * @param urlLaunchData if launched from an intent for mdoc://<base64-of-device-engagement>, this
 *   contains the URL and the app will start at [SelectRequestDestination]. This is useful for when
 *   being launched from the camera.
 */
class App(
    private val urlLaunchData: UrlLaunchData?
) {
    private val promptModel = Platform.promptModel
    private var startDestination: String? = null
    private val readerModel = ReaderModel()
    private lateinit var documentTypeRepository: DocumentTypeRepository
    private lateinit var builtInTrustManager: TrustManagerLocal
    private lateinit var userTrustManager: TrustManagerLocal
    private lateinit var compositeTrustManager: TrustManager
    private lateinit var settingsModel: SettingsModel
    private lateinit var readerBackendClient: ReaderBackendClient

    private val initLock = Mutex()
    private var initialized = false

    suspend fun initialize() {
        initLock.withLock {
            if (initialized) {
                return
            }

            startDestination = if (urlLaunchData != null) {
                val encodedDeviceEngagement =
                    ByteString(urlLaunchData.url.substringAfter("mdoc:").fromBase64Url())
                readerModel.reset()
                readerModel.setConnectionEndpoint(
                    encodedDeviceEngagement = encodedDeviceEngagement,
                    handover = Simple.NULL,
                    existingTransport = null
                )
                SelectRequestDestination.route
            } else {
                null
            }

            documentTypeRepository = DocumentTypeRepository()
            documentTypeRepository.addDocumentType(DrivingLicense.getDocumentType())
            // TODO: Get built-in trusted issuers from reader backend
            builtInTrustManager = TrustManagerLocal(EphemeralStorage())
            builtInTrustManager.addX509Cert(
                certificate = X509Cert(encodedCertificate = "308202a83082022da003020102021036ead7e431722dbf66c76398266f8020300a06082a8648ce3d040303302e311f301d06035504030c164f5746204d756c746970617a20544553542049414341310b300906035504060c025553301e170d3234313230313030303030305a170d3334313230313030303030305a302e311f301d06035504030c164f5746204d756c746970617a20544553542049414341310b300906035504060c0255533076301006072a8648ce3d020106052b8104002203620004f900f27bbd26d8ed2594f5cc8d58f1559cf79b993a6a04fec2287e2fbf5bee3caa525f7db1b7949e9c5a2c3f9c981dc72b7b70900edf995252a1b05cfbd0838648779b1ea7f98a07e51ba569259385605f332463b1f54e0e4a2c1cb0839db3d5a382010e3082010a300e0603551d0f0101ff04040302010630120603551d130101ff040830060101ff020100304c0603551d1204453043864168747470733a2f2f6769746875622e636f6d2f6f70656e77616c6c65742d666f756e646174696f6e2d6c6162732f6964656e746974792d63726564656e7469616c30560603551d1f044f304d304ba049a047864568747470733a2f2f6769746875622e636f6d2f6f70656e77616c6c65742d666f756e646174696f6e2d6c6162732f6964656e746974792d63726564656e7469616c2f63726c301d0603551d0e04160414ab651be056c29053f1dd7f6ce487be68de60c9f5301f0603551d23041830168014ab651be056c29053f1dd7f6ce487be68de60c9f5300a06082a8648ce3d0403030369003066023100e5fec5304626e9ee0456c0421acffa40f38b1f75b7fec4779dea4dfc463ea1dd94d36b3cadec950e0c87f62e580703450231009ed622dee7f933898b37120a06a8362a6ebae99816c4e2d5f928ffbab4bc9f4591a85d526a90d67dafe8793c85d1a246".fromHex()),
                metadata = TrustMetadata(
                    displayName = "OWF Multipaz TestApp",
                    displayIcon = null,
                    privacyPolicyUrl = "https://apps.multipaz.org",
                    testOnly = true,
                )
            )
            userTrustManager = TrustManagerLocal(Platform.storage)
            compositeTrustManager = CompositeTrustManager(listOf(builtInTrustManager, userTrustManager))

            settingsModel = SettingsModel.create(Platform.storage)

            readerBackendClient = ReaderBackendClient(
                // Use the deployed backend by default..
                readerBackendUrl = BuildConfig.IDENTITY_READER_BACKEND_URL,
                //readerBackendUrl = "http://127.0.0.1:8020",
                storage = Platform.nonBackedUpStorage,
                httpClientEngineFactory = platformHttpClientEngineFactory(),
                secureArea = Platform.getSecureArea(),
                numKeys = 10,
            )
            try {
                val (keyInfo, certification) = readerBackendClient.getKey()
                println("Woohoo replenished key")
                println("keyInfo $keyInfo")
                println("certification $certification")
            } catch (e: Throwable) {
                println("Error replenishing keys: $e")
                e.printStackTrace()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun Content() {
        var isInitialized = remember { mutableStateOf<Boolean>(false) }
        if (!isInitialized.value) {
            CoroutineScope(Dispatchers.Main).launch {
                initialize()
                isInitialized.value = true
            }
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Initializing...")
            }
            return
        }

        PromptDialogs(promptModel)

        val coroutineScope = rememberCoroutineScope()

        val navController = rememberNavController()
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        AppTheme {
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
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                fontWeight = FontWeight.Bold
                            )
                            HorizontalDivider()

                            NavigationDrawerItem(
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.AccountBalance,
                                        contentDescription = null
                                    )
                                },
                                label = { Text(text = stringResource(Res.string.trusted_issuers_screen_title)) },
                                selected = false,
                                onClick = {
                                    navController.navigate(route = TrustedIssuersDestination.route)
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
                                    navController.navigate(route = AboutDestination.route)
                                    coroutineScope.launch { drawerState.close() }
                                }
                            )
                        }
                    }
                },
                drawerState = drawerState
            ) {
                PromptDialogs(Platform.promptModel)

                NavHost(
                    navController = navController,
                    startDestination = startDestination ?: StartDestination.route,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(route = StartDestination.route) {
                        StartScreen(
                            settingsModel = settingsModel,
                            promptModel = promptModel,
                            onMenuPressed = {
                                coroutineScope.launch {
                                    drawerState.open()
                                }
                            },
                            onScanQrClicked = {
                                navController.navigate(route = ScanQrDestination.route)
                            },
                            onNfcHandover = { transport, encodedDeviceEngagement, handover, updateMessage ->
                                readerModel.reset()
                                readerModel.setConnectionEndpoint(
                                    encodedDeviceEngagement = encodedDeviceEngagement,
                                    handover = handover,
                                    existingTransport = transport
                                )
                                val readerQuery = ReaderQuery.valueOf(settingsModel.selectedQueryName.value)
                                readerModel.setDeviceRequest(
                                    readerQuery.generateDeviceRequest(
                                        settingsModel = settingsModel,
                                        encodedSessionTranscript = readerModel.encodedSessionTranscript,
                                        readerBackendClient = readerBackendClient
                                    )
                                )
                                navController.navigate(route = TransferDestination.route)
                            }
                        )
                    }
                    composable(route = ScanQrDestination.route) {
                        ScanQrScreen(
                            onBackPressed = { navController.navigateUp() },
                            onMdocQrCodeScanned = { mdocUri ->
                                coroutineScope.launch {
                                    val encodedDeviceEngagement =
                                        ByteString(mdocUri.substringAfter("mdoc:").fromBase64Url())
                                    readerModel.reset()
                                    readerModel.setConnectionEndpoint(
                                        encodedDeviceEngagement = encodedDeviceEngagement,
                                        handover = Simple.NULL,
                                        existingTransport = null
                                    )
                                    val readerQuery = ReaderQuery.valueOf(settingsModel.selectedQueryName.value)
                                    readerModel.setDeviceRequest(
                                        readerQuery.generateDeviceRequest(
                                            settingsModel = settingsModel,
                                            encodedSessionTranscript = readerModel.encodedSessionTranscript,
                                            readerBackendClient = readerBackendClient
                                        )
                                    )
                                    navController.popBackStack()
                                    navController.navigate(route = TransferDestination.route)
                                }
                            }
                        )
                    }
                    composable(route = SelectRequestDestination.route) {
                        SelectRequestScreen(
                            readerModel = readerModel,
                            settingsModel = settingsModel,
                            readerBackendClient = readerBackendClient,
                            onBackPressed = { urlLaunchData?.finish() ?: navController.navigateUp() },
                            onContinueClicked = {
                                navController.popBackStack()
                                navController.navigate(route = TransferDestination.route)
                            }
                        )
                    }
                    composable(route = TransferDestination.route) {
                        TransferScreen(
                            readerModel = readerModel,
                            onBackPressed = { urlLaunchData?.finish() ?: navController.navigateUp() },
                            onTransferComplete = {
                                navController.popBackStack()
                                navController.navigate(route = ShowResultsDestination.route)
                            }
                        )
                    }
                    composable(route = ShowResultsDestination.route) {
                        ShowResultsScreen(
                            readerQuery = ReaderQuery.valueOf(settingsModel.selectedQueryName.value),
                            readerModel = readerModel,
                            documentTypeRepository = documentTypeRepository,
                            issuerTrustManager = compositeTrustManager,
                            onBackPressed = { urlLaunchData?.finish() ?: navController.navigateUp() },
                        )
                    }
                    composable(route = TrustedIssuersDestination.route) {
                        TrustedIssuersScreen(
                            builtInTrustManager = builtInTrustManager,
                            userTrustManager = userTrustManager,
                            settingsModel = settingsModel,
                            onBackPressed = { navController.navigateUp() },
                            onTrustEntryClicked = { trustManagerId, entryId, justImported ->
                                navController.navigate(
                                    route = TrustEntryViewerDestination.route +
                                            "/" + trustManagerId + "/" + entryId + "/" + justImported
                                )
                            }
                        )
                    }
                    composable(route = AboutDestination.route) {
                        AboutScreen(
                            onBackPressed = { navController.navigateUp() },
                        )
                    }
                    composable(
                        route = CertificateViewerDestination.routeWithArgs,
                        arguments = CertificateViewerDestination.arguments
                    ) { backStackEntry ->
                        CertificateViewerScreen(
                            certificateDataBase64 = backStackEntry.arguments?.getString(
                                CertificateViewerDestination.CERTIFICATE_DATA_BASE64
                            )!!,
                            onBackPressed = { navController.navigateUp() },
                        )
                    }
                    composable(
                        route = TrustEntryViewerDestination.routeWithArgs,
                        arguments = TrustEntryViewerDestination.arguments
                    ) { backStackEntry ->
                        TrustEntryViewerScreen(
                            builtInTrustManager = builtInTrustManager,
                            userTrustManager = userTrustManager,
                            trustManagerId = backStackEntry.arguments?.getString(
                                TrustEntryViewerDestination.TRUST_MANAGER_ID
                            )!!,
                            entryId = backStackEntry.arguments?.getString(
                                TrustEntryViewerDestination.ENTRY_ID
                            )!!,
                            justImported = backStackEntry.arguments?.getBoolean(
                                TrustEntryViewerDestination.JUST_IMPORTED
                            )!!,
                            onBackPressed = { navController.navigateUp() },
                            onEditPressed = { entryId ->
                                navController.navigate(
                                    route = TrustEntryEditorDestination.route + "/" + entryId
                                )
                            },
                            onShowVicalEntry = { trustManagerId, entryId, vicalCertNum ->
                                navController.navigate(
                                    route = VicalEntryViewerDestination.route +
                                            "/" + trustManagerId + "/" + entryId + "/" + vicalCertNum
                                )
                            },
                            onShowCertificate = { certificate ->
                                val certificateDataBase64 = Cbor.encode(certificate.toDataItem()).toBase64Url()
                                navController.navigate(
                                    route = CertificateViewerDestination.route + "/" + certificateDataBase64
                                )
                            },
                            onShowCertificateChain = { certificateChain ->
                                val certificateDataBase64 = Cbor.encode(certificateChain.toDataItem()).toBase64Url()
                                navController.navigate(
                                    route = CertificateViewerDestination.route + "/" + certificateDataBase64
                                )
                            },
                        )
                    }
                    composable(
                        route = TrustEntryEditorDestination.routeWithArgs,
                        arguments = TrustEntryEditorDestination.arguments
                    ) { backStackEntry ->
                        TrustEntryEditorScreen(
                            userTrustManager = userTrustManager,
                            entryId = backStackEntry.arguments?.getString(
                                TrustEntryViewerDestination.ENTRY_ID
                            )!!,
                            onBackPressed = { navController.navigateUp() },
                        )
                    }
                    composable(
                        route = VicalEntryViewerDestination.routeWithArgs,
                        arguments = VicalEntryViewerDestination.arguments
                    ) { backStackEntry ->
                        VicalEntryViewerScreen(
                            builtInTrustManager = builtInTrustManager,
                            userTrustManager = userTrustManager,
                            trustManagerId = backStackEntry.arguments?.getString(
                                VicalEntryViewerDestination.TRUST_MANAGER_ID
                            )!!,
                            entryId = backStackEntry.arguments?.getString(
                                VicalEntryViewerDestination.ENTRY_ID
                            )!!,
                            certificateIndex = backStackEntry.arguments?.getInt(
                                VicalEntryViewerDestination.CERTIFICATE_INDEX
                            )!!,
                            onBackPressed = { navController.navigateUp() },
                        )
                    }
                }
            }
        }
    }
}

