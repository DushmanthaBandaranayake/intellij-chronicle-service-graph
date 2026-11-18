package com.dush.csdiagram;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * Saves or copies an image of the diagram that the page drew. Kinds: {@code png-copy} (a Base64 PNG for the clipboard),
 * {@code png-save} (a Base64 PNG to save) and {@code svg-save} (SVG text to save).
 *
 * @author Dushmantha Bandaranayake
 */
final class DiagramExport {

    private DiagramExport() {
    }

    /** Call on the EDT: it may show a save dialog. */
    static void handle(@NotNull Project project, @NotNull VirtualFile yamlFile, @NotNull String kind, @NotNull String data) {
        try {
            switch (kind) {
                case "png-copy":
                    Image image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(data)));
                    if (image == null) throw new IOException("the page sent an image that couldn't be read");
                    CopyPasteManager.getInstance().setContents(new ImageSelection(image));
                    notify(project, "Diagram copied to the clipboard as an image", NotificationType.INFORMATION);
                    break;
                case "png-save":
                    save(project, yamlFile, "png", Base64.getDecoder().decode(data));
                    break;
                case "svg-save":
                    save(project, yamlFile, "svg", data.getBytes(StandardCharsets.UTF_8));
                    break;
                default:
                    break;
            }
        } catch (IOException | IllegalArgumentException ex) {
            notify(project, "Could not export the diagram: " + ex.getMessage(), NotificationType.ERROR);
        }
    }

    private static void save(Project project, VirtualFile yamlFile, String extension, byte[] bytes) {
        // The String[] keeps this bound to the varargs constructor, the only one that exists in every supported IDE
        FileSaverDescriptor descriptor = new FileSaverDescriptor("Export Diagram",
                "Save the diagram as " + ("png".equals(extension) ? "a PNG image" : "an SVG image"), new String[]{extension});
        VirtualFile folder = yamlFile.getParent();
        Path start = folder != null && folder.isInLocalFileSystem() ? Paths.get(folder.getPath()) : null;
        VirtualFileWrapper target = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
                .save(start, yamlFile.getNameWithoutExtension() + "-diagram." + extension);
        if (target == null) return;

        File file = target.getFile();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                Files.write(file.toPath(), bytes);
                LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);   // show it in the Project view
                notify(project, "Diagram saved to " + file.getPath(), NotificationType.INFORMATION);
            } catch (IOException ex) {
                notify(project, "Could not save the diagram: " + ex.getMessage(), NotificationType.ERROR);
            }
        });
    }

    private static void notify(Project project, String message, NotificationType type) {
        NotificationGroupManager.getInstance().getNotificationGroup("Chronicle Services Diagram")
                .createNotification(message, type).notify(project);
    }

    /** An image on the clipboard. */
    private static final class ImageSelection implements Transferable {

        private final Image image;

        ImageSelection(Image image) {
            this.image = image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public @NotNull Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
            return image;
        }
    }
}
