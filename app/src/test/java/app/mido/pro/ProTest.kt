package app.mido.pro

import app.mido.settings.WidgetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class ProTest {
    private val kp = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    private val pub = Base64.getEncoder().encodeToString(kp.public.encoded)

    private fun sign(payload: String, key: java.security.PrivateKey = kp.private): String {
        val p = payload.toByteArray()
        val sig = Signature.getInstance("SHA256withECDSA").run { initSign(key); update(p); sign() }
        val enc = Base64.getUrlEncoder().withoutPadding()
        return "MIDO1.${enc.encodeToString(p)}.${enc.encodeToString(sig)}"
    }

    @Test fun validKeyIsAccepted() {
        val l = LicenseKey.verify(sign("Sam Smith|20261101"), pub)
        assertNotNull(l)
        assertEquals("Sam Smith", l!!.name)
        assertEquals("20261101", l.issued)
    }

    @Test fun whitespaceAndNewlinesFromPastingAreIgnored() {
        val k = sign("Sam|20261101")
        assertNotNull(LicenseKey.verify("  " + k.take(20) + "\n" + k.drop(20) + " \n", pub))
    }

    @Test fun tamperedOrForeignKeysAreRejected() {
        val k = sign("Sam|20261101")
        val parts = k.split('.')
        val forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString("Eve|20261101".toByteArray())
        assertNull(LicenseKey.verify("${parts[0]}.$forgedPayload.${parts[2]}", pub))
        val other = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        assertNull(LicenseKey.verify(sign("Sam|20261101", other.private), pub))
    }

    @Test fun garbageAndEmptyPublicKeyAreRejected() {
        assertNull(LicenseKey.verify("", pub))
        assertNull(LicenseKey.verify("hello", pub))
        assertNull(LicenseKey.verify("MIDO1.abc.def", pub))
        assertNull(LicenseKey.verify(sign("Sam|20261101"), "")) // no public key built into the app
        assertNull(LicenseKey.verify(sign("Sam|20261101"), "not base64!!"))
    }

    @Test fun freeThemesAreBlackAndLight() {
        assertTrue(ProPolicy.themeAllowed(WidgetTheme.BLACK, false))
        assertTrue(ProPolicy.themeAllowed(WidgetTheme.LIGHT, false))
        assertFalse(ProPolicy.themeAllowed(WidgetTheme.BLUE, false))
        assertEquals(WidgetTheme.BLACK, ProPolicy.effectiveTheme(WidgetTheme.BLUE, false))
        assertEquals(WidgetTheme.LIGHT, ProPolicy.effectiveTheme(WidgetTheme.LIGHT, false))
        assertEquals(WidgetTheme.BLUE, ProPolicy.effectiveTheme(WidgetTheme.BLUE, true))
    }

    @Test fun opacityIsProOnly() {
        assertEquals(100, ProPolicy.effectiveOpacity(50, false))
        assertEquals(50, ProPolicy.effectiveOpacity(50, true))
    }

    @Test fun freeTextSuggestionsRunOutAfterFive() {
        assertTrue(ProPolicy.textSuggestionAllowed(4, false))
        assertFalse(ProPolicy.textSuggestionAllowed(5, false))
        assertTrue(ProPolicy.textSuggestionAllowed(500, true))
        assertEquals(0, ProPolicy.freeTextSuggestionsLeft(9))
        assertEquals(3, ProPolicy.freeTextSuggestionsLeft(2))
    }
}
