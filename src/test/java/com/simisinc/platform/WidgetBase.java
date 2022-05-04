/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
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

package com.simisinc.platform;

import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import com.simisinc.platform.presentation.controller.login.UserSession;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author matt rajkowski
 * @created 5/3/2022 7:00 PM
 */
public class WidgetBase {

  public ServletContext servletContext = mock(ServletContext.class);
  public HttpServletRequest request = mock(HttpServletRequest.class);
  public HttpServletResponse response = mock(HttpServletResponse.class);
  public HttpSession session = mock(HttpSession.class);
  public WidgetContext widgetContext = null;

  @BeforeEach
  void setupWidgetContext() {
    // Provide a mock for the servlet request and response
    when(servletContext.getContextPath()).thenReturn("/");
    when(request.getServletContext()).thenReturn(servletContext);
    when(request.getSession()).thenReturn(session);

    // Request attributes
    // https://stackoverflow.com/questions/22714359/how-to-partially-mock-httpservletrequest-using-mockito
    final Map<String, Object> attributes = new ConcurrentHashMap<String, Object>();
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        String key = invocation.getArgument(0, String.class);
        Object value = invocation.getArgument(1, Object.class);
        if (value != null) {
          attributes.put(key, value);
        }
        return null;
      }
    }).when(request).setAttribute(anyString(), any());

    // Mock getAttribute
    // https://stackoverflow.com/questions/22714359/how-to-partially-mock-httpservletrequest-using-mockito
    Mockito.doAnswer(new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        String key = invocation.getArgument(0, String.class);
        Object value = attributes.get(key);
        return value;
      }
    }).when(request).getAttribute(anyString());

    // Every widget needs a widget context
    widgetContext = new WidgetContext(request, response, "widget1");

    // Widgets are accessed by users and guests
    List<Role> roleList = new ArrayList<>();
    Role administratorRole = new Role("System Administrator", "admin");
    roleList.add(administratorRole);

    // Related user information
    List<Group> groupList = new ArrayList<>();

    // User information
    User user = new User();
    user.setId(1L);
    user.setRoleList(roleList);
    user.setGroupList(groupList);
    UserSession userSession = new UserSession();
    userSession.login(user);
    widgetContext.setUserSession(userSession);

    Map<String, String> coreData = new HashMap<>();
    coreData.put("userId", String.valueOf(userSession.getUserId()));
    widgetContext.setCoreData(coreData);

    // Collection to store attributes keys/values
    Map<String, String> sharedWidgetValueMap = null;
    widgetContext.setSharedRequestValueMap(sharedWidgetValueMap);
  }
}