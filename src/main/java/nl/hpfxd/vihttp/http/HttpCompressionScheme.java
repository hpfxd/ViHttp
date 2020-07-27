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

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

@AllArgsConstructor
@Getter
public enum HttpCompressionScheme {
    GZIP("gzip", GZIPInputStream.class)
    ;

    private final String token;
    private final Class<? extends InflaterInputStream> stream;

    @SneakyThrows(ReflectiveOperationException.class)
    public InputStream getInputStream(InputStream in, int bufferSize) {
        return stream.getConstructor(InputStream.class, int.class).newInstance(in, bufferSize);
    }

    public static HttpCompressionScheme getSchemeByToken(String token) {
        for (HttpCompressionScheme scheme : values()) {
            if (scheme.getToken().equals(token)) return scheme;
        }

        return null;
    }

    public static List<HttpCompressionScheme> parseSchemeList(String str) {
        return Arrays.stream(str.split(", "))
                .map(HttpCompressionScheme::getSchemeByToken)
                .collect(Collectors.toList());
    }

    public static InputStream wrapInputStream(InputStream stream, List<HttpCompressionScheme> schemes, int bufferSize) {
        InputStream in = stream;
        for (HttpCompressionScheme scheme : schemes) {
            in = scheme.getInputStream(in, bufferSize);
        }
        return in;
    }
}
