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

package nl.hpfxd.vihttp.http.impl;

import nl.hpfxd.vihttp.http.HttpCompressionScheme;
import nl.hpfxd.vihttp.http.ViHttpRequest;
import nl.hpfxd.vihttp.http.ViHttpResponse;
import nl.hpfxd.vihttp.proxy.ViProxyType;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class Http1Impl implements HttpImpl {
    @Override
    public void sendRequest(ViHttpRequest request, OutputStream outputStream) throws IOException {
        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(outputStream)));

        String path = request.getPath();
        if (!request.getQueryParams().isEmpty()) {
            path += request.getQueryParams().entrySet().stream()
                    .map(p -> urlEncodeUTF8(p.getKey()) + "=" + urlEncodeUTF8(p.getValue()))
                    .reduce((p1, p2) -> p1 + "&" + p2)
                    .map(s -> "?" + s)
                    .orElse("");
        }

        if (request.getProxy() != null && request.getProxy().getType() == ViProxyType.HTTP_REQUEST) {
            /*
             * for http request proxies, we have to change the request a bit
             * normal request example:
             * GET /test HTTP/1.1
             * http request proxy example:
             * GET http://host:port/test HTTP/1.1
             */
            path = "http" + (request.isSsl() ? "s" : "") + "://" + request.getAddress().getAddress().getHostAddress() + ":" + request.getPort()
                    + path;
        }

        if (request.getCompressionSchemes().size() > 0) {
            request.setHeader("Accept-Encoding", request.getCompressionSchemes().stream()
                    .map(HttpCompressionScheme::getToken)
                    .collect(Collectors.joining(", "))); // set Accept-Encoding header to a list of our supported compression schemes
        }

        out.println(request.getRequestMethod().name() + " " + path + " HTTP/1.1"); // write status line
        request.getHeaders().forEach((name, value) -> out.println(name + ": " + value)); // write headers

        if (request.getBody() != null) { // write body
            byte[] body = request.getBody().getBytes(StandardCharsets.UTF_8);
            out.println("Content-Length: " + body.length);
            out.println();
            outputStream.write(body);
        }

        out.println(); // finish request
        out.flush();
    }

    @Override
    public ViHttpResponse getResponse(InputStream in) throws IOException {
        int statusCode = Integer.parseInt(readLine(in).split(" ")[1]);
        Map<String, String> headers = new HashMap<>();
        for (String header = readLine(in); header.length() > 0; header = readLine(in)) {
            int pos = header.indexOf(":");
            String name = header.substring(0, pos);
            String value = header.substring(pos + 2);
            headers.put(name, value);
        }

        String body = null;
        if (headers.containsKey("Content-Length")) {
            int bodyLength = Integer.parseInt(headers.get("Content-Length"));
            InputStream bodyIn = in;
            if (headers.containsKey("Content-Encoding")) {
                System.out.println("ya");
                System.out.println(bodyIn.getClass().getSimpleName());
                bodyIn = HttpCompressionScheme.wrapInputStream(bodyIn, HttpCompressionScheme.parseSchemeList(headers.get("Content-Encoding")), bodyLength);
            }
            byte[] bodyBytes = new byte[bodyLength];
            for (int i = 0; i < bodyBytes.length; i++) {
                bodyBytes[i] = (byte) bodyIn.read();
                if (bodyBytes[i] == -1) throw new IOException("EOF reached while reading body. Expected " + bodyBytes.length + " bytes but only got to " + i);
            }
            body = new String(bodyBytes, StandardCharsets.UTF_8);
        }

        return new ViHttpResponse(statusCode, Collections.unmodifiableMap(headers), body);
    }

    private static String urlEncodeUTF8(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public static String readLine(InputStream inputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int c;
        for (c = inputStream.read(); c != '\n' && c != -1 ; c = inputStream.read()) {
            if (c != '\r') baos.write(c);
        }

        if (c == -1 && baos.size() == 0) {
            throw new IOException("EOF while reading line.");
        }
        return baos.toString("UTF-8");
    }
}
