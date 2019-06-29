// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * Utility class for running an external process and capturing its standard output and error streams.
 *
 * @author yole
 */
public class CapturingProcessHandler extends OSProcessHandler {
  private final CapturingProcessRunner myProcessRunner;

  public CapturingProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    super(commandLine);
    myProcessRunner = new CapturingProcessRunner(this, processOutput -> createProcessAdapter(processOutput));
  }

  /** @deprecated Use {@link #CapturingProcessHandler(Process, Charset, String)} instead (to be removed in IDEA 17) */
  @Deprecated
  public CapturingProcessHandler(@NotNull Process process) {
    this(process, null, "");
  }

  /**
   * {@code commandLine} must not be not empty (for correct thread attribution in the stacktrace)
   */
  public CapturingProcessHandler(@NotNull Process process, @Nullable Charset charset, /*@NotNull*/ String commandLine) {
    super(process, commandLine, charset);
    myProcessRunner = new CapturingProcessRunner(this, processOutput -> createProcessAdapter(processOutput));
  }

  protected CapturingProcessAdapter createProcessAdapter(ProcessOutput processOutput) {
    return new CapturingProcessAdapter(processOutput);
  }

  @Override
  public Charset getCharset() {
    return myCharset != null ? myCharset : super.getCharset();
  }

  @NotNull
  public ProcessOutput runProcess() {
    return myProcessRunner.runProcess();
  }

  /**
   * Starts process with specified timeout
   *
   * @param timeoutInMilliseconds non-positive means infinity
   */
  public ProcessOutput runProcess(int timeoutInMilliseconds) {
    return myProcessRunner.runProcess(timeoutInMilliseconds);
  }

  /**
   * Starts process with specified timeout
   *
   * @param timeoutInMilliseconds non-positive means infinity
   * @param destroyOnTimeout whether to kill the process after timeout passes
   */
  public ProcessOutput runProcess(int timeoutInMilliseconds, boolean destroyOnTimeout) {
    return myProcessRunner.runProcess(timeoutInMilliseconds, destroyOnTimeout);
  }

  @NotNull
  public ProcessOutput runProcessWithProgressIndicator(@NotNull ProgressIndicator indicator) {
    return myProcessRunner.runProcess(indicator);
  }

  @NotNull
  public ProcessOutput runProcessWithProgressIndicator(@NotNull ProgressIndicator indicator, int timeoutInMilliseconds) {
    return myProcessRunner.runProcess(indicator, timeoutInMilliseconds);
  }

  @NotNull
  public ProcessOutput runProcessWithProgressIndicator(@NotNull ProgressIndicator indicator, int timeoutInMilliseconds, boolean destroyOnTimeout) {
    return myProcessRunner.runProcess(indicator, timeoutInMilliseconds, destroyOnTimeout);
  }
}