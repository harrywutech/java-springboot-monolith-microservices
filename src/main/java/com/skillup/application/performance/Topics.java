package com.skillup.application.performance;
public final class Topics {
    public static final String CREATE="step2-create-order", LOCK="step2-lock-stock",
        CHECK="step2-pay-check", REVERT="step2-revert-stock", DEDUCT="step2-deduct-stock";
    public static final String[] ALL={CREATE,LOCK,CHECK,REVERT,DEDUCT};
    private Topics() {}
}
