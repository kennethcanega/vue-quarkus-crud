package com.example.users;

public record UserResponse(
        Long id,
        String name,
        String email,
        String username,
        String role,
        boolean active
) {
    public static UserResponse from(User user) {
        boolean isActive = Boolean.TRUE.equals(user.active);
        return new UserResponse(user.id, user.name, user.email, user.username, user.role, isActive);
    }
}
