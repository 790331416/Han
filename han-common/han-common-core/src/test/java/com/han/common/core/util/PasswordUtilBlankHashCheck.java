package com.han.common.core.util;

public final class PasswordUtilBlankHashCheck {

    public static void main(String[] args) {
        if (PasswordUtil.matches("test-password", "")) {
            throw new AssertionError("blank hash must not authenticate");
        }
    }
}
