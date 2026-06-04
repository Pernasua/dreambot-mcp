package com.pernasua.dreambot.mcp;

import org.dreambot.api.wrappers.widgets.message.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RecentMessages {
    private final int capacity;
    private final List<MessageRecord> messages = Collections.synchronizedList(new ArrayList<>());

    RecentMessages(int capacity) {
        this.capacity = capacity;
    }

    void record(Message message) {
        if (message == null) {
            return;
        }
        String text = RuntimeText.cleanText(message.getMessage());
        if (text.isEmpty()) {
            return;
        }
        synchronized (messages) {
            messages.add(new MessageRecord(
                System.currentTimeMillis(),
                message.getTypeID(),
                String.valueOf(message.getType()),
                message.getUsername(),
                message.getMessage(),
                message.getClan(),
                message.getTime()
            ));
            while (messages.size() > capacity) {
                messages.remove(0);
            }
        }
    }

    String json(int limit) {
        StringBuilder sb = new StringBuilder("[");
        if (limit > 0) {
            List<MessageRecord> copy;
            synchronized (messages) {
                copy = new ArrayList<>(messages);
            }
            int start = Math.max(0, copy.size() - limit);
            for (int i = start; i < copy.size(); i++) {
                if (i > start) {
                    sb.append(",");
                }
                MessageRecord message = copy.get(i);
                sb.append("{");
                RuntimeJson.field(sb, "seen_at_ms", message.seenAtMs).append(",");
                RuntimeJson.field(sb, "type_id", message.typeId).append(",");
                RuntimeJson.field(sb, "type", message.type).append(",");
                RuntimeJson.field(sb, "username", message.username).append(",");
                RuntimeJson.field(sb, "message", message.message).append(",");
                RuntimeJson.field(sb, "clean", RuntimeText.cleanText(message.message)).append(",");
                RuntimeJson.field(sb, "clan", message.clan).append(",");
                RuntimeJson.field(sb, "time", message.time);
                sb.append("}");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private static class MessageRecord {
        final long seenAtMs;
        final int typeId;
        final String type;
        final String username;
        final String message;
        final String clan;
        final int time;

        MessageRecord(long seenAtMs, int typeId, String type, String username, String message, String clan, int time) {
            this.seenAtMs = seenAtMs;
            this.typeId = typeId;
            this.type = type;
            this.username = username;
            this.message = message;
            this.clan = clan;
            this.time = time;
        }
    }
}
