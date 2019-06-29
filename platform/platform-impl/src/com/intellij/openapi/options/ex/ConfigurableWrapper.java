// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.ex;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public class ConfigurableWrapper implements SearchableConfigurable, Weighted {
  private static final Logger LOG = Logger.getInstance(ConfigurableWrapper.class);

  @Nullable
  public static <T extends UnnamedConfigurable> T wrapConfigurable(@NotNull ConfigurableEP<T> ep) {
    return wrapConfigurable(ep, false);
  }

  @Nullable
  public static <T extends UnnamedConfigurable> T wrapConfigurable(@NotNull ConfigurableEP<T> ep, boolean settings) {
    if (!ep.canCreateConfigurable()) {
      return null;
    }
    if (settings || ep.displayName != null || ep.key != null || ep.parentId != null || ep.groupId != null) {
      //noinspection unchecked
      return (T)(!ep.dynamic && ep.children == null && ep.childrenEPName == null ? new ConfigurableWrapper(ep) : new CompositeWrapper(ep));
    }
    return createConfigurable(ep, LOG.isDebugEnabled());
  }

  private static <T extends UnnamedConfigurable> T createConfigurable(@NotNull ConfigurableEP<T> ep, boolean log) {
    long time = System.currentTimeMillis();
    T configurable = ep.createConfigurable();
    if (configurable instanceof Configurable) {
      ConfigurableCardPanel.warn((Configurable)configurable, "init", time);
      if (log) {
        LOG.debug("cannot create configurable wrapper for " + configurable.getClass());
      }
    }
    return configurable;
  }

  public static <T extends UnnamedConfigurable> List<T> createConfigurables(ExtensionPointName<? extends ConfigurableEP<T>> name) {
    return ContainerUtil.mapNotNull(name.getExtensions(), (NullableFunction<ConfigurableEP<T>, T>)ep -> wrapConfigurable(ep));
  }

  public static boolean hasOwnContent(UnnamedConfigurable configurable) {
    SearchableConfigurable.Parent parent = cast(SearchableConfigurable.Parent.class, configurable);
    return parent != null && parent.hasOwnContent();
  }

  public static boolean isNonDefaultProject(Configurable configurable) {
    //noinspection deprecation
    return configurable instanceof NonDefaultProjectConfigurable ||
           (configurable instanceof ConfigurableWrapper && ((ConfigurableWrapper)configurable).myEp.nonDefaultProject);
  }

  @Nullable
  public static <T> T cast(@NotNull Class<T> type, UnnamedConfigurable configurable) {
    if (configurable instanceof ConfigurableWrapper) {
      ConfigurableWrapper wrapper = (ConfigurableWrapper)configurable;
      if (wrapper.myConfigurable == null) {
        Class<?> configurableType = wrapper.getExtensionPoint().getConfigurableType();
        if (configurableType != null) {
          if (!type.isAssignableFrom(configurableType)) {
            return null; // do not create configurable that cannot be cast to the specified type
          }
        }
        else if (type == OptionalConfigurable.class) {
          return null; // do not create configurable from ConfigurableProvider which replaces OptionalConfigurable
        }
      }
      configurable = wrapper.getConfigurable();
    }
    return type.isInstance(configurable)
           ? type.cast(configurable)
           : null;
  }

  private final ConfigurableEP myEp;
  int myWeight; // see ConfigurableExtensionPointUtil.getConfigurableToReplace

  private ConfigurableWrapper(@NotNull ConfigurableEP ep) {
    myEp = ep;
    myWeight = ep.groupWeight;
  }

  @Nullable
  private UnnamedConfigurable myConfigurable;

  @Nullable
  public UnnamedConfigurable getRawConfigurable() {
    return myConfigurable;
  }

  public UnnamedConfigurable getConfigurable() {
    if (myConfigurable == null) {
      myConfigurable = createConfigurable(myEp, false);
      if (myConfigurable == null) {
        LOG.error("Can't instantiate configurable for " + myEp);
      }
      else if (LOG.isDebugEnabled()) {
        LOG.debug("created configurable for " + myConfigurable.getClass());
      }
    }
    return myConfigurable;
  }

  @Override
  public int getWeight() {
    return myWeight;
  }

  @Nls
  @Override
  public String getDisplayName() {
    if (myEp.displayName == null && myEp.key == null) {
      boolean loaded = myConfigurable != null;
      Configurable configurable = cast(Configurable.class, this);
      if (configurable != null) {
        String name = configurable.getDisplayName();
        if (!loaded && LOG.isDebugEnabled()) {
          LOG.debug("XML does not provide displayName for " + configurable.getClass());
        }
        return name;
      }
    }
    return myEp.getDisplayName();
  }

  public String getProviderClass() {
    return myEp.providerClass;
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    UnnamedConfigurable configurable = getConfigurable();
    return configurable instanceof Configurable ? ((Configurable)configurable).getHelpTopic() : null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return getConfigurable().createComponent();
  }

  @Override
  public boolean isModified() {
    return getConfigurable().isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    getConfigurable().apply();
  }

  @Override
  public void reset() {
    getConfigurable().reset();
  }

  @Override
  public void disposeUIResources() {
    UnnamedConfigurable configurable = myConfigurable;
    if (configurable != null) {
      configurable.disposeUIResources();
    }
  }

  @NotNull
  @Override
  public String getId() {
    if (myEp.id != null) {
      return myEp.id;
    }
    boolean loaded = myConfigurable != null;
    SearchableConfigurable configurable = cast(SearchableConfigurable.class, this);
    if (configurable != null) {
      String id = configurable.getId();
      if (!loaded) {
        LOG.debug("XML does not provide id for " + configurable.getClass());
      }
      return id;
    }
    // order from #ConfigurableEP(PicoContainer, Project)
    return myEp.providerClass != null
           ? myEp.providerClass
           : myEp.instanceClass != null
             ? myEp.instanceClass
             : myEp.implementationClass;
  }

  @NotNull
  public ConfigurableEP getExtensionPoint() {
    return myEp;
  }

  public String getParentId() {
    return myEp.parentId;
  }

  public ConfigurableWrapper addChild(Configurable configurable) {
    return new CompositeWrapper(myEp, configurable);
  }

  @Override
  public String toString() {
    return getDisplayName();
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    final UnnamedConfigurable configurable = getConfigurable();
    return configurable instanceof SearchableConfigurable ? ((SearchableConfigurable)configurable).enableSearch(option) : null;
  }

  @NotNull
  @Override
  public Class<?> getOriginalClass() {
    final UnnamedConfigurable configurable = getConfigurable();
    return configurable instanceof SearchableConfigurable
           ? ((SearchableConfigurable)configurable).getOriginalClass()
           : configurable.getClass();
  }

  private static class CompositeWrapper extends ConfigurableWrapper implements Configurable.Composite {

    private Configurable[] myKids;
    private Comparator<Configurable> myComparator;
    private boolean isInitialized;

    private CompositeWrapper(@NotNull ConfigurableEP ep, Configurable... kids) {
      super(ep);
      myKids = kids;
    }

    @NotNull
    @Override
    public Configurable[] getConfigurables() {
      if (!isInitialized) {
        long time = System.currentTimeMillis();
        ArrayList<Configurable> list = new ArrayList<>();
        if (super.myEp.dynamic) {
          Composite composite = cast(Composite.class, this);
          if (composite != null) {
            Collections.addAll(list, composite.getConfigurables());
          }
        }
        if (super.myEp.children != null) {
          for (ConfigurableEP ep : super.myEp.getChildren()) {
            if (ep.isAvailable()) {
              list.add((Configurable)wrapConfigurable(ep));
            }
          }
        }
        if (super.myEp.childrenEPName != null) {
          Object[] extensions = Extensions.getArea(super.myEp.getProject()).getExtensionPoint(super.myEp.childrenEPName).getExtensions();
          if (extensions.length > 0) {
            if (extensions[0] instanceof ConfigurableEP) {
              for (Object object : extensions) {
                list.add((Configurable)wrapConfigurable((ConfigurableEP)object));
              }
            }
            else if (!super.myEp.dynamic) {
              Composite composite = cast(Composite.class, this);
              if (composite != null) {
                Collections.addAll(list, composite.getConfigurables());
              }
            }
          }
        }
        Collections.addAll(list, myKids);
        // sort configurables is needed
        for (Configurable configurable : list) {
          if (configurable instanceof Weighted) {
            if (((Weighted)configurable).getWeight() != 0) {
              myComparator = COMPARATOR;
              Collections.sort(list, myComparator);
              break;
            }
          }
        }
        myKids = list.toArray(new Configurable[0]);
        isInitialized = true;
        ConfigurableCardPanel.warn(this, "children", time);
      }
      return myKids;
    }

    @Override
    public ConfigurableWrapper addChild(Configurable configurable) {
      if (myComparator != null) {
        int index = Arrays.binarySearch(myKids, configurable, myComparator);
        LOG.assertTrue(index < 0, "similar configurable is already exist");
        myKids = ArrayUtil.insert(myKids, -1 - index, configurable);
      }
      else {
        myKids = ArrayUtil.append(myKids, configurable);
      }
      return this;
    }
  }
}
