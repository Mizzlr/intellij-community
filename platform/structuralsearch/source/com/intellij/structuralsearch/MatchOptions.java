// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.codeInsight.template.impl.TemplateImplUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.structuralsearch.impl.matcher.compiler.StringToConstraintsTransformer;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MatchOptions implements JDOMExternalizable {

  private final Map<String, MatchVariableConstraint> variableConstraints;
  private boolean looseMatching;
  private boolean recursiveSearch;
  private boolean caseSensitiveMatch;
  private LanguageFileType myFileType;
  private Language myDialect;
  private SearchScope scope;
  private Scopes.Type scopeType;
  private String scopeDescriptor;
  @NotNull
  private String pattern;

  private String myPatternContextId;

  @NonNls private static final String TEXT_ATTRIBUTE_NAME = "text";
  @NonNls private static final String LOOSE_MATCHING_ATTRIBUTE_NAME = "loose";
  @NonNls private static final String RECURSIVE_ATTRIBUTE_NAME = "recursive";
  @NonNls private static final String CASESENSITIVE_ATTRIBUTE_NAME = "caseInsensitive";
  @NonNls private static final String CONSTRAINT_TAG_NAME = "constraint";
  @NonNls private static final String FILE_TYPE_ATTR_NAME = "type";
  @NonNls private static final String DIALECT_ATTR_NAME = "dialect";
  @NonNls private static final String PATTERN_CONTEXT_ATTR_NAME = "pattern_context";
  @NonNls private static final String SCOPE_TYPE = "scope_type";
  @NonNls private static final String SCOPE_DESCRIPTOR = "scope_descriptor";

  @NonNls public static final String INSTANCE_MODIFIER_NAME = "Instance";
  @NonNls public static final String MODIFIER_ANNOTATION_NAME = "Modifier";

  public MatchOptions() {
    variableConstraints = new LinkedHashMap<>();
    looseMatching = true;
    pattern = "";
  }

  MatchOptions(MatchOptions options) {
    variableConstraints = new LinkedHashMap<>(options.variableConstraints.size());
    options.variableConstraints.forEach((key, value) -> variableConstraints.put(key, value.copy())); // deep copy
    looseMatching = options.looseMatching;
    recursiveSearch = options.recursiveSearch;
    caseSensitiveMatch = options.caseSensitiveMatch;
    myFileType = options.myFileType;
    myDialect = options.myDialect;
    scope = options.scope;
    scopeType = options.scopeType;
    scopeDescriptor = options.scopeDescriptor;
    pattern = options.pattern;
    myPatternContextId = options.myPatternContextId;
  }

  public MatchOptions copy() {
    return new MatchOptions(this);
  }

  public void initScope(Project project) {
    if (scope == null && scopeType != null && scopeDescriptor != null) {
      scope = Scopes.createScope(project, scopeDescriptor, scopeType);
    }
  }

  public void addVariableConstraint(@NotNull MatchVariableConstraint constraint) {
    variableConstraints.put(constraint.getName(), constraint);
  }

  public Set<String> getUsedVariableNames() {
    final Set<String> set = TemplateImplUtil.parseVariableNames(pattern);
    set.add(Configuration.CONTEXT_VAR_NAME);
    return set;
  }

  public void removeUnusedVariables() {
    variableConstraints.keySet().removeIf(key -> !getUsedVariableNames().contains(key));
  }

  public MatchVariableConstraint getVariableConstraint(String name) {
    return variableConstraints.get(name);
  }

  public Set<String> getVariableConstraintNames() {
    return Collections.unmodifiableSet(variableConstraints.keySet());
  }

  public void setCaseSensitiveMatch(boolean caseSensitiveMatch) {
    this.caseSensitiveMatch = caseSensitiveMatch;
  }

  public boolean isCaseSensitiveMatch() {
    return caseSensitiveMatch;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "match options:\n" +
           "pattern:\n" + pattern +
           "\nscope:\n" + ((scope != null) ? scope.toString() : "undefined scope") +
           "\nrecursive: " + recursiveSearch +
           "\ncase sensitive: " + caseSensitiveMatch +
           "\nloose: " + looseMatching;
  }

  public boolean isRecursiveSearch() {
    return recursiveSearch;
  }

  public void setRecursiveSearch(boolean recursiveSearch) {
    this.recursiveSearch = recursiveSearch;
  }

  public boolean isLooseMatching() {
    return looseMatching;
  }

  public void setLooseMatching(boolean looseMatching) {
    this.looseMatching = looseMatching;
  }

  public void setSearchPattern(@NotNull String text) {
    pattern = text;
  }

  @NotNull
  public String getSearchPattern() {
    return pattern;
  }

  public void fillSearchCriteria(String criteria) {
    if (!variableConstraints.isEmpty()) variableConstraints.clear();
    StringToConstraintsTransformer.transformCriteria(criteria, this);
  }

  @Nullable
  public SearchScope getScope() {
    return scope;
  }

  public void setScope(SearchScope scope) {
    this.scope = scope;
  }

  @Override
  public void writeExternal(Element element) {
    element.setAttribute(TEXT_ATTRIBUTE_NAME, pattern);
    if (!looseMatching) {
      element.setAttribute(LOOSE_MATCHING_ATTRIBUTE_NAME, String.valueOf(false));
    }
    element.setAttribute(RECURSIVE_ATTRIBUTE_NAME,String.valueOf(recursiveSearch));
    element.setAttribute(CASESENSITIVE_ATTRIBUTE_NAME,String.valueOf(caseSensitiveMatch));

    if (myFileType != null) {
      element.setAttribute(FILE_TYPE_ATTR_NAME, myFileType.getName());
    }
    if (myDialect != null && (myFileType == null || myFileType.getLanguage() != myDialect)) {
      element.setAttribute(DIALECT_ATTR_NAME, myDialect.getID());
    }
    if (myPatternContextId != null) {
      element.setAttribute(PATTERN_CONTEXT_ATTR_NAME, myPatternContextId);
    }

    if (scope != null) {
      element.setAttribute(SCOPE_TYPE, Scopes.getType(scope).toString()).setAttribute(SCOPE_DESCRIPTOR, Scopes.getDescriptor(scope));
    }

    final Set<String> constraintNames = getUsedVariableNames();
    for (final MatchVariableConstraint matchVariableConstraint : variableConstraints.values()) {
      if (matchVariableConstraint.isArtificial() || !constraintNames.contains(matchVariableConstraint.getName())) {
        continue;
      }
      final Element infoElement = new Element(CONSTRAINT_TAG_NAME);
      element.addContent(infoElement);
      matchVariableConstraint.writeExternal(infoElement);
    }
  }

  @Override
  public void readExternal(Element element) {
    pattern = StringUtil.notNullize(element.getAttributeValue(TEXT_ATTRIBUTE_NAME));

    looseMatching = MatchVariableConstraint.getBooleanValue(element, LOOSE_MATCHING_ATTRIBUTE_NAME, true);

    recursiveSearch = MatchVariableConstraint.getBooleanValue(element, RECURSIVE_ATTRIBUTE_NAME, false);
    caseSensitiveMatch = MatchVariableConstraint.getBooleanValue(element, CASESENSITIVE_ATTRIBUTE_NAME, false);

    myFileType = getFileTypeByName(element.getAttributeValue(FILE_TYPE_ATTR_NAME));
    myDialect = Language.findLanguageByID(element.getAttributeValue(DIALECT_ATTR_NAME));
    myPatternContextId = element.getAttributeValue(PATTERN_CONTEXT_ATTR_NAME);

    final String value = element.getAttributeValue(SCOPE_TYPE);
    scopeType = (value == null) ? null : Scopes.Type.valueOf(value);
    scopeDescriptor = element.getAttributeValue(SCOPE_DESCRIPTOR);

    for (final Element element1 : element.getChildren(CONSTRAINT_TAG_NAME)) {
      final MatchVariableConstraint constraint = new MatchVariableConstraint();
      constraint.readExternal(element1);
      addVariableConstraint(constraint);
    }
  }

  private static LanguageFileType getFileTypeByName(String value) {
    if (value != null) {
      for (LanguageFileType type : StructuralSearchUtil.getSuitableFileTypes()) {
        if (value.equals(type.getName())) {
          return type;
        }
      }
    }

    return StructuralSearchUtil.getDefaultFileType();
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MatchOptions)) return false;

    final MatchOptions matchOptions = (MatchOptions)o;

    if (caseSensitiveMatch != matchOptions.caseSensitiveMatch) return false;
    if (looseMatching != matchOptions.looseMatching) return false;
    if (recursiveSearch != matchOptions.recursiveSearch) return false;
    if (!Objects.equals(scope, matchOptions.scope)) return false;
    if (!pattern.equals(matchOptions.pattern)) return false;
    if (!variableConstraints.equals(matchOptions.variableConstraints)) return false;
    if (myFileType != matchOptions.myFileType) return false;
    if (!Objects.equals(myDialect, matchOptions.myDialect)) return false;
    if (!Objects.equals(myPatternContextId, matchOptions.myPatternContextId)) return false;

    return true;
  }

  public int hashCode() {
    int result = (looseMatching ? 1 : 0);
    result = 29 * result + (recursiveSearch ? 1 : 0);
    result = 29 * result + (caseSensitiveMatch ? 1 : 0);
    result = 29 * result + pattern.hashCode();
    result = 29 * result + variableConstraints.hashCode();
    if (scope != null) //noinspection deprecation
      result = 29 * result + scope.hashCode();
    if (myFileType != null) result = 29 * result + myFileType.hashCode();
    if (myDialect != null) result = 29 * result + myDialect.hashCode();
    if (myPatternContextId != null) result = 29 * result + myPatternContextId.hashCode();
    return result;
  }

  public void setFileType(LanguageFileType fileType) {
    myFileType = fileType;
  }

  @NotNull
  public LanguageFileType getFileType() {
    if (myFileType == null) {
      myFileType = StructuralSearchUtil.getDefaultFileType();
    }
    return myFileType;
  }

  @NotNull
  public Language getDialect() {
    if (myDialect == null) {
      return getFileType().getLanguage();
    }
    return myDialect;
  }

  public void setDialect(Language dialect) {
    myDialect = dialect;
  }

  public PatternContext getPatternContext() {
    if (myPatternContextId == null) return null;
    return StructuralSearchUtil.findPatternContextByID(myPatternContextId, getDialect());
  }

  public void setPatternContext(PatternContext patternContext) {
    myPatternContextId = (patternContext == null) ? null : patternContext.getId();
  }
}
