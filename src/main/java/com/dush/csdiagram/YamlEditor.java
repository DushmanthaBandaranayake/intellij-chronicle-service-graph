package com.dush.csdiagram;

import com.dush.csdiagram.ServicesModel.Queue;
import com.dush.csdiagram.ServicesModel.Ref;
import com.dush.csdiagram.ServicesModel.Service;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.YAMLValue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Writes diagram edits back into the YAML text. Each edit is one undoable command. Edits replace document ranges
 * rather than PSI elements, which keeps the user's layout and avoids PSI factory APIs that differ between IDE versions.
 * <p>
 * Owners are written {@code q:<queue>}, {@code s:<service>} or {@code b:<queue>} (that queue's builder).
 * Every method returns null on success, or a message saying why nothing was changed.
 * Call on the EDT.
 *
 * @author Dushmantha Bandaranayake
 */
final class YamlEditor {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_$][A-Za-z0-9_.$-]*");
    private static final Pattern PLAIN = Pattern.compile("[A-Za-z0-9_./$@+-]([A-Za-z0-9_./$@+ -]*[A-Za-z0-9_./$@+-])?");

    private final Project project;
    private final PsiFile file;
    private final Document document;

    YamlEditor(@NotNull Project project, @NotNull PsiFile file) {
        this.project = project;
        this.file = file;
        Document doc = PsiDocumentManager.getInstance(project).getDocument(file);
        if (doc == null) throw new IllegalArgumentException("No document for " + file.getName());
        this.document = doc;
    }

    @Nullable String renameQueue(@NotNull String oldName, @NotNull String newName) {
        if (oldName.equals(newName)) return null;
        String bad = checkName(newName, "Queue");
        if (bad != null) return bad;
        ServicesModel model = model();
        Queue q = model.queues.get(oldName);
        if (q == null) return "There is no queue called " + oldName + ".";
        if (model.queues.containsKey(newName)) return "There is already a queue called " + newName + ".";

        List<Edit> edits = new ArrayList<>();
        if (q.nameRange != null) edits.add(new Edit(q.nameRange, newName));
        for (Ref r : model.referencesTo(oldName)) edits.add(new Edit(r.range, newName));
        return apply("Rename Queue " + oldName + " to " + newName, edits);
    }

    @Nullable String renameService(@NotNull String oldName, @NotNull String newName) {
        if (oldName.equals(newName)) return null;
        String bad = checkName(newName, "Service");
        if (bad != null) return bad;
        ServicesModel model = model();
        Service s = model.services.get(oldName);
        if (s == null) return "There is no service called " + oldName + ".";
        if (model.services.containsKey(newName)) return "There is already a service called " + newName + ".";
        List<Edit> edits = new ArrayList<>();
        edits.add(new Edit(s.nameRange, newName));
        return apply("Rename Service " + oldName + " to " + newName, edits);
    }

    /** Sets a scalar. A tagged value keeps its tag: {@code !RollCycles FAST_HOURLY} becomes {@code !RollCycles HOURLY}. */
    @Nullable String setScalar(@NotNull String owner, @NotNull String key, @NotNull String value) {
        return setScalar(owner, key, value, null);
    }

    /** Like {@link #setScalar(String, String, String)}; a new entry is written with {@code tag} (without '!'), if given. */
    @Nullable String setScalar(@NotNull String owner, @NotNull String key, @NotNull String value, @Nullable String newTag) {
        String text = scalarText(value.trim());
        if (newTag != null && !newTag.isEmpty()) {
            if (!NAME.matcher(newTag).matches()) return "\"" + newTag + "\" isn't a valid tag.";
            return set(owner, key, text, true, "!" + newTag + " " + text);
        }
        return set(owner, key, text, true, text);
    }

    /** Sets a list, written in flow style: {@code [ a, b ]}. */
    @Nullable String setList(@NotNull String owner, @NotNull String key, @NotNull List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            sb.append(i == 0 ? " " : ", ").append(scalarText(items.get(i).trim()));
        }
        sb.append(" ]");
        return set(owner, key, sb.toString(), false, sb.toString());
    }

    @Nullable String addBuilder(@NotNull String queue, @NotNull String type) {
        if (!NAME.matcher(type).matches()) return "\"" + type + "\" isn't a valid builder type.";
        ServicesModel model = model();
        Queue q = model.queues.get(queue);
        if (q == null || q.mapping == null) return "Declare " + queue + " in the queues section first.";
        if (q.builder != null) return queue + " already has a builder.";
        List<Edit> edits = new ArrayList<>();
        String bad = insertEntry(q.mapping, ServicesModel.BUILDER, "!" + type + " { }", edits);
        return bad != null ? bad : apply("Add Builder to " + queue, edits);
    }

    /** {@code text} replaces an existing value; {@code newText} is the value of an entry that has to be added. */
    private @Nullable String set(String owner, String key, String text, boolean keepTag, String newText) {
        if (!NAME.matcher(key).matches()) return "\"" + key + "\" isn't a valid key.";
        ServicesModel model = model();
        YAMLMapping mapping = ownerMapping(model, owner);
        if (mapping == null) return "Can't find " + describe(owner) + " in the YAML.";

        List<Edit> edits = new ArrayList<>();
        YAMLKeyValue kv = mapping.getKeyValueByKey(key);
        if (kv == null) {
            String bad = insertEntry(mapping, key, newText, edits);
            if (bad != null) return bad;
        } else {
            YAMLValue v = kv.getValue();
            if (v == null) {
                int at = colonEnd(kv);
                edits.add(new Edit(new TextRange(at, at), " " + newText));
            } else if (v instanceof YAMLScalar && keepTag) {
                edits.add(new Edit(ServicesModelBuilder.scalarValueRange((YAMLScalar) v), text));
            } else {
                edits.add(new Edit(v.getTextRange(), text));
            }
        }
        return apply("Set " + key + " of " + describe(owner), edits);
    }

    private static @Nullable YAMLMapping ownerMapping(ServicesModel model, String owner) {
        if (owner.length() < 3 || owner.charAt(1) != ':') return null;
        String name = owner.substring(2);
        switch (owner.charAt(0)) {
            case 'q': {
                Queue q = model.queues.get(name);
                return q == null ? null : q.mapping;
            }
            case 'b': {
                Queue q = model.queues.get(name);
                return q == null || q.builder == null ? null : q.builder.mapping;
            }
            case 's': {
                Service s = model.services.get(name);
                return s == null ? null : s.mapping;
            }
            default:
                return null;
        }
    }

    private static String describe(String owner) {
        String name = owner.length() > 2 ? owner.substring(2) : owner;
        switch (owner.isEmpty() ? ' ' : owner.charAt(0)) {
            case 'q': return "queue " + name;
            case 'b': return "the builder of " + name;
            case 's': return "service " + name;
            default: return owner;
        }
    }

    /**
     * Adds {@code key: text} as the last entry of a mapping, following its style: on its own line when the mapping
     * spans lines, inline otherwise, and with a trailing comma if the current last entry has one.
     */
    private @Nullable String insertEntry(YAMLMapping mapping, String key, String text, List<Edit> edits) {
        String doc = document.getCharsSequence().toString();
        TextRange mr = mapping.getTextRange();
        String entry = key + ": " + text;
        int open = flowOpenBrace(mapping);
        List<YAMLKeyValue> kvs = new ArrayList<>(mapping.getKeyValues());

        if (kvs.isEmpty()) {
            if (open < 0) return "Can't add to an empty block mapping.";
            edits.add(new Edit(new TextRange(open + 1, open + 1), " " + entry));
            return null;
        }
        YAMLKeyValue last = kvs.get(kvs.size() - 1);
        int end = trimmedEnd(doc, last.getTextRange());
        String indent = indentOf(doc, last.getTextRange().getStartOffset());
        boolean multiline = doc.substring(mr.getStartOffset(), mr.getEndOffset()).indexOf('\n') >= 0;

        if (open < 0) {   // block style
            edits.add(new Edit(new TextRange(end, end), "\n" + indent + entry));
            return null;
        }
        String gap = multiline ? "\n" + indent : " ";
        int i = end;
        while (i < doc.length() && Character.isWhitespace(doc.charAt(i))) i++;
        if (i < doc.length() && doc.charAt(i) == ',') {
            edits.add(new Edit(new TextRange(i + 1, i + 1), gap + entry + ","));
        } else {
            edits.add(new Edit(new TextRange(end, end), "," + gap + entry));
        }
        return null;
    }

    /** Offset of the '{' that opens a flow mapping, after any tag, or -1 for a block mapping. */
    private static int flowOpenBrace(YAMLMapping mapping) {
        for (PsiElement c = mapping.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c.getNode().getElementType() == YAMLTokenTypes.LBRACE) return c.getTextRange().getStartOffset();
            if (c instanceof YAMLKeyValue) return -1;
        }
        return -1;
    }

    private static int colonEnd(YAMLKeyValue kv) {
        PsiElement key = kv.getKey();
        for (PsiElement c = key == null ? kv.getFirstChild() : key.getNextSibling(); c != null; c = c.getNextSibling()) {
            if (c.getNode().getElementType() == YAMLTokenTypes.COLON) return c.getTextRange().getEndOffset();
        }
        return kv.getTextRange().getEndOffset();
    }

    private static int trimmedEnd(String doc, TextRange r) {
        int end = r.getEndOffset();
        while (end > r.getStartOffset() && Character.isWhitespace(doc.charAt(end - 1))) end--;
        return end;
    }

    private static String indentOf(String doc, int offset) {
        int lineStart = doc.lastIndexOf('\n', offset - 1) + 1;
        StringBuilder sb = new StringBuilder();
        for (int i = lineStart; i < offset; i++) {
            char c = doc.charAt(i);
            sb.append(c == '\t' ? '\t' : ' ');   // keep the column, even when other entries precede it on the line
        }
        return sb.toString();
    }

    private static @Nullable String checkName(String name, String what) {
        if (name.isEmpty()) return what + " name can't be empty.";
        if (!NAME.matcher(name).matches()) {
            return what + " name \"" + name + "\" can only use letters, digits, '_', '$', '.' and '-', and can't start with '.' or '-'.";
        }
        return null;
    }

    /** The value as YAML: plain when that's safe, otherwise double-quoted. */
    static String scalarText(String value) {
        if (PLAIN.matcher(value).matches() && !value.startsWith("- ") && !value.contains(" #")) return value;
        StringBuilder sb = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c == '\n') sb.append("\\n");
            else if (c == '\t') sb.append("\\t");
            else sb.append(c);
        }
        return sb.append('"').toString();
    }

    private ServicesModel model() {
        PsiDocumentManager.getInstance(project).commitDocument(document);
        return ServicesModelBuilder.build(file);
    }

    private @Nullable String apply(String commandName, List<Edit> edits) {
        if (edits.isEmpty()) return null;
        if (!document.isWritable()) return file.getName() + " is read-only.";
        edits.sort(Comparator.comparingInt((Edit e) -> e.range.getStartOffset()).reversed());
        WriteCommandAction.writeCommandAction(project, file).withName(commandName).run(() -> {
            for (Edit e : edits) document.replaceString(e.range.getStartOffset(), e.range.getEndOffset(), e.text);
            PsiDocumentManager.getInstance(project).commitDocument(document);
        });
        return null;
    }

    private static final class Edit {
        final TextRange range;
        final String text;

        Edit(TextRange range, String text) {
            this.range = range;
            this.text = text;
        }
    }
}
