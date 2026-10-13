package de.moba.core.runtime

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

private const val ALGORITHM = "SHA256withECDSA"

/**
 * Prueft Lizenzschluessel mit dem oeffentlichen Schluessel des Herstellers (X.509, Base64).
 * Laeuft ohne Internet; faelschen laesst sich ein Schluessel nur mit dem privaten Schluessel.
 */
class EcLicenseVerifier(publicKeyBase64: String) : LicenseVerifier {
    private val publicKey: PublicKey? = publicKeyBase64.takeIf { it.isNotBlank() }?.let {
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(it.trim())))
    }

    override fun check(key: String, today: String): LicenseCheck {
        val publicKey = publicKey ?: return LicenseCheck.Invalid("Die Lizenzprüfung ist in dieser Version noch nicht eingerichtet.")
        val (payload, signature) = LicenseFormat.decode(key)
            ?: return LicenseCheck.Invalid("Das ist kein Lizenzschlüssel. Er beginnt mit ${LicenseFormat.PREFIX}")
        val valid = runCatching {
            Signature.getInstance(ALGORITHM).run {
                initVerify(publicKey)
                update(payload)
                verify(signature)
            }
        }.getOrDefault(false)
        if (!valid) return LicenseCheck.Invalid("Der Lizenzschlüssel ist ungültig. Bitte genau so einfügen, wie er geliefert wurde.")
        val license = LicenseFormat.parsePayload(payload.decodeToString())
            ?: return LicenseCheck.Invalid("Der Lizenzschlüssel ist beschädigt.")
        if (license.expires.isNotEmpty() && license.expires < today) {
            return LicenseCheck.Invalid("Die Lizenz ist am ${license.expires} abgelaufen.")
        }
        return LicenseCheck.Valid(license)
    }
}

/** Erstellt Lizenzschluessel; nur fuer das Lizenzwerkzeug und Tests, nie in der App. */
class LicenseSigner(private val privateKey: PrivateKey) {
    constructor(privateKeyBase64: String) : this(
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyBase64.trim()))),
    )

    fun sign(license: License): String {
        val payload = LicenseFormat.payload(license).encodeToByteArray()
        val signature = Signature.getInstance(ALGORITHM).run {
            initSign(privateKey)
            update(payload)
            sign()
        }
        return LicenseFormat.encode(payload.decodeToString(), signature)
    }

    companion object {
        /** Neues Schluesselpaar: privat (PKCS#8) und oeffentlich (X.509), beide Base64. */
        fun generateKeyPair(): Pair<String, String> {
            val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
            val encoder = Base64.getEncoder()
            return encoder.encodeToString(pair.private.encoded) to encoder.encodeToString(pair.public.encoded)
        }
    }
}
