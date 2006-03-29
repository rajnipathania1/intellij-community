/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.javaee.model.ElementPresentation;
import com.intellij.openapi.module.Module;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.reflect.DomGenericInfo;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.HighlightSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.List;

/**
 * @author peter
 */
public interface DomElement {

  @Nullable
  XmlTag getXmlTag();

  DomFileElement<?> getRoot();

  DomElement getParent();

  XmlTag ensureTagExists();

  void undefine();

  boolean isValid();

  DomGenericInfo getGenericInfo();

  String getXmlElementName();

  void accept(final DomElementVisitor visitor);

  void acceptChildren(DomElementVisitor visitor);

  DomManager getManager();

  Type getDomElementType();

  DomNameStrategy getNameStrategy();

  @NotNull
  ElementPresentation getPresentation();

  GlobalSearchScope getResolveScope();

  <T extends DomElement> T getParentOfType(Class<T> requiredClass, boolean strict);

  Module getModule();

  void copyFrom(DomElement other);

  DomElement createMockCopy(final boolean physical);
}
