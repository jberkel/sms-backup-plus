package com.zegoggles.smssync.mail;

import android.annotation.SuppressLint;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.ssl.TrustedSocketFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

// NB: Trust-all logic was removed in
// https://github.com/k9mail/k-9/commit/daea7f1ecdb4515298a6c57dd5a829689426c2c9
@SuppressLint("TrustAllX509TrustManager")
class AllTrustedSocketFactory implements TrustedSocketFactory {
    public static TrustedSocketFactory INSTANCE = new AllTrustedSocketFactory();

    private AllTrustedSocketFactory() {}

    @Override
    public Socket createSocket(Socket socket, String host, int port, String clientCertificateAlias) throws NoSuchAlgorithmException, KeyManagementException, MessagingException, IOException {
        TrustManager[] trustManagers = new TrustManager[] { new InsecureX509TrustManager() };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagers, null);
        SSLSocketFactory socketFactory = sslContext.getSocketFactory();
        Socket trustedSocket;
        if (socket == null) {
            trustedSocket = socketFactory.createSocket();
        } else {
            trustedSocket = socketFactory.createSocket(socket, host, port, true);
        }

        return trustedSocket;
    }

    private static class InsecureX509TrustManager implements X509TrustManager
    {
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException
        {
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException
        {
        }

        public X509Certificate[] getAcceptedIssuers()
        {
            return null;
        }
    }
}
