package dev.josephwilliams.freecasts.data.playback.auto

import android.Manifest.permission.MEDIA_CONTENT_CONTROL
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import android.os.Process
import android.util.Base64
import android.util.Log
import androidx.annotation.XmlRes
import androidx.core.app.NotificationManagerCompat
import dev.josephwilliams.freecasts.R
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * Validates that the calling package is authorized to browse the media library.
 * Unknown callers are logged so their signature can be added to
 * [R.xml.allowed_media_browser_callers].
 */
class PackageValidator(context: Context, @XmlRes xmlResId: Int) {

    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val certificateAllowList: Map<String, KnownCallerInfo>
    private val platformSignature: String
    private val callerChecked = mutableMapOf<String, Pair<Int, Boolean>>()

    init {
        certificateAllowList = buildCertificateAllowList(appContext.resources.getXml(xmlResId))
        platformSignature = getSystemSignature()
    }

    fun isKnownCaller(callingPackage: String, callingUid: Int): Boolean {
        val (checkedUid, checkResult) = callerChecked[callingPackage] ?: (0 to false)
        if (checkedUid == callingUid) {
            return checkResult
        }

        val callerPackageInfo = buildCallerInfo(callingPackage) ?: run {
            callerChecked[callingPackage] = callingUid to false
            return false
        }

        if (callerPackageInfo.uid != callingUid) {
            callerChecked[callingPackage] = callingUid to false
            return false
        }

        val callerSignature = callerPackageInfo.signature
        val isPackageInAllowList = certificateAllowList[callingPackage]?.signatures?.any {
            it.signature == callerSignature
        } == true

        val isCallerKnown = when {
            callingUid == Process.myUid() -> true
            isPackageInAllowList -> true
            callingUid == Process.SYSTEM_UID -> true
            callerSignature == platformSignature -> true
            callerPackageInfo.permissions.contains(MEDIA_CONTENT_CONTROL) -> true
            NotificationManagerCompat.getEnabledListenerPackages(appContext)
                .contains(callerPackageInfo.packageName) -> true
            else -> false
        }

        if (!isCallerKnown) {
            logUnknownCaller(callerPackageInfo)
        }

        callerChecked[callingPackage] = callingUid to isCallerKnown
        return isCallerKnown
    }

    private fun logUnknownCaller(callerPackageInfo: CallerPackageInfo) {
        if (callerPackageInfo.signature == null) return
        val isDebuggable = (appContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebuggable) return

        Log.i(
            TAG,
            appContext.getString(
                R.string.allowed_caller_log,
                callerPackageInfo.name,
                callerPackageInfo.packageName,
                callerPackageInfo.signature
            )
        )
    }

    private fun buildCallerInfo(callingPackage: String): CallerPackageInfo? {
        val packageInfo = getPackageInfo(callingPackage) ?: return null
        val appName = packageInfo.applicationInfo?.loadLabel(packageManager)?.toString().orEmpty()
        val uid = packageInfo.applicationInfo?.uid ?: return null
        val signature = getSignature(packageInfo) ?: return null
        val requestedPermissions = packageInfo.requestedPermissions ?: emptyArray()
        val permissionFlags = packageInfo.requestedPermissionsFlags ?: intArrayOf()
        val activePermissions = buildSet {
            requestedPermissions.forEachIndexed { index, permission ->
                val flags = permissionFlags.getOrNull(index) ?: 0
                if (flags and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0) {
                    add(permission)
                }
            }
        }
        return CallerPackageInfo(appName, callingPackage, uid, signature, activePermissions)
    }

    @Suppress("DEPRECATION")
    private fun getPackageInfo(callingPackage: String): PackageInfo? = try {
        packageManager.getPackageInfo(
            callingPackage,
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_PERMISSIONS
        )
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    @Suppress("DEPRECATION")
    private fun getSignature(packageInfo: PackageInfo): String? {
        val certificates = packageInfo.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        if (certificates.size != 1) return null
        return getSignatureSha256(certificates.first())
    }

    private fun buildCertificateAllowList(parser: XmlResourceParser): Map<String, KnownCallerInfo> {
        val certificateAllowList = LinkedHashMap<String, KnownCallerInfo>()
        try {
            var eventType = parser.next()
            while (eventType != XmlResourceParser.END_DOCUMENT) {
                if (eventType == XmlResourceParser.START_TAG) {
                    val callerInfo = when (parser.name) {
                        "signing_certificate" -> parseV1Tag(parser)
                        "signature" -> parseV2Tag(parser)
                        else -> null
                    }
                    callerInfo?.let { info ->
                        val existingCallerInfo = certificateAllowList[info.packageName]
                        if (existingCallerInfo != null) {
                            existingCallerInfo.signatures += info.signatures
                        } else {
                            certificateAllowList[info.packageName] = info
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (xmlException: XmlPullParserException) {
            Log.e(TAG, "Could not read allowed callers from XML.", xmlException)
        } catch (ioException: IOException) {
            Log.e(TAG, "Could not read allowed callers from XML.", ioException)
        }
        return certificateAllowList
    }

    private fun parseV1Tag(parser: XmlResourceParser): KnownCallerInfo {
        val name = parser.getAttributeValue(null, "name").orEmpty()
        val packageName = parser.getAttributeValue(null, "package").orEmpty()
        val isRelease = parser.getAttributeBooleanValue(null, "release", false)
        val certificate = parser.nextText().replace(WHITESPACE_REGEX, "")
        val signature = getSignatureSha256(Base64.decode(certificate, Base64.DEFAULT))
        return KnownCallerInfo(name, packageName, mutableSetOf(KnownSignature(signature, isRelease)))
    }

    private fun parseV2Tag(parser: XmlResourceParser): KnownCallerInfo {
        val name = parser.getAttributeValue(null, "name").orEmpty()
        val packageName = parser.getAttributeValue(null, "package").orEmpty()
        val callerSignatures = mutableSetOf<KnownSignature>()
        var eventType = parser.next()
        while (eventType != XmlResourceParser.END_TAG) {
            if (eventType == XmlResourceParser.START_TAG && parser.name == "key") {
                val isRelease = parser.getAttributeBooleanValue(null, "release", false)
                val signature = parser.nextText().replace(WHITESPACE_REGEX, "").lowercase()
                callerSignatures += KnownSignature(signature, isRelease)
            }
            eventType = parser.next()
        }
        return KnownCallerInfo(name, packageName, callerSignatures)
    }

    @SuppressLint("PrivateApi")
    private fun getSystemSignature(): String =
        getPackageInfo(ANDROID_PLATFORM)?.let(::getSignature)
            ?: throw IllegalStateException("Platform signature not found")

    private fun getSignatureSha256(certificate: ByteArray): String {
        val md = try {
            MessageDigest.getInstance("SHA256")
        } catch (noSuchAlgorithmException: NoSuchAlgorithmException) {
            throw RuntimeException("Could not find SHA256 hash algorithm", noSuchAlgorithmException)
        }
        md.update(certificate)
        return md.digest().joinToString(":") { String.format("%02x", it) }
    }

    private data class KnownCallerInfo(
        val name: String,
        val packageName: String,
        val signatures: MutableSet<KnownSignature>,
    )

    private data class KnownSignature(
        val signature: String,
        val release: Boolean,
    )

    private data class CallerPackageInfo(
        val name: String,
        val packageName: String,
        val uid: Int,
        val signature: String?,
        val permissions: Set<String>,
    )

    private companion object {
        const val TAG = "PackageValidator"
        const val ANDROID_PLATFORM = "android"
        val WHITESPACE_REGEX = "\\s|\\n".toRegex()
    }
}
