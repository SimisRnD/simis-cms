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

package com.simisinc.platform.infrastructure.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.ImageHtmlEmail;
import org.jeasy.flows.work.TaskContext;
import org.jeasy.flows.work.WorkContext;
import org.jeasy.flows.work.WorkReport;
import org.jeasy.flows.work.WorkStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.email.EmailCommand;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;

import jakarta.servlet.ServletContext;

/**
 * Verifies {@link EmailTask#execute} reports {@code WorkStatus.FAILED} (not {@code COMPLETED})
 * when the send itself fails (issue #1124) -- before this fix, the report was hard-coded to
 * {@code COMPLETED} regardless of outcome, so even after {@link WorkflowManager} was fixed to
 * propagate a failed report, this task's own report never actually reflected the real result.
 * <p>
 * Sets {@link SchedulerManager}'s servlet-context field directly via reflection, rather than
 * {@link SchedulerManager#startup}, which also bootstraps a live JobRunr scheduler -- not
 * appropriate to trigger from a unit test.
 *
 * @author SimIS Inc.
 */
class EmailTaskTest {

  private static final String TEMPLATE = "cms/site-sign-up";

  @AfterEach
  void resetServletContext() throws Exception {
    setServletContext(null);
  }

  @Test
  void reportsFailedWhenTheSendThrows() throws Exception {
    setServletContext(fakeServletContext());

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<EmailCommand> emailCommand = mockStatic(EmailCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      EmailException sendFailure = new EmailException("simulated SMTP failure");
      emailCommand.when(() -> EmailCommand.prepareNewEmail(org.mockito.ArgumentMatchers.any()))
          .thenReturn(new StubEmail(sendFailure));

      WorkReport report = new EmailTask().execute(new WorkContext(), taskContext());

      assertEquals(WorkStatus.FAILED, report.getStatus());
      assertEquals(sendFailure, report.getError());
    }
  }

  @Test
  void reportsCompletedWhenTheSendSucceeds() throws Exception {
    setServletContext(fakeServletContext());

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<EmailCommand> emailCommand = mockStatic(EmailCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      emailCommand.when(() -> EmailCommand.prepareNewEmail(org.mockito.ArgumentMatchers.any())).thenReturn(new StubEmail(null));

      WorkReport report = new EmailTask().execute(new WorkContext(), taskContext());

      assertEquals(WorkStatus.COMPLETED, report.getStatus());
    }
  }

  private static TaskContext taskContext() {
    TaskContext taskContext = new TaskContext(new EmailTask());
    taskContext.put(EmailTask.TEMPLATE, TEMPLATE);
    taskContext.put(EmailTask.TO_EMAIL, "test@example.com");
    return taskContext;
  }

  private static void setServletContext(ServletContext value) throws Exception {
    Field field = SchedulerManager.class.getDeclaredField("servletContext");
    field.setAccessible(true);
    field.set(null, value);
  }

  // A minimal ServletContext that serves the real on-disk email templates -- Thymeleaf's Jakarta
  // integration reads through getResourceAsStream, so this lets the real template engine run
  // without a live servlet container.
  private static ServletContext fakeServletContext() {
    ServletContext servletContext = Mockito.mock(ServletContext.class);
    when(servletContext.getResourceAsStream(anyString())).thenAnswer(invocation -> {
      String path = invocation.getArgument(0);
      java.io.File file = new java.io.File("src/main/webapp" + path);
      return file.exists() ? new java.io.FileInputStream(file) : null;
    });
    when(servletContext.getMinorVersion()).thenReturn(0);
    when(servletContext.getMajorVersion()).thenReturn(6);
    when(servletContext.getEffectiveMinorVersion()).thenReturn(0);
    when(servletContext.getEffectiveMajorVersion()).thenReturn(6);
    when(servletContext.getContextPath()).thenReturn("");
    return servletContext;
  }

  // Mockito cannot mock ImageHtmlEmail/Commons Email directly on this project's JDK (inline mock
  // maker fails retransformation) -- a real subclass overriding just send() is the established
  // workaround (see MEMORY: simis-cms-mockito-cannot-mock-commons-email). The real superclass
  // still runs its normal addTo/setSubject/setMsg validation, so this exercises more production
  // behavior than a full mock would.
  private static class StubEmail extends ImageHtmlEmail {
    private final EmailException toThrow;

    StubEmail(EmailException toThrow) {
      this.toThrow = toThrow;
    }

    @Override
    public String send() throws EmailException {
      if (toThrow != null) {
        throw toThrow;
      }
      return "stub-message-id";
    }
  }
}
