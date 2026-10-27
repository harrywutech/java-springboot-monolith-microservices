package com.skillup.application.performance;

public final class Topics {
  public static final String LOCK = "step3-lock-stock",
      REVERT = "step3-revert-stock",
      DEDUCT = "step3-deduct-stock";

  public static String create(int shard) {
    return "step3-create-order-" + shard;
  }

  public static String check(int shard) {
    return "step3-pay-check-" + shard;
  }
}
