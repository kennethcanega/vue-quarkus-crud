package com.example.users;

public record CreateUserRequest(
        String name,
        String email,
        String username,
        String password,
        String role,
        Boolean active
) {
}
