/*
 * Copyright 2022 SimIS Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.simisinc.platform.presentation.controller;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Opt-in resolution of the real client IP address when SimIS CMS runs behind a
 * trusted reverse proxy or load balancer.
 *
 * <p>Every IP-based control in the platform (the blocked-IP firewall, rate
 * limiting, geo filtering) and all IP logging read
 * {@link ServletRequest#getRemoteAddr()}, which returns the immediate TCP peer.
 * Behind a proxy that peer is the proxy, not the visitor. When the
 * {@code CMS_TRUSTED_PROXIES} environment variable is set to a regular
 * expression matching the trusted proxy addresses, this filter delegates to
 * Tomcat's {@code RemoteIpFilter}, which rewrites {@code getRemoteAddr()} to the
 * client address carried in {@code X-Forwarded-For} -- but only for requests
 * whose immediate peer matches that expression. Requests arriving from any other
 * address are left untouched, so an untrusted client cannot spoof its address.
 *
 * <p>Some proxies do not use {@code X-Forwarded-For}. Azure Front Door publishes the true client in
 * {@code X-Azure-ClientIP}; Cloudflare uses {@code CF-Connecting-IP}. Set
 * {@code CMS_CLIENT_IP_HEADER} to that header name and it is read instead. This matters where the
 * proxy's own address is not covered by {@code CMS_TRUSTED_PROXIES}: {@code RemoteIpFilter} walks
 * {@code X-Forwarded-For} right-to-left and stops at the first address it does not trust, so an
 * untrusted proxy hop is itself reported as the client. Front Door is the case in point -- its
 * origin-facing addresses are public and span 147 IPv4 prefixes that Microsoft revises over time,
 * so enumerating them in the expression is neither practical nor stable. A single-value header
 * sidesteps the walk entirely.
 *
 * <p>The header is only honoured for requests whose immediate peer is already trusted, so the same
 * spoofing protection applies. Note the deployment requirement that follows: the origin must not be
 * reachable except through the proxy, or a client could set the header itself. Front Door and
 * Cloudflare both overwrite it on the way through.
 *
 * <p>When the variable is unset (the default), the filter is a transparent
 * pass-through and behavior is unchanged. {@code RemoteIpFilter} is loaded
 * reflectively because it is supplied by the servlet container rather than
 * bundled in the web application.
 *
 * @author SimIS Inc.
 */
public class TrustedProxyIpFilter implements Filter {

  public static final String TRUSTED_PROXIES_ENV = "CMS_TRUSTED_PROXIES";
  public static final String CLIENT_IP_HEADER_ENV = "CMS_CLIENT_IP_HEADER";
  private static final String REMOTE_IP_FILTER = "org.apache.catalina.filters.RemoteIpFilter";
  private static Log LOG = LogFactory.getLog(TrustedProxyIpFilter.class);

  private Filter delegate = null;

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    String trustedProxies = trustedProxiesSetting();
    if (StringUtils.isBlank(trustedProxies)) {
      LOG.info(TRUSTED_PROXIES_ENV + " is not set; the client IP is read from the direct connection");
      return;
    }
    String clientIpHeader = StringUtils.trimToNull(clientIpHeaderSetting());
    try {
      delegate = (Filter) Class.forName(REMOTE_IP_FILTER).getDeclaredConstructor().newInstance();
      delegate.init(new InternalProxiesConfig(filterConfig, trustedProxies.trim(), clientIpHeader));
      LOG.info(TRUSTED_PROXIES_ENV + " is set; resolving the client IP from "
          + (clientIpHeader == null ? "X-Forwarded-For" : clientIpHeader) + " for trusted proxies");
    } catch (ReflectiveOperationException e) {
      // A misconfiguration must not take the site down: fall back to the direct address and warn.
      delegate = null;
      LOG.error(TRUSTED_PROXIES_ENV + " is set but " + REMOTE_IP_FILTER
          + " is unavailable (is this Tomcat?); the client IP will be read from the direct connection", e);
    }
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (delegate != null) {
      delegate.doFilter(request, response, chain);
    } else {
      chain.doFilter(request, response);
    }
  }

  @Override
  public void destroy() {
    if (delegate != null) {
      delegate.destroy();
    }
  }

  /** The configured trusted-proxy expression; overridable for testing. */
  protected String trustedProxiesSetting() {
    return System.getenv(TRUSTED_PROXIES_ENV);
  }

  /** The configured client-IP header name, or null to use X-Forwarded-For; overridable for testing. */
  protected String clientIpHeaderSetting() {
    return System.getenv(CLIENT_IP_HEADER_ENV);
  }

  /**
   * Presents the configured trusted-proxy expression to {@code RemoteIpFilter} as its
   * {@code internalProxies} init parameter; every other parameter falls back to the filter's defaults.
   */
  // Package-private rather than private so the parameter wiring can be asserted directly: the
  // container supplies RemoteIpFilter, so it is absent from the test classpath and init() always
  // takes the fall-back path there.
  static class InternalProxiesConfig implements FilterConfig {

    private final FilterConfig delegate;
    private final String internalProxies;
    private final String remoteIpHeader;

    InternalProxiesConfig(FilterConfig delegate, String internalProxies, String remoteIpHeader) {
      this.delegate = delegate;
      this.internalProxies = internalProxies;
      this.remoteIpHeader = remoteIpHeader;
    }

    @Override
    public String getFilterName() {
      return delegate.getFilterName();
    }

    @Override
    public ServletContext getServletContext() {
      return delegate.getServletContext();
    }

    @Override
    public String getInitParameter(String name) {
      if ("internalProxies".equals(name)) {
        return internalProxies;
      }
      if (remoteIpHeader != null && "remoteIpHeader".equals(name)) {
        return remoteIpHeader;
      }
      return delegate.getInitParameter(name);
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
      if (remoteIpHeader == null) {
        return Collections.enumeration(Collections.singletonList("internalProxies"));
      }
      return Collections.enumeration(Arrays.asList("internalProxies", "remoteIpHeader"));
    }
  }
}
