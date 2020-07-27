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

import lombok.Data;

import java.util.Map;

@Data
public class ViHttpResponse {
    private final int statusCode;
    private final Map<String, String> headers;
    private final String body;

    /**
     * Get a header value.
     * @param header the header name
     * @return the header value
     */
    public String getHeader(String header) {
        return this.headers.get(header);
    }

    /**
     * See if a header exists.
     * @param header the header name
     * @return if the header exists
     */
    public boolean hasHeader(String header) {
        return this.headers.containsKey(header);
    }
}
