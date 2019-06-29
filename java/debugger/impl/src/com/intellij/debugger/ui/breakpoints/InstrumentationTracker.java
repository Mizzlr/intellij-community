// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.requests.Requestor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.*;
import com.sun.jdi.event.LocatableEvent;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * @author egor
 */
public class InstrumentationTracker {
  private static final Logger LOG = Logger.getInstance(InstrumentationTracker.class);

  @SuppressWarnings("FieldCanBeLocal") private final InstrumentationMethodBreakpoint myRedefineBreakpoint;
  @SuppressWarnings("FieldCanBeLocal") private final InstrumentationMethodBreakpoint myRetransformBreakpoint;
  @NotNull private final DebugProcessImpl myDebugProcess;

  private static final java.lang.reflect.Method ourNoticeRedefineClassMethod;

  static {
    java.lang.reflect.Method redefineMethod = null;
    try {
      redefineMethod = ReflectionUtil.getDeclaredMethod(Class.forName("com.sun.tools.jdi.ReferenceTypeImpl"), "noticeRedefineClass");
    }
    catch (ClassNotFoundException e) {
      LOG.warn(e);
    }
    ourNoticeRedefineClassMethod = redefineMethod;
  }

  public static void track(DebugProcessImpl debugProcess) {
    if (ourNoticeRedefineClassMethod != null) {
      new InstrumentationTracker(debugProcess);
    }
  }

  private InstrumentationTracker(DebugProcessImpl debugProcess) {
    myRedefineBreakpoint =
      new InstrumentationMethodBreakpoint(debugProcess.getProject(), "sun.instrument.InstrumentationImpl", "redefineClasses") {
        @Override
        public boolean processLocatableEvent(SuspendContextCommandImpl action, LocatableEvent event) {
          try {
            Value value = ContainerUtil.getFirstItem(DebuggerUtilsEx.getArgumentValues(event.thread().frame(0)));
            if (value instanceof ArrayReference) {
              ((ArrayReference)value).getValues().forEach(v -> {
                Value aClass = ((ObjectReference)v).getValue(((ReferenceType)v.type()).fieldByName("mClass"));
                noticeRedefineClass(((ClassObjectReference)aClass).reflectedType());
              });
            }
          }
          catch (IncompatibleThreadStateException e) {
            LOG.warn(e);
          }
          return false;
        }
      };
    myRetransformBreakpoint =
      new InstrumentationMethodBreakpoint(debugProcess.getProject(), "sun.instrument.InstrumentationImpl", "retransformClasses") {
        @Override
        public boolean processLocatableEvent(SuspendContextCommandImpl action, LocatableEvent event) {
          try {
            Value value = ContainerUtil.getFirstItem(DebuggerUtilsEx.getArgumentValues(event.thread().frame(0)));
            if (value instanceof ArrayReference) {
              ((ArrayReference)value).getValues().forEach(v -> noticeRedefineClass(((ClassObjectReference)v).reflectedType()));
            }
          }
          catch (IncompatibleThreadStateException e) {
            LOG.warn(e);
          }
          return false;
        }
      };
    myDebugProcess = debugProcess;

    myRedefineBreakpoint.createRequest(debugProcess);
    myRetransformBreakpoint.createRequest(debugProcess);
  }

  private void noticeRedefineClass(ReferenceType type) {
    if (!ourNoticeRedefineClassMethod.getDeclaringClass().isAssignableFrom(type.getClass())) {
      return;
    }
    List<Requestor> requestors = StreamEx.of(type.virtualMachine().eventRequestManager().breakpointRequests())
      .filter(r -> type.equals(r.location().declaringType()))
      .map(RequestManagerImpl::findRequestor)
      .toList();
    requestors.forEach(myDebugProcess.getRequestsManager()::deleteRequest);

    try {
      ourNoticeRedefineClassMethod.invoke(type);
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      LOG.error(e);
    }

    StreamEx.of(requestors).select(Breakpoint.class).forEach(b -> b.createRequest(myDebugProcess));
  }

  public static class InstrumentationMethodBreakpoint extends SyntheticLineBreakpoint {
    private final String myClassName;
    private final String myMethodName;

    public InstrumentationMethodBreakpoint(@NotNull Project project, String className, String methodName) {
      super(project);
      myClassName = className;
      myMethodName = methodName;
    }

    @Override
    public void createRequest(@NotNull DebugProcessImpl debugProcess) {
      createOrWaitPrepare(debugProcess, myClassName);
    }

    @Override
    protected void createRequestForPreparedClass(DebugProcessImpl debugProcess, ReferenceType classType) {
      for (Method method : classType.methodsByName(myMethodName)) {
        createRequestInMethod(debugProcess, method);
      }
    }

    protected void createRequestInMethod(DebugProcessImpl debugProcess, Method method) {
      try {
        Location location = ContainerUtil.getLastItem(method.allLineLocations());
        BreakpointWithHighlighter.createLocationBreakpointRequest(this, location, debugProcess);
      }
      catch (AbsentInformationException ignored) {
      }
    }

    @Override
    public String getDisplayName() {
      return "Instrumentation tracker: " + myMethodName;
    }
  }
}
