/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.mock;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.testFramework.LightVirtualFile;
import javax.annotation.Nonnull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;

public class MockVirtualFileSystem extends DeprecatedVirtualFileSystem {
  private final MyVirtualFile myRoot = new MyVirtualFile("", null);
  public static final String PROTOCOL = "mock";

  @Override
  public VirtualFile findFileByPath(@Nonnull String path) {
    path = path.replace(File.separatorChar, '/');
    path = path.replace('/', ':');
    if (StringUtil.startsWithChar(path, ':')) path = path.substring(1);
    String[] components = path.split(":");
    MyVirtualFile file = myRoot;
    for (String component : components) {
      file = file.getOrCreate(component);
    }
    return file;
  }

  @Override
  @Nonnull
  public String getProtocol() {
    return PROTOCOL;
  }

  @Override
  public void refresh(boolean asynchronous) {
  }

  @Override
  public void deleteFile(Object requestor, @Nonnull VirtualFile vFile) throws IOException {
  }

  @Override
  public void moveFile(Object requestor, @Nonnull VirtualFile vFile, @Nonnull VirtualFile newParent) throws IOException {
  }

  @Override
  public VirtualFile copyFile(Object requestor, @Nonnull VirtualFile vFile, @Nonnull VirtualFile newParent, @Nonnull final String copyName) throws IOException {
    return null;
  }

  @Override
  public void renameFile(Object requestor, @Nonnull VirtualFile vFile, @Nonnull String newName) throws IOException {
  }

  @Override
  public VirtualFile createChildFile(Object requestor, @Nonnull VirtualFile vDir, @Nonnull String fileName) throws IOException {
    return null;
  }

  @Override
  @Nonnull
  public VirtualFile createChildDirectory(Object requestor, @Nonnull VirtualFile vDir, @Nonnull String dirName) throws IOException {
    throw new IOException();
  }

  @Override
  public VirtualFile refreshAndFindFileByPath(@Nonnull String path) {
    return findFileByPath(path);
  }

  public class MyVirtualFile extends LightVirtualFile {
    private final HashMap<String, MyVirtualFile> myChildren = new HashMap<String, MyVirtualFile>();
    private final MyVirtualFile myParent;

    public MyVirtualFile(String name, MyVirtualFile parent) {
      super(name);
      myParent = parent;
    }

    @Override
    @Nonnull
    public VirtualFileSystem getFileSystem() {
      return MockVirtualFileSystem.this;
    }

    public MyVirtualFile getOrCreate(String name) {
      MyVirtualFile file = myChildren.get(name);
      if (file == null) {
        file = new MyVirtualFile(name, this);
        myChildren.put(name, file);
      }
      return file;
    }

    @Override
    public boolean isDirectory() {
      return myChildren.size() != 0;
    }

    @Override
    public String getPath() {
      final MockVirtualFileSystem.MyVirtualFile parent = getParent();
      return parent == null ? getName() : parent.getPath() + "/" + getName();
    }

    @Override
    public MyVirtualFile getParent() {
      return myParent;
    }

    @Override
    public VirtualFile[] getChildren() {
      Collection<MyVirtualFile> children = myChildren.values();
      return children.toArray(new MyVirtualFile[children.size()]);
    }
  }
}
