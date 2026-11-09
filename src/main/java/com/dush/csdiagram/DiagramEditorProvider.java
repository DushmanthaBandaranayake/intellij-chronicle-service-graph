package com.dush.csdiagram;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Adds the Diagram editor to Chronicle Services configs. PLACE_AFTER_DEFAULT_EDITOR keeps the normal text editor
 * and shows both as "Text" and "Diagram" tabs at the bottom of the editor.
 *
 * @author Dushmantha Bandaranayake
 */
public final class DiagramEditorProvider implements FileEditorProvider, DumbAware {

    static final String EDITOR_TYPE_ID = "chronicle-services-diagram";

    @Override
    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        return ServicesFileDetector.isServicesFile(file);
    }

    @Override
    public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
        return new DiagramFileEditor(project, file);
    }

    @Override
    public @NotNull String getEditorTypeId() {
        return EDITOR_TYPE_ID;
    }

    @Override
    public @NotNull FileEditorPolicy getPolicy() {
        return FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR;
    }
}
