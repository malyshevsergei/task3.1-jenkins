/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.notification;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import consulo.ui.annotation.RequiredUIAccess;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.BiConsumer;

/**
 * @author Alexander Lobas
 */
public abstract class NotificationAction extends DumbAwareAction {
  @Nonnull
  public static NotificationAction create(@Nullable String text, @RequiredUIAccess @Nonnull BiConsumer<? super AnActionEvent, ? super Notification> actionPerformed) {
    return new NotificationAction(text) {
      @RequiredUIAccess
      @Override
      public void actionPerformed(@Nonnull AnActionEvent e, @Nonnull Notification notification) {
        actionPerformed.accept(e, notification);
      }
    };
  }

  public NotificationAction(@Nullable String text) {
    super(text);
  }

  @RequiredUIAccess
  @Override
  public void actionPerformed(@Nonnull AnActionEvent e) {
    actionPerformed(e, Notification.get(e));
  }

  @RequiredUIAccess
  public abstract void actionPerformed(@Nonnull AnActionEvent e, @Nonnull Notification notification);
}