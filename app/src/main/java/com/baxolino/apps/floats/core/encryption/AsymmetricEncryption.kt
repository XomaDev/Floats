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

  private const val KEY_SIZE_BITS = 1024

  fun getKeyPair(): KeyPair {
    KeyPairGenerator.getInstance("RSA").apply {
      initialize(KEY_SIZE_BITS)
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
}