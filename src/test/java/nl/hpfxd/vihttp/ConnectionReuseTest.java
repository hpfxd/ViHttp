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

import nl.hpfxd.vihttp.http.ViHttpResponse;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;

public class ConnectionReuseTest {
    @Test
    public void connectionReuseTest() throws InterruptedException {
        ViHttpClient client = new ViHttpClient()
                .enableConnectionReuse()
                .setUserAgent("ViHttp Test");
        ExecutorService executor = Executors.newFixedThreadPool(64);

        AtomicLong total = new AtomicLong();
        long t1 = System.currentTimeMillis();
        for (int i = 0; i < 256; i++) {
            executor.execute(() -> {
                long time = System.currentTimeMillis();
                ViHttpResponse response = client.get("https://detectportal.firefox.com/success.txt")
                        .send();
                assertEquals(200, response.getStatusCode());
                assertEquals("success\n", response.getBody());
                total.addAndGet(System.currentTimeMillis() - time);
            });
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        System.out.println("connection reuse total took " + (System.currentTimeMillis() - t1) + "ms (average " + (total.get() / 256) + "ms)");
    }
}
