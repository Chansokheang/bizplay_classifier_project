package com.api.bizplay_conversational.config;

import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * Lets {@code @PropertySource} read a YAML file — Spring's default factory only understands
 * {@code .properties}. Delegates to Boot's {@link YamlPropertySourceLoader}, which flattens the
 * document to dotted keys (e.g. {@code bizplay.endpoints.settlement-draft}) for relaxed binding.
 *
 * <p>Used only by {@link BizplayEndpoints} to load {@code bizplay-endpoints.yml}.
 */
public class YamlPropertySourceFactory implements PropertySourceFactory {

    @Override
    @NonNull
    public PropertySource<?> createPropertySource(@Nullable String name,
                                                  @NonNull EncodedResource resource) throws IOException {
        String sourceName = name != null ? name : resource.getResource().getFilename();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load(sourceName == null ? "bizplay-endpoints.yml" : sourceName, resource.getResource());
        if (sources.isEmpty()) {
            throw new IOException("No properties loaded from YAML resource: " + resource.getResource());
        }
        return sources.get(0);
    }
}
