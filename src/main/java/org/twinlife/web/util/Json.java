/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package org.twinlife.web.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

/**
 * Helper class used to manage global instances of JSON reader / writer.
 */
public final class Json {
    private static final ObjectMapper JSON_OBJECT_MAPPER;
    private static final ObjectReader JSON_OBJECT_READER;
    private static final ObjectWriter JSON_OBJECT_WRITER;

    static {
        // Create a JSON mapper with default config. There is no getter for this mapper instance
        // to prevent further (re)configuration. So we ensure that the object reader & writer instances
        // do not need to be re-created.
        JSON_OBJECT_MAPPER = new ObjectMapper();

        JSON_OBJECT_READER = JSON_OBJECT_MAPPER.reader();
        JSON_OBJECT_WRITER = JSON_OBJECT_MAPPER.writer();
    }

    public static ObjectReader getObjectReader() {
        return JSON_OBJECT_READER;
    }

    public static ObjectWriter getObjectWriter() {
        return JSON_OBJECT_WRITER;
    }

    // Prevent any instantiation of this class
    private Json() {
    }

    @Nullable
    public static String getString(final @NonNull JsonNode jsonNode, final String name) {

        JsonNode item = jsonNode.get(name);
        return item == null || !item.isTextual() ? null : item.asText();
    }

    public static int getInteger(final @NonNull JsonNode jsonNode, final String name, int defaultValue) {

        JsonNode item = jsonNode.get(name);
        return item == null || !item.isInt() ? defaultValue : item.asInt();
    }

    public static boolean getBoolean(final @NonNull JsonNode jsonNode, final String name, boolean defaultValue) {

        JsonNode item = jsonNode.get(name);
        return item == null || !item.isBoolean() ? defaultValue : item.asBoolean();
    }
}
