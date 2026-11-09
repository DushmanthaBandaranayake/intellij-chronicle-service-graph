package com.dush.csdiagram;

import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public abstract class ServicesTestCase extends BasePlatformTestCase {

    protected static String sample() {
        try (InputStream in = ServicesTestCase.class.getResourceAsStream("/samples/services.yaml")) {
            assertNotNull("sample missing", in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    protected void open(String text) {
        myFixture.configureByText("services.yaml", text);
    }

    protected ServicesModel model() {
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        return ServicesModelBuilder.build(myFixture.getFile());
    }

    protected YamlEditor editor() {
        return new YamlEditor(getProject(), myFixture.getFile());
    }

    protected String text() {
        return myFixture.getEditor().getDocument().getText();
    }
}
