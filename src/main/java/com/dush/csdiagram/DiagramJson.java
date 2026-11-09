package com.dush.csdiagram;

import com.dush.csdiagram.ServicesModel.Prop;
import com.dush.csdiagram.ServicesModel.Queue;
import com.dush.csdiagram.ServicesModel.Ref;
import com.dush.csdiagram.ServicesModel.Service;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Writes a {@link ServicesModel} as the JSON that {@code web/diagram.html} draws:
 * <pre>
 * { queues:   [ { name, declared, props: [prop], builder: { type, entries: [prop] } | null } ],
 *   services: [ { name, inputs: [name], output, snapshot, props: [prop] } ],
 *   problems: [ text ] }
 * prop = { key, kind: "scalar" | "list" | "map", tag, value, items: [text] }
 * </pre>
 *
 * @author Dushmantha Bandaranayake
 */
final class DiagramJson {

    private DiagramJson() {
    }

    static String build(ServicesModel model) {
        StringBuilder sb = new StringBuilder("{\"queues\":[");
        boolean first = true;
        for (Queue q : model.queues.values()) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"name\":").append(str(q.name))
                    .append(",\"declared\":").append(q.declared)
                    .append(",\"props\":");
            props(sb, q.props);
            sb.append(",\"builder\":");
            if (q.builder == null) {
                sb.append("null");
            } else {
                sb.append("{\"type\":").append(str(q.builder.type)).append(",\"entries\":");
                props(sb, q.builder.entries);
                sb.append('}');
            }
            sb.append('}');
        }
        sb.append("],\"services\":[");
        first = true;
        for (Service s : model.services.values()) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"name\":").append(str(s.name)).append(",\"inputs\":[");
            for (int i = 0; i < s.inputs.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(str(s.inputs.get(i).queue));
            }
            sb.append("],\"output\":").append(ref(s.output))
                    .append(",\"snapshot\":").append(ref(s.snapshot))
                    .append(",\"props\":");
            props(sb, s.props);
            sb.append('}');
        }
        sb.append("],\"problems\":[");
        for (int i = 0; i < model.problems.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(str(model.problems.get(i)));
        }
        return sb.append("]}").toString();
    }

    private static void props(StringBuilder sb, List<Prop> props) {
        sb.append('[');
        for (int i = 0; i < props.size(); i++) {
            Prop p = props.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"key\":").append(str(p.key))
                    .append(",\"kind\":").append(str(p.kind.name().toLowerCase()))
                    .append(",\"tag\":").append(str(p.tag))
                    .append(",\"value\":").append(str(p.value))
                    .append(",\"items\":[");
            for (int j = 0; j < p.items.size(); j++) {
                if (j > 0) sb.append(',');
                sb.append(str(p.items.get(j)));
            }
            sb.append("]}");
        }
        sb.append(']');
    }

    private static String ref(@Nullable Ref r) {
        return r == null ? "null" : str(r.queue);
    }

    /** A JSON string literal, also safe to paste into JavaScript source. */
    static String str(@Nullable String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '<': sb.append("\\u003c"); break;   // no "</script>" inside the page
                default:
                    if (c < 0x20 || c == ' ' || c == ' ') sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
