/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.lang;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import consulo.annotation.access.RequiredReadAction;
import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class LanguageUtil {
  public static final Comparator<Language> LANGUAGE_COMPARATOR = (o1, o2) -> StringUtil.naturalCompare(o1.getDisplayName(), o2.getDisplayName());

  private LanguageUtil() {
  }

  public static
  @Nullable
  Language getLanguageForPsi(@Nonnull Project project, @Nullable VirtualFile file) {
    return getLanguageForPsi(project, file, null);
  }

  @Nullable
  public static Language getLanguageForPsi(@Nonnull Project project, @Nullable VirtualFile file, @Nullable FileType fileType) {
    if (file == null) return null;
    // a copy-paste of getFileLanguage(file)
    Language explicit = file instanceof LightVirtualFile ? ((LightVirtualFile)file).getLanguage() : null;
    Language fileLanguage = explicit != null ? explicit : getFileTypeLanguage(fileType != null ? fileType : file.getFileType());

    if (fileLanguage == null) return null;
    // run generic file-level substitutors, e.g. for scratches
    for (LanguageSubstitutor substitutor : LanguageSubstitutors.INSTANCE.forKey(Language.ANY)) {
      Language language = substitutor.getLanguage(file, project);
      if (language != null && language != Language.ANY) {
        fileLanguage = language;
        break;
      }
    }
    return LanguageSubstitutors.INSTANCE.substituteLanguage(fileLanguage, file, project);
  }

  @Nullable
  public static Language getFileLanguage(@Nullable VirtualFile file) {
    if (file == null) return null;
    Language l = file instanceof LightVirtualFile ? ((LightVirtualFile)file).getLanguage() : null;
    return l != null ? l : getFileTypeLanguage(file.getFileType());
  }

  @Nullable
  public static Language getFileTypeLanguage(@Nullable FileType fileType) {
    return fileType instanceof LanguageFileType ? ((LanguageFileType)fileType).getLanguage() : null;
  }

  @Nullable
  public static FileType getLanguageFileType(@Nullable Language language) {
    return language == null ? null : language.getAssociatedFileType();
  }

  @Nonnull
  @RequiredReadAction
  public static ParserDefinition.SpaceRequirements canStickTokensTogetherByLexer(ASTNode left, ASTNode right, Lexer lexer) {
    String textStr = left.getText() + right.getText();

    lexer.start(textStr, 0, textStr.length());
    if (lexer.getTokenType() != left.getElementType()) return ParserDefinition.SpaceRequirements.MUST;
    if (lexer.getTokenEnd() != left.getTextLength()) return ParserDefinition.SpaceRequirements.MUST;
    lexer.advance();
    if (lexer.getTokenEnd() != textStr.length()) return ParserDefinition.SpaceRequirements.MUST;
    if (lexer.getTokenType() != right.getElementType()) return ParserDefinition.SpaceRequirements.MUST;
    return ParserDefinition.SpaceRequirements.MAY;
  }

  @Nonnull
  public static Language[] getLanguageDialects(@Nonnull final Language base) {
    final List<Language> list = ContainerUtil.findAll(Language.getRegisteredLanguages(), new Condition<Language>() {
      @Override
      public boolean value(final Language language) {
        return language.getBaseLanguage() == base;
      }
    });
    return list.toArray(new Language[list.size()]);
  }

  public static boolean isInTemplateLanguageFile(@Nullable final PsiElement element) {
    if (element == null) return false;

    final PsiFile psiFile = element.getContainingFile();
    if (psiFile == null) return false;

    final Language language = psiFile.getViewProvider().getBaseLanguage();
    return language instanceof TemplateLanguage;
  }

  public static boolean isInjectableLanguage(@Nonnull Language language) {
    if (language == Language.ANY) {
      return false;
    }
    if (language.getID().startsWith("$")) {
      return false;
    }
    if (language instanceof InjectableLanguage) {
      return true;
    }
    if (language instanceof TemplateLanguage) {
      return false;
    }
    if (LanguageParserDefinitions.INSTANCE.forLanguage(language) == null) {
      return false;
    }
    return true;
  }

  public static boolean isFileLanguage(@Nonnull Language language) {
    if (language instanceof InjectableLanguage) return false;
    if (LanguageParserDefinitions.INSTANCE.forLanguage(language) == null) return false;
    LanguageFileType type = language.getAssociatedFileType();
    if (type == null || StringUtil.isEmpty(type.getDefaultExtension())) return false;
    String name = language.getDisplayName();
    if (StringUtil.isEmpty(name) || name.startsWith("<") || name.startsWith("[")) return false;
    return StringUtil.isNotEmpty(type.getDefaultExtension());
  }

  @Nonnull
  public static List<Language> getFileLanguages() {
    List<Language> result = ContainerUtil.newArrayList();
    for (Language language : Language.getRegisteredLanguages()) {
      if (!isFileLanguage(language)) continue;
      result.add(language);
    }
    Collections.sort(result, LANGUAGE_COMPARATOR);
    return result;
  }

  @Nonnull
  @RequiredReadAction
  public static Language getRootLanguage(@Nonnull PsiElement element) {
    final FileViewProvider provider = element.getContainingFile().getViewProvider();
    final Set<Language> languages = provider.getLanguages();
    if (languages.size() > 1) {
      PsiElement current = element;
      while (current != null) {
        final Language language = current.getLanguage();
        if (languages.contains(language)) return language;
        current = current.getParent();
      }
    }
    return provider.getBaseLanguage();
  }

  @Nonnull
  static JBIterable<Language> hierarchy(@Nonnull Language language) {
    return JBIterable.generate(language, Language::getBaseLanguage);
  }
}
