package com.skillup.infrastructure;

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InstanceHeader extends OncePerRequestFilter {
  @Value("${spring.application.name}-${server.port}")
  private String id;

  protected void doFilterInternal(HttpServletRequest q, HttpServletResponse s, FilterChain chain)
      throws ServletException, IOException {
    s.setHeader("X-Service-Instance", id);
    chain.doFilter(q, s);
  }
}
