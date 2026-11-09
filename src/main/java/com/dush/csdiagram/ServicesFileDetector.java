package com.dush.csdiagram;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Decides whether a file is a Chronicle Services config. Runs for every file the user opens, so it stays cheap:
 * a name check, then at most the first 4 KB of a YAML file.
 *
 * @author Dushmantha Bandaranayake
 */
final class ServicesFileDetector {

    static final String ROOT_TAG = "!ChronicleServicesCfg";
    private static final int HEAD = 4096;

    private ServicesFileDetector() {
    }

    static boolean isServicesFile(@NotNull VirtualFile file) {
        if (file.isDirectory() || !file.isValid()) return false;
        String name = file.getName();
        if ("service.yaml".equals(name) || "services.yaml".equals(name)) return true;
        String ext = file.getExtension();
        if (!"yaml".equalsIgnoreCase(ext) && !"yml".equalsIgnoreCase(ext)) return false;
        return headContains(file, ROOT_TAG);
    }

    private static boolean headContains(VirtualFile file, String text) {
        try (InputStream in = file.getInputStream()) {
            byte[] buf = new byte[HEAD];
            int n = 0, r;
            while (n < HEAD && (r = in.read(buf, n, HEAD - n)) > 0) n += r;
            return new String(buf, 0, n, StandardCharsets.UTF_8).contains(text);
        } catch (IOException e) {
            return false;
        }
    }
}
