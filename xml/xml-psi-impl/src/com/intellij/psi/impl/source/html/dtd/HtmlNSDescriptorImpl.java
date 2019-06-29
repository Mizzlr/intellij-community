// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.html.dtd;

import com.intellij.html.RelaxedHtmlNSDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SimpleFieldCache;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtilRt;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.TypeDescriptor;
import com.intellij.xml.impl.schema.XmlNSTypeDescriptorProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Maxim.Mossienko
 */
public class HtmlNSDescriptorImpl implements XmlNSDescriptor, DumbAware, XmlNSTypeDescriptorProvider {
  private final XmlNSDescriptor myDelegate;
  private final boolean myRelaxed;
  private final boolean myCaseSensitive;

  private static final SimpleFieldCache<Map<String, XmlElementDescriptor>, HtmlNSDescriptorImpl> myCachedDeclsCache = new SimpleFieldCache<Map<String, XmlElementDescriptor>, HtmlNSDescriptorImpl>() {
    @Override
    protected Map<String, XmlElementDescriptor> compute(final HtmlNSDescriptorImpl htmlNSDescriptor) {
      return htmlNSDescriptor.doBuildCachedMap();
    }

    @Override
    protected Map<String, XmlElementDescriptor> getValue(final HtmlNSDescriptorImpl htmlNSDescriptor) {
      return htmlNSDescriptor.myCachedDecls;
    }

    @Override
    protected void putValue(final Map<String, XmlElementDescriptor> map, final HtmlNSDescriptorImpl htmlNSDescriptor) {
      htmlNSDescriptor.myCachedDecls = map;
    }
  };

  private volatile Map<String, XmlElementDescriptor> myCachedDecls;

  public HtmlNSDescriptorImpl(XmlNSDescriptor _delegate) {
    this(_delegate, _delegate instanceof RelaxedHtmlNSDescriptor, false);
  }

  public HtmlNSDescriptorImpl(XmlNSDescriptor _delegate, boolean relaxed, boolean caseSensitive) {
    myDelegate = _delegate;
    myRelaxed = relaxed;
    myCaseSensitive = caseSensitive;
  }

  @Nullable
  public static XmlAttributeDescriptor getCommonAttributeDescriptor(@NotNull final String attributeName, @Nullable final XmlTag context) {
    final XmlElementDescriptor descriptor = guessTagForCommonAttributes(context);
    if (descriptor != null) {
      return descriptor.getAttributeDescriptor(attributeName, context);
    }
    return null;
  }

  @NotNull
  public static XmlAttributeDescriptor[] getCommonAttributeDescriptors(XmlTag context) {
    final XmlElementDescriptor descriptor = guessTagForCommonAttributes(context);
    if (descriptor != null) {
      return descriptor.getAttributesDescriptors(context);
    }
    return XmlAttributeDescriptor.EMPTY;
  }

  @Nullable
  public static XmlElementDescriptor guessTagForCommonAttributes(@Nullable final XmlTag context) {
    if (context == null) return null;
    final XmlNSDescriptor nsDescriptor = context.getNSDescriptor(context.getNamespace(), false);
    if (nsDescriptor instanceof HtmlNSDescriptorImpl) {
      XmlElementDescriptor descriptor = ((HtmlNSDescriptorImpl)nsDescriptor).getElementDescriptorByName("div");
      descriptor = descriptor == null ? ((HtmlNSDescriptorImpl)nsDescriptor).getElementDescriptorByName("span") : descriptor;
      return descriptor;
    }
    return null;
  }

  private Map<String,XmlElementDescriptor> buildDeclarationMap() {
    return myCachedDeclsCache.get(this);
  }

  // Read-only calculation
  private HashMap<String, XmlElementDescriptor> doBuildCachedMap() {
    HashMap<String, XmlElementDescriptor> decls = new HashMap<>();
    XmlElementDescriptor[] elements = myDelegate == null ? XmlElementDescriptor.EMPTY_ARRAY : myDelegate.getRootElementsDescriptors(null);

    for (XmlElementDescriptor element : elements) {
      decls.put(
        element.getName(),
        new HtmlElementDescriptorImpl(element, myRelaxed, myCaseSensitive)
      );
    }
    return decls;
  }

  @Override
  public XmlElementDescriptor getElementDescriptor(@NotNull XmlTag tag) {
    XmlElementDescriptor xmlElementDescriptor = getElementDescriptorByName(tag.getLocalName());
    if (xmlElementDescriptor == null && myRelaxed) {
      xmlElementDescriptor = myDelegate.getElementDescriptor(tag);
    }
    return xmlElementDescriptor;
  }

  private XmlElementDescriptor getElementDescriptorByName(String name) {
    if (!myCaseSensitive) name = StringUtil.toLowerCase(name);

    return buildDeclarationMap().get(name);
  }

  @Override
  @NotNull
  public XmlElementDescriptor[] getRootElementsDescriptors(@Nullable final XmlDocument document) {
    return myDelegate == null ? XmlElementDescriptor.EMPTY_ARRAY : myDelegate.getRootElementsDescriptors(document);
  }

  @Override
  @Nullable
  public XmlFile getDescriptorFile() {
    return myDelegate == null ? null : myDelegate.getDescriptorFile();
  }

  @Override
  public PsiElement getDeclaration() {
    return myDelegate == null ? null : myDelegate.getDeclaration();
  }

  @Override
  public String getName(PsiElement context) {
    return myDelegate == null ? "" : myDelegate.getName(context);
  }

  @Override
  public String getName() {
    return myDelegate == null ? "" : myDelegate.getName();
  }

  @Override
  public void init(PsiElement element) {
    myDelegate.init(element);
  }

  @NotNull
  @Override
  public Object[] getDependencies() {
    return myDelegate == null ? ArrayUtilRt.EMPTY_OBJECT_ARRAY : myDelegate.getDependencies();
  }

  @Override
  public TypeDescriptor getTypeDescriptor(String name, XmlTag context) {
    return myDelegate instanceof XmlNSTypeDescriptorProvider ?
           ((XmlNSTypeDescriptorProvider)myDelegate).getTypeDescriptor(name, context) : null;
  }

  @Override
  public TypeDescriptor getTypeDescriptor(XmlTag descriptorTag) {
    return myDelegate instanceof XmlNSTypeDescriptorProvider ?
           ((XmlNSTypeDescriptorProvider)myDelegate).getTypeDescriptor(descriptorTag) : null;
  }
}
