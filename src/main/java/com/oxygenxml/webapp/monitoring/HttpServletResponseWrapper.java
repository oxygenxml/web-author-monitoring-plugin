package com.oxygenxml.webapp.monitoring;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Locale;

import ro.sync.ecss.extensions.api.webapp.plugin.servlet.ServletOutputStream;
import ro.sync.ecss.extensions.api.webapp.plugin.servlet.http.Cookie;
import ro.sync.ecss.extensions.api.webapp.plugin.servlet.http.HttpServletResponse;

public class HttpServletResponseWrapper implements HttpServletResponse {

  private HttpServletResponse delegate;

  public HttpServletResponseWrapper(HttpServletResponse delegate) {
    this.delegate = delegate;
  }

  @Override
  public String getCharacterEncoding() {
    return this.delegate.getCharacterEncoding();
  }

  @Override
  public String getContentType() {
    return this.delegate.getContentType();
  }

  @Override
  public ServletOutputStream getOutputStream() throws IOException {
    return this.delegate.getOutputStream();
  }

  @Override
  public PrintWriter getWriter() throws IOException {
    return this.delegate.getWriter();
  }

  @Override
  public void setCharacterEncoding(String encoding) {
    this.delegate.setCharacterEncoding(encoding);
  }

  @Override
  public void setContentLength(int len) {
    this.delegate.setContentLength(len);
  }

  @Override
  public void setContentLengthLong(long len) {
    this.delegate.setContentLengthLong(len);
  }

  @Override
  public void setContentType(String type) {
    this.delegate.setContentType(type);
  }

  @Override
  public void setBufferSize(int size) {
    this.delegate.setBufferSize(size);
  }

  @Override
  public int getBufferSize() {
    return this.delegate.getBufferSize();
  }

  @Override
  public void flushBuffer() throws IOException {
    this.delegate.flushBuffer();
  }

  @Override
  public void resetBuffer() {
    this.delegate.resetBuffer();
  }

  @Override
  public boolean isCommitted() {
    return this.delegate.isCommitted();
  }

  @Override
  public void reset() {
    this.delegate.reset();
  }

  @Override
  public void setLocale(Locale loc) {
    this.delegate.setLocale(loc);
  }

  @Override
  public Locale getLocale() {
    return this.delegate.getLocale();
  }

  @Override
  public void addCookie(Cookie cookie) {
    this.delegate.addCookie(cookie);
  }

  @Override
  public boolean containsHeader(String name) {
    return this.delegate.containsHeader(name);
  }

  @Override
  public String encodeURL(String url) {
    return this.delegate.encodeURL(url);
  }

  @Override
  public String encodeRedirectURL(String url) {
    return this.delegate.encodeRedirectURL(url);
  }

  @Override
  public void sendError(int sc, String msg) throws IOException {
    this.delegate.sendError(sc, msg);
  }

  @Override
  public void sendError(int sc) throws IOException {
    this.delegate.sendError(sc);
  }

  @Override
  public void sendRedirect(String location) throws IOException {
    this.delegate.sendRedirect(location);
  }

  @Override
  public void setDateHeader(String name, long date) {
    this.delegate.setDateHeader(name, date);
  }

  @Override
  public void addDateHeader(String name, long date) {
    this.delegate.addDateHeader(name, date);
  }

  @Override
  public void setHeader(String name, String value) {
    this.delegate.setHeader(name, value);
  }

  @Override
  public void addHeader(String name, String value) {
    this.delegate.addHeader(name, value);
  }

  @Override
  public void setIntHeader(String name, int value) {
    this.delegate.setIntHeader(name, value);
  }

  @Override
  public void addIntHeader(String name, int value) {
    this.delegate.addIntHeader(name, value);
  }

  @Override
  public void setStatus(int sc) {
    this.delegate.setStatus(sc);
  }

  @Override
  public int getStatus() {
    return this.delegate.getStatus();
  }

  @Override
  public String getHeader(String name) {
    return this.delegate.getHeader(name);
  }

  @Override
  public Collection<String> getHeaders(String name) {
    return this.delegate.getHeaders(name);
  }

  @Override
  public Collection<String> getHeaderNames() {
    return this.delegate.getHeaderNames();
  }
}
