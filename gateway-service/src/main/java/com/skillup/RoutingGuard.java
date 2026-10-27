package com.skillup;

import org.springframework.cloud.gateway.filter.*;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RoutingGuard implements GlobalFilter, Ordered {
  public int getOrder() {
    return -100;
  }

  public Mono<Void> filter(ServerWebExchange e, GatewayFilterChain chain) {
    String p = e.getRequest().getURI().getPath(),
        mark = e.getRequest().getHeaders().getFirst("mark");
    if (p.startsWith("/internal/")) {
      e.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
      return e.getResponse().setComplete();
    }
    if ((p.equals("/order") || p.startsWith("/order/"))
        && !("1".equals(mark) || "2".equals(mark))) {
      e.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
      return e.getResponse().setComplete();
    }
    return chain.filter(e);
  }
}
