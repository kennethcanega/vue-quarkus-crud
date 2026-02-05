package com.example.users;

import io.quarkus.elytron.security.common.BcryptUtil;

public final class PasswordUtils {
    private PasswordUtils() {
    }

    public static String hash(String password) {
        return BcryptUtil.bcryptHash(password);
    }

    public static boolean matches(String password, String hash) {
        return BcryptUtil.matches(password, hash);
    }
}
