package org.who.ddccverifier.services.qrs.divoc.jsonldcrypto

import com.danubetech.keyformats.crypto.ByteVerifier
import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.JWSVerifier

import info.weboftrust.ldsignatures.LdProof
import info.weboftrust.ldsignatures.adapter.JWSVerifierAdapter
import info.weboftrust.ldsignatures.suites.RsaSignature2018SignatureSuite
import info.weboftrust.ldsignatures.suites.SignatureSuites
import info.weboftrust.ldsignatures.util.JWSUtil

import info.weboftrust.ldsignatures.verifier.LdVerifier
import java.security.GeneralSecurityException
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.text.ParseException

class RsaSignature2018withPS256Verifier
    constructor(verifier: ByteVerifier?) : LdVerifier<RsaSignature2018SignatureSuite?>(SignatureSuites.SIGNATURE_SUITE_RSASIGNATURE2018, verifier, AndroidURDNA2015Canonicalizer()) {
    constructor(publicKey: PublicKey?) : this(RsaPS256BouncyCastlePublicKeyVerifier(publicKey as RSAPublicKey))

    override fun verify(signingInput: ByteArray, ldProof: LdProof): Boolean {
        try {
            val detachedJwsObject = JWSObject.parse(ldProof.jws)
            val jwsSigningInput = JWSUtil.getJwsSigningInput(detachedJwsObject.header, signingInput)
            val jwsVerifier: JWSVerifier = JWSVerifierAdapter(verifier, JWSAlgorithm.PS256)
            return jwsVerifier.verify(detachedJwsObject.header, jwsSigningInput, detachedJwsObject.signature)
        } catch (ex: JOSEException) {
            throw GeneralSecurityException("JOSE verification problem: " + ex.message, ex)
        } catch (ex: ParseException) {
            throw GeneralSecurityException("JOSE verification problem: " + ex.message, ex)
        }
    }
}