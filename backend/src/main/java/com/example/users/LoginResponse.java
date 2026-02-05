package com.example.users;

public record LoginResponse(String token, UserResponse user) {
}
