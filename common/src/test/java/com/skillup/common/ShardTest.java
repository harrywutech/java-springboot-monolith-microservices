package com.skillup.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ShardTest {
  @Test
  void oddUserGoesToOne() {
    assertEquals(1, Shard.of("1"));
  }

  @Test
  void evenUserGoesToTwo() {
    assertEquals(2, Shard.of("2"));
    assertEquals(2, Shard.of("abc0"));
  }

  @Test
  void scanPastUuidLetters() {
    assertEquals(2, Shard.of("a2fff"));
    assertEquals(1, Shard.of("a7fff"));
  }

  @Test
  void noDigitDefaultsToOne() {
    assertEquals(1, Shard.of("abcdef"));
  }

  @Test
  void missingIdentityRejected() {
    assertThrows(IllegalArgumentException.class, () -> Shard.of(null));
    assertThrows(IllegalArgumentException.class, () -> Shard.of(" "));
  }
}
