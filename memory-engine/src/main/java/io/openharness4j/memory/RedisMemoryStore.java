package io.openharness4j.memory;

import io.openharness4j.api.Message;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class RedisMemoryStore implements MemoryStore {

    private final String host;
    private final int port;
    private final String keyPrefix;

    public RedisMemoryStore(String host, int port) {
        this(host, port, "openharness:memory:");
    }

    public RedisMemoryStore(String host, int port, String keyPrefix) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port <= 0) {
            throw new IllegalArgumentException("port must be greater than zero");
        }
        this.host = host;
        this.port = port;
        this.keyPrefix = keyPrefix == null ? "openharness:memory:" : keyPrefix;
    }

    @Override
    public List<Message> load(String sessionId) {
        List<String> encoded = command("LRANGE", key(sessionId), "0", "-1");
        List<Message> messages = new ArrayList<>();
        for (String value : encoded) {
            messages.add(StoredMessageCodec.decode(value));
        }
        return List.copyOf(messages);
    }

    @Override
    public void save(String sessionId, Message message) {
        if (message == null) {
            return;
        }
        command("RPUSH", key(sessionId), StoredMessageCodec.encode(message));
    }

    @Override
    public void replace(String sessionId, List<Message> messages) {
        clear(sessionId);
        if (messages == null || messages.isEmpty()) {
            return;
        }
        List<String> args = new ArrayList<>();
        args.add(key(sessionId));
        messages.stream().map(StoredMessageCodec::encode).forEach(args::add);
        command("RPUSH", args.toArray(String[]::new));
    }

    @Override
    public void clear(String sessionId) {
        command("DEL", key(sessionId));
    }

    private String key(String sessionId) {
        return keyPrefix + MemoryStore.normalizeSessionId(sessionId);
    }

    private List<String> command(String command, String... args) {
        List<String> parts = new ArrayList<>();
        parts.add(command);
        parts.addAll(List.of(args));

        try (Socket socket = new Socket(host, port);
             BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
             BufferedInputStream input = new BufferedInputStream(socket.getInputStream())) {
            output.write(toResp(parts));
            output.flush();
            return readResp(input);
        } catch (IOException ex) {
            throw new MemoryStoreException("failed to execute Redis memory command", ex);
        }
    }

    private static byte[] toResp(List<String> parts) {
        StringBuilder builder = new StringBuilder();
        builder.append('*').append(parts.size()).append("\r\n");
        for (String part : parts) {
            byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
            builder.append('$').append(bytes.length).append("\r\n");
            builder.append(part).append("\r\n");
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static List<String> readResp(BufferedInputStream input) throws IOException {
        int marker = input.read();
        if (marker == '+') {
            readLine(input);
            return List.of();
        }
        if (marker == ':') {
            readLine(input);
            return List.of();
        }
        if (marker == '-') {
            throw new IOException(readLine(input));
        }
        if (marker == '*') {
            int count = Integer.parseInt(readLine(input));
            List<String> values = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                values.add(readBulkString(input));
            }
            return values;
        }
        if (marker == '$') {
            return List.of(readBulkStringAfterLength(input));
        }
        throw new IOException("unsupported Redis response marker: " + (char) marker);
    }

    private static String readBulkString(BufferedInputStream input) throws IOException {
        int marker = input.read();
        if (marker != '$') {
            throw new IOException("expected bulk string");
        }
        return readBulkStringAfterLength(input);
    }

    private static String readBulkStringAfterLength(BufferedInputStream input) throws IOException {
        int length = Integer.parseInt(readLine(input));
        if (length < 0) {
            return "";
        }
        byte[] bytes = input.readNBytes(length);
        input.read();
        input.read();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String readLine(BufferedInputStream input) throws IOException {
        StringBuilder builder = new StringBuilder();
        int previous = -1;
        int current;
        while ((current = input.read()) != -1) {
            if (previous == '\r' && current == '\n') {
                builder.setLength(builder.length() - 1);
                return builder.toString();
            }
            builder.append((char) current);
            previous = current;
        }
        throw new IOException("unexpected end of Redis response");
    }
}
