/*
 * Copyright (c) 2020 Nathan M.
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT
 * OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package nl.hpfxd.vihttp;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import nl.hpfxd.vihttp.http.HttpCompressionScheme;
import nl.hpfxd.vihttp.http.HttpRequestMethod;
import nl.hpfxd.vihttp.http.HttpVersion;
import nl.hpfxd.vihttp.http.ViHttpRequest;
import nl.hpfxd.vihttp.network.ConnectionManager;
import nl.hpfxd.vihttp.network.ViHttpConnection;
import nl.hpfxd.vihttp.proxy.ViProxy;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ViHttpClient {
    @Getter private final ConnectionManager connectionManager;
    @Getter @Setter private HttpVersion httpVersion = HttpVersion.HTTP_1;
    @Getter private ViProxy proxy;
    @Getter private String userAgent = "ViHttp Client";
    @Getter private long timeout = 30000;
    private final List<HttpCompressionScheme> compressionSchemes = new ArrayList<>();
    @Getter private boolean connectionReuseEnabled = true;
    @Getter private boolean pipeliningEnabled = false;

    public ViHttpClient() {
        this.connectionManager = new ConnectionManager();
    }

    public ViHttpRequest request(HttpRequestMethod method, URL url) {
        boolean ssl = url.getProtocol().equals("https");
        int port = url.getPort() != -1 ? url.getPort() : (ssl ? 443 : 80);
        return new ViHttpRequest(this, method, url.getHost(), port, ssl, url.getPath(), url.getQuery());
    }

    @SneakyThrows(MalformedURLException.class)
    public ViHttpRequest request(HttpRequestMethod method, String url) {
        return this.request(method, new URL(url));
    }

    public ViHttpRequest get(URL url) {
        return this.request(HttpRequestMethod.GET, url);
    }

    public ViHttpRequest get(String url) {
        return this.request(HttpRequestMethod.GET, url);
    }

    public ViHttpRequest post(URL url) {
        return this.request(HttpRequestMethod.POST, url);
    }

    public ViHttpRequest post(String url) {
        return this.request(HttpRequestMethod.POST, url);
    }

    public ViHttpRequest put(URL url) {
        return this.request(HttpRequestMethod.PUT, url);
    }

    public ViHttpRequest put(String url) {
        return this.request(HttpRequestMethod.PUT, url);
    }

    public ViHttpRequest patch(URL url) {
        return this.request(HttpRequestMethod.PATCH, url);
    }

    public ViHttpRequest patch(String url) {
        return this.request(HttpRequestMethod.PATCH, url);
    }

    /**
     * Set the proxy to be used for requests by this client.
     * Use {@code null} to not set a default proxy.
     * @param proxy the proxy
     */
    public ViHttpClient setProxy(ViProxy proxy) {
        this.proxy = proxy;
        return this;
    }

    /**
     * Set the user agent to be used for requests.
     * @param userAgent the user agent
     */
    public ViHttpClient setUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    /**
     * Set the timeout to be used for requests.
     * @param timeout the timeout in milliseconds
     */
    public ViHttpClient setTimeout(long timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Get the enabled compression schemes.
     * @return an unmodifiable list of enabled compression schemes
     */
    public List<HttpCompressionScheme> getCompressionSchemes() {
        return Collections.unmodifiableList(this.compressionSchemes);
    }

    /**
     * Enable a compression scheme.
     * @param compressionScheme the compression scheme to be enabled
     */
    public ViHttpClient enableCompressionScheme(HttpCompressionScheme compressionScheme) {
        if (this.compressionSchemes.contains(compressionScheme)) return this;
        this.compressionSchemes.add(compressionScheme);
        return this;
    }

    /**
     * Disable a compression scheme.
     * @param compressionScheme the compression scheme to be disabled
     */
    public ViHttpClient disableCompressionScheme(HttpCompressionScheme compressionScheme) {
        this.compressionSchemes.remove(compressionScheme);
        return this;
    }

    /**
     * Allow connections to be reused.
     */
    public ViHttpClient enableConnectionReuse() {
        this.connectionReuseEnabled = true;
        return this;
    }

    /**
     * Disallow connections to be reused.
     */
    public ViHttpClient disableConnectionReuse() {
        this.connectionReuseEnabled = false;
        return this;
    }

    /**
     * Allow pipelining.
     * This allows multiple requests to be sent at once, then be read FIFO.
     * This will only take effect if multiple threads are making requests to a host at once.
     */
    public ViHttpClient enablePipelining() {
        this.pipeliningEnabled = true;
        return this;
    }

    /**
     * Disallow pipelining.
     */
    public ViHttpClient disablePipelining() {
        this.pipeliningEnabled = false;
        return this;
    }

    public void shutdown() {
        this.connectionManager.getConnections().values().forEach(ViHttpConnection::close);
    }
}
