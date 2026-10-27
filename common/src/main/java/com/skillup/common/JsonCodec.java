package com.skillup.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonCodec {
  private final ObjectMapper mapper;

  public String write(Object o) {
    try {
      return mapper.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  public <T> T read(String s, Class<T> t) {
    try {
      return mapper.readValue(s, t);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }
}
