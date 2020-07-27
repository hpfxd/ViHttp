# ViHttp
Simple and lightweight Java HTTP Client library with proxy support.

- Requests to the same host/proxy can share a socket for increased performance.
- Supports compression in responses.
- Supports HTTP/SOCKS proxies.
- Experimental HTTP pipelining support to send multiple requests at once with the same socket. 

## Example
```java
ViHttpClient client = new ViHttpClient()
    .enableConnectionReuse() // enabled by default
    .enablePipelining() // disabled by default
    .setTimeout(5000) // read timeout in milliseconds
    // the following can also be used on individual requests!
    .enableCompressionScheme(HttpCompressionScheme.GZIP)
    .setProxy(new ViProxy(ViProxyType.HTTP_CONNECT, "127.0.0.1", 8080)) // the proxy to use for requests
    .setUserAgent("ViHttp Test"); // the user-agent to send with requests

ViHttpResponse response = client.get("https://google.com/search")
    .setQueryParam("q", "example") // https://google.com/search?q=example
    .send(); // send the request and read the response

System.out.println(response.getBody());
```

You can see some more examples in the `src/test/java/` directory.

## Maven
First, add the JitPack repository.
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Then, add the dependency!
```xml
<dependency>
    <groupId>com.github.hpfxd</groupId>
    <artifactId>ViHttp</artifactId>
    <version>2.1.0</version>
</dependency>
```