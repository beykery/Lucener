package org.lucener.util;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class Mappers {

    public static final ObjectMapper jsonMapper;

    static {
        jsonMapper = new ObjectMapper();
    }

    public static <T> T parseJson(String json, TypeReference<T> type) {
        try {
            return jsonMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public static String json(Object obj) {
        return json(obj, jsonMapper);
    }

    private static String json(Object obj, ObjectMapper mapper) {
        if (obj == null)
            return null;
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
