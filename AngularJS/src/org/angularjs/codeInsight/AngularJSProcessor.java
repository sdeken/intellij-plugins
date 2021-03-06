package org.angularjs.codeInsight;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.javascript.flex.XmlBackedJSClassImpl;
import com.intellij.lang.javascript.psi.JSDefinitionExpression;
import com.intellij.lang.javascript.psi.JSFile;
import com.intellij.lang.javascript.psi.JSPsiElementBase;
import com.intellij.lang.javascript.psi.resolve.ImplicitJSVariableImpl;
import com.intellij.lang.javascript.psi.resolve.JSResolveUtil;
import com.intellij.lang.javascript.psi.stubs.JSImplicitElement;
import com.intellij.lang.javascript.psi.stubs.impl.JSImplicitElementImpl;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.html.HtmlEmbeddedContentImpl;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.Consumer;
import com.intellij.xml.util.documentation.HtmlDescriptorsTable;
import org.angularjs.codeInsight.attributes.AngularAttributesRegistry;
import org.angularjs.lang.parser.AngularJSElementTypes;
import org.angularjs.lang.psi.AngularJSRecursiveVisitor;
import org.angularjs.lang.psi.AngularJSRepeatExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Dennis.Ushakov
 */
public class AngularJSProcessor {
  private static final Map<String, String> NG_REPEAT_IMPLICITS = new HashMap<String, String>();
  static {
    NG_REPEAT_IMPLICITS.put("$index", "Number");
    NG_REPEAT_IMPLICITS.put("$first", "Boolean");
    NG_REPEAT_IMPLICITS.put("$middle", "Boolean");
    NG_REPEAT_IMPLICITS.put("$last", "Boolean");
    NG_REPEAT_IMPLICITS.put("$even", "Boolean");
    NG_REPEAT_IMPLICITS.put("$odd", "Boolean");
  }

  public static void process(final PsiElement element, final Consumer<JSPsiElementBase> consumer) {
    final PsiElement original = CompletionUtil.getOriginalOrSelf(element);
    PsiFile hostFile = FileContextUtil.getContextFile(original != element ? original : element.getContainingFile().getOriginalFile());
    if (!(hostFile instanceof XmlFile)) {
      hostFile = original.getContainingFile();
    }
    if (!(hostFile instanceof XmlFile)) return;

    final XmlFile file = (XmlFile)hostFile;

    final Collection<JSPsiElementBase> cache = CachedValuesManager.getCachedValue(file, new CachedValueProvider<Collection<JSPsiElementBase>>() {
      @Nullable
      @Override
      public Result<Collection<JSPsiElementBase>> compute() {
        final Collection<JSPsiElementBase> result = new ArrayList<JSPsiElementBase>();
        processDocument(file.getDocument(), result);

        return Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
    for (JSPsiElementBase namedElement : cache) {
      if (scopeMatches(original, namedElement)){
        consumer.consume(namedElement);
      }
    }
  }

  private static void processDocument(XmlDocument document, final Collection<JSPsiElementBase> result) {
    if (document == null) return;
    final AngularInjectedFilesVisitor visitor = new AngularInjectedFilesVisitor(result);

    for (XmlTag tag : PsiTreeUtil.getChildrenOfTypeAsList(document, XmlTag.class)) {
      new XmlBackedJSClassImpl.InjectedScriptsVisitor(tag, null, true, true, visitor, true){
        @Override
        public boolean execute(@NotNull PsiElement element) {
          if (element instanceof HtmlEmbeddedContentImpl) {
            processDocument(PsiTreeUtil.findChildOfType(element, XmlDocument.class), result);
          }
          if (element instanceof XmlAttribute) {
            visitor.accept(element);
          }
          return super.execute(element);
        }
      }.go();
    }
  }

  private static boolean scopeMatches(PsiElement element, PsiElement declaration) {
    if (declaration instanceof JSImplicitElement) {
      declaration = declaration.getParent();
    }
    final InjectedLanguageManager injector = InjectedLanguageManager.getInstance(element.getProject());
    final PsiLanguageInjectionHost elementContainer = injector.getInjectionHost(element);
    final XmlTagChild elementTag = PsiTreeUtil.getNonStrictParentOfType(elementContainer, XmlTag.class, XmlText.class);
    final PsiLanguageInjectionHost declarationContainer = injector.getInjectionHost(declaration);
    final XmlTagChild declarationTag = PsiTreeUtil.getNonStrictParentOfType(declarationContainer, XmlTag.class, XmlText.class);

    if (declarationContainer != null && elementContainer != null && elementTag != null && declarationTag != null) {
      return PsiTreeUtil.isAncestor(declarationTag, elementTag, true) ||
             (PsiTreeUtil.isAncestor(declarationTag, elementTag, false) &&
              declarationContainer.getTextOffset() < elementContainer.getTextOffset());
    }
    return true;
  }

  public static JSImplicitElementImpl.Builder createVariable(HtmlTag tag, XmlAttribute attribute, String name) {
    final JSImplicitElementImpl.Builder elementBuilder = new JSImplicitElementImpl.Builder(name.substring(1), attribute)
      .setType(JSImplicitElement.Type.Variable);

    final String tagName = tag.getName();
    if (HtmlDescriptorsTable.getTagDescriptor(tagName) != null) {
      elementBuilder.setTypeString("HTML" + StringUtil.capitalize(tagName) + "Element");
    }
    return elementBuilder;
  }

  private static class AngularInjectedFilesVisitor extends JSResolveUtil.JSInjectedFilesVisitor {
    private final Collection<JSPsiElementBase> myResult;

    public AngularInjectedFilesVisitor(Collection<JSPsiElementBase> result) {
      myResult = result;
    }

    @Override
    protected void process(JSFile file) {
      accept(file);
    }

    protected void accept(PsiElement element) {
      element.accept(new AngularJSRecursiveVisitor() {
        @Override
        public void visitJSDefinitionExpression(JSDefinitionExpression node) {
          myResult.add(node);
          super.visitJSDefinitionExpression(node);
        }

        @Override
        public void visitAngularJSRepeatExpression(AngularJSRepeatExpression repeatExpression) {
          if (repeatExpression.getNode().getElementType() == AngularJSElementTypes.REPEAT_EXPRESSION) {
            for (Map.Entry<String, String> entry : NG_REPEAT_IMPLICITS.entrySet()) {
              myResult.add(new ImplicitJSVariableImpl(entry.getKey(), entry.getValue(), repeatExpression));
            }
          }
          super.visitAngularJSRepeatExpression(repeatExpression);
        }
      });
      if (element instanceof XmlAttribute) {
        final String name = ((XmlAttribute)element).getName();
        if (AngularAttributesRegistry.isVariableAttribute(name, element.getProject())) {
          final JSImplicitElementImpl.Builder builder = createVariable((HtmlTag)element.getParent(),
                                                                                                 (XmlAttribute)element, name);
          myResult.add(builder.toImplicitElement());
        }
      }
    }
  }
}
