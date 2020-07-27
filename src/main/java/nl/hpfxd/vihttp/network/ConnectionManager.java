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

package nl.hpfxd.vihttp.network;

import lombok.Getter;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import nl.hpfxd.vihttp.exception.ProxyException;
import nl.hpfxd.vihttp.http.ViHttpRequest;
import nl.hpfxd.vihttp.http.impl.Http1Impl;
import nl.hpfxd.vihttp.proxy.ViProxy;
import nl.hpfxd.vihttp.proxy.ViProxyType;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ConnectionManager {
    private final SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
    private static Timer timer = null;
    @Getter private final ExpiringMap<String, ViHttpConnection> connections = ExpiringMap.builder()
            .expiration(60, TimeUnit.SECONDS)
            .expirationPolicy(ExpirationPolicy.ACCESSED)
            .expirationListener((key, connection) -> {
                ViHttpConnection conn = (ViHttpConnection) connection;
                conn.close();
            })
            .build();

    public ConnectionManager() {
    }

    public ViHttpConnection getConnection(ViHttpRequest request) throws IOException {
        if (request.getClient().isConnectionReuseEnabled()) {
            ViHttpConnection connection = this.findConnection(request);

            if (connection != null) return connection;
        }
        if (request.getProxy() == null) {
            Socket socket = new Socket(request.getAddress().getAddress(), request.getAddress().getPort());
            socket.setTcpNoDelay(true);
            socket.setSoTimeout((int) request.getTimeout());
            return this.getConnection(request, this.getSSLSocket(socket, request));
        }

        Socket socket = new Socket(request.getProxy().getHost(), request.getProxy().getPort());
        socket.setTcpNoDelay(true);
        socket.setSoTimeout((int) request.getTimeout());

        try {
            if (request.getProxy().getType() == ViProxyType.HTTP_REQUEST) {
                return this.getConnection(request, socket);
            } else if (request.getProxy().getType() == ViProxyType.HTTP_CONNECT) {
                OutputStream out = socket.getOutputStream();
                byte[] msg = ("CONNECT " + request.getAddress().getAddress().getHostAddress() + ":" + request.getPort() + " HTTP/1.0\r\n" +
                        (request.getProxy().getAuthentication() != null ? "Proxy-Authorization: " + this.getProxyAuthorization(request.getProxy()) + "\r\n" : "") +
                        "\r\n").getBytes("ASCII7");

                out.write(msg);
                out.flush();

                InputStream in = socket.getInputStream();
                String statusLine = Http1Impl.readLine(in);
                if (!statusLine.contains(" 200 ")) {
                    throw new ProxyException("Unable to establish tunnel. Proxy returned \"" + statusLine + "\"");
                }
                //noinspection StatementWithEmptyBody
                while (Http1Impl.readLine(in).length() > 0);
                return this.getConnection(request, this.getSSLSocket(socket, request));
            } else if (request.getProxy().getType() == ViProxyType.SOCKS4) {
                // https://en.wikipedia.org/wiki/SOCKS#SOCKS4
                OutputStream out = socket.getOutputStream();

                byte[] bytes; // byte array because some socks4 servers don't like it if you send data not all at once
                if (request.getProxy().getAuthentication() == null) {
                    bytes = new byte[9];
                } else {
                    byte[] usernameBytes = request.getProxy().getAuthentication().getUsername().getBytes("ASCII7");
                    bytes = new byte[9 + usernameBytes.length];
                    System.arraycopy(usernameBytes, 0, bytes, 8, usernameBytes.length);
                }
                bytes[0] = 0x04; // VER (0x04 | SOCKS4)
                bytes[1] = 0x01; // CMD (0x01 | Establish a TCP/IP stream connection)
                byte[] port = encodePort(request.getPort()); // DSTPORT
                bytes[2] = port[0];
                bytes[3] = port[1];
                byte[] ip = request.getAddress().getAddress().getAddress(); // DSTIP
                bytes[4] = ip[0];
                bytes[5] = ip[1];
                bytes[6] = ip[2];
                bytes[7] = ip[3];
                bytes[bytes.length - 1] = 0x00;
                out.write(bytes);
                out.flush();

                if (socket.isClosed()) throw new ProxyException("Proxy was closed before response could be read.");
                InputStream in = socket.getInputStream();
                int version = in.read();
                if (version == -1) throw new ProxyException("Unexpected end of data while reading SOCKS reply VN.");
                if (version != 0)
                    throw new ProxyException("Expected a null byte for SOCKS reply VN. Instead received " + version);
                int replyCode = in.read();
                if (replyCode == -1) throw new ProxyException("Unexpected end of data while reading SOCKS reply REP.");

                for (int i = 0; i < 2; i++) {
                    if (in.read() == -1)
                        throw new ProxyException("Unexpected end of data while reading byte " + (i + 1) + " of SOCKS reply DSTPORT.");
                }

                for (int i = 0; i < 4; i++) {
                    if (in.read() == -1)
                        throw new ProxyException("Unexpected end of data while reading byte " + (i + 1) + " of SOCKS reply DSTIP.");
                }

                if (replyCode != 0x5a) {
                    throw new ProxyException("SOCKS server rejected request. Reply code: " + replyCode);
                }

                return this.getConnection(request, this.getSSLSocket(socket, request));
            } else if (request.getProxy().getType() == ViProxyType.SOCKS5) {
                // https://en.wikipedia.org/wiki/SOCKS#SOCKS5
                OutputStream out = socket.getOutputStream();
                InputStream in = socket.getInputStream();

                // CLIENT GREETING
                out.write(0x05); // VER (0x05 | SOCKS5)
                if (request.getProxy().getAuthentication() == null) {
                    out.write(0x01); // NAUTH
                    out.write(0x00); // No authentication
                } else {
                    out.write(0x02); // NAUTH
                    out.write(0x00); // No authentication
                    out.write(0x02); // Username/password
                }
                out.flush();


                // SERVER CHOICE
                if (in.read() != 0x05) throw new ProxyException("SOCKS server replied with incompatible version.");
                int auth = in.read(); // CAUTH
                if (auth == -1) throw new ProxyException("Unexpected end of data while reading SOCKS reply CAUTH.");

                if (auth == 0x02) { // Username/password
                    // CLIENT AUTHENTICATION REQUEST
                    byte[] username = request.getProxy().getAuthentication().getUsername().getBytes("ASCII7");
                    byte[] password = request.getProxy().getAuthentication().getPassword().getBytes("ASCII7");

                    out.write(0x01); // VER
                    out.write(username.length); // IDLEN
                    out.write(username); // ID
                    out.write(password.length); // PWLEN
                    out.write(password); // PW
                    out.flush();

                    // SERVER AUTHENTICATION RESPONSE
                    int authVer = in.read();
                    if (authVer != 0x01)
                        throw new ProxyException("Unexpected version for SOCKS username/password authentication. Expected 0x01, got " + authVer);
                    int authStatus = in.read();
                    if (authStatus != 0x00)
                        throw new ProxyException("Error authenticating with SOCKS proxy. Response: " + authStatus);
                }

                // CONNECTION REQUEST
                out.write(0x05); // VER
                out.write(0x01); // CMD
                out.write(0x00); // RSV
                out.write(0x01); // DSTADDR TYPE
                out.write(request.getAddress().getAddress().getAddress()); // DSTADDR ADDR
                out.write(encodePort(request.getPort())); // DSTPORT
                out.flush();

                // CONNECTION RESPONSE
                if (in.read() != 0x05)
                    throw new ProxyException("SOCKS server replied with incompatible version."); // VER
                int status = in.read(); // STATUS

                if (status != 0x00) {
                    throw new ProxyException("SOCKS server rejected request. Status: " + status);
                }

                if (in.read() == -1) throw new ProxyException("Unexpected end of data while reading SOCKS reply RSV.");
                //noinspection ResultOfMethodCallIgnored
                in.read(new byte[7]); // skip over BNDADDR and BNDPORT since they're not needed
                return this.getConnection(request, this.getSSLSocket(socket, request));
            } else {
                throw new UnsupportedOperationException("The requested proxy type is not implemented.");
            }
        } catch (ProxyException e) {
            socket.close();
            throw e;
        }
    }

    private ViHttpConnection getConnection(ViHttpRequest request, Socket socket) {
        if (timer == null) timer = new Timer("ViHttp Connection Timer");
        ViHttpConnection connection = new ViHttpConnection(request.getClient(), request.getAddress(), socket);

        if (request.getClient().isConnectionReuseEnabled()) {
            connections.put(request.getAddress().toString(), connection);
        }

        return connection;
    }

    private ViHttpConnection findConnection(ViHttpRequest request) {
        return this.connections.get(request.getAddress().toString());
    }

    private Socket getSSLSocket(Socket socket, ViHttpRequest request) throws IOException {
        if (!request.isSsl()) return socket;
        SSLSocket sslSocket = (SSLSocket) this.sslSocketFactory.createSocket(socket, request.getHost(), request.getPort(), false);
        sslSocket.startHandshake();
        return sslSocket;
    }

    private String getProxyAuthorization(ViProxy proxy) {
        return new String(Base64.getEncoder().encode((proxy.getAuthentication().getUsername() + ":" + proxy.getAuthentication().getPassword()).getBytes()));
    }

    private static byte[] encodePort(int value) {
        byte[] bytes = new byte[2];
        bytes[0] = (byte) ((value >> 8) & 0xFF);
        bytes[1] = (byte) (value & 0xFF);
        return bytes;
    }
}
