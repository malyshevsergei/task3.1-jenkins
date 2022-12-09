// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.DiskQueryRelay;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.win32.Win32LocalFileSystem;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.intellij.lang.annotations.MagicConstant;
import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;

import static com.intellij.util.BitUtil.isSet;

public abstract class PersistentFS extends ManagingFS {
  static final int CHILDREN_CACHED_FLAG = 0x01;
  static final int IS_DIRECTORY_FLAG = 0x02;
  static final int IS_READ_ONLY = 0x04;
  static final int MUST_RELOAD_CONTENT = 0x08;
  static final int IS_SYMLINK = 0x10;
  static final int IS_SPECIAL = 0x20;
  static final int IS_HIDDEN = 0x40;

  @MagicConstant(flags = {CHILDREN_CACHED_FLAG, IS_DIRECTORY_FLAG, IS_READ_ONLY, MUST_RELOAD_CONTENT, IS_SYMLINK, IS_SPECIAL, IS_HIDDEN})
  public @interface Attributes {
  }

  static final int ALL_VALID_FLAGS = CHILDREN_CACHED_FLAG | IS_DIRECTORY_FLAG | IS_READ_ONLY | MUST_RELOAD_CONTENT | IS_SYMLINK | IS_SPECIAL | IS_HIDDEN;

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static PersistentFS getInstance() {
    return (PersistentFS)ManagingFS.getInstance();
  }

  @Nonnull
  protected <P, R> Function<P, R> accessDiskWithCheckCanceled(Function<? super P, ? extends R> function) {
    return new DiskQueryRelay<>(function)::accessDiskWithCheckCanceled;
  }

  public abstract void clearIdCache();

  @Nonnull
  public abstract String[] listPersisted(@Nonnull VirtualFile parent);

  @Nonnull
  public abstract FSRecords.NameId[] listAll(@Nonnull VirtualFile parent);

  public abstract int getId(@Nonnull VirtualFile parent, @Nonnull String childName, @Nonnull NewVirtualFileSystem delegate);

  public abstract String getName(int id);

  public abstract long getLastRecordedLength(@Nonnull VirtualFile file);

  public abstract boolean isHidden(@Nonnull VirtualFile file);

  @Attributes
  public abstract int getFileAttributes(int id);

  public static boolean isDirectory(@Attributes int attributes) {
    return isSet(attributes, IS_DIRECTORY_FLAG);
  }

  public static boolean isWritable(@Attributes int attributes) {
    return !isSet(attributes, IS_READ_ONLY);
  }

  public static boolean isSymLink(@Attributes int attributes) {
    return isSet(attributes, IS_SYMLINK);
  }

  public static boolean isSpecialFile(@Attributes int attributes) {
    return isSet(attributes, IS_SPECIAL);
  }

  public static boolean isHidden(@Attributes int attributes) {
    return isSet(attributes, IS_HIDDEN);
  }

  @Nullable
  public abstract NewVirtualFile findFileByIdIfCached(int id);

  public abstract int storeUnlinkedContent(@Nonnull byte[] bytes);

  @Nonnull
  public abstract byte[] contentsToByteArray(int contentId) throws IOException;

  @Nonnull
  public abstract byte[] contentsToByteArray(@Nonnull VirtualFile file, boolean cacheContent) throws IOException;

  public abstract int acquireContent(@Nonnull VirtualFile file);

  public abstract void releaseContent(int contentId);

  public abstract int getCurrentContentId(@Nonnull VirtualFile file);

  public abstract void processEvents(@Nonnull List<? extends VFileEvent> events);

  @Nonnull
  public static NewVirtualFileSystem replaceWithNativeFS(@Nonnull final NewVirtualFileSystem fs) {
    if (SystemInfo.isWindows && !(fs instanceof Win32LocalFileSystem) && fs.getProtocol().equals(LocalFileSystem.PROTOCOL) && Win32LocalFileSystem.isAvailable()) {
      return Win32LocalFileSystem.getWin32Instance();
    }
    return fs;
  }

  // true if FS persisted at least one child or it has never been queried for children
  public abstract boolean mayHaveChildren(int id);

  @Nonnull
  public static FileAttributes toFileAttributes(int attributes) {
    final boolean isDirectory = isSet(attributes, IS_DIRECTORY_FLAG);
    final boolean isSpecial = isSet(attributes, IS_SPECIAL);
    final boolean isSymlink = isSet(attributes, IS_SYMLINK);
    final boolean isHidden = isSet(attributes, IS_HIDDEN);
    final boolean isWritable = !isSet(attributes, IS_READ_ONLY);
    return new FileAttributes(isDirectory, isSpecial, isSymlink, isHidden, -1, -1, isWritable);
  }
}
