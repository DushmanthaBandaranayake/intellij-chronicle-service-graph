package com.dush.csdiagram;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Queues and services read from a Chronicle Services config. Ranges are document offsets of the PSI the model was
 * built from, so a model is only good for edits until the document changes.
 *
 * @author Dushmantha Bandaranayake
 */
final class ServicesModel {

    /** Keys of a service whose values name queues. */
    static final String INPUTS = "inputs", OUTPUT = "output", SNAPSHOT = "snapshotQueue", BUILDER = "builder";

    final Map<String, Queue> queues = new LinkedHashMap<>();
    final Map<String, Service> services = new LinkedHashMap<>();
    final List<String> problems = new ArrayList<>();
    @Nullable YAMLMapping queuesMapping, servicesMapping;

    /** A key and its value. A value is a scalar (optionally tagged, like {@code !RollCycles FAST_HOURLY}), a list or a mapping. */
    static final class Prop {
        enum Kind { SCALAR, LIST, MAP }

        final String key;
        final Kind kind;
        final @Nullable String tag;      // without the '!'
        final String value;              // scalar text without the tag; for a list or mapping, its source text
        final List<String> items;        // list items, empty unless kind == LIST
        final TextRange valueRange;      // scalar: the text after the tag; list/mapping: the whole value
        final YAMLKeyValue keyValue;

        Prop(String key, Kind kind, @Nullable String tag, String value, List<String> items,
             TextRange valueRange, YAMLKeyValue keyValue) {
            this.key = key;
            this.kind = kind;
            this.tag = tag;
            this.value = value;
            this.items = items;
            this.valueRange = valueRange;
            this.keyValue = keyValue;
        }
    }

    /** A queue name used by a service, with the range of that name in the document. */
    static final class Ref {
        final String queue;
        final TextRange range;

        Ref(String queue, TextRange range) {
            this.queue = queue;
            this.range = range;
        }
    }

    static final class Builder {
        final @Nullable String type;
        final YAMLMapping mapping;
        final List<Prop> entries = new ArrayList<>();

        Builder(@Nullable String type, YAMLMapping mapping) {
            this.type = type;
            this.mapping = mapping;
        }
    }

    static final class Queue {
        final String name;
        final boolean declared;           // false: only referenced by a service
        final @Nullable TextRange nameRange;
        final @Nullable YAMLMapping mapping;
        final List<Prop> props = new ArrayList<>();   // everything except the builder
        @Nullable Builder builder;

        Queue(String name, boolean declared, @Nullable TextRange nameRange, @Nullable YAMLMapping mapping) {
            this.name = name;
            this.declared = declared;
            this.nameRange = nameRange;
            this.mapping = mapping;
        }
    }

    static final class Service {
        final String name;
        final TextRange nameRange;
        final @Nullable YAMLMapping mapping;
        final List<Ref> inputs = new ArrayList<>();
        @Nullable Ref output, snapshot;
        final List<Prop> props = new ArrayList<>();   // everything except inputs, output and snapshotQueue

        Service(String name, TextRange nameRange, @Nullable YAMLMapping mapping) {
            this.name = name;
            this.nameRange = nameRange;
            this.mapping = mapping;
        }
    }

    /** Every reference to the queue in the services, in document order. */
    @NotNull List<Ref> referencesTo(@NotNull String queue) {
        List<Ref> refs = new ArrayList<>();
        for (Service s : services.values()) {
            for (Ref r : s.inputs) if (r.queue.equals(queue)) refs.add(r);
            if (s.output != null && s.output.queue.equals(queue)) refs.add(s.output);
            if (s.snapshot != null && s.snapshot.queue.equals(queue)) refs.add(s.snapshot);
        }
        return refs;
    }

    static @Nullable Prop find(List<Prop> props, String key) {
        for (Prop p : props) if (p.key.equals(key)) return p;
        return null;
    }
}
