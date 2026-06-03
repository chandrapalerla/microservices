package com.order.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;

import java.io.IOException;

/**
 * Overrides Spring Cloud OpenFeign's SortJsonComponent.SortSerializer.
 *
 * Feign registers a @JsonComponent for Sort that serializes each order as a
 * plain string ("property: DIRECTION"). When Jackson serializes a PageImpl
 * response containing a Sort with actual orders it calls the same serializer,
 * which wraps any IOException as EncodeException (a RuntimeException).
 * Jackson cannot handle that and the response write fails with
 * HttpMessageNotWritableException, triggering a 500 that trips the gateway CB.
 *
 * This customizer registers a replacement Sort serializer after Feign's is
 * registered, so it wins. It emits a compact JSON object that the frontend
 * already expects: { "sorted": true, "unsorted": false, "empty": false }.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer sortSerializerCustomizer() {
        return builder -> builder.serializerByType(Sort.class, new SortSerializer());
    }

    static class SortSerializer extends StdSerializer<Sort> {

        SortSerializer() {
            super(Sort.class);
        }

        @Override
        public void serialize(Sort sort, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            gen.writeBooleanField("sorted", sort.isSorted());
            gen.writeBooleanField("unsorted", sort.isUnsorted());
            gen.writeBooleanField("empty", sort.isEmpty());
            gen.writeEndObject();
        }
    }
}
