package com.pernasua.dreambot.mcp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ScriptHttpServer implements AutoCloseable {
    interface Handler {
        RuntimeResponse handle(RuntimeRequest request) throws Exception;
    }

    private final String bindHost;
    private final int port;
    private final Handler handler;
    private ExecutorService executor;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean running;

    ScriptHttpServer(String bindHost, int port, Handler handler) {
        this.bindHost = bindHost;
        this.port = port;
        this.handler = handler;
    }

    void start() {
        running = true;
        executor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "DreamBotMcpScript-http-worker");
            thread.setDaemon(true);
            return thread;
        });
        acceptThread = new Thread(this::acceptLoop, "DreamBotMcpScript-http-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    @Override
    public void close() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void acceptLoop() {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(InetAddress.getByName(bindHost), port), 50);
            serverSocket = socket;
            while (running) {
                try {
                    Socket client = socket.accept();
                    executor.submit(() -> handleClient(client));
                } catch (IOException e) {
                    if (running) {
                        System.out.println("DreamBotMcpScript accept error: " + e);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("DreamBotMcpScript failed to listen on " + bindHost + ":" + port + ": " + e);
            running = false;
        }
    }

    private void handleClient(Socket socket) {
        try {
            socket.setSoTimeout(8000);
            RuntimeRequest request = readRequest(socket.getInputStream());
            RuntimeResponse response = handler.handle(request);
            writeResponse(socket.getOutputStream(), response);
        } catch (Throwable t) {
            try {
                writeResponse(socket.getOutputStream(), RuntimeResponse.serverError(t));
            } catch (IOException ignored) {
            }
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private RuntimeRequest readRequest(InputStream input) throws IOException {
        ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
        int matched = 0;
        int b;
        byte[] end = new byte[]{'\r', '\n', '\r', '\n'};
        while ((b = input.read()) != -1) {
            headerBytes.write(b);
            matched = b == end[matched] ? matched + 1 : (b == end[0] ? 1 : 0);
            if (matched == end.length) {
                break;
            }
            if (headerBytes.size() > 65536) {
                throw new IOException("headers too large");
            }
        }
        String headersText = headerBytes.toString(StandardCharsets.UTF_8.name());
        String[] lines = headersText.split("\\r?\\n");
        if (lines.length == 0) {
            throw new IOException("empty request");
        }
        String[] requestLine = lines[0].split(" ");
        if (requestLine.length < 2) {
            throw new IOException("bad request line");
        }
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int idx = lines[i].indexOf(':');
            if (idx > 0) {
                headers.put(lines[i].substring(0, idx).trim().toLowerCase(Locale.ROOT), lines[i].substring(idx + 1).trim());
            }
        }
        int length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
        if (length > 65536) {
            throw new IOException("body too large");
        }
        byte[] bodyBytes = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(bodyBytes, offset, length - offset);
            if (read == -1) {
                break;
            }
            offset += read;
        }
        return RuntimeRequest.from(requestLine[0], requestLine[1], headers, new String(bodyBytes, 0, offset, StandardCharsets.UTF_8));
    }

    private void writeResponse(OutputStream output, RuntimeResponse response) throws IOException {
        byte[] bytes = response.body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + response.status + " " + statusText(response.status) + "\r\n"
            + "Content-Type: " + response.contentType + "\r\n"
            + "Content-Length: " + bytes.length + "\r\n"
            + "Connection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.US_ASCII));
        output.write(bytes);
        output.flush();
    }

    private String statusText(int status) {
        if (status == 200) {
            return "OK";
        }
        if (status == 202) {
            return "Accepted";
        }
        if (status == 400) {
            return "Bad Request";
        }
        if (status == 405) {
            return "Method Not Allowed";
        }
        return "Error";
    }
}
