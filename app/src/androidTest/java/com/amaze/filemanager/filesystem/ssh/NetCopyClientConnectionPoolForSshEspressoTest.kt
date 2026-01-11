package com.amaze.filemanager.filesystem.ssh

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amaze.filemanager.BuildConfig
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetCopyClientConnectionPoolForSshEspressoTest {
    private val rsaPrivateKey = BuildConfig.SSH_PRIVATE_KEY_RSA
    private val ed25519PrivateKey = BuildConfig.SSH_PRIVATE_KEY_ED25519

    @Test
    fun test1() {
        assertNotNull(rsaPrivateKey)
        assertNotNull(ed25519PrivateKey)
    }
}
