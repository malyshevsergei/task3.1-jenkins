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
package com.intellij.compiler;

import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManagerImpl;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.annotation.Nonnull;
import java.nio.charset.Charset;
import java.util.*;

/**
 * @author nik
 */
@Singleton
public class CompilerEncodingServiceImpl extends CompilerEncodingService {
  @Nonnull
  private final Project myProject;
  private final CachedValue<Map<Module, Set<Charset>>> myModuleFileEncodings;

  @Inject
  public CompilerEncodingServiceImpl(@Nonnull Project project) {
    myProject = project;
    myModuleFileEncodings = CachedValuesManager.getManager(project).createCachedValue(new CachedValueProvider<Map<Module, Set<Charset>>>() {
      @Override
      public Result<Map<Module, Set<Charset>>> compute() {
        Map<Module, Set<Charset>> result = computeModuleCharsetMap();
        return Result.create(result, ProjectRootManager.getInstance(myProject),
                             ((EncodingProjectManagerImpl)EncodingProjectManager.getInstance(myProject)).getModificationTracker());
      }
    }, false);
  }

  private Map<Module, Set<Charset>> computeModuleCharsetMap() {
    final Map<Module, Set<Charset>> map = new HashMap<Module, Set<Charset>>();
    final Map<? extends VirtualFile, ? extends Charset> mappings = ((EncodingProjectManagerImpl) EncodingProjectManager.getInstance(myProject)).getAllMappings();
    ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
    final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
    for (Map.Entry<? extends VirtualFile, ? extends Charset> entry : mappings.entrySet()) {
      final VirtualFile file = entry.getKey();
      final Charset charset = entry.getValue();
      if (file == null || charset == null || (!file.isDirectory() && !compilerManager.isCompilableFileType(file.getFileType()))
          || !index.isInSourceContent(file)) continue;

      final Module module = index.getModuleForFile(file);
      if (module == null) continue;

      Set<Charset> set = map.get(module);
      if (set == null) {
        set = new LinkedHashSet<Charset>();
        map.put(module, set);

        final VirtualFile sourceRoot = index.getSourceRootForFile(file);
        VirtualFile current = file.getParent();
        Charset parentCharset = null;
        while (current != null) {
          final Charset currentCharset = mappings.get(current);
          if (currentCharset != null) {
            parentCharset = currentCharset;
          }
          if (current.equals(sourceRoot)) {
            break;
          }
          current = current.getParent();
        }
        if (parentCharset != null) {
          set.add(parentCharset);
        }
      }
      set.add(charset);
    }
    //todo[nik,jeka] perhaps we should take into account encodings of source roots only not individual files
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      for (VirtualFile file : ModuleRootManager.getInstance(module).getSourceRoots(true)) {
        Charset encoding = EncodingProjectManager.getInstance(myProject).getEncoding(file, true);
        if (encoding != null) {
          Set<Charset> charsets = map.get(module);
          if (charsets == null) {
            charsets = new LinkedHashSet<Charset>();
            map.put(module, charsets);
          }
          charsets.add(encoding);
        }
      }
    }

    return map;
  }

  @Override
  @javax.annotation.Nullable
  public Charset getPreferredModuleEncoding(@Nonnull Module module) {
    final Set<Charset> encodings = myModuleFileEncodings.getValue().get(module);
    return ContainerUtil.getFirstItem(encodings, EncodingProjectManager.getInstance(myProject).getDefaultCharset());
  }

  @Nonnull
  @Override
  public Collection<Charset> getAllModuleEncodings(@Nonnull Module module) {
    final Set<Charset> encodings = myModuleFileEncodings.getValue().get(module);
    if (encodings != null) {
      return encodings;
    }
    return ContainerUtil.createMaybeSingletonList(EncodingProjectManager.getInstance(myProject).getDefaultCharset());
  }
}
