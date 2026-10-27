package com.skillup.common;
/** Course rule: scan backwards for the last decimal digit; odd/no digit -> 1, even -> 2. */
public final class Shard {
  private Shard() {}

  public static int of(String userId) {
    if (userId == null || userId.trim().isEmpty())
      throw new IllegalArgumentException("userId is required");
    for (int i = userId.length() - 1; i >= 0; i--) {
      char c = userId.charAt(i);
      if (c >= '0' && c <= '9') return (c - '0') % 2 == 0 ? 2 : 1;
    }
    return 1;
  }
}
