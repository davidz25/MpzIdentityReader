package org.multipaz.identityreader

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.io.bytestring.ByteString
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.X509CertChain
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.mdoc.request.DeviceRequestGenerator
import org.multipaz.securearea.SecureArea

enum class ReaderQuery(
    val icon: ImageVector,
    val displayName: String,
) {
    AGE_OVER_18(
        icon = Icons.Filled.Numbers,
        displayName = "Age Over 18",
    ),
    AGE_OVER_21(
        icon = Icons.Filled.Numbers,
        displayName = "Age Over 21",
    ),
    IDENTIFICATION(
        icon = Icons.Filled.Person,
        displayName = "Identification",
    ),

    ;

    suspend fun generateDeviceRequest(
        settingsModel: SettingsModel,
        encodedSessionTranscript: ByteString,
        readerBackendClient: ReaderBackendClient
    ): ByteString {
        val deviceRequest = when (settingsModel.readerAuthMethod.value) {
            ReaderAuthMethod.NO_READER_AUTH -> {
                generateEncodedDeviceRequest(
                    query = this,
                    intentToRetain = settingsModel.logTransactions.value,
                    encodedSessionTranscript = encodedSessionTranscript.toByteArray(),
                    readerKey = null,
                    readerKeyAlias = null,
                    readerKeySecureArea = null,
                    readerKeyCertification = null
                )
            }
            ReaderAuthMethod.STANDARD_READER_AUTH -> {
                val (keyInfo, keyCertification) = try {
                    readerBackendClient.getKey()
                } catch (e: Throwable) {
                    println("Error getting certified reader key, proceeding without reader authentication: $e")
                    Pair(null, null)
                }
                generateEncodedDeviceRequest(
                    query = this,
                    intentToRetain = settingsModel.logTransactions.value,
                    encodedSessionTranscript = encodedSessionTranscript.toByteArray(),
                    readerKey = null,
                    readerKeyAlias = keyInfo?.alias,
                    readerKeySecureArea = keyInfo?.let { readerBackendClient.secureArea },
                    readerKeyCertification = keyCertification
                ).also {
                    keyInfo?.let { readerBackendClient.markKeyAsUsed(it) }
                }
            }
            ReaderAuthMethod.CUSTOM_KEY -> {
                generateEncodedDeviceRequest(
                    query = this,
                    intentToRetain = settingsModel.logTransactions.value,
                    encodedSessionTranscript = encodedSessionTranscript.toByteArray(),
                    readerKey = settingsModel.customReaderAuthKey.value!!,
                    readerKeyAlias = null,
                    readerKeySecureArea = null,
                    readerKeyCertification = settingsModel.customReaderAuthCertChain.value!!
                )
            }
        }
        return ByteString(deviceRequest)
    }
}

suspend fun generateEncodedDeviceRequest(
    query: ReaderQuery,
    intentToRetain: Boolean,
    encodedSessionTranscript: ByteArray,
    readerKey: EcPrivateKey?,
    readerKeyAlias: String?,
    readerKeySecureArea: SecureArea?,
    readerKeyCertification: X509CertChain?
): ByteArray {

    val itemsToRequest = mutableMapOf<String, MutableMap<String, Boolean>>()
    val mdlNs = itemsToRequest.getOrPut(DrivingLicense.MDL_NAMESPACE) { mutableMapOf() }
    when (query) {
        ReaderQuery.AGE_OVER_18 -> {
            mdlNs.put("age_over_18", intentToRetain)
            mdlNs.put("portrait", intentToRetain)
        }
        ReaderQuery.AGE_OVER_21 -> {
            mdlNs.put("age_over_21", intentToRetain)
            mdlNs.put("portrait", intentToRetain)
        }
        ReaderQuery.IDENTIFICATION -> {
            mdlNs.put("given_name", intentToRetain)
            mdlNs.put("family_name", intentToRetain)
            mdlNs.put("birth_date", intentToRetain)
            mdlNs.put("birth_place", intentToRetain)
            mdlNs.put("sex", intentToRetain)
            mdlNs.put("portrait", intentToRetain)
            mdlNs.put("resident_address", intentToRetain)
            mdlNs.put("resident_city", intentToRetain)
            mdlNs.put("resident_state", intentToRetain)
            mdlNs.put("resident_postal_code", intentToRetain)
            mdlNs.put("resident_country", intentToRetain)
            mdlNs.put("issuing_authority", intentToRetain)
            mdlNs.put("document_number", intentToRetain)
            mdlNs.put("issue_date", intentToRetain)
            mdlNs.put("expiry_date", intentToRetain)
        }
    }
    val docType = DrivingLicense.MDL_DOCTYPE

    // TODO: for now we're only requesting an mDL, in the future we might request many different doctypes

    val deviceRequestGenerator = DeviceRequestGenerator(encodedSessionTranscript)
    if (readerKey != null) {
        deviceRequestGenerator.addDocumentRequest(
            docType = docType,
            itemsToRequest = itemsToRequest,
            requestInfo = null,
            readerKey = readerKey,
            signatureAlgorithm = readerKey.curve.defaultSigningAlgorithmFullySpecified,
            readerKeyCertificateChain = readerKeyCertification,
        )
    } else if (readerKeyAlias != null) {
        deviceRequestGenerator.addDocumentRequest(
            docType = docType,
            itemsToRequest = itemsToRequest,
            requestInfo = null,
            readerKeySecureArea = readerKeySecureArea!!,
            readerKeyAlias = readerKeyAlias,
            readerKeyCertificateChain = readerKeyCertification!!,
            keyUnlockData = null
        )
    } else {
        deviceRequestGenerator.addDocumentRequest(
            docType = docType,
            itemsToRequest = itemsToRequest,
            requestInfo = null,
            readerKey = null,
            signatureAlgorithm = Algorithm.UNSET,
            readerKeyCertificateChain = null,
        )
    }
    return deviceRequestGenerator.generate()
}


fun List<ReaderQuery>.findIndexForId(id: String): Int? {
    this.forEachIndexed { idx, readerQuery ->
        if (readerQuery.name == id) {
            return idx
        }
    }
    return null
}