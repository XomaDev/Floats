package com.baxolino.apps.floats.core.encryption

import java.security.Key
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher


object AsymmetricEncryption {

  const val KEY_SIZE_BITS = 4096

  fun getKeyPair(): KeyPair {
    val random = SecureRandom()
    KeyPairGenerator.getInstance("RSA").apply {
      initialize(KEY_SIZE_BITS, random)
      return generateKeyPair()
    }
  }

  fun getPublicKey(bytes: ByteArray): PublicKey {
    return KeyFactory.getInstance("RSA")
      .generatePublic(X509EncodedKeySpec(bytes))
  }

  fun init(mode: Int, key: Key): Cipher {
    Cipher.getInstance("RSA")
      .apply {
        init(
          mode, key
        )
        return this
      }
  }

  fun doFinalBytes(
    cipher: Cipher,
    plainText: String,
  ): ByteArray {
    return cipher.doFinal(
      plainText.toByteArray()
    )
  }
}