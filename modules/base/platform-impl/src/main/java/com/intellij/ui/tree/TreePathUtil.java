// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Contract;
import javax.annotation.Nonnull;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

public class TreePathUtil {
  /**
   * @param parent    the parent path or {@code null} to indicate the root
   * @param component the last path component
   * @return a tree path with all the parent components plus the given component
   */
  @Nonnull
  public static TreePath createTreePath(TreePath parent, @Nonnull Object component) {
    return parent != null ? parent.pathByAddingChild(component) : new TreePath(component);
  }

  /**
   * @param path a tree path to convert
   * @return an array with the string representations of path components or {@code null}
   * if the specified path is wrong
   * or a path component is {@code null}
   * or its string representation is {@code null}
   */
  public static String[] convertTreePathToStrings(@Nonnull TreePath path) {
    return convertTreePathToArray(path, Object::toString, String.class);
  }

  /**
   * @param path a tree path to convert
   * @return an array with the same path components or {@code null}
   * if the specified path is wrong
   * or a path component is {@code null}
   */
  public static Object[] convertTreePathToArray(@Nonnull TreePath path) {
    return convertTreePathToArray(path, object -> object, Object.class);
  }

  /**
   * @param path      a tree path to convert
   * @param converter a function to convert path components
   * @return an array with the converted path components or {@code null}
   * if the specified path is wrong
   * or a path component is {@code null}
   * or a path component is converted to {@code null}
   */
  public static Object[] convertTreePathToArray(@Nonnull TreePath path, @Nonnull Function<Object, Object> converter) {
    return convertTreePathToArray(path, converter, Object.class);
  }

  /**
   * @param path      a tree path to convert
   * @param converter a function to convert path components
   * @param type      a type of components of the new array
   * @return an array of the specified type with the converted path components or {@code null}
   * if the specified path is wrong
   * or a path component is {@code null}
   * or a path component is converted to {@code null}
   */
  private static <T> T[] convertTreePathToArray(@Nonnull TreePath path, @Nonnull Function<Object, ? extends T> converter, @Nonnull Class<T> type) {
    int count = path.getPathCount();
    if (count <= 0) return null;
    T[] array = ArrayUtil.newArray(type, count);
    while (path != null && count > 0) {
      Object component = path.getLastPathComponent();
      if (component == null) return null;
      T object = convert(component, converter);
      if (object == null) return null;
      array[--count] = object;
      path = path.getParentPath();
    }
    return path != null || count > 0 ? null : array;
  }

  /**
   * @param array an array of path components to convert
   * @return a tree path with the same path components or {@code null}
   * if the specified array is empty
   * or a path component is {@code null}
   * or a path component is converted to {@code null}
   */
  @SafeVarargs
  public static <T> TreePath convertArrayToTreePath(@Nonnull T... array) {
    return convertArrayToTreePath(array, object -> object);
  }

  /**
   * @param array     an array of path components to convert
   * @param converter a function to convert path components
   * @return a tree path with the converted path components or {@code null}
   * if the specified array is empty
   * or a path component is {@code null}
   * or a path component is converted to {@code null}
   */
  public static <T> TreePath convertArrayToTreePath(@Nonnull T[] array, @Nonnull Function<? super T, Object> converter) {
    return array.length == 0 ? null : convertCollectionToTreePath(Arrays.asList(array), converter);
  }

  /**
   * @param collection a collection of path components to convert
   * @param converter  a function to convert path components
   * @return a tree path with the converted path components or {@code null}
   * if the specified collection is empty
   * or a path component is {@code null}
   * or a path component is converted to {@code null}
   */
  private static <T> TreePath convertCollectionToTreePath(@Nonnull List<? extends T> collection, @Nonnull Function<? super T, Object> converter) {
    TreePath path = null;
    for (T object : collection) {
      Object component = convert(object, converter);
      if (component == null) return null;
      path = createTreePath(path, component);
    }
    return path;
  }

  private static <T> TreePath convertReversedToTreePath(@Nonnull List<? extends T> collection, @Nonnull Function<? super T, Object> converter) {
    TreePath path = null;
    for (int i = collection.size() - 1; i >= 0; i--) {
      T object = collection.get(i);
      Object component = convert(object, converter);
      if (component == null) return null;
      path = createTreePath(path, component);
    }
    return path;
  }

  /**
   * @param node the tree node to get the path for
   * @return a tree path with the converted path components or {@code null}
   * if a path component is {@code null}
   * or a path component is converted to {@code null}
   */
  public static TreePath pathToTreeNode(@Nonnull TreeNode node) {
    return pathToTreeNode(node, object -> object);
  }

  /**
   * @param node      the tree node to get the path for
   * @param converter a function to convert path components
   * @return a tree path with the converted path components or {@code null}
   * if a path component is {@code null}
   * or a path component is converted to {@code null}
   */
  public static TreePath pathToTreeNode(@Nonnull TreeNode node, @Nonnull Function<? super TreeNode, Object> converter) {
    return pathToCustomNode(node, TreeNode::getParent, converter);
  }

  /**
   * @param node      the node to get the path for
   * @param getParent a function to get a parent node for the given one
   * @return a tree path with the converted path components or {@code null}
   * if a path component is {@code null}
   * or a path component is converted to {@code null}
   */
  public static <T> TreePath pathToCustomNode(@Nonnull T node, @Nonnull Function<? super T, ? extends T> getParent) {
    return pathToCustomNode(node, getParent, object -> object);
  }

  /**
   * @param node      the node to get the path for
   * @param getParent a function to get a parent node for the given one
   * @param converter a function to convert path components
   * @return a tree path with the converted path components or {@code null}
   * if a path component is {@code null}
   * or a path component is converted to {@code null}
   */
  public static <T> TreePath pathToCustomNode(@Nonnull T node, @Nonnull Function<? super T, ? extends T> getParent, @Nonnull Function<? super T, Object> converter) {
    List<T> deque = new ArrayList<>();
    while (node != null) {
      deque.add(node);
      node = getParent.apply(node);
    }
    return convertReversedToTreePath(deque, converter);
  }

  private static <I, O> O convert(I object, @Nonnull Function<I, O> converter) {
    return object == null ? null : converter.apply(object);
  }

  public static TreeNode toTreeNode(TreePath path) {
    Object component = path == null ? null : path.getLastPathComponent();
    return component instanceof TreeNode ? (TreeNode)component : null;
  }

  @Contract("!null->!null")
  public static TreeNode[] toTreeNodes(TreePath... paths) {
    return paths == null ? null : Stream.of(paths).map(TreePathUtil::toTreeNode).filter(Objects::nonNull).toArray(TreeNode[]::new);
  }

  public static TreePath toTreePath(TreeNode node) {
    return node == null ? null : pathToTreeNode(node);
  }

  public static TreePath[] toTreePaths(TreeNode... nodes) {
    return nodes == null ? null : Stream.of(nodes).map(TreePathUtil::toTreePath).filter(Objects::nonNull).toArray(TreePath[]::new);
  }
}
