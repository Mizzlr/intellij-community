/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.data;

import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsLogDateFilter;
import com.intellij.vcs.log.VcsLogDetailsFilter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.Objects;

@ApiStatus.Internal
public class VcsLogDateFilterImpl implements VcsLogDateFilter, VcsLogDetailsFilter {

  @Nullable private final Date myAfter;
  @Nullable private final Date myBefore;

  /**
     * @deprecated use {@link com.intellij.vcs.log.visible.filters.VcsLogFilterObject#fromDates(Date, Date)}
     */
  @Deprecated
  public VcsLogDateFilterImpl(@Nullable Date after, @Nullable Date before) {
    myAfter = after;
    myBefore = before;
  }

  @Override
  public boolean matches(@NotNull VcsCommitMetadata details) {
    Date date = new Date(details.getCommitTime());  // Git itself also filters by commit time, not author time
    boolean matches = true;
    if (myAfter != null) {
      matches &= date.after(myAfter);
    }
    if (myBefore != null) {
      matches &= date.before(myBefore);
    }
    return matches;
  }

  @Override
  @Nullable
  public Date getAfter() {
    return myAfter;
  }

  @Override
  @Nullable
  public Date getBefore() {
    return myBefore;
  }

  @Override
  public String toString() {
    return (myAfter != null ? "after " + myAfter + (myBefore != null ? " " : "") : "") +
           (myBefore != null ? "before " + myBefore : "");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    VcsLogDateFilterImpl filter = (VcsLogDateFilterImpl)o;
    return Objects.equals(getAfter(), filter.getAfter()) &&
           Objects.equals(getBefore(), filter.getBefore());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getAfter(), getBefore());
  }
}
