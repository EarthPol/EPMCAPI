package com.earthpol.epmcapi.networking;

import com.earthpol.epmcapi.exception.BadRequestResponse;
import com.google.gson.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** A bounded field tree, shared by projection and optional data gathering. */
public final class FieldSelection {
    private static final FieldSelection ALL = new FieldSelection(new Node(true));
    private final Node root;

    private static final class Node {
        boolean whole;
        final Map<String, Node> children = new LinkedHashMap<>();
        Node(boolean whole) { this.whole = whole; }
    }

    private FieldSelection(Node root) { this.root = root; }

    public static FieldSelection all() { return ALL; }
    public boolean isAll() { return root.whole; }

    public static FieldSelection parse(JsonElement value) {
        if (value == null) return ALL;
        if (!value.isJsonArray() || value.getAsJsonArray().isEmpty() || value.getAsJsonArray().size() > 50)
            throw new BadRequestResponse("'fields' must contain between 1 and 50 field paths");
        Node root = new Node(false);
        for (JsonElement field : value.getAsJsonArray()) {
            if (!field.isJsonPrimitive() || !field.getAsJsonPrimitive().isString())
                throw new BadRequestResponse("Field paths must be strings");
            String path = field.getAsString();
            String[] parts = path.split("\\.", -1);
            if (path.length() > 200 || parts.length > 8 || !path.matches("[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*"))
                throw new BadRequestResponse("Invalid field path");
            Node current = root;
            for (String part : parts) {
                if (current.whole) break;
                current = current.children.computeIfAbsent(part, ignored -> new Node(false));
            }
            current.whole = true;
            current.children.clear();
        }
        return new FieldSelection(root);
    }

    public boolean includes(String path) {
        Node current = root;
        for (String part : path.split("\\.")) {
            if (current.whole) return true;
            current = current.children.get(part);
            if (current == null) return false;
        }
        return true;
    }

    public JsonElement project(JsonElement value) {
        if (isAll()) return value;
        JsonElement selected = project(value, root);
        return selected == null ? JsonNull.INSTANCE : selected;
    }

    private static JsonElement project(JsonElement value, Node node) {
        if (node.whole || value.isJsonNull()) return value.deepCopy();
        if (value.isJsonArray()) {
            JsonArray selected = new JsonArray();
            for (JsonElement item : value.getAsJsonArray()) {
                JsonElement child = project(item, node);
                if (child != null) selected.add(child);
            }
            return selected;
        }
        if (!value.isJsonObject()) return null;
        JsonObject selected = new JsonObject();
        for (var field : node.children.entrySet()) {
            JsonElement child = value.getAsJsonObject().get(field.getKey());
            if (child != null) {
                JsonElement projected = project(child, field.getValue());
                if (projected != null) selected.add(field.getKey(), projected);
            }
        }
        return selected;
    }
}
