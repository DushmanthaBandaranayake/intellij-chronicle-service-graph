package com.dush.csdiagram;

import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLScalar;

import java.util.List;

/**
 * Makes a service's {@code implClass} value a reference to that class, so Ctrl+click (or Ctrl+B) on it opens the class.
 * The reference is soft: a class that isn't in this project is not reported as an error.
 *
 * @author Dushmantha Bandaranayake
 */
final class ImplClassReferenceContributor extends PsiReferenceContributor {

    static final String IMPL_CLASS = "implClass";

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(YAMLScalar.class), new PsiReferenceProvider() {
            @Override
            public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                TextRange range = implClassRange((YAMLScalar) element);
                return range == null ? PsiReference.EMPTY_ARRAY : new PsiReference[]{new ImplClassReference(element, range)};
            }
        });
    }

    /**
     * The class name's range inside the scalar, when the scalar is the value of {@code implClass} in a service:
     * {@code services: { name: { implClass: com.acme.Impl } }} in a Chronicle Services file.
     */
    static @Nullable TextRange implClassRange(@NotNull YAMLScalar scalar) {
        if (!(scalar.getParent() instanceof YAMLKeyValue)) return null;
        YAMLKeyValue entry = (YAMLKeyValue) scalar.getParent();
        if (entry.getValue() != scalar || !IMPL_CLASS.equals(entry.getKeyText())) return null;
        YAMLMapping serviceBody = entry.getParentMapping();
        if (serviceBody == null || !(serviceBody.getParent() instanceof YAMLKeyValue)) return null;
        YAMLMapping services = ((YAMLKeyValue) serviceBody.getParent()).getParentMapping();
        if (services == null || !(services.getParent() instanceof YAMLKeyValue)
                || !"services".equals(((YAMLKeyValue) services.getParent()).getKeyText())) return null;
        VirtualFile file = scalar.getContainingFile().getOriginalFile().getVirtualFile();
        if (file == null || !ServicesFileDetector.isServicesFile(file)) return null;

        String name = scalar.getTextValue().trim(), text = scalar.getText();
        int start = name.isEmpty() ? -1 : text.indexOf(name);
        return start < 0 ? null : new TextRange(start, start + name.length());
    }

    private static final class ImplClassReference extends PsiReferenceBase<PsiElement> {

        ImplClassReference(PsiElement element, TextRange range) {
            super(element, range, true);
        }

        @Override
        public @Nullable PsiElement resolve() {
            List<NavigationItem> found = ClassFinder.find(getElement().getProject(), getValue());
            // ClassFinder only returns PsiQualifiedNamedElement items, which are PSI elements
            return found.isEmpty() ? null : (PsiElement) found.get(0);
        }

        @Override
        public Object @NotNull [] getVariants() {
            return EMPTY_ARRAY;
        }
    }
}
