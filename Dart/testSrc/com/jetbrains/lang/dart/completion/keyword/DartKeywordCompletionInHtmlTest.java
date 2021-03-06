package com.jetbrains.lang.dart.completion.keyword;

import com.jetbrains.lang.dart.util.DartHtmlUtil;

import java.io.IOException;

public abstract class DartKeywordCompletionInHtmlTest extends DartKeywordCompletionTest {
  @Override
  protected void configure(String... files) throws IOException {
    DartHtmlUtil.createHtmlAndConfigureFixture(myFixture, files);
  }
}
