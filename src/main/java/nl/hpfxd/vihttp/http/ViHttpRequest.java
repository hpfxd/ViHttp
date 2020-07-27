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

package nl.hpfxd.vihttp.http;

import lombok.Getter;
import lombok.SneakyThrows;
import nl.hpfxd.vihttp.ViHttpClient;
import nl.hpfxd.vihttp.http.impl.HttpImpl;
import nl.hpfxd.vihttp.network.ViHttpConnection;
import nl.hpfxd.vihttp.proxy.ViProxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.stream.Collectors;

public class ViHttpRequest {
    @Getter private final ViHttpClient client;
    @Getter private final HttpVersion httpVersion;
    @Getter private final HttpRequestMethod requestMethod;
    @Getter private final String host;
    @Getter private final int port;
    @Getter private final boolean ssl;
    @Getter private final InetSocketAddress address;
    @Getter private final String path;
    @Getter private String body = null;
    private final Map<String, String> headers = new HashMap<>();
    private final Map<String, String> queryParams = new HashMap<>();
    private final List<HttpCompressionScheme> compressionSchemes = new ArrayList<>();

    @Getter private ViProxy proxy;
    @Getter private long timeout;

    public ViHttpRequest(ViHttpClient client, HttpRequestMethod requestMethod, String host, int port, boolean ssl, String path, String queryString) {
        this.client = client;
        this.requestMethod = requestMethod;
        this.host = host;
        this.port = port;
        this.ssl = ssl;
        this.address = new InetSocketAddress(host, port);
        this.httpVersion = this.client.getHttpVersion();
        this.proxy = this.client.getProxy();
        this.timeout = this.client.getTimeout();
        this.path = path.isEmpty() ? "/" : path;
        this.compressionSchemes.addAll(client.getCompressionSchemes());
        if (queryString != null && !queryString.isEmpty()) {
            this.queryParams.putAll(Arrays.stream(queryString.split("&"))
                    .map(s -> Arrays.copyOf(s.split("="), 2))
                    .collect(Collectors.toMap(o -> o[0], o -> o[1])));
        }

        this.setHeader("Host", this.host);
        this.setHeader("Connection", this.client.isConnectionReuseEnabled() ? "keep-alive" : "close");
        this.setHeader("User-Agent", this.client.getUserAgent());
    }

    /**
     * Send the request to the server.
     * @return the response
     */
    @SneakyThrows(IOException.class)
    public ViHttpResponse send() {
        ViHttpConnection connection = null;
        try {
            connection = this.client.getConnectionManager().getConnection(this);
            if (this.client.isConnectionReuseEnabled()) connection.getLock().lock();
            HttpImpl impl = this.httpVersion.getImpl();
            impl.sendRequest(this, connection.getSocket().getOutputStream());
            if (this.client.isPipeliningEnabled()) {
                connection.getLock().unlock();
                connection.getReadLock().lock();
            }
            return impl.getResponse(connection.getSocket().getInputStream());
        } finally {
            if (connection != null) {
                if (this.client.isConnectionReuseEnabled()) {
                    if (this.client.isPipeliningEnabled()) {
                        connection.getReadLock().unlock();
                    } else {
                        connection.getLock().unlock();
                    }
                }
                if (!this.client.isConnectionReuseEnabled()) connection.close();
            }
        }
    }

    /*
     * Headers
     */

    /**
     * Get an unmodifiable copy of the request headers map.
     * @return the request headers
     */
    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(this.headers);
    }

    /**
     * Get a request header by name.
     * @param name the name of the header to be retrieved
     * @return the header value
     */
    public String getHeader(String name) {
        return this.headers.get(name);
    }

    /**
     * Clears all request headers and puts the provided ones.
     * Warning: This will clear headers such as the Host header if you do not provide one!
     * @param headers the headers
     */
    public ViHttpRequest setHeaders(Map<String, String> headers) {
        this.headers.clear();
        headers.forEach(this::setHeader);
        return this;
    }

    /**
     * Set a request header.
     * @param name the header name
     * @param value the header value
     */
    public ViHttpRequest setHeader(String name, String value) {
        this.headers.put(name, value);
        return this;
    }

    /**
     * Remove a request header.
     * @param name the header name
     */
    public ViHttpRequest removeHeader(String name) {
        this.headers.remove(name);
        return this;
    }

    /*
     * Query Parameters
     */

    /**
     * Get an unmodifiable copy of the query parameters map.
     * @return the query parameters
     */
    public Map<String, String> getQueryParams() {
        return Collections.unmodifiableMap(this.queryParams);
    }

    /**
     * Get a query parameter by name.
     * @param name the name of the query parameter to be retrieved
     * @return the query parameter value
     */
    public String getQueryParam(String name) {
        return this.queryParams.get(name);
    }

    /**
     * Clears all request query parameter and puts the provided ones.
     * @param queryParams query parameters
     */
    public ViHttpRequest setQueryParams(Map<String, String> queryParams) {
        this.queryParams.clear();
        queryParams.forEach(this::setQueryParam);
        return this;
    }

    /**
     * Set a query parameter.
     * @param name the query parameter name
     * @param value the query parameter value
     */
    public ViHttpRequest setQueryParam(String name, String value) {
        this.queryParams.put(name, value);
        return this;
    }

    /**
     * Remove a query parameter.
     * @param name the query parameter name
     */
    public ViHttpRequest removeQueryParam(String name) {
        this.queryParams.remove(name);
        return this;
    }

    /*
     * Compression
     */

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
    public ViHttpRequest enableCompressionScheme(HttpCompressionScheme compressionScheme) {
        if (this.compressionSchemes.contains(compressionScheme)) return this;
        this.compressionSchemes.add(compressionScheme);
        return this;
    }

    /**
     * Disable a compression scheme.
     * @param compressionScheme the compression scheme to be disabled
     */
    public ViHttpRequest disableCompressionScheme(HttpCompressionScheme compressionScheme) {
        this.compressionSchemes.remove(compressionScheme);
        return this;
    }

    /*
     * Miscellaneous
     */

    /**
     * Set the proxy to be used for this request.
     * Use {@code null} to not proxy this request.
     * @param proxy the proxy
     */
    public ViHttpRequest setProxy(ViProxy proxy) {
        this.proxy = proxy;
        return this;
    }

    /**
     * Set the timeout to be used for this request.
     * @param timeout the timeout in milliseconds
     */
    public ViHttpRequest setTimeout(long timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Set the proxy to be used for this request.
     * Use {@code null} to not send a user agent.
     * @param userAgent the proxy
     */
    public ViHttpRequest setUserAgent(String userAgent) {
        this.setHeader("User-Agent", userAgent);
        return this;
    }

    /**
     * Set the request body.
     * @param body the request body
     */
    public ViHttpRequest setBody(String body) {
        if (!this.requestMethod.isBodyValid()) throw new IllegalStateException("A body cannot be set on a request with the method " + this.requestMethod.name());
        this.body = body;
        return this;
    }
}
