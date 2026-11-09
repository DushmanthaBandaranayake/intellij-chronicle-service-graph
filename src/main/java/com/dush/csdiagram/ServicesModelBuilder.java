package com.dush.csdiagram;

import com.dush.csdiagram.ServicesModel.Builder;
import com.dush.csdiagram.ServicesModel.Prop;
import com.dush.csdiagram.ServicesModel.Queue;
import com.dush.csdiagram.ServicesModel.Ref;
import com.dush.csdiagram.ServicesModel.Service;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.YAMLSequence;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jetbrains.yaml.psi.YAMLValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads a {@link ServicesModel} from YAML PSI. Call inside a read action, on a committed document.
 * <pre>
 * !ChronicleServicesCfg {
 *   queues:   { name: { path: ..., builder: !SingleChronicleQueueBuilder { rollCycle: !RollCycles FAST_HOURLY, ... } } },
 *   services: { name: { inputs: [ q1, q2 ], output: q3, snapshotQueue: q3, implClass: ... } }
 * }
 * </pre>
 * Block-style YAML gives the same PSI and works too.
 *
 * @author Dushmantha Bandaranayake
 */
final class ServicesModelBuilder {

    private ServicesModelBuilder() {
    }

    static @NotNull ServicesModel build(@NotNull PsiFile file) {
        ServicesModel model = new ServicesModel();
        YAMLMapping root = rootMapping(file);
        if (root == null) {
            model.problems.add("No YAML mapping found. Expected !ChronicleServicesCfg { queues: {...}, services: {...} }");
            return model;
        }
        if (PsiTreeUtil.hasErrorElements(file)) {
            model.problems.add("The YAML has syntax errors, so the diagram may be incomplete.");
        }

        YAMLKeyValue queues = root.getKeyValueByKey("queues");
        model.queuesMapping = queues == null ? null : asMapping(queues.getValue());
        if (model.queuesMapping == null) model.problems.add("No queues section.");
        else for (YAMLKeyValue kv : model.queuesMapping.getKeyValues()) readQueue(model, kv);

        YAMLKeyValue services = root.getKeyValueByKey("services");
        model.servicesMapping = services == null ? null : asMapping(services.getValue());
        if (model.servicesMapping == null) model.problems.add("No services section.");
        else for (YAMLKeyValue kv : model.servicesMapping.getKeyValues()) readService(model, kv);

        // Queues the services use but the queues section doesn't declare
        for (Service s : model.services.values()) {
            List<Ref> refs = new ArrayList<>(s.inputs);
            if (s.output != null) refs.add(s.output);
            if (s.snapshot != null) refs.add(s.snapshot);
            for (Ref r : refs) {
                if (!model.queues.containsKey(r.queue)) model.queues.put(r.queue, new Queue(r.queue, false, null, null));
            }
        }
        return model;
    }

    static @Nullable YAMLMapping rootMapping(PsiFile file) {
        if (!(file instanceof YAMLFile)) return null;
        for (YAMLDocument doc : ((YAMLFile) file).getDocuments()) {
            YAMLMapping m = asMapping(doc.getTopLevelValue());
            if (m != null) return m;
        }
        return null;
    }

    private static void readQueue(ServicesModel model, YAMLKeyValue kv) {
        PsiElement key = kv.getKey();
        if (key == null) return;
        YAMLMapping mapping = asMapping(kv.getValue());
        Queue q = new Queue(kv.getKeyText(), true, key.getTextRange(), mapping);
        if (mapping != null) {
            for (YAMLKeyValue entry : mapping.getKeyValues()) {
                YAMLMapping builder = ServicesModel.BUILDER.equals(entry.getKeyText()) ? asMapping(entry.getValue()) : null;
                if (builder != null) {
                    q.builder = new Builder(tagOf(builder), builder);
                    for (YAMLKeyValue b : builder.getKeyValues()) addProp(q.builder.entries, b);
                } else {
                    addProp(q.props, entry);
                }
            }
        }
        model.queues.put(q.name, q);
    }

    private static void readService(ServicesModel model, YAMLKeyValue kv) {
        PsiElement key = kv.getKey();
        if (key == null) return;
        YAMLMapping mapping = asMapping(kv.getValue());
        Service s = new Service(kv.getKeyText(), key.getTextRange(), mapping);
        if (mapping != null) {
            for (YAMLKeyValue entry : mapping.getKeyValues()) {
                String k = entry.getKeyText();
                YAMLValue v = entry.getValue();
                if (ServicesModel.INPUTS.equals(k)) {
                    if (v instanceof YAMLSequence) {
                        for (YAMLSequenceItem item : ((YAMLSequence) v).getItems()) {
                            Ref r = ref(item.getValue());
                            if (r != null) s.inputs.add(r);
                        }
                    } else {
                        Ref r = ref(v);   // a single input written without brackets
                        if (r != null) s.inputs.add(r);
                    }
                } else if (ServicesModel.OUTPUT.equals(k)) {
                    s.output = ref(v);
                } else if (ServicesModel.SNAPSHOT.equals(k)) {
                    s.snapshot = ref(v);
                } else {
                    addProp(s.props, entry);
                }
            }
        }
        model.services.put(s.name, s);
    }

    private static @Nullable Ref ref(@Nullable YAMLValue v) {
        if (!(v instanceof YAMLScalar)) return null;
        TextRange range = scalarValueRange((YAMLScalar) v);
        String name = scalarValue((YAMLScalar) v);
        return name.isEmpty() ? null : new Ref(name, range);
    }

    private static void addProp(List<Prop> props, YAMLKeyValue kv) {
        YAMLValue v = kv.getValue();
        String key = kv.getKeyText();
        if (v instanceof YAMLScalar) {
            YAMLScalar s = (YAMLScalar) v;
            props.add(new Prop(key, Prop.Kind.SCALAR, tagOf(s), scalarValue(s), Collections.emptyList(),
                    scalarValueRange(s), kv));
        } else if (v instanceof YAMLSequence) {
            List<String> items = new ArrayList<>();
            for (YAMLSequenceItem item : ((YAMLSequence) v).getItems()) {
                YAMLValue iv = item.getValue();
                items.add(iv instanceof YAMLScalar ? scalarValue((YAMLScalar) iv) : iv == null ? "" : iv.getText());
            }
            props.add(new Prop(key, Prop.Kind.LIST, tagOf(v), v.getText(), items, v.getTextRange(), kv));
        } else if (v instanceof YAMLMapping) {
            props.add(new Prop(key, Prop.Kind.MAP, tagOf(v), v.getText(), Collections.emptyList(), v.getTextRange(), kv));
        } else {
            // "key:" with nothing after it: an empty scalar, which an edit fills in right after the colon
            int end = kv.getKey() == null ? kv.getTextRange().getEndOffset() : colonEnd(kv);
            props.add(new Prop(key, Prop.Kind.SCALAR, null, "", Collections.emptyList(), new TextRange(end, end), kv));
        }
    }

    private static int colonEnd(YAMLKeyValue kv) {
        for (PsiElement c = kv.getKey().getNextSibling(); c != null; c = c.getNextSibling()) {
            if (c.getNode().getElementType() == YAMLTokenTypes.COLON) return c.getTextRange().getEndOffset();
        }
        return kv.getKey().getTextRange().getEndOffset();
    }

    /** The tag on a value, without the '!': {@code SingleChronicleQueueBuilder} for {@code !SingleChronicleQueueBuilder { ... }}. */
    static @Nullable String tagOf(PsiElement value) {
        PsiElement tag = tagElement(value);
        return tag == null ? null : tag.getText().substring(1);
    }

    private static @Nullable PsiElement tagElement(PsiElement value) {
        for (PsiElement c = value.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c.getNode().getElementType() == YAMLTokenTypes.TAG) return c;
        }
        return null;
    }

    /** The range of a scalar's own text, leaving out a leading tag. An edit replaces this range, so the tag is kept. */
    static TextRange scalarValueRange(YAMLScalar s) {
        TextRange whole = s.getTextRange();
        PsiElement tag = tagElement(s);
        if (tag == null) return whole;
        String text = s.getText();
        int i = tag.getTextRange().getEndOffset() - whole.getStartOffset();
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        return new TextRange(whole.getStartOffset() + i, whole.getEndOffset());
    }

    static String scalarValue(YAMLScalar s) {
        if (tagElement(s) == null) return s.getTextValue();
        TextRange r = scalarValueRange(s);
        String raw = r.shiftLeft(s.getTextRange().getStartOffset()).substring(s.getText()).trim();
        if (raw.length() >= 2 && (raw.charAt(0) == '"' || raw.charAt(0) == '\'') && raw.charAt(raw.length() - 1) == raw.charAt(0)) {
            raw = raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    private static @Nullable YAMLMapping asMapping(@Nullable YAMLValue v) {
        return v instanceof YAMLMapping ? (YAMLMapping) v : null;
    }
}
