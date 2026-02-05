package com.example.users;

import io.smallrye.jwt.build.Jwt;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @POST
    @Path("/login")
    @PermitAll
    public Response login(LoginRequest request) {
        if (request == null || request.username() == null || request.password() == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        User user = User.findByUsername(request.username());
        if (user == null || !user.active || !PasswordUtils.matches(request.password(), user.passwordHash)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        String token = Jwt.issuer("quarkus-crud")
                .subject(user.username)
                .groups(Set.of(user.role))
                .expiresAt(Instant.now().plus(Duration.ofHours(8)))
                .sign();
        return Response.ok(new LoginResponse(token, UserResponse.from(user))).build();
    }
}
