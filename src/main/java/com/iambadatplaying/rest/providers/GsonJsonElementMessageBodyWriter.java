package com.iambadatplaying.rest.providers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

@Provider
@Produces(MediaType.APPLICATION_JSON)
public class GsonJsonElementMessageBodyWriter implements MessageBodyWriter<JsonElement> {

    private final Gson GSON;

    public GsonJsonElementMessageBodyWriter() {
        GSON = new Gson();
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return JsonElement.class.isAssignableFrom(type);
    }

    @Override
    public void writeTo(JsonElement jsonElement, Class<?> type, Type genericType, Annotation[] annotations,
                        MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
            throws IOException {
        try (Writer writer = new OutputStreamWriter(entityStream)) {
            GSON.toJson(jsonElement, writer);
        }
    }
}