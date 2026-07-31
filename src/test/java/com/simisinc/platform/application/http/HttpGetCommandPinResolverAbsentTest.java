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

package com.simisinc.platform.application.http;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Regression guard for the issue #760 dev/prod parity gap: this project's own README documents
 * deploying the plain {@code .war} to a self-managed servlet container as a supported path
 * independent of {@code docker/app/Dockerfile}, and that path does not automatically get
 * {@code target/simis-cms-ssrf-pin-resolver.jar} onto the container's shared classpath. Loads a
 * FRESH copy of {@link HttpGetCommand} under an isolated classloader built from exactly
 * {@code build/} + {@code lib/build/**}{@code /*.jar} -- the shape of a deployed WAR's
 * {@code WEB-INF/classes} + {@code WEB-INF/lib}, with no {@code CATALINA_HOME/lib} equivalent
 * -- and confirms {@code executeUserUrl(...)} degrades gracefully (SSRF guard still enforced,
 * fetch still attempted, no crash) rather than throwing {@code NoClassDefFoundError}, which is
 * what it did before {@code HttpGetCommand.PIN_RESOLVER_AVAILABLE} was added (confirmed by
 * temporarily removing that guard while writing this test).
 *
 * @author Liz Houser
 * @created 7/31/2026
 */
class HttpGetCommandPinResolverAbsentTest {

  @Test
  void executeUserUrlDoesNotCrashWhenThePinResolverJarIsMissingFromTheClasspath() throws Exception {
    File classesDir = new File("build");
    File depsDir = new File("lib/build");
    assumeTrue(classesDir.isDirectory() && depsDir.isDirectory(),
        "expects to run with the project basedir as the working directory (build/ and lib/build/ must exist)");

    List<URL> urls = new ArrayList<>();
    urls.add(classesDir.toURI().toURL());
    collectJars(depsDir, urls);
    // The one thing deliberately excluded: target/simis-cms-ssrf-pin-resolver.jar (see
    // build.xml's pin-resolver-jar target). Everything else a real WAR deployment would have
    // on WEB-INF/classes + WEB-INF/lib is included.

    try (URLClassLoader isolated = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader())) {
      Class<?> isolatedHttpGetCommand = Class.forName("com.simisinc.platform.application.http.HttpGetCommand", true, isolated);
      assertTrue(isolatedHttpGetCommand.getClassLoader() == isolated,
          "sanity check: must be a FRESH class load under the isolated loader, not one delegated back to this test's own classloader");

      Method executeUserUrl = isolatedHttpGetCommand.getMethod("executeUserUrl", String.class);
      Object result;
      try {
        // 169.254.169.254 is blocked by RemoteUrlValidationCommand regardless of pinning; the
        // point here is only that the CALL doesn't throw NoClassDefFoundError -- the guard
        // itself is already covered by RemoteUrlValidationCommandTest.
        result = executeUserUrl.invoke(null, "http://169.254.169.254/metadata");
      } catch (InvocationTargetException e) {
        throw new AssertionError("executeUserUrl threw when the pin-resolver jar is absent from the classpath "
            + "-- it must degrade gracefully instead (see HttpGetCommand.PIN_RESOLVER_AVAILABLE)", e.getCause());
      }
      assertNull(result, "the SSRF guard must still block a cloud-metadata address even without pinning available");
    }
  }

  private static void collectJars(File dir, List<URL> urls) throws Exception {
    File[] entries = dir.listFiles();
    if (entries == null) {
      return;
    }
    for (File entry : entries) {
      if (entry.isDirectory()) {
        collectJars(entry, urls);
      } else if (entry.getName().endsWith(".jar")) {
        urls.add(entry.toURI().toURL());
      }
    }
  }
}
