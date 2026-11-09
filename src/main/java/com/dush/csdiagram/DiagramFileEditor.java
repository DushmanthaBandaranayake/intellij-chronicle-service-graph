package com.dush.csdiagram;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.JBSplitter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Diagram tab. Rebuilds the diagram from the YAML when the text changes (only while the tab is showing),
 * and turns messages from the page into {@link YamlEditor} edits.
 * <p>
 * Page → IDE messages, fields separated by tabs:
 * <ul>
 *   <li>{@code I}: the page has loaded and wants data</li>
 *   <li>{@code N kind old new}: rename a queue ({@code q}) or service ({@code s})</li>
 *   <li>{@code P owner key value [tag]}: set a scalar ({@code owner} is {@code q:name}, {@code s:name} or {@code b:name});
 *       the optional tag is used only when the entry has to be added</li>
 *   <li>{@code L owner key item…}: set a list</li>
 *   <li>{@code A queue type}: add a builder</li>
 *   <li>{@code G owner}: show the declaration in the YAML beside the diagram and focus it</li>
 *   <li>{@code S owner}: a node was selected; scroll the YAML to its declaration without taking focus</li>
 *   <li>{@code V dir style}: the layout toggles changed; remember them in {@link ViewSettings}</li>
 * </ul>
 *
 * @author Dushmantha Bandaranayake
 */
final class DiagramFileEditor extends UserDataHolderBase implements FileEditor {

    private final Project project;
    private final VirtualFile file;
    private final JComponent component;
    private final @Nullable DiagramPanel panel;
    private final @Nullable Editor yamlEditor;
    private final Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
    private boolean showing, stale = true, disposed;

    DiagramFileEditor(@NotNull Project project, @NotNull VirtualFile file) {
        this.project = project;
        this.file = file;

        DiagramPanel created;
        JComponent comp;
        try {
            created = new DiagramPanel(this, currentJson(), this::receive, this::refresh);
            comp = created.getComponent();
        } catch (NoClassDefFoundError missingBrowser) {
            created = null;
            comp = new JLabel("<html><center>The diagram needs IntelliJ's embedded browser, which isn't available.<br>"
                    + "In Settings &gt; Plugins, enable or install \"Web Browser (JCEF)\", then restart the IDE.</center></html>",
                    SwingConstants.CENTER);
        }
        panel = created;

        // The YAML text beside the diagram: a second editor on the same document, so both stay in sync and share undo
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document != null) {
            yamlEditor = EditorFactory.getInstance().createEditor(document, project, file, false);
            JBSplitter split = new JBSplitter(false, "chronicle.services.diagram.split", 0.35f);
            split.setFirstComponent(yamlEditor.getComponent());
            split.setSecondComponent(comp);
            component = split;
        } else {
            yamlEditor = null;
            component = comp;
        }

        if (document != null) {
            document.addDocumentListener(new DocumentListener() {
                @Override
                public void documentChanged(@NotNull DocumentEvent event) {
                    stale = true;
                    if (showing) scheduleRefresh();
                }
            }, this);
        }
    }

    private void scheduleRefresh() {
        alarm.cancelAllRequests();
        alarm.addRequest(this::refresh, 300);
    }

    private void refresh() {
        if (disposed || panel == null || project.isDisposed()) return;
        PsiDocumentManager.getInstance(project).performWhenAllCommitted(() -> {
            if (disposed || !file.isValid()) return;
            stale = false;
            panel.update(currentJson());
        });
    }

    private String currentJson() {
        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
            PsiFile psi = file.isValid() ? PsiManager.getInstance(project).findFile(file) : null;
            return psi == null ? "null" : DiagramJson.build(ServicesModelBuilder.build(psi));
        });
    }

    private void receive(String message) {
        if (disposed || panel == null || message.isEmpty()) return;
        List<String> f = new ArrayList<>(Arrays.asList(message.substring(1).split("\t", -1)));
        char kind = message.charAt(0);
        if (kind == 'I') {
            refresh();
            return;
        }
        if (kind == 'V') {
            if (f.size() >= 2) ViewSettings.save(f.get(0), f.get(1));
            return;
        }
        if (kind == 'G' || kind == 'S') {
            navigate(f.get(0), kind == 'G');
            return;
        }

        PsiFile psi = PsiManager.getInstance(project).findFile(file);
        if (psi == null) return;
        YamlEditor editor = new YamlEditor(project, psi);
        String error;
        try {
            switch (kind) {
                case 'N':
                    error = "q".equals(f.get(0)) ? editor.renameQueue(f.get(1), f.get(2).trim())
                            : editor.renameService(f.get(1), f.get(2).trim());
                    break;
                case 'P':
                    error = editor.setScalar(f.get(0), f.get(1), f.get(2), f.size() > 3 ? f.get(3) : null);
                    break;
                case 'L':
                    List<String> items = new ArrayList<>(f.subList(2, f.size()));
                    items.removeIf(s -> s.trim().isEmpty());
                    error = editor.setList(f.get(0), f.get(1), items);
                    break;
                case 'A':
                    error = editor.addBuilder(f.get(0), f.get(1));
                    break;
                default:
                    return;
            }
        } catch (IndexOutOfBoundsException badMessage) {
            error = "Unexpected message from the diagram page.";
        }
        if (error != null) {
            panel.showError(error);
            NotificationGroupManager.getInstance().getNotificationGroup("Chronicle Services Diagram")
                    .createNotification(error, NotificationType.WARNING).notify(project);
        }
        refresh();
    }

    /**
     * Shows the declaration of a queue or service in the YAML beside the diagram: scrolls to it and selects the name,
     * and with {@code focus} also moves the keyboard focus there. Without that editor, switches to the Text tab.
     */
    private void navigate(String owner, boolean focus) {
        PsiFile psi = PsiManager.getInstance(project).findFile(file);
        if (psi == null || owner.length() < 3) return;
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        ServicesModel model = ServicesModelBuilder.build(psi);
        String name = owner.substring(2);
        TextRange range = null;
        if (owner.startsWith("s:") && model.services.containsKey(name)) {
            range = model.services.get(name).nameRange;
        } else if (model.queues.containsKey(name)) {
            ServicesModel.Queue q = model.queues.get(name);
            range = q.nameRange;
            if (range == null && !model.referencesTo(name).isEmpty()) range = model.referencesTo(name).get(0).range;
        }
        int offset = range == null ? 0 : range.getStartOffset();
        if (yamlEditor != null) {
            yamlEditor.getCaretModel().moveToOffset(offset);
            if (range != null) yamlEditor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
            yamlEditor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
            if (focus) IdeFocusManager.getInstance(project).requestFocus(yamlEditor.getContentComponent(), true);
            return;
        }
        // openTextEditor selects the Text tab of this file's editor
        FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, file, offset), true);
    }

    @Override
    public @NotNull JComponent getComponent() {
        return component;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return component;
    }

    @Override
    public @NotNull String getName() {
        return "Diagram";
    }

    @Override
    public void setState(@NotNull FileEditorState state) {
    }

    @Override
    public boolean isModified() {
        return false;   // edits go straight into the document, which the text editor saves
    }

    @Override
    public boolean isValid() {
        return file.isValid();
    }

    @Override
    public void selectNotify() {
        showing = true;
        if (stale) refresh();
    }

    @Override
    public void deselectNotify() {
        showing = false;
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    }

    @Override
    public @Nullable FileEditorLocation getCurrentLocation() {
        return null;
    }

    @Override
    public @NotNull VirtualFile getFile() {
        return file;
    }

    @Override
    public void dispose() {
        disposed = true;
        if (yamlEditor != null && !yamlEditor.isDisposed()) EditorFactory.getInstance().releaseEditor(yamlEditor);
    }
}
