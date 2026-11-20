package com.shilapi.xcertplay.mfi

import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.spec.ECGenParameterSpec
import java.util.Date
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERBitString
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x509.Time
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.bouncycastle.asn1.DERSequence
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalMfiAuthenticationClientTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun load(directory: File, onSignature: (Int) -> Unit = {}): LocalMfiAuthenticationClient =
        LocalMfiAuthenticationClient.loadWithSigner(directory, { key, digest ->
            Signature.getInstance("NONEwithECDSA").run {
                initSign(key)
                update(digest)
                sign()
            }
        }, onSignature)

    private fun identity(): File = temporary.newFolder().also { directory ->
        // Fresh self-signed test material for each test. No stored accessory identity.
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val pair = generator.generateKeyPair()
        val algorithm = AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA256)
        val name = X500Name("CN=DiPlay synthetic test only")
        val tbs = V3TBSCertificateGenerator().apply {
            setSerialNumber(ASN1Integer(BigInteger.ONE))
            setSignature(algorithm)
            setIssuer(name)
            setSubject(name)
            setStartDate(Time(Date(0)))
            setEndDate(Time(Date(4102444800000L)))
            setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(pair.public.encoded))
        }.generateTBSCertificate()
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(pair.private)
        signer.update(tbs.encoded)
        val certificate = DERSequence(arrayOf<ASN1Encodable>(
            tbs, algorithm, DERBitString(signer.sign()),
        )).encoded
        File(directory, "identity.pk8").writeBytes(pair.private.encoded)
        File(directory, "certificate.p7b").writeBytes(certificate)
    }

    @Test fun freshDigestsVerifyWithoutDoubleHashingAndRejectReplay() {
        val directory = identity()
        var count = 0
        val client = load(directory) { count++ }
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificates(client.readCertificate().inputStream()).single()
        repeat(8) { index ->
            val digest = MessageDigest.getInstance("SHA-256").digest("challenge-$index".toByteArray())
            val signature = client.signChallenge(digest)
            assertEquals(64, signature.size)
            val der = DERSequence(arrayOf(
                ASN1Integer(BigInteger(1, signature.copyOfRange(0, 32))),
                ASN1Integer(BigInteger(1, signature.copyOfRange(32, 64))),
            )).encoded
            val verifier = Signature.getInstance("NONEwithECDSA")
            verifier.initVerify(certificate.publicKey)
            verifier.update(digest)
            assertTrue(verifier.verify(der))
            digest[0] = (digest[0].toInt() xor 1).toByte()
            verifier.initVerify(certificate.publicKey)
            verifier.update(digest)
            assertFalse(verifier.verify(der))
        }
        assertEquals(8, count)
        assertEquals(3, client.protocolMajor())
        assertEquals(MfiCertificateType.MFI, client.certificateType)
    }

    @Test fun rejectsDifferentPrivateKey() {
        val directory = identity()
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        File(directory, "identity.pk8").writeBytes(generator.generateKeyPair().private.encoded)
        assertThrows(IllegalArgumentException::class.java) { load(directory) }
    }

    @Test fun rejectsIncompleteIdentityAndWrongChallengeLength() {
        assertThrows(Exception::class.java) { load(temporary.newFolder()) }
        val client = load(identity())
        for (length in listOf(0, 20, 31, 33, 64)) {
            assertThrows(IllegalArgumentException::class.java) { client.signChallenge(ByteArray(length)) }
        }
        assertThrows(IllegalArgumentException::class.java) { client.readCertificate(1) }
    }

    @Test fun certificateIsReturnedAsIndependentCopyAndSurvivesReload() {
        val directory = identity()
        val first = load(directory)
        val certificate = first.readCertificate()
        val second = load(directory)
        assertArrayEquals(certificate, second.readCertificate())
        certificate.fill(0)
        assertFalse(first.readCertificate().all { it == 0.toByte() })
        assertEquals(64, second.signChallenge(ByteArray(32) { it.toByte() }).size)
    }

    @Test fun reusesValidatedKeyButKeepsEachConnectionsSignatureCallback() {
        val directory = identity()
        var firstCount = 0
        var secondCount = 0
        val first = load(directory) { firstCount++ }
        val second = load(directory) { secondCount++ }
        val key = LocalMfiAuthenticationClient::class.java.getDeclaredField("privateKey").apply { isAccessible = true }
        assertSame("Reload must reuse the parsed, validated key", key.get(first), key.get(second))
        assertNotSame(first, second)
        first.signChallenge(ByteArray(32))
        assertEquals(1, firstCount)
        assertEquals(0, secondCount)
        second.signChallenge(ByteArray(32) { 1 })
        assertEquals(1, firstCount)
        assertEquals(1, secondCount)
    }

    @Test fun changedKeyWithUnchangedFileMetadataCannotReuseTheValidatedIdentity() {
        val directory = identity()
        load(directory)
        val file = File(directory, "identity.pk8")
        val modified = file.lastModified()
        val originalSize = file.length()
        // Same size and timestamp, but a different private scalar for the same curve.
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        file.writeBytes(generator.generateKeyPair().private.encoded)
        assertEquals(originalSize, file.length())
        assertTrue(file.setLastModified(modified))
        assertThrows(Exception::class.java) { load(directory) }
    }

    @Test fun missingOrChangedCertificateCannotUseThePreviouslyValidatedKey() {
        val directory = identity()
        load(directory)
        val certificate = File(directory, "certificate.p7b")
        certificate.writeBytes(ByteArray(certificate.length().toInt()))
        assertThrows(Exception::class.java) { load(directory) }
        assertTrue(certificate.delete())
        assertThrows(Exception::class.java) { load(directory) }
    }
}
