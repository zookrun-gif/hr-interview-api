package com.zook.hrinterview.interfaces.auth.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class PasswordHashTool {

    private PasswordHashTool() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: PasswordHashTool <plain-password>");
            return;
        }
        System.out.println(new BCryptPasswordEncoder().encode(args[0]));
    }
}
