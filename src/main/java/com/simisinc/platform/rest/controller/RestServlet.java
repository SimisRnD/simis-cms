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

package com.simisinc.platform.rest.controller;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.SaveWebPageHitCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.App;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.presentation.controller.ContextConstants;
import com.simisinc.platform.presentation.controller.RequestConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

/**
 * Handles all web api requests
 *
 * @author matt rajkowski
 * @created 7/17/18 1:51 PM
 */
@MultipartConfig(fileSizeThreshold = 1024 * 1024 * 2, // 2MB
    maxFileSize = 1024 * 1024 * 30, // 30MB
    maxRequestSize = 1024 * 1024 * 50) // 50MB
public class RestServlet extends HttpServlet {

  private static Log LOG = LogFactory.getLog(RestServlet.class);

  // Services Cache
  private Map<String, Object> serviceInstances = new HashMap<String, Object>();

  @Override
  public void init(ServletConfig config) throws ServletException {

    LOG.info("RestServlet starting up...");
    String startupSuccessful = (String) config.getServletContext().getAttribute(ContextConstants.STARTUP_SUCCESSFUL);
    if (!"true".equals(startupSuccessful)) {
      throw new ServletException("Startup failed due to previous error");
    }

    // Find the available services
    LOG.info("Loading the services library...");
    XMLServiceLoader xmlServiceLoader = new XMLServiceLoader();
    xmlServiceLoader.addDirectory(config.getServletContext(), "rest-services");

    // Instantiate the services
    LOG.info("Instantiating the services...");
    for (Map<String, String> service : xmlServiceLoader.getServiceLibrary()) {
      String endpoint = service.get("endpoint");
      try {
        String serviceClass = service.get("serviceClass");
        Object classRef = Class.forName(serviceClass).getDeclaredConstructor().newInstance();
        serviceInstances.put(endpoint, classRef);
        LOG.info("Added service class: " + endpoint + " = " + serviceClass);
      } catch (Exception e) {
        LOG.error("Class not found for '" + endpoint + "': " + e.getMessage());
      }
    }
    LOG.info("Services loaded: " + serviceInstances.size());
  }

  @Override
  public void destroy() {

  }

  @Override
  public void service(HttpServletRequest request, HttpServletResponse response) {

    long startRequestTime = System.currentTimeMillis();

    LOG.debug("Service processor...");

    // Determine request values
    String requestMethod = request.getMethod().toLowerCase();
    String contextPath = request.getServletContext().getContextPath();
    String requestURI = request.getRequestURI();
    String endpoint = requestURI.substring(contextPath.length() + "/api/".length());
    String pathParam = null;
    String pathParam2 = null;
    if (LOG.isDebugEnabled()) {
      LOG.debug("method: " + requestMethod);
      LOG.debug("contextPath: " + contextPath);
      LOG.debug("requestURI: " + requestURI);
      LOG.debug("endpoint: " + endpoint);
    }

    // Prep the response
    String siteUrl = LoadSitePropertyCommand.loadByName("site.url");
    if (StringUtils.isNotBlank(request.getHeader("Origin")) && StringUtils.isNotBlank(siteUrl)) {
      response.addHeader("Access-Control-Allow-Origin", siteUrl);
    }
    response.setContentType("application/json");
    try {
      response.setCharacterEncoding("UTF-8");
      request.setCharacterEncoding("UTF-8");
    } catch (Exception e) {
      LOG.warn("Unsupported encoding UTF-8: " + e.getMessage());
    }

    // Determine the resource
    try {
      // Get the cached class reference for processing
      Object classRef = serviceInstances.get(endpoint);
      String pathEndpoint = null;
      if (classRef == null) {
        LOG.debug("Could not find endpoint: " + endpoint);
        if (endpoint.contains("/")) {
          // Try as a pathParam
          pathEndpoint = endpoint.substring(0, endpoint.indexOf("/"));
          pathParam = endpoint.substring(endpoint.indexOf("/") + 1);
          if (pathParam.contains("/")) {
            pathParam2 = pathParam.substring(pathParam.indexOf("/") + 1);
            pathParam = pathParam.substring(0, pathParam.indexOf("/"));
          }
          classRef = serviceInstances.get(pathEndpoint);
        }
      }
      if (classRef == null) {
        LOG.error("Class not found for service: " + endpoint);
        sendError(response, SC_NOT_FOUND, "Endpoint not found");
        return;
      }
      if (LOG.isDebugEnabled()) {
        if (pathEndpoint != null) {
          LOG.debug("pathEndpoint: " + pathEndpoint);
          LOG.debug("pathParam: " + pathParam);
          LOG.debug("pathParam2: " + pathParam2);
        }
      }

      // Determine the method
      String methodName = "get";
      if ("post".equals(requestMethod)) {
        methodName = "post";
      } else if ("put".equals(requestMethod)) {
        methodName = "put";
      } else if ("delete".equals(requestMethod)) {
        methodName = "delete";
      }

      // REST endpoint hits
      SaveWebPageHitCommand.saveHit(request.getRemoteAddr(), request.getMethod(),
          "/api/" + (pathEndpoint != null ? pathEndpoint : endpoint),
          (User) request.getAttribute(RequestConstants.REST_USER));

      // Setup the context for this service processor
      ServiceContext serviceContext = new ServiceContext(request, response);
      serviceContext.setPathParam(pathParam);
      serviceContext.setPathParam2(pathParam2);
      serviceContext.setParameterMap(request.getParameterMap());
      serviceContext.setApp((App) request.getAttribute(RequestConstants.REST_APP));
      serviceContext.setUser((User) request.getAttribute(RequestConstants.REST_USER));

      // Execute the service
      ServiceResponse result = null;
      try {
        LOG.debug("-----------------------------------------------------------------------");
        if (pathEndpoint != null) {
          LOG.debug("Executing service: " + pathEndpoint);
        } else {
          LOG.debug("Executing service: " + endpoint);
        }
        Method method = classRef.getClass().getMethod(methodName, new Class[] { serviceContext.getClass() });
        result = (ServiceResponse) method.invoke(classRef, new Object[] { serviceContext });
      } catch (NoSuchMethodException nm) {
        LOG.error("No Such Method Exception for method execute. MESSAGE = " + nm.getMessage(), nm);
      } catch (IllegalAccessException ia) {
        LOG.error("Illegal Access Exception. MESSAGE = " + ia.getMessage(), ia);
      } catch (Exception e) {
        LOG.error("Exception. MESSAGE = " + e.getMessage(), e);
      }
      if (result == null) {
        LOG.debug("Returning an error...");
        sendError(response, SC_NOT_FOUND, "Service error occurred");
        return;
      }
      if (result.getStatus() != 200) {
        LOG.debug("Setting result: " + result.getStatus());
        response.setStatus(result.getStatus());
        result.getError().put("status", String.valueOf(result.getStatus()));
      }

      // Aspire to, but not quite there:
      // https://google.github.io/styleguide/jsoncstyleguide.xml
      LOG.debug("Returning JSON...");
      try (Jsonb jsonb = JsonbBuilder.create()) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean hasValues = false;
        if (!result.getMeta().isEmpty()) {
          hasValues = true;
          String meta = jsonb.toJson(result.getMeta());
          sb.append("\"meta\": ").append(meta);
        }
        if (!result.getError().isEmpty()) {
          if (hasValues) {
            sb.append(",");
          } else {
            hasValues = true;
          }
          sb.append("\"error\": ")
              .append("{")
              .append("\"code\": ").append(result.getStatus()).append(",")
              .append("\"message\": \"").append(JsonCommand.toJson(result.getError().get("title"))).append("\"")
              .append("}");
        }
        if (result.getData() != null) {
          if (hasValues) {
            sb.append(",");
          } else {
            hasValues = true;
          }
          String data = jsonb.toJson(result.getData());
          sb.append("\"data\": ").append(data);
        }
        if (!result.getLinks().isEmpty()) {
          if (hasValues) {
            sb.append(",");
          }
          String links = jsonb.toJson(result.getLinks());
          sb.append("\"links\": ").append(links);
        }
        sb.append("}");
        String json = sb.toString();

        long endRequestTime = System.currentTimeMillis();
        long totalTime = endRequestTime - startRequestTime;
        LOG.debug("REST total time: " + totalTime + "ms");

        response.setContentLength(json.length());
        PrintWriter out = response.getWriter();
        out.print(json);
        out.flush();
        if (LOG.isDebugEnabled()) {
          LOG.debug("Response sent: " + json);
        }
      }
    } catch (Exception e) {
      LOG.error("Could not render: " + e.getMessage());
      LOG.error(e);
      try {
        sendError(response, 500, "Error occurred");
      } catch (Exception io) {
        // no connection
      }
    }
  }

  public static void sendError(HttpServletResponse response, int code, String errorMessage) throws IOException {
    response.setStatus(code);
    PrintWriter out = response.getWriter();
    out.print("{\n" +
        "      \"error\": {\n" +
        "        \"code\": " + code + ",\n" +
        "        \"message\": \"" + JsonCommand.toJson(errorMessage) + "\"\n" +
        "      }\n" +
        "    }");
    out.flush();
  }
}
