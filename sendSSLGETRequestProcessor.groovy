import org.apache.commons.io.IOUtils
import org.apache.http.client.methods.HttpGet
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.conn.ssl.TrustStrategy
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.ssl.PrivateKeyDetails
import org.apache.http.ssl.PrivateKeyStrategy
import org.apache.http.ssl.SSLContexts
import org.apache.nifi.processor.io.StreamCallback

import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate

def flowFile = session.get()
if (!flowFile) return
try {

    String external_url_endpoint = flowFile.getAttribute("external_url_endpoint")
    String urlParams = flowFile.getAttribute("urlParams")

    def code = ""
    def content = ""

    flowFile = session.write(flowFile, { inputStream, outputStream ->
        def text = IOUtils.toString(inputStream, StandardCharsets.UTF_8)


        def url = "https://some_host:446" + external_url_endpoint + urlParams
        def keystorePath = "client.p12"
        def keystorePass = "12345"


        def keystore = KeyStore.getInstance("PKCS12")
        def keyStoreFile = new FileInputStream(keystorePath)
        keystore.load(keyStoreFile, keystorePass.toCharArray())


        def sslContext = SSLContexts.custom().loadKeyMaterial(keystore, keystorePass.toCharArray(), new PrivateKeyStrategy() {
            @Override
            public String chooseAlias(Map<String, PrivateKeyDetails> aliases, Socket socket) {
                return aliases.keySet().iterator().next();
            }
        })
                .loadTrustMaterial(new TrustStrategy() {
                    @Override
                    public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        return true;
                    }
                })
                .build();


        def sslSocketFactory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE)
        CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(sslSocketFactory).build()

        def getRequest = new HttpGet(url)    
        getRequest.addHeader("Content-Type", "application/json")
        getRequest.addHeader("Accept", "application/json")


        def response = httpClient.execute(getRequest)

        code = response.getStatusLine().getStatusCode()

        httpClient.close()


         content = response.getEntity().getContent().text
        outputStream.write(content.getBytes(StandardCharsets.UTF_8))
    } as StreamCallback)

    session.putAttribute(flowFile, "invokehttp.status.code", String.valueOf(code))
    session.putAttribute(flowFile, "invokehttp.response.body", String.valueOf(content))
    session.transfer(flowFile, REL_SUCCESS)
} catch (e) {
    log.error('Failed to read avro schema from file.', e);
    session.transfer(flowFile, REL_FAILURE)
}
