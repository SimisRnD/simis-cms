package com.simisinc.platform;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

/**
 * Ant's junitlauncher prints each class's "Tests run: N, Failures: M" line using
 * TestExecutionSummary#getTestsFailedCount() (test-level only), but sets the
 * failureproperty that fails the build using #getTotalFailureCount() (test+container
 * level). A container failure -- a @BeforeAll/@AfterAll or extension callback throwing --
 * can fail the build while every printed per-class line still reads "Failures: 0", making
 * ci-test fail with no visible cause anywhere in the log. This listener prints the
 * container, its source, and the exception whenever that gap would otherwise hide it.
 */
public class ContainerFailureReportingListener implements TestExecutionListener {

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult result) {
        if (!testIdentifier.isContainer() || result.getStatus() != TestExecutionResult.Status.FAILED) {
            return;
        }
        System.out.println("================================================================================");
        System.out.println("CONTAINER-LEVEL TEST FAILURE (invisible to any class's \"Tests run\" summary line)");
        System.out.println("Container: " + testIdentifier.getDisplayName());
        System.out.println("Source:    " + testIdentifier.getSource().map(Object::toString).orElse("<unknown>"));
        System.out.println("================================================================================");
        result.getThrowable().ifPresent(t -> t.printStackTrace(System.out));
        System.out.println("================================================================================");
    }
}
