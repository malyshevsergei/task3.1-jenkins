// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import consulo.util.dataholder.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.fs.FilePath;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nonnull;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Singleton
public final class DocumentReferenceManagerImpl extends DocumentReferenceManager {
  private static final Key<List<VirtualFile>> DELETED_FILES = Key.create(DocumentReferenceManagerImpl.class.getName() + ".DELETED_FILES");

  private final Map<Document, DocumentReference> myDocToRef = ContainerUtil.createWeakKeyWeakValueMap();

  private static final Key<Reference<DocumentReference>> FILE_TO_REF_KEY = Key.create("FILE_TO_REF_KEY");
  private static final Key<DocumentReference> FILE_TO_STRONG_REF_KEY = Key.create("FILE_TO_STRONG_REF_KEY");
  private final Map<FilePath, DocumentReference> myDeletedFilePathToRef = ContainerUtil.createWeakValueMap();

  @Inject
  DocumentReferenceManagerImpl(@Nonnull Application application) {
    application.getMessageBus().connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void before(@Nonnull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          if (event instanceof VFileDeleteEvent) {
            beforeFileDeletion((VFileDeleteEvent)event);
          }
        }
      }

      @Override
      public void after(@Nonnull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          if (event instanceof VFileCreateEvent) {
            fileCreated((VFileCreateEvent)event);
          }
          else if (event instanceof VFileDeleteEvent) {
            fileDeleted((VFileDeleteEvent)event);
          }
        }
      }

      private void beforeFileDeletion(@Nonnull VFileDeleteEvent event) {
        VirtualFile f = event.getFile();
        f.putUserData(DELETED_FILES, collectDeletedFiles(f, new ArrayList<>()));
      }

      private void fileDeleted(@Nonnull VFileDeleteEvent event) {
        VirtualFile f = event.getFile();
        List<VirtualFile> files = f.getUserData(DELETED_FILES);
        f.putUserData(DELETED_FILES, null);

        assert files != null : f;
        for (VirtualFile each : files) {
          DocumentReference ref = SoftReference.dereference(each.getUserData(FILE_TO_REF_KEY));
          each.putUserData(FILE_TO_REF_KEY, null);
          if (ref != null) {
            myDeletedFilePathToRef.put(new FilePath(each.getUrl()), ref);
          }
        }
      }

      private void fileCreated(@Nonnull VFileCreateEvent event) {
        VirtualFile f = event.getFile();
        DocumentReference ref = f == null ? null : myDeletedFilePathToRef.remove(new FilePath(f.getUrl()));
        if (ref != null) {
          f.putUserData(FILE_TO_REF_KEY, new WeakReference<>(ref));
          ((DocumentReferenceByVirtualFile)ref).update(f);
        }
      }
    });
  }

  @Nonnull
  private static List<VirtualFile> collectDeletedFiles(@Nonnull VirtualFile f, @Nonnull List<VirtualFile> files) {
    if (!(f instanceof NewVirtualFile)) return files;

    if (!f.isDirectory()) {
      files.add(f);
    }
    else {
      for (VirtualFile each : ((NewVirtualFile)f).iterInDbChildren()) {
        collectDeletedFiles(each, files);
      }
    }
    return files;
  }

  @Nonnull
  @Override
  public DocumentReference create(@Nonnull Document document) {
    assertIsWriteThread();

    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    return file == null ? createFromDocument(document) : create(file);
  }

  @Nonnull
  private DocumentReference createFromDocument(@Nonnull final Document document) {
    DocumentReference result = myDocToRef.get(document);
    if (result == null) {
      result = new DocumentReferenceByDocument(document);
      myDocToRef.put(document, result);
    }
    return result;
  }

  @Nonnull
  @Override
  public DocumentReference create(@Nonnull VirtualFile file) {
    assertIsWriteThread();

    if (!file.isInLocalFileSystem()) { // we treat local files differently from non local because we can undo their deletion
      DocumentReference reference = file.getUserData(FILE_TO_STRONG_REF_KEY);
      if (reference == null) {
        file.putUserData(FILE_TO_STRONG_REF_KEY, reference = new DocumentReferenceByNonlocalVirtualFile(file));
      }
      return reference;
    }

    assert file.isValid() : "file is invalid: " + file;

    DocumentReference result = SoftReference.dereference(file.getUserData(FILE_TO_REF_KEY));
    if (result == null) {
      result = new DocumentReferenceByVirtualFile(file);
      file.putUserData(FILE_TO_REF_KEY, new WeakReference<>(result));
    }
    return result;
  }

  private static void assertIsWriteThread() {
    ApplicationManager.getApplication().assertIsWriteThread();
  }

  @TestOnly
  public void cleanupForNextTest() {
    myDeletedFilePathToRef.clear();
    myDocToRef.clear();
  }

}
