package com.iambadatplaying.rest.providers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import javax.ws.rs.Consumes;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

@Provider
@Consumes(MediaType.APPLICATION_JSON)
public class GsonJsonElementMessageBodyReader implements MessageBodyReader<JsonElement> {

    private final Gson GSON;

    public GsonJsonElementMessageBodyReader() {
        GSON = new Gson();
    }

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return JsonElement.class.isAssignableFrom(type);
    }

    @Override
    public JsonElement readFrom(Class<JsonElement> type, Type genericType, Annotation[] annotations,
                                MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
            throws IOException {
        try (Reader reader = new InputStreamReader(entityStream)) {
            return GSON.fromJson(reader, JsonElement.class);
        } catch (JsonParseException e) {
            throw new IOException("Failed to parse JSON", e);
        }
    }
}