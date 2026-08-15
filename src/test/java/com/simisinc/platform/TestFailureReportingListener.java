package com.simisinc.platform;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

/**
 * Prints the cause of every test failure to the build log, because Ant's junitlauncher does not.
 *
 * <p>
 * {@code printsummary="true"} emits only counts -- "Tests run: 22, Failures: 1" -- so on its own it
 * can tell you a build broke but never which method broke or why. Two distinct gaps follow from
 * that, and this listener closes both:
 * </p>
 *
 * <ul>
 * <li><b>Test-level failures.</b> The summary counts them, but the assertion, the method name and
 * the stack trace appear nowhere in the log. Reproducing the failure locally becomes the only way
 * to find out what happened, which is expensive for anything intermittent -- a flaky assertion that
 * fails on a small percentage of runs may not reproduce locally at all.</li>
 * <li><b>Container-level failures.</b> A {@code @BeforeAll}/{@code @AfterAll} or extension callback
 * throwing fails the build via {@code TestExecutionSummary#getTotalFailureCount()} (test+container),
 * while every printed per-class line still reads "Failures: 0" because that line uses
 * {@code #getTestsFailedCount()} (test-level only). The build fails with no visible cause at all.</li>
 * </ul>
 *
 * <p>
 * One further reading trap this does not fix, but which the banners below make easier to navigate:
 * junitlauncher prints a class's "Tests run" line <em>before</em> the next class's "Running" line,
 * so a summary visually attaches to the class named beneath it rather than the one it describes.
 * </p>
 */
public class TestFailureReportingListener implements TestExecutionListener {

  private static final String RULE =
      "================================================================================";

  @Override
  public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult result) {
    if (result.getStatus() != TestExecutionResult.Status.FAILED) {
      return;
    }
    boolean container = testIdentifier.isContainer();
    System.out.println(RULE);
    if (container) {
      System.out.println("CONTAINER-LEVEL TEST FAILURE (invisible to any class's \"Tests run\" summary line)");
    } else {
      System.out.println("TEST FAILURE (the \"Tests run\" summary line counts this but never says why)");
    }
    System.out.println((container ? "Container: " : "Test:      ") + testIdentifier.getDisplayName());
    System.out.println("Unique ID: " + testIdentifier.getUniqueId());
    System.out.println("Source:    " + testIdentifier.getSource().map(Object::toString).orElse("<unknown>"));
    System.out.println(RULE);
    result.getThrowable().ifPresent(t -> t.printStackTrace(System.out));
    System.out.println(RULE);
  }
}
