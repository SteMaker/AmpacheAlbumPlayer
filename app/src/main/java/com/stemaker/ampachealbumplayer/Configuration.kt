package com.stemaker.ampachealbumplayer

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import kotlinx.serialization.Serializable
import java.security.Key
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec

fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

@Serializable
class ConfigurationStore {
    var serverUrl: String = ""
    var user: String = ""
    var encryptedPasswordHash: String = ""
    var usedIV: String = ""
    var tagLength: Int = 0
    }

/* External interface regarding password is:
   - set the password by writing to password. This is the plaintext password
   - get the password hashed as required by the Ampache API.
   note: no read access to password, no write access to password hash. The hashed and encrypted
   password is stored in ConfigurationStore
 */
object Configuration {
    var store = ConfigurationStore()
    val KEY_ALIAS = "PasswordEncryptionKey"
    fun initialize() {
        StorageHandler.initialize()
        Log.d("Configuration::initialized", "${store.serverUrl} ${store.user} ${store.encryptedPasswordHash} ${store.usedIV} ${store.tagLength}")
    }

    fun save() {
        StorageHandler.saveConfigurationToFile()
    }

    fun loginDataAvailable():Boolean {
        if(serverUrl == "" ||
           user == "" ||
           passwordHash == "" ||
           (store.usedIV == "" || store.tagLength == 0) && (android.os.Build.VERSION.SDK_INT >= 19))
            return false
        return true
    }

    var serverUrl: String
        get(): String = store.serverUrl
        set(value) {store.serverUrl = value}

    var user: String
        get(): String = store.user
        set(value) {store.user = value}

    var password: String
        set(value) {
            if(value != "") {
                store.encryptedPasswordHash = hashAndEncryptPassword(value)
            }
        }
        get() {return ""}

    var passwordHash: String = ""
        private set(value: String) {field = value}
        get(): String {
            if(field != "") return field
            if(android.os.Build.VERSION.SDK_INT >= 19) {
                if (store.encryptedPasswordHash == "" || store.usedIV == "") return ""
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                val secretKey: SecretKey = keyStore.getKey("KEY_ALIAS", null) as SecretKey
                return decryptPasswordHash(store.encryptedPasswordHash, secretKey)
            } else {
                return store.encryptedPasswordHash
            }
        }

    /* TODO: Catch exceptions */
    private fun hashAndEncryptPassword(pwd: String): String {
        Log.d("Configuration::hashAndEncryptPassword", "password = $pwd")
        val md = MessageDigest.getInstance("SHA-256")
        md.update(pwd.toByteArray())
        passwordHash = md.digest().toHexString()
        Log.d("Configuration::hashAndEncryptPassword", "passwordHash = $passwordHash")

        if(android.os.Build.VERSION.SDK_INT >= 19) {
            /* Now we try to store the password hash in an encrypted way. First we retrieve a key
               from the Android key store */
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            if (!keyStore.containsAlias("KEY_ALIAS")) {
                createPasswordEncryptionKey()
            } else {
                Log.d("Configuration.hashAndEncryptPassword", "Encryption key already exists")
            }

            val secretKey: SecretKey = keyStore.getKey("KEY_ALIAS", null) as SecretKey
            return encryptPasswordHash(passwordHash, secretKey)
        } else {
            return passwordHash
        }
    }

    private fun createPasswordEncryptionKey() {
        Log.d("Configuration::createPasswordEncryptionKey", "Creating encryption key")
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val paramSpec: KeyGenParameterSpec = KeyGenParameterSpec.Builder(
            "KEY_ALIAS",
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .run {
                setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                setRandomizedEncryptionRequired(true)
                setUserAuthenticationRequired(false)
                build()
            }
        keyGenerator.init(paramSpec)
        keyGenerator.generateKey()
    }

    private fun encryptPasswordHash(hash: String, key: SecretKey): String {
        val ciph: Cipher? =
            try {
                Cipher.getInstance("AES/GCM/NoPadding")
            } catch(e: Exception) {
                Log.d("Configuration::encryptPasswordHash", "Cannot encrypt password since Cipher cannot be found")
                throw UnsupportedOperationException("Verschlüsseln des Passworts wird von ihrem Telefon nicht unterstützt. Das Passwort kann nicht gespeichert werden")
            }
        ciph?: return ""
        ciph.init(Cipher.ENCRYPT_MODE, key)
        val decryptedByteArray = hash.toByteArray(Charsets.UTF_8)
        val encryptedByteArray = ciph.doFinal(decryptedByteArray)
        val encryptedBase64Encoded = Base64.encodeToString(encryptedByteArray, Base64.NO_WRAP)
        store.usedIV = Base64.encodeToString(ciph.iv, Base64.NO_WRAP)
        store.tagLength = ciph.parameters.getParameterSpec(GCMParameterSpec::class.java).tLen
        return encryptedBase64Encoded
    }

    private fun decryptPasswordHash(encryptedBase64Encoded: String, key: SecretKey): String {
        Log.d("Configuration::decryptPasswordHash", "called")
        val ciph: Cipher? =
            try {
                Cipher.getInstance("AES/GCM/NoPadding")
            } catch(e: Exception) {
                Log.d("Configuration::decryptPasswordHash", "Cannot decrypt password since Cipher cannot be found")
                throw UnsupportedOperationException("Entschlüsseln des Passworts wird von ihrem Telefon nicht unterstützt. Das Passwort kann nicht gespeichert werden")
            }
        ciph?: return ""
        val iv = Base64.decode(store.usedIV, Base64.NO_WRAP)
        val gcmParameterSpec = GCMParameterSpec(store.tagLength, iv)
        ciph.init(Cipher.DECRYPT_MODE, key, gcmParameterSpec)
        val encryptedByteArray = Base64.decode(encryptedBase64Encoded, Base64.NO_WRAP)
        val decryptedByteArray = ciph.doFinal(encryptedByteArray)
        val decrypted = String(decryptedByteArray, Charsets.UTF_8)
        return decrypted
    }
}

