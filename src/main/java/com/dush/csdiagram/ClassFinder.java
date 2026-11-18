package com.dush.csdiagram;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiQualifiedNamedElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds a class by its fully qualified name, such as a service's {@code implClass}. It asks the IDE's "Go to Class"
 * contributors, so it needs no Java plugin and finds classes in any language that has them (Java, Kotlin, …),
 * in the project and its libraries. Call it in a read action; it uses the indexes, so it finds nothing while they are
 * being built.
 *
 * @author Dushmantha Bandaranayake
 */
final class ClassFinder {

    private ClassFinder() {
    }

    static @NotNull List<NavigationItem> find(@NotNull Project project, @NotNull String qualifiedName) {
        String fqn = qualifiedName.trim().replace('$', '.');   // a nested class may be written with $
        List<NavigationItem> found = new ArrayList<>();
        if (fqn.isEmpty() || project.isDisposed()) return found;
        String shortName = fqn.substring(fqn.lastIndexOf('.') + 1);
        for (ChooseByNameContributor contributor : ChooseByNameContributor.CLASS_EP_NAME.getExtensionList()) {
            NavigationItem[] items;
            try {
                items = contributor.getItemsByName(shortName, shortName, project, true);
            } catch (IndexNotReadyException indexing) {
                continue;
            }
            for (NavigationItem item : items) {
                if (item instanceof PsiQualifiedNamedElement && !found.contains(item)
                        && fqn.equals(((PsiQualifiedNamedElement) item).getQualifiedName())) {
                    found.add(item);
                }
            }
        }
        return found;
    }
}
