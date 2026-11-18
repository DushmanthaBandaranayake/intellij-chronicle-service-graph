package com.dush.csdiagram;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The diagram's layout choices, kept in the IDE settings so every diagram opens the way it was last left:
 * direction {@code tb} (top to bottom) or {@code lr} (left to right), and queues drawn as {@code arrows} or {@code boxes}.
 * Also the services and queues hidden from each file's diagram.
 *
 * @author Dushmantha Bandaranayake
 */
final class ViewSettings {

    private static final String KEY = "chronicle.services.diagram.view";
    private static final String HIDDEN_KEY = "chronicle.services.diagram.hidden:";

    private ViewSettings() {
    }

    /** The saved choice as the JSON the page reads, or "null" for the page's defaults. */
    static @NotNull String json() {
        String saved = PropertiesComponent.getInstance().getValue(KEY);
        if (saved == null) return "null";
        String[] parts = saved.split(",");
        if (parts.length != 2 || !validDir(parts[0]) || !validStyle(parts[1])) return "null";
        return "{\"dir\":\"" + parts[0] + "\",\"style\":\"" + parts[1] + "\"}";
    }

    static void save(@NotNull String dir, @NotNull String style) {
        if (validDir(dir) && validStyle(style)) PropertiesComponent.getInstance().setValue(KEY, dir + "," + style);
    }

    /**
     * The services and queues hidden from one file's diagram, as a JSON array of ids ({@code s:name}, {@code q:name}).
     * Kept per project and per file, so they survive closing the file and restarting the IDE.
     */
    static @NotNull String hiddenJson(@NotNull Project project, @NotNull VirtualFile file) {
        String saved = PropertiesComponent.getInstance(project).getValue(hiddenKey(file));
        StringBuilder json = new StringBuilder("[");
        if (saved != null) {
            for (String id : saved.split("\n")) {
                if (!validId(id)) continue;
                if (json.length() > 1) json.append(',');
                json.append(DiagramJson.str(id));
            }
        }
        return json.append(']').toString();
    }

    static void saveHidden(@NotNull Project project, @NotNull VirtualFile file, @NotNull List<String> ids) {
        StringBuilder value = new StringBuilder();
        for (String id : ids) {
            if (!validId(id)) continue;
            if (value.length() > 0) value.append('\n');
            value.append(id);
        }
        PropertiesComponent settings = PropertiesComponent.getInstance(project);
        if (value.length() == 0) settings.unsetValue(hiddenKey(file));
        else settings.setValue(hiddenKey(file), value.toString());
    }

    private static String hiddenKey(VirtualFile file) {
        return HIDDEN_KEY + file.getUrl();
    }

    private static boolean validId(String id) {
        return id.length() > 2 && (id.startsWith("s:") || id.startsWith("q:")) && id.indexOf('\n') < 0;
    }

    private static boolean validDir(String dir) {
        return "tb".equals(dir) || "lr".equals(dir);
    }

    private static boolean validStyle(String style) {
        return "arrows".equals(style) || "boxes".equals(style);
    }
}
