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

package com.simisinc.platform.presentation.rest;

import com.simisinc.platform.application.cms.SaveWebPageHitCommand;
import com.simisinc.platform.domain.model.App;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.presentation.controller.RequestConstants;
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
    maxFileSize = 1024 * 1024 * 30,      // 30MB
    maxRequestSize = 1024 * 1024 * 50)   // 50MB
public class RestServlet extends HttpServlet {

  private static Log LOG = LogFactory.getLog(RestServlet.class);

  // Services Cache
  private Map<String, Object> serviceInstances = new HashMap<String, Object>();

  public void init(ServletConfig config) throws ServletException {

    LOG.info("RestServlet starting up...");
    String startupSuccessful = (String) config.getServletContext().getAttribute("STARTUP_SUCCESSFUL");
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

  public void destroy() {

  }

  public void service(HttpServletRequest request, HttpServletResponse response) {

    long startRequestTime = System.currentTimeMillis();

    LOG.trace("Service processor...");
    response.setContentType("application/json");
    try {
      response.setCharacterEncoding("UTF-8");
      request.setCharacterEncoding("UTF-8");
    } catch (Exception e) {
      LOG.warn("Unsupported encoding UTF-8: " + e.getMessage());
    }

    try {
      // Determine the resource
      String contextPath = request.getServletContext().getContextPath();
      String requestURI = request.getRequestURI();
      String endpoint = requestURI.substring(contextPath.length() + "/api/".length());
      String pathParam = null;

      // Get the cached class reference for processing
      Object classRef = serviceInstances.get(endpoint);
      String pathEndpoint = null;
      if (classRef == null) {
        LOG.debug("Could not find endpoint: " + endpoint);
        if (endpoint.contains("/")) {
          // Try as a pathParam
          pathEndpoint = endpoint.substring(0, endpoint.lastIndexOf("/"));
          pathParam = endpoint.substring(endpoint.lastIndexOf("/") + 1);
          classRef = serviceInstances.get(pathEndpoint);
        }
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("contextPath: " + contextPath);
        LOG.debug("requestURI: " + requestURI);
        LOG.debug("endpoint: " + endpoint);
        if (pathEndpoint != null) {
          LOG.debug("pathEndpoint: " + pathEndpoint);
          LOG.debug("pathParam: " + pathParam);
        }
      }
      if (classRef == null) {
        LOG.error("Class not found for service: " + endpoint);
        response.sendError(SC_NOT_FOUND);
        return;
      }

      // Determine the method
      String methodName = "get";
      if ("post".equalsIgnoreCase(request.getMethod())) {
        methodName = "post";
      } else if ("put".equalsIgnoreCase(request.getMethod())) {
        methodName = "put";
      } else if ("delete".equalsIgnoreCase(request.getMethod())) {
        methodName = "delete";
      }

      // REST endpoint hits
      SaveWebPageHitCommand.saveHit(request.getRemoteAddr(), request.getMethod(), "/api/" + (pathEndpoint != null ? pathEndpoint : endpoint), (User) request.getAttribute(RequestConstants.REST_USER));

      // Setup the context for this service processor
      ServiceContext serviceContext = new ServiceContext(request, response);
      serviceContext.setPathParam(pathParam);
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
        Method method = classRef.getClass().getMethod(methodName, new Class[]{serviceContext.getClass()});
        result = (ServiceResponse) method.invoke(classRef, new Object[]{serviceContext});
      } catch (NoSuchMethodException nm) {
        LOG.error("No Such Method Exception for method execute. MESSAGE = " + nm.getMessage(), nm);
      } catch (IllegalAccessException ia) {
        LOG.error("Illegal Access Exception. MESSAGE = " + ia.getMessage(), ia);
      } catch (Exception e) {
        LOG.error("Exception. MESSAGE = " + e.getMessage(), e);
      }
      if (result == null) {
        response.sendError(SC_NOT_FOUND);
        return;
      }
      if (result.getStatus() != 200) {
        response.setStatus(result.getStatus());
        result.getError().put("status", String.valueOf(result.getStatus()));
      }

      Jsonb jsonb = JsonbBuilder.create();
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
        String error = jsonb.toJson(result.getError());
        sb.append("\"errors\": ").append("[").append(error).append("]");
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
    } catch (Exception e) {
      LOG.error("Could not render: " + e.getMessage());
      LOG.error(e);
    }
  }
}
