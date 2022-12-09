// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup;

import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.lang.ref.WeakReference;

import static com.intellij.openapi.util.registry.Registry.intValue;

/**
 * This helper class is intended to prevent opening
 * a popup, a popup menu or a balloon right after its closing.
 */
public abstract class PopupState<Popup> {
  private WeakReference<Popup> reference;
  private boolean hiddenLongEnough = true;
  private long timeHiddenAt;


  @Nonnull
  public static PopupState<JBPopup> forPopup() {
    return new JBPopupState();
  }

  @Nonnull
  public static PopupState<JPopupMenu> forPopupMenu() {
    return new JPopupMenuState();
  }

  @Nonnull
  public static PopupState<Balloon> forBalloon() {
    return new BalloonState();
  }

  public void prepareToShow(@Nonnull Popup popup) {
    hidePopup();
    addListener(popup);
    reference = new WeakReference<>(popup);
  }

  public boolean isRecentlyHidden() {
    if (hiddenLongEnough) return false;
    hiddenLongEnough = true;
    return (System.currentTimeMillis() - timeHiddenAt) < intValue("ide.popup.hide.show.threshold", 200);
  }

  public boolean isHidden() {
    return !isShowing();
  }

  public boolean isShowing() {
    Popup popup = getPopup();
    return popup != null && isShowing(popup);
  }

  public void hidePopup() {
    Popup popup = getPopup();
    if (popup != null) hide(popup);
    reference = null;
  }

  @Nullable
  public Popup getPopup() {
    WeakReference<Popup> reference = this.reference;
    return reference == null ? null : reference.get();
  }

  abstract void addListener(@Nonnull Popup popup);

  abstract void removeListener(@Nonnull Popup popup);

  abstract boolean isShowing(@Nonnull Popup popup);

  abstract void hide(@Nonnull Popup popup);

  void onHide() {
    Popup popup = getPopup();
    if (popup != null) removeListener(popup);
    reference = null;
    hiddenLongEnough = false;
    timeHiddenAt = System.currentTimeMillis();
  }


  private static final class JBPopupState extends PopupState<JBPopup> implements JBPopupListener {
    @Override
    void addListener(@Nonnull JBPopup popup) {
      popup.addListener(this);
    }

    @Override
    void removeListener(@Nonnull JBPopup popup) {
      popup.removeListener(this);
    }

    @Override
    boolean isShowing(@Nonnull JBPopup popup) {
      return popup.isVisible();
    }

    @Override
    void hide(@Nonnull JBPopup popup) {
      popup.cancel();
      removeListener(popup);
    }

    @Override
    public void onClosed(@Nonnull LightweightWindowEvent event) {
      onHide();
    }
  }


  private static final class JPopupMenuState extends PopupState<JPopupMenu> implements PopupMenuListener {
    @Override
    void addListener(@Nonnull JPopupMenu menu) {
      menu.addPopupMenuListener(this);
    }

    @Override
    void removeListener(@Nonnull JPopupMenu menu) {
      menu.removePopupMenuListener(this);
    }

    @Override
    boolean isShowing(@Nonnull JPopupMenu menu) {
      return menu.isShowing();
    }

    @Override
    void hide(@Nonnull JPopupMenu menu) {
      menu.setVisible(false);
      removeListener(menu);
    }

    @Override
    public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
    }

    @Override
    public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {
      onHide();
    }

    @Override
    public void popupMenuCanceled(PopupMenuEvent event) {
    }
  }


  private static final class BalloonState extends PopupState<Balloon> implements JBPopupListener {
    @Override
    void addListener(@Nonnull Balloon balloon) {
      balloon.addListener(this);
    }

    @Override
    void removeListener(@Nonnull Balloon balloon) {
      // there is no method to remove a listener from a balloon
      // we assume that all listeners will be removed when it is hidden
    }

    @Override
    boolean isShowing(@Nonnull Balloon balloon) {
      return !balloon.isDisposed();
    }

    @Override
    void hide(@Nonnull Balloon balloon) {
      balloon.hide();
      removeListener(balloon);
    }

    @Override
    public void onClosed(@Nonnull LightweightWindowEvent event) {
      onHide();
    }
  }
}
