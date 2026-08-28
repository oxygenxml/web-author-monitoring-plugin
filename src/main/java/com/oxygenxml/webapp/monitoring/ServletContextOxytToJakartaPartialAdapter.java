package com.oxygenxml.webapp.monitoring;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.ServletRegistration.Dynamic;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.SessionTrackingMode;
import jakarta.servlet.descriptor.JspConfigDescriptor;

public class ServletContextOxytToJakartaPartialAdapter implements jakarta.servlet.ServletContext {

  private ro.sync.ecss.extensions.api.webapp.plugin.servlet.ServletContext servletContext;

  public ServletContextOxytToJakartaPartialAdapter(
      ro.sync.ecss.extensions.api.webapp.plugin.servlet.ServletContext servletContext) {
    this.servletContext = servletContext;
  }

  @Override
  public void setAttribute(String name, Object object) {
    this.servletContext.setAttribute(name, object);
  }

  @Override
  public Object getAttribute(String name) {
    return this.servletContext.getAttribute(name);
  }
  
  @Override
  public String getContextPath() {
    return servletContext.getContextPath();
  }

  @Override
  public ServletContext getContext(String uripath) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getMajorVersion() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getMinorVersion() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getEffectiveMajorVersion() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getEffectiveMinorVersion() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getMimeType(String file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<String> getResourcePaths(String path) {
    throw new UnsupportedOperationException();
  }

  @Override
  public URL getResource(String path) throws MalformedURLException {
    throw new UnsupportedOperationException();
  }

  @Override
  public InputStream getResourceAsStream(String path) {
    throw new UnsupportedOperationException();
  }

  @Override
  public RequestDispatcher getRequestDispatcher(String path) {
    throw new UnsupportedOperationException();
  }

  @Override
  public RequestDispatcher getNamedDispatcher(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void log(String msg) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void log(String message, Throwable throwable) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getRealPath(String path) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getServerInfo() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getInitParameter(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Enumeration<String> getInitParameterNames() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean setInitParameter(String name, String value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Enumeration<String> getAttributeNames() {
    throw new UnsupportedOperationException();
  }
  @Override
  public void removeAttribute(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getServletContextName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Dynamic addServlet(String servletName, String className) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Dynamic addServlet(String servletName, Servlet servlet) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Dynamic addJspFile(String servletName, String jspFile) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T extends Servlet> T createServlet(Class<T> clazz) throws ServletException {
    throw new UnsupportedOperationException();
  }

  @Override
  public ServletRegistration getServletRegistration(String servletName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<String, ? extends ServletRegistration> getServletRegistrations() {
    throw new UnsupportedOperationException();
  }

  @Override
  public jakarta.servlet.FilterRegistration.Dynamic addFilter(String filterName, String className) {
    throw new UnsupportedOperationException();
  }

  @Override
  public jakarta.servlet.FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
    throw new UnsupportedOperationException();
  }

  @Override
  public jakarta.servlet.FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T extends Filter> T createFilter(Class<T> clazz) throws ServletException {
    throw new UnsupportedOperationException();
  }

  @Override
  public FilterRegistration getFilterRegistration(String filterName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
    throw new UnsupportedOperationException();
  }

  @Override
  public SessionCookieConfig getSessionCookieConfig() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addListener(String className) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T extends EventListener> void addListener(T t) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addListener(Class<? extends EventListener> listenerClass) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T extends EventListener> T createListener(Class<T> clazz) throws ServletException {
    throw new UnsupportedOperationException();
  }

  @Override
  public JspConfigDescriptor getJspConfigDescriptor() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ClassLoader getClassLoader() {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public void declareRoles(String... roleNames) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getVirtualServerName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getSessionTimeout() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setSessionTimeout(int sessionTimeout) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getRequestCharacterEncoding() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setRequestCharacterEncoding(String encoding) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getResponseCharacterEncoding() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setResponseCharacterEncoding(String encoding) {
    throw new UnsupportedOperationException();
  }
}
