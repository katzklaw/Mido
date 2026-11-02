package app.mido.pro

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Offline licence keys. A key is `MIDO1.<payload>.<signature>` (both base64url): the payload is
 * "name|YYYYMMDD" and the signature is ECDSA (P-256, SHA-256) made with a private key that only the
 * developer holds. The app carries just the public key, so it can check a key with no server.
 */
object LicenseKey {
    const val PREFIX = "MIDO1"

    data class License(val name: String, val issued: String)

    /** The licence inside [key], or null if it is malformed or the signature doesn't match [publicKeyB64]. */
    fun verify(key: String, publicKeyB64: String): License? {
        if (publicKeyB64.isBlank()) return null
        return try {
            val parts = key.filter { !it.isWhitespace() }.split('.')
            if (parts.size != 3 || parts[0] != PREFIX) return null
            val payload = Base64.getUrlDecoder().decode(parts[1])
            val sig = Base64.getUrlDecoder().decode(parts[2])
            val ok = Signature.getInstance("SHA256withECDSA").run {
                initVerify(publicKey(publicKeyB64))
                update(payload)
                verify(sig)
            }
            if (!ok) return null
            val fields = String(payload, Charsets.UTF_8).split('|')
            if (fields.size != 2) return null
            License(fields[0], fields[1])
        } catch (e: Exception) {
            null
        }
    }

    private fun publicKey(b64: String): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(b64.trim())))
}
