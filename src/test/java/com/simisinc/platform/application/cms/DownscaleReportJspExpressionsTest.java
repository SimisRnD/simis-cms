/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.simisinc.platform.application.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import com.simisinc.platform.domain.model.cms.Image;

/**
 * Evaluates every property the Oversized image report reads, with the expression language Tomcat 11
 * runs the JSP with.
 *
 * <p>The report's rows are records ({@link DownscaleLibraryImagesCommand.Candidate},
 * {@link DownscaleLibraryImagesCommand.Reference}, {@link DownscaleLibraryImagesCommand.Spellings}).
 * EL 6.0 reads a record only through its components and never falls back to a JavaBean getter, so
 * {@code ${reference.sourceType}} -- a getter, not a component -- threw PropertyNotFoundException.
 * PageServlet catches that and answers an empty 200, so on the live site the report was a blank page
 * whenever an oversized image was used on a page. Nothing in the unit tests renders the JSP, and
 * compiling it cannot see this: property lookup happens at runtime.
 *
 * <p>EL is not on the test classpath, so this loads the WAR's own {@code el-api.jar} and
 * {@code jasper-el.jar} -- the jars Tomcat 11 runs -- in a separate class loader.
 *
 * @author SimIS Inc.
 */
class DownscaleReportJspExpressionsTest {

  private static final Path REPORT_JSP = Path.of("src/main/webapp/WEB-INF/jsp/admin/image-downscale-report.jsp");
  private static final Path[] EL_JARS = {Path.of("lib/war/el-api.jar"), Path.of("lib/war/jasper-el.jar")};

  /** The page's loop and request variables whose properties are read; each maps to a sample below. */
  private static final Pattern PROPERTY_CHAIN = Pattern.compile("\\b(candidate|reference|usage|image)((?:\\.[A-Za-z_]\\w*)+)");
  private static final Pattern EXPRESSION = Pattern.compile("\\$\\{([^}]*)}");

  /** One row as the report sees it: an image used in a content block and as a page's share image. */
  private static Map<String, Object> sampleBeans() {
    Image image = new Image();
    image.setId(102L);
    image.setFilename("team photo.jpg");
    image.setWebPath("def456");
    image.setFileType("image/jpeg");
    image.setFileLength(1_800_000L);
    image.setWidth(4000);
    image.setHeight(3000);
    DownscaleLibraryImagesCommand.Reference reference = new DownscaleLibraryImagesCommand.Reference(
        DownscaleLibraryImagesCommand.Source.CONTENT, 7L, "home-intro", new DownscaleLibraryImagesCommand.Spellings(1, 1, 0));
    ImageUsageCommand.UsageReference usage = new ImageUsageCommand.UsageReference("Web Page", "/legal/privacy",
        "web_pages", "page_image_url");
    DownscaleLibraryImagesCommand.Candidate candidate = new DownscaleLibraryImagesCommand.Candidate(image,
        List.of(reference), List.of(usage), null);
    return Map.of("candidate", candidate, "reference", reference, "usage", usage, "image", image);
  }

  /** Every property chain on those variables inside an EL expression in the report, e.g. reference.spellings.encoded. */
  private static Set<String> propertyChainsInReport() throws IOException {
    Set<String> chains = new LinkedHashSet<>();
    Matcher expression = EXPRESSION.matcher(Files.readString(REPORT_JSP, StandardCharsets.UTF_8));
    while (expression.find()) {
      Matcher chain = PROPERTY_CHAIN.matcher(expression.group(1));
      while (chain.find()) {
        chains.add(chain.group(1) + chain.group(2));
      }
    }
    return chains;
  }

  /** Evaluates each expression with Tomcat's EL against the sample beans, keyed by expression. */
  private static Map<String, Object> evaluate(Set<String> expressions) throws Exception {
    URL[] jars = new URL[EL_JARS.length];
    for (int i = 0; i < EL_JARS.length; i++) {
      assertTrue(Files.isRegularFile(EL_JARS[i]), "missing " + EL_JARS[i]);
      jars[i] = EL_JARS[i].toUri().toURL();
    }
    // The test classpath has the JSP API but not EL, and EL's CompositeELResolver probes for the JSP
    // API's ScopedAttributeELResolver: found through the test classpath, that class cannot see the
    // EL classes it extends. So the application's classes come from the test classpath, and EL and
    // the JSP API from these jars only.
    ClassLoader applicationClasses = new ClassLoader(DownscaleReportJspExpressionsTest.class.getClassLoader()) {
      @Override
      protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name.startsWith("jakarta.el.") || name.startsWith("jakarta.servlet.jsp.")) {
          throw new ClassNotFoundException(name);
        }
        return super.loadClass(name, resolve);
      }
    };
    Thread thread = Thread.currentThread();
    ClassLoader previous = thread.getContextClassLoader();
    try (URLClassLoader el = new URLClassLoader(jars, applicationClasses)) {
      // ExpressionFactory.newInstance() finds org.apache.el.ExpressionFactoryImpl through the context loader
      thread.setContextClassLoader(el);
      Class<?> processorClass = el.loadClass("jakarta.el.ELProcessor");
      Object processor = processorClass.getConstructor().newInstance();
      Method defineBean = processorClass.getMethod("defineBean", String.class, Object.class);
      Method eval = processorClass.getMethod("eval", String.class);
      for (Map.Entry<String, Object> bean : sampleBeans().entrySet()) {
        defineBean.invoke(processor, bean.getKey(), bean.getValue());
      }
      Map<String, Object> values = new LinkedHashMap<>();
      List<String> failures = new ArrayList<>();
      for (String expression : expressions) {
        try {
          values.put(expression, eval.invoke(processor, expression));
        } catch (InvocationTargetException e) {
          failures.add("${" + expression + "}: " + e.getCause());
        }
      }
      assertEquals(List.of(), failures, "the report reads properties Tomcat 11's EL cannot resolve");
      return values;
    } finally {
      thread.setContextClassLoader(previous);
    }
  }

  @Test
  void everyPropertyTheReportReadsResolvesUnderTomcatsExpressionLanguage() throws Exception {
    Set<String> chains = propertyChainsInReport();
    // The rows the bug was in, so a rewrite of the page that drops them cannot pass by reading nothing
    assertTrue(chains.contains("reference.label"), "found " + chains);
    assertTrue(chains.contains("reference.spellings.encoded"), "found " + chains);
    assertTrue(chains.contains("usage.sourceType"), "found " + chains);
    evaluate(chains);
  }

  @Test
  void eachRowNamesWhereTheImageIsUsed() throws Exception {
    Map<String, Object> values = evaluate(new LinkedHashSet<>(List.of(
        "reference.source.sourceType", "reference.label", "usage.sourceType", "candidate.image.filename")));
    assertEquals("Content Block", values.get("reference.source.sourceType"));
    assertEquals("home-intro", values.get("reference.label"));
    assertEquals("Web Page", values.get("usage.sourceType"));
    assertEquals("team photo.jpg", values.get("candidate.image.filename"));
  }

  /** Holds the premise: if EL ever resolves getters on records, this test's reason to exist is gone. */
  @Test
  void tomcatsExpressionLanguageDoesNotReadAGetterThatIsNotARecordComponent() {
    AssertionFailedError refused = assertThrows(AssertionFailedError.class,
        () -> evaluate(new LinkedHashSet<>(List.of("reference.sourceType"))));
    assertFalse(refused.getMessage().isEmpty());
    assertTrue(refused.getMessage().contains("PropertyNotFoundException"), refused.getMessage());
  }
}
