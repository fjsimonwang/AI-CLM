package com.acme.clm.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Thin, null-tolerant JSON helpers over a shared ObjectMapper. */
@Component
public class Json {

    private static ObjectMapper MAPPER = new ObjectMapper();

    public Json(ObjectMapper mapper) { MAPPER = mapper; }

    public static ObjectMapper mapper() { return MAPPER; }

    public static String write(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (Exception e) { throw new RuntimeException("JSON serialize failed", e); }
    }

    public static JsonNode read(String json) {
        try { return json == null || json.isBlank() ? MAPPER.createObjectNode() : MAPPER.readTree(json); }
        catch (Exception e) { throw new ApiExceptions.BadRequestException("Invalid JSON: " + e.getMessage()); }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> readMap(String json) {
        try {
            if (json == null || json.isBlank()) return new java.util.LinkedHashMap<>();
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            throw new ApiExceptions.BadRequestException("Invalid JSON object: " + e.getMessage());
        }
    }

    public static List<Map<String, Object>> readListOfMaps(String json) {
        try {
            if (json == null || json.isBlank()) return new java.util.ArrayList<>();
            return MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            throw new ApiExceptions.BadRequestException("Invalid JSON array: " + e.getMessage());
        }
    }

    public static <T> T convert(Object value, Class<T> type) {
        return MAPPER.convertValue(value, type);
    }
}
