package com.octo4a.utils

import android.content.Context
import com.octo4a.R
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.*

class TLSSocketFactory(val context: Context) : SSLSocketFactory() {
    private val delegate: SSLSocketFactory
    private var trustManagers: Array<TrustManager>? = emptyArray()
    @Throws(KeyStoreException::class, NoSuchAlgorithmException::class)
    private fun generateTrustManagers() {
        val inputStream: InputStream = context.resources.openRawResource(R.raw.cacert)

        // Load PEM-encoded certificate bundle
        val pemCertificates = mutableListOf<X509Certificate>()
        var certBuffer = StringBuilder()
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line!!.startsWith("-----BEGIN CERTIFICATE-----")) {
                    certBuffer = StringBuilder()
                }
                certBuffer.append(line).append("\n")
                if (line!!.startsWith("-----END CERTIFICATE-----")) {
                    val certBytes = certBuffer.toString().toByteArray()
                    val certificateFactory = CertificateFactory.getInstance("X.509")
                    val certificate = certificateFactory.generateCertificate(certBytes.inputStream()) as X509Certificate
                    pemCertificates.add(certificate)
                }
            }
        }

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null)
        for ((index, certificate) in pemCertificates.withIndex()) {
            keyStore.setCertificateEntry("ca$index", certificate)
        }

        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(keyStore)

        val trustManagers = trustManagerFactory.trustManagers
        val trustManager = trustManagers.firstOrNull { it is X509TrustManager } as? X509TrustManager
            ?: throw IllegalStateException("No X509TrustManager found")
        this.trustManagers = trustManagers
    }

    override fun getDefaultCipherSuites(): Array<String> {
        return delegate.defaultCipherSuites
    }

    override fun getSupportedCipherSuites(): Array<String> {
        return delegate.supportedCipherSuites
    }

    @Throws(IOException::class)
    override fun createSocket(): Socket? {
        return enableTLSOnSocket(delegate.createSocket())
    }

    @Throws(IOException::class)
    override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket? {
        return enableTLSOnSocket(delegate.createSocket(s, host, port, autoClose))
    }


    @Throws(IOException::class, UnknownHostException::class)
    override fun createSocket(host: String?, port: Int): Socket? {
        return enableTLSOnSocket(delegate.createSocket(host, port))
    }

    @Throws(IOException::class, UnknownHostException::class)
    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket? {
        return enableTLSOnSocket(delegate.createSocket(host, port, localHost, localPort))
    }

    @Throws(IOException::class)
    override fun createSocket(host: InetAddress?, port: Int): Socket? {
        return enableTLSOnSocket(delegate.createSocket(host, port))
    }

    @Throws(IOException::class)
    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket? {
        return enableTLSOnSocket(delegate.createSocket(address, port, localAddress, localPort))
    }

    private fun enableTLSOnSocket(socket: Socket?): Socket? {
        if (socket != null && (socket is SSLSocket)) {
            (socket as SSLSocket).setEnabledProtocols(arrayOf("TLSv1.1", "TLSv1.2"))
        }
        return socket
    }

    fun getTrustManager(): TrustManager {
        return trustManagers!!.first() as X509TrustManager
    }

    init {
        generateTrustManagers()
        val context: SSLContext = SSLContext.getInstance("TLS")
        context.init(null, trustManagers, null)
        delegate = context.getSocketFactory()
    }
}