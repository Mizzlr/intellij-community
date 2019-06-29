// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.model.MavenDomParent;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.references.MavenPropertyPsiReference;
import org.jetbrains.idea.maven.dom.references.MavenPsiElementWrapper;
import org.jetbrains.idea.maven.server.MavenServerManager;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.util.containers.ContainerUtil.findInstance;

/**
 * @author ibessonov
 */
public class MavenPropertyInParentInspection extends XmlSuppressableInspectionTool {

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return MavenDomBundle.message("inspection.group");
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return MavenDomBundle.message("inspection.property.in.parent.name");
  }

  @Override
  @NotNull
  public String getShortName() {
    return "MavenPropertyInParent";
  }

  @Override
  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (file instanceof XmlFile && (file.isPhysical() || ApplicationManager.getApplication().isUnitTestMode())) {
      DomManager domManager = DomManager.getDomManager(file.getProject());
      DomFileElement<MavenDomProjectModel> model = domManager.getFileElement((XmlFile)file, MavenDomProjectModel.class);

      if (model != null) {
        boolean maven35 = StringUtil.compareVersionNumbers(MavenServerManager.getInstance().getCurrentMavenVersion(), "3.5") >= 0;
        List<ProblemDescriptor> problems = new ArrayList<>(3);

        MavenDomParent mavenParent = model.getRootElement().getMavenParent();
        validate(manager, isOnTheFly, maven35, problems, mavenParent.getGroupId());
        validate(manager, isOnTheFly, maven35, problems, mavenParent.getArtifactId());
        validate(manager, isOnTheFly, maven35, problems, mavenParent.getVersion());

        if (problems.isEmpty()) return ProblemDescriptor.EMPTY_ARRAY;
        return problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
      }
    }

    return null;
  }

  private static void validate(@NotNull InspectionManager manager, boolean isOnTheFly, boolean maven35,
                               @NotNull List<ProblemDescriptor> problems, @NotNull GenericDomValue<String> domValue) {
    String unresolvedValue = domValue.getRawText();
    if (unresolvedValue == null) return;

    String valueToCheck = maven35 ? unresolvedValue.replaceAll("\\$\\{(revision|sha1|changelist)}", "")
                                  : unresolvedValue;
    if (valueToCheck.contains("${")) {
      LocalQuickFix fix = null;
      String resolvedValue = domValue.getStringValue();
      if (resolvedValue == null) return;

      if (unresolvedValue.equals(resolvedValue) || resolvedValue.contains("${")) {
        resolvedValue = resolveXmlElement(domValue.getXmlElement());
      }

      if (!unresolvedValue.equals(resolvedValue) && !isEmpty(resolvedValue)) {
        String finalResolvedValue = resolvedValue;
        fix = new LocalQuickFixBase(MavenDomBundle.message("refactoring.inline.property")) {
          @Override
          public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement psiElement = descriptor.getPsiElement();
            if (psiElement instanceof XmlTag) {
              ((XmlTag)psiElement).getValue().setText(finalResolvedValue);
            }
            else if (psiElement instanceof XmlText) {
              ((XmlText)psiElement).setValue(finalResolvedValue);
            }
          }
        };
      }
      XmlText[] textElements = domValue.getXmlTag().getValue().getTextElements();
      if (textElements.length > 0) {
        problems.add(manager.createProblemDescriptor(textElements[0], MavenDomBundle.message("inspection.property.in.parent.description"),
                                                     fix, ProblemHighlightType.GENERIC_ERROR, isOnTheFly));
      }
    }
  }

  @Nullable
  private static String resolveXmlElement(@Nullable XmlElement xmlElement) {
    if (xmlElement == null) return null;

    MavenPropertyPsiReference psiReference = findInstance(xmlElement.getReferences(), MavenPropertyPsiReference.class);
    if (psiReference == null) return null;

    PsiElement resolvedElement = psiReference.resolve();
    if (!(resolvedElement instanceof MavenPsiElementWrapper)) return null;

    PsiElement xmlTag = ((MavenPsiElementWrapper)resolvedElement).getWrappee();
    if (!(xmlTag instanceof XmlTag)) return null;

    return ((XmlTag)xmlTag).getValue().getTrimmedText();
  }
}
