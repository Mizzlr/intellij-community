package com.intellij.openapi.externalSystem.model.task;

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 */
public interface ExternalSystemTask {

  @NotNull
  ExternalSystemTaskId getId();

  @NotNull
  ExternalSystemTaskState getState();

  /**
   * @return    error occurred during the task execution (if any)
   */
  @Nullable
  Throwable getError();

  /**
   * Executes current task and updates given indicator's {@link ProgressIndicator#setText2(String) status} during that.
   * 
   * @param indicator  target progress indicator
   * @param listeners  callbacks to be notified on task execution update
   */
  void execute(@NotNull ProgressIndicator indicator, @NotNull ExternalSystemTaskNotificationListener... listeners);
  
  /**
   * Executes current task at the calling thread, i.e. the call to this method blocks.
   * 
   * @param listeners  callbacks to be notified about the task execution update
   */
  void execute(@NotNull ExternalSystemTaskNotificationListener... listeners);

  /**
   * Initiates cancel request of the current task and updates given indicator's {@link ProgressIndicator#setText2(String) status} during that.
   *
   * @param indicator  target progress indicator
   * @param listeners  callbacks to be notified on task execution update
   */
  boolean cancel(@NotNull ProgressIndicator indicator, @NotNull ExternalSystemTaskNotificationListener... listeners);

  /**
   * Initiates cancel request.
   *
   * @param listeners  callbacks to be notified about the task execution update
   */
  boolean cancel(@NotNull ExternalSystemTaskNotificationListener... listeners);

  /**
   * Forces current task to refresh {@link #getState() its state}.
   */
  void refreshState();
}
