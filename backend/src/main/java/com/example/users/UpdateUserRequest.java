package com.example.users;

public record UpdateUserRequest(
        String name,
        String email,
        String username,
        String password,
        String role,
        Boolean active
) {
}
