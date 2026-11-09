package com.dush.csdiagram;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBColor;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.ui.jcef.JBCefJSQuery;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Hosts {@code web/diagram.html} in the embedded browser. Messages from the page are handed to {@code onMessage}
 * on the EDT; {@link #update} and {@link #showError} call into the page.
 * <p>
 * Every JCEF class is referenced here and nowhere else, so a missing embedded browser shows up as a
 * {@link NoClassDefFoundError} when this class is first used, which {@link DiagramFileEditor} catches.
 *
 * @author Dushmantha Bandaranayake
 */
final class DiagramPanel implements Disposable {

    private final JComponent component;
    private JBCefBrowser browser;

    /**
     * @param initialJson the first diagram, written into the page so it draws without a round trip
     * @param onLoaded    called on the EDT each time the page finishes loading. Calls into the page made before that are
     *                    lost, and so are messages the page sends while it loads: the browser runs in a separate
     *                    process and the bridge only connects once the page is loaded.
     */
    DiagramPanel(@NotNull Disposable parent, @NotNull String initialJson, @NotNull Consumer<String> onMessage,
                 @NotNull Runnable onLoaded) {
        Disposer.register(parent, this);
        if (!JBCefApp.isSupported()) {
            component = new JLabel("Embedded browser (JCEF) is not supported by this IDE runtime.", SwingConstants.CENTER);
            return;
        }
        browser = new JBCefBrowser();
        // The cast binds to the create(JBCefBrowserBase) overload, the one to rely on across versions
        JBCefJSQuery query = JBCefJSQuery.create((JBCefBrowserBase) browser);
        Disposer.register(this, browser);
        Disposer.register(this, query);
        query.addHandler(message -> {
            ApplicationManager.getApplication().invokeLater(() -> onMessage.accept(message));
            return null;
        });

        browser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadEnd(CefBrowser cefBrowser, CefFrame frame, int httpStatusCode) {
                if (frame.isMain()) ApplicationManager.getApplication().invokeLater(onLoaded);
            }
        }, browser.getCefBrowser());

        String page = loadResource("/web/diagram.html")
                .replace("/*BRIDGE*/", "window.host = function(m){ " + query.inject("m") + " };")
                .replace("/*DARK*/false", String.valueOf(!JBColor.isBright()))
                .replace("/*DATA*/null", initialJson)
                .replace("/*VIEW*/null", ViewSettings.json());
        browser.loadHTML(page);
        component = browser.getComponent();
    }

    JComponent getComponent() {
        return component;
    }

    /** Redraws the diagram, keeping the page's pan, zoom and selection. */
    void update(@NotNull String json) {
        run("window.update(" + json + ");");
    }

    void showError(@NotNull String message) {
        run("window.showError(" + DiagramJson.str(message) + ");");
    }

    private void run(String js) {
        if (browser == null) return;
        browser.getCefBrowser().executeJavaScript(js, browser.getCefBrowser().getURL(), 0);
    }

    @Override
    public void dispose() {
    }

    private static String loadResource(String path) {
        try (InputStream in = DiagramPanel.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
