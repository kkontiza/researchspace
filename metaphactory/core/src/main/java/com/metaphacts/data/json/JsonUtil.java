/*
 * Copyright (C) 2015-2017, metaphacts GmbH
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, you can receive a copy
 * of the GNU Lesser General Public License from http://www.gnu.org/
 */

package com.metaphacts.data.json;

import java.io.IOException;
import java.io.OutputStream;
import java.util.function.BiConsumer;

import javax.ws.rs.core.StreamingOutput;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.base.Throwables;

/**
 * @author Aretm Kozlov <ak@metaphacts.com>
 */
public class JsonUtil {

    private static final ObjectMapper mapper = new ObjectMapper();
    static {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Resource.class, new RdfValueSerializers.ResourceSerializer());
        module.addSerializer(IRI.class, new RdfValueSerializers.IriSerializer());
        mapper.registerModule(module);
    }

    public static <T> String toJson(T obj) throws RuntimeException {
        String json = null;
        try {
            json = JsonUtil.mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            Throwables.propagate(e);
        }
        return json;
    }

    public static ObjectMapper getDefaultObjectMapper() {
        return mapper;
    }

    @FunctionalInterface
    public static interface JsonFieldProducer extends BiConsumer<JsonGenerator, String> {}

    /**
     * Utility that helps to map over JSON Array in a streaming fashion,
     * apply some function for every value,
     * and produce JSON object where initial value will be used as a key and produced one as a value.
     */
    public static StreamingOutput processJsonMap(
        final JsonParser jp, final JsonFieldProducer fn
    ) throws IOException {
        return new StreamingOutput() {
            @Override
            public void write(OutputStream os) throws IOException {
                JsonFactory jfactory = new JsonFactory();
                try (JsonGenerator jGenerator = jfactory.createGenerator(os)) {
                    jGenerator.writeStartObject();
                    while (jp.nextToken() != JsonToken.END_ARRAY) {
                        String iriString = jp.getText();
                        fn.accept(jGenerator, iriString);
                    }
                    jGenerator.writeEndObject();
                }
            }
        };
    }
}
