package org.who.ddccverifier.trust.pathcheck

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.who.ddccverifier.trust.TrustRegistry
import java.net.URI
import java.net.URL
import java.security.Security
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


operator fun <T> List<T>.component6() = this[5]
operator fun <T> List<T>.component7() = this[6]
operator fun <T> List<T>.component8() = this[7]

/**
 * Resolve Keys for Verifiers from PathCheck's CSV file
 */
class PCFTrustRegistry : TrustRegistry {
    companion object {
        const val REPO = "https://raw.githubusercontent.com/Path-Check/trust-registry/main"
        val PRODUCTION_REGISTRY = TrustRegistry.RegistryEntity(TrustRegistry.Scope.PRODUCTION, URI("$REPO/registry_normalized.csv"), null)
        val ACCEPTANCE_REGISTRY =  TrustRegistry.RegistryEntity(TrustRegistry.Scope.ACCEPTANCE_TEST, URI("$REPO/test_registry_normalized.csv"), null)
    }

    // Using old java.time to keep compatibility down to Android SDK 22.
    private val df: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

    // Builds a map of all Frameworks
    private val registry = EnumMap(TrustRegistry.Framework.values().associateWith {
        mutableMapOf<String, TrustRegistry.TrustedEntity>()
    });

    private fun decode(b64: String): String {
        return Base64.getDecoder().decode(b64).toString(Charsets.UTF_8)
    }

    private fun parseDate(date: String): Date? {
        return if (date.isNotEmpty()) df.parse(date) else null
    }

    private fun wrapPem(pemB64: String): String {
        return "-----BEGIN PUBLIC KEY-----\n$pemB64\n-----END PUBLIC KEY-----"
    }

    fun load(registryURL: TrustRegistry.RegistryEntity) {
        // Parsing the CSV
        val reader = registryURL.resolvableURI.toURL().openStream().bufferedReader()
        reader.forEachLine {
            val (
                specName, kid, status, displayNameB64, displayLogoB64,
                validFromISOStr, validUntilISOStr, publicKey,
            ) = it.split(",")

            try {
                registry[TrustRegistry.Framework.valueOf(specName.uppercase())]
                    ?.put(kid, TrustRegistry.TrustedEntity(
                        mapOf("en" to decode(displayNameB64)),
                        decode(displayLogoB64),
                        TrustRegistry.Status.valueOf(status.uppercase()),
                        registryURL.scope,
                        parseDate(validFromISOStr),
                        parseDate(validUntilISOStr),
                        KeyUtils.publicKeyFromPEM(wrapPem(publicKey))
                    )
                )
            } catch(t: Throwable) {
                println("Exception while loading kid: $specName $kid");
                t.printStackTrace()
            }
        }
    }

    override fun init(vararg customRegistries: TrustRegistry.RegistryEntity) {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())

        TrustRegistry.Framework.values().forEach {
            registry[it]?.clear()
        }

        customRegistries.forEach {
            println("Loading TrustRegistry from $it");
            load(it)
        }
    }

    override fun init() {
        init(PRODUCTION_REGISTRY)
    }

    override fun resolve(framework: TrustRegistry.Framework, kid: String): TrustRegistry.TrustedEntity? {
        println("DDCCVerifer: Resolving $kid");
        return registry[framework]?.get(kid)
    }
}