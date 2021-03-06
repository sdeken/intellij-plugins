package org.angularjs.codeInsight;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlToken;
import org.angularjs.codeInsight.refs.AngularJSReferencesContributor;
import org.angularjs.index.AngularIndexUtil;
import org.angularjs.index.AngularModuleIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static org.angularjs.codeInsight.AngularJavaScriptCompletionContributor.addCompletionVariants;

/**
 * @author Irina.Chernushina on 3/30/2016.
 */
public class AngularJSXmlCompletionContributor extends CompletionContributor {
  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    final PsiElement originalPosition = parameters.getOriginalPosition();
    if (originalPosition != null && AngularIndexUtil.hasAngularJS(originalPosition.getProject())) {
      final PsiElement position = originalPosition instanceof XmlToken ? originalPosition.getParent() : originalPosition;
      if (AngularJSReferencesContributor.NG_APP_REF.accepts(position)) {
        final Collection<String> keys = AngularIndexUtil.getAllKeys(AngularModuleIndex.KEY, originalPosition.getProject());
        addCompletionVariants(result, keys, " (AngularJS module)");
        result.stopHere();
      }
    }
  }
}
