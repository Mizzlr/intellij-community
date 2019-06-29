// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ConstantFunction;
import com.intellij.util.NotNullFunction;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentInstanceMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.highlighting.DomElementsAnnotator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Use {@code com.intellij.dom.fileMetaData} to register.
 *
 * @author peter
 * @see MergingFileDescription
 */
public class DomFileDescription<T> {

  /**
   * @deprecated use {@code com.intellij.dom.fileMetaData} extension instead
   */
  @Deprecated
  public static final ExtensionPointName<DomFileDescription> EP_NAME = ExtensionPointName.create("com.intellij.dom.fileDescription");

  private final Map<Class<? extends ScopeProvider>, ScopeProvider> myScopeProviders = ConcurrentInstanceMap.create();
  protected final Class<T> myRootElementClass;
  protected final String myRootTagName;
  private final String[] myAllPossibleRootTagNamespaces;
  private volatile boolean myInitialized;
  private final Map<Class<? extends DomElement>,Class<? extends DomElement>> myImplementations = new HashMap<>();
  private final TypeChooserManager myTypeChooserManager = new TypeChooserManager();
  private final List<DomReferenceInjector> myInjectors = new SmartList<>();
  private final Map<String, NotNullFunction<XmlTag, List<String>>> myNamespacePolicies =
    ContainerUtil.newConcurrentMap();

  public DomFileDescription(final Class<T> rootElementClass, @NonNls final String rootTagName, @NonNls @NotNull String... allPossibleRootTagNamespaces) {
    myRootElementClass = rootElementClass;
    myRootTagName = rootTagName;
    myAllPossibleRootTagNamespaces = allPossibleRootTagNamespaces.length == 0 ? ArrayUtilRt.EMPTY_STRING_ARRAY
                                                                              : allPossibleRootTagNamespaces;
  }

  @NotNull
  public String[] getAllPossibleRootTagNamespaces() {
    return myAllPossibleRootTagNamespaces;
  }

  /**
   * Register an implementation class to provide additional functionality for DOM elements.
   *
   * @param domElementClass     interface class.
   * @param implementationClass abstract implementation class.
   * @see #initializeFileDescription()
   * @deprecated use dom.implementation extension point instead
   */
  @Deprecated
  public final <Dom extends DomElement> void registerImplementation(Class<Dom> domElementClass, Class<? extends Dom> implementationClass) {
    myImplementations.put(domElementClass, implementationClass);
  }

  /**
   * @param namespaceKey namespace identifier
   * @see Namespace
   * @param policy function that takes XML file root tag and returns (maybe empty) list of possible namespace URLs or DTD public ids. This
   * function shouldn't use DOM since it may be not initialized for the file at the moment
   * @deprecated use {@link #registerNamespacePolicy(String, String...)} or override {@link #getAllowedNamespaces(String, XmlFile)} instead
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  protected final void registerNamespacePolicy(String namespaceKey, NotNullFunction<XmlTag,List<String>> policy) {
    myNamespacePolicies.put(namespaceKey, policy);
  }

  /**
   * @param namespaceKey namespace identifier
   * @see Namespace
   * @param namespaces XML namespace or DTD public or system id value for the given namespaceKey
   */
  public final void registerNamespacePolicy(String namespaceKey, final String... namespaces) {
    registerNamespacePolicy(namespaceKey, new ConstantFunction<>(Arrays.asList(namespaces)));
  }

  /**
   * Consider using {@link DomService#getXmlFileHeader(XmlFile)} when implementing this.
   */
  @NotNull
  public List<String> getAllowedNamespaces(@NotNull String namespaceKey, @NotNull XmlFile file) {
    final NotNullFunction<XmlTag, List<String>> function = myNamespacePolicies.get(namespaceKey);
    if (function instanceof ConstantFunction) {
      return function.fun(null);
    }

    if (function != null) {
      final XmlDocument document = file.getDocument();
      if (document != null) {
        final XmlTag tag = document.getRootTag();
        if (tag != null) {
          return function.fun(tag);
        }
      }
    } else {
      return Collections.singletonList(namespaceKey);
    }
    return Collections.emptyList();
  }

  /**
   * @return some version. Override and change (e.g. {@code super.getVersion()+1}) when after some changes some files stopped being
   * described by this description or vice versa, so that the
   * {@link DomService#getDomFileCandidates(Class, com.intellij.openapi.project.Project, com.intellij.psi.search.GlobalSearchScope)}
   * index is rebuilt correctly.
   * @deprecated use "domVersion" attribute of {@code com.intellij.dom.fileMetaData} extension instead
   */
  @Deprecated
  public int getVersion() {
    return myRootTagName.hashCode();
  }

  protected final void registerTypeChooser(final Type aClass, final TypeChooser typeChooser) {
    myTypeChooserManager.registerTypeChooser(aClass, typeChooser);
  }

  public final TypeChooserManager getTypeChooserManager() {
    return myTypeChooserManager;
  }

  protected final void registerReferenceInjector(DomReferenceInjector injector) {
    myInjectors.add(injector);
  }

  public List<DomReferenceInjector> getReferenceInjectors() {
    return myInjectors;
  }

  public boolean isAutomaticHighlightingEnabled() {
    return true;
  }

  @Nullable
  public Icon getFileIcon(@Iconable.IconFlags int flags) {
    return null;
  }

  /**
   * The right place to call
   * <ul>
   * <li>{@link #registerNamespacePolicy(String, String...)}</li>
   * <li>{@link #registerTypeChooser(Type, TypeChooser)}</li>
   * <li>{@link #registerReferenceInjector(DomReferenceInjector)}</li>
   * </ul>
   */
  protected void initializeFileDescription() {}

  /**
   * Create custom DOM annotator that will be used when error-highlighting DOM. The results will be collected to
   * {@link com.intellij.util.xml.highlighting.DomElementsProblemsHolder}. The highlighting will be most probably done in an
   * {@link com.intellij.util.xml.highlighting.BasicDomElementsInspection} instance.
   * @return Annotator or null
   */
  @Nullable
  public DomElementsAnnotator createAnnotator() {
    return null;
  }

  public final Map<Class<? extends DomElement>,Class<? extends DomElement>> getImplementations() {
    if (!myInitialized) {
      initializeFileDescription();
      myInitialized = true;
    }
    return myImplementations;
  }

  @NotNull
  public final Class<T> getRootElementClass() {
    return myRootElementClass;
  }

  public final String getRootTagName() {
    return myRootTagName;
  }

  public boolean isMyFile(@NotNull XmlFile file, @Nullable final Module module) {
    final Namespace namespace = DomReflectionUtil.findAnnotationDFS(myRootElementClass, Namespace.class);
    if (namespace != null) {
      final String key = namespace.value();
      Set<String> allNs = new HashSet<>(getAllowedNamespaces(key, file));
      if (allNs.isEmpty()) {
        return false;
      }

      XmlFileHeader header = DomService.getInstance().getXmlFileHeader(file);
      return allNs.contains(header.getPublicId()) || allNs.contains(header.getSystemId()) || allNs.contains(header.getRootTagNamespace());
    }

    return true;
  }

  public boolean acceptsOtherRootTagNames() {
    return false;
  }

  /**
   * Get dependency items (the same, as in {@link com.intellij.psi.util.CachedValue}) for file. On any dependency item change, the
   * {@link #isMyFile(XmlFile, Module)} method will be invoked once more to ensure that the file description still
   * accepts this file.
   *
   * @param file XML file to get dependencies of
   * @return dependency item set
   */
  @NotNull
  public Set<?> getDependencyItems(XmlFile file) {
    return Collections.emptySet();
  }

  /**
   * @param reference DOM reference
   * @return element, whose all children will be searched for declaration
   */
  @NotNull
  public DomElement getResolveScope(GenericDomValue<?> reference) {
    final DomElement annotation = getScopeFromAnnotation(reference);
    if (annotation != null) return annotation;

    return DomUtil.getRoot(reference);
  }

  /**
   * @param element DOM element
   * @return element, whose direct children names will be compared by name. Basically it's parameter element's parent (see {@link ParentScopeProvider}).
   */
  @NotNull
  public DomElement getIdentityScope(DomElement element) {
    final DomElement annotation = getScopeFromAnnotation(element);
    if (annotation != null) return annotation;

    return element.getParent();
  }

  @Nullable
  protected final DomElement getScopeFromAnnotation(final DomElement element) {
    final Scope scope = element.getAnnotation(Scope.class);
    if (scope != null) {
      return myScopeProviders.get(scope.value()).getScope(element);
    }
    return null;
  }

  /**
   * @see Stubbed
   * @deprecated define "stubVersion" of {@code com.intellij.dom.fileMetaData} extension instead
   */
  @Deprecated
  public boolean hasStubs() {
    return false;
  }

  /**
   * @see Stubbed
   * @deprecated define "stubVersion" of {@code com.intellij.dom.fileMetaData} extension instead
   */
  @Deprecated
  public int getStubVersion() {
    throw new UnsupportedOperationException("define \"stubVersion\" of \"com.intellij.dom.fileMetaData\" extension instead");
  }

  @Override
  public String toString() {
    return getRootElementClass() + " <" + getRootTagName() + "> \n" + StringUtil.join(getAllPossibleRootTagNamespaces());
  }
}
