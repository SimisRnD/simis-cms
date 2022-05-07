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
import com.simisinc.platform.presentation.controller.XMLContainerCommands;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.controller.UserSession;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Sets up the common HTTP servlet objects for executing widgets
 *
 * @author matt rajkowski
 * @author https://stackoverflow.com/questions/22714359/how-to-partially-mock-httpservletrequest-using-mockito
 * @created 5/3/2022 7:00 PM
 */
public class WidgetBase {

  public static final String ADMIN = "admin";
  public static final String CONTENT_MANAGER = "content-manager";
  public static final String COMMUNITY_MANAGER = "community-manager";

  public ServletContext servletContext = mock(ServletContext.class);
  public HttpServletRequest request = mock(HttpServletRequest.class);
  public HttpServletResponse response = mock(HttpServletResponse.class);
  public HttpSession session = mock(HttpSession.class);
  public WidgetContext widgetContext = null;
  public Map<String, String> preferences = null;

  @BeforeEach
  void setupWidgetContext() {
    // Provide a mock for the servlet request and response
    when(servletContext.getContextPath()).thenReturn("/");
    when(request.getServletContext()).thenReturn(servletContext);
    when(request.getSession()).thenReturn(session);

    // Mock Request setAttribute
    final Map<String, String[]> parameterMap = new HashMap<>();
    final Map<String, Object> attributes = new ConcurrentHashMap<String, Object>();
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        String key = invocation.getArgument(0, String.class);
        Object value = invocation.getArgument(1, Object.class);
        if (value != null) {
          attributes.put(key, value);
          if (value instanceof String) {
            parameterMap.put(key, new String[]{(String) value});
          }
        } else {
          attributes.remove(key);
        }
        return null;
      }
    }).when(request).setAttribute(anyString(), any());

    // Mock Request getAttribute
    Mockito.doAnswer(new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        String key = invocation.getArgument(0, String.class);
        return attributes.get(key);
      }
    }).when(request).getAttribute(anyString());

    // Mock Request getParameter
    Mockito.doAnswer(new Answer<String>() {
      @Override
      public String answer(InvocationOnMock invocation) throws Throwable {
        String key = invocation.getArgument(0, String.class);
        return (String) attributes.get(key);
      }
    }).when(request).getParameter(anyString());

    // Mock Session setAttribute
    final Map<String, Object> sessionAttributes = new ConcurrentHashMap<String, Object>();
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        String key = invocation.getArgument(0, String.class);
        Object value = invocation.getArgument(1, Object.class);
        if (value != null) {
          sessionAttributes.put(key, value);
        } else {
          sessionAttributes.remove(key);
        }
        return null;
      }
    }).when(session).setAttribute(anyString(), any());

    // Mock Session getAttribute
    Mockito.doAnswer(new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        String key = invocation.getArgument(0, String.class);
        return sessionAttributes.get(key);
      }
    }).when(session).getAttribute(anyString());

    // Every widget needs a widget context
    widgetContext = new WidgetContext(request, response, "widget1");

    // Collection to store attributes keys/values between requests
    Map<String, String> sharedWidgetValueMap = null;
    widgetContext.setSharedRequestValueMap(sharedWidgetValueMap);

    // Widgets can access core data which is provided by the container
    Map<String, String> coreData = new HashMap<>();
    widgetContext.setCoreData(coreData);

    // Widgets can have preferences
    preferences = new HashMap<>();
    widgetContext.setPreferences(preferences);

    // Widgets can read request parameters
    widgetContext.setParameterMap(parameterMap);

    // Default to being logged in
    login(widgetContext);
  }

  public static void setRoles(WidgetContext context, String... args) {
    UserSession userSession = context.getUserSession();
    if (userSession.isLoggedIn()) {
      List<Role> roleList = new ArrayList<>();
      for (String roleValue : args) {
        if (ADMIN.equals(roleValue)) {
          Role role = new Role("System Administrator", ADMIN);
          roleList.add(role);
        } else if (CONTENT_MANAGER.equals(roleValue)) {
          Role role = new Role("Content Manager", CONTENT_MANAGER);
          roleList.add(role);
        } else if (COMMUNITY_MANAGER.equals(roleValue)) {
          Role role = new Role("Content Manager", COMMUNITY_MANAGER);
          roleList.add(role);
        }
      }
      userSession.setRoleList(roleList);
    }
  }

  public static void setGroups(WidgetContext context, String... args) {
    UserSession userSession = context.getUserSession();
    if (userSession.isLoggedIn()) {
      List<Group> groupList = new ArrayList<>();
      for (String groupValue : args) {
        Group group = new Group();
        group.setUniqueId(groupValue);
        group.setName(groupValue);
        groupList.add(group);
      }
      userSession.setGroupList(groupList);
    }
  }

  public static void login(WidgetContext widgetContext) {
    // Widgets are accessed by users and guests
    List<Role> roleList = new ArrayList<>();

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
    widgetContext.getCoreData().put("userId", String.valueOf(userSession.getUserId()));
  }

  public static void logout(WidgetContext widgetContext) {
    // User information
    UserSession userSession = new UserSession();
    widgetContext.setUserSession(userSession);
    widgetContext.getCoreData().remove("userId");
  }

  public static void addPreferencesFromWidgetXml(WidgetContext context, String xmlFragment) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document document = null;
      try (InputStream is = IOUtils.toInputStream(xmlFragment, "UTF-8")) {
        document = builder.parse(is);
      }
      NodeList nodeList = document.getElementsByTagName("widget");
      if (nodeList.getLength() < 0) {
        return;
      }
      XMLContainerCommands.addWidgetPreferences(context.getPreferences(), nodeList.item(0).getChildNodes());
    } catch (Exception e) {
      Assertions.fail(e.getMessage());
    }
  }
}