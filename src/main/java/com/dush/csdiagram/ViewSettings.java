package com.dush.csdiagram;

import com.intellij.ide.util.PropertiesComponent;
import org.jetbrains.annotations.NotNull;

/**
 * The diagram's layout choices, kept in the IDE settings so every diagram opens the way it was last left:
 * direction {@code tb} (top to bottom) or {@code lr} (left to right), and queues drawn as {@code arrows} or {@code boxes}.
 *
 * @author Dushmantha Bandaranayake
 */
final class ViewSettings {

    private static final String KEY = "chronicle.services.diagram.view";

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

    private static boolean validDir(String dir) {
        return "tb".equals(dir) || "lr".equals(dir);
    }

    private static boolean validStyle(String style) {
        return "arrows".equals(style) || "boxes".equals(style);
    }
}
