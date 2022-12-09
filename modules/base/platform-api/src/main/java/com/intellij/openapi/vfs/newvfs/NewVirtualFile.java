/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import consulo.util.lang.DeprecatedMethodException;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collection;

/**
 * @author max
 */
public abstract class NewVirtualFile extends VirtualFile implements VirtualFileWithId {

  @Override
  public boolean isValid() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return exists();
  }

  @Override
  @Nonnull
  public byte[] contentsToByteArray() throws IOException {
    throw new IOException("Cannot get content of " + this);
  }

  @Override
  @Nonnull
  public abstract NewVirtualFileSystem getFileSystem();

  @Override
  public abstract NewVirtualFile getParent();

  @Override
  @Nullable
  public abstract NewVirtualFile getCanonicalFile();

  @Override
  @Nullable
  public abstract NewVirtualFile findChild(@Nonnull @NonNls final String name);

  @Nullable
  public abstract NewVirtualFile refreshAndFindChild(@Nonnull String name);

  @Nullable
  public abstract NewVirtualFile findChildIfCached(@Nonnull String name);


  public abstract void setTimeStamp(final long time) throws IOException;

  @Override
  @Nonnull
  public abstract CharSequence getNameSequence();

  @Override
  public abstract int getId();

  @Nullable
  @Deprecated
  //@ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  public NewVirtualFile findChildById(int id) {
    DeprecatedMethodException.report("deprecated method");
    return null;
  }

  @Override
  public void refresh(final boolean asynchronous, final boolean recursive, final Runnable postRunnable) {
    RefreshQueue.getInstance().refresh(asynchronous, recursive, postRunnable, this);
  }

  @Override
  public abstract void setWritable(boolean writable) throws IOException;

  public abstract void markDirty();

  public abstract void markDirtyRecursively();

  public abstract boolean isDirty();

  public abstract void markClean();

  @Override
  public void move(final Object requestor, @Nonnull final VirtualFile newParent) throws IOException {
    if (!exists()) {
      throw new IOException("File to move does not exist: " + getPath());
    }

    if (!newParent.exists()) {
      throw new IOException("Destination folder does not exist: " + newParent.getPath());
    }

    if (!newParent.isDirectory()) {
      throw new IOException("Destination is not a folder: " + newParent.getPath());
    }

    final VirtualFile child = newParent.findChild(getName());
    if (child != null) {
      throw new IOException("Destination already exists: " + newParent.getPath() + "/" + getName());
    }

    EncodingRegistry.doActionAndRestoreEncoding(this, () -> {
      getFileSystem().moveFile(requestor, this, newParent);
      return this;
    });
  }

  @Nonnull
  public abstract Collection<VirtualFile> getCachedChildren();

  /**
   * iterated children will NOT contain NullVirtualFile.INSTANCE
   */
  @Nonnull
  public abstract Iterable<VirtualFile> iterInDbChildren();

  @Nonnull
  @Deprecated
  //@ApiStatus.Experimental
  public Iterable<VirtualFile> iterInDbChildrenWithoutLoadingVfsFromOtherProjects() {
    return iterInDbChildren();
  }
}
