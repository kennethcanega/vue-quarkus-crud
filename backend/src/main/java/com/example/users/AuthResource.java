package com.example.users;

import io.smallrye.jwt.build.Jwt;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";

    @ConfigProperty(name = "auth.access-token.ttl-seconds", defaultValue = "300")
    long accessTokenTtlSeconds;

    @ConfigProperty(name = "auth.refresh-token.ttl-seconds", defaultValue = "1209600")
    long refreshTokenTtlSeconds;

    @ConfigProperty(name = "auth.refresh-cookie.secure", defaultValue = "false")
    boolean refreshCookieSecure;

    @Inject
    RefreshTokenService refreshTokenService;

    @POST
    @Path("/login")
    @PermitAll
    @Transactional
    public Response login(LoginRequest request) {
        if (request == null || request.username() == null || request.password() == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        User user = User.findByUsername(request.username());
        if (user == null || !user.active || !PasswordUtils.matches(request.password(), user.passwordHash)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        String refreshToken = refreshTokenService.issue(user, refreshTokenTtlSeconds);
        LoginResponse payload = new LoginResponse(buildAccessToken(user), UserResponse.from(user));

        return Response.ok(payload)
                .cookie(buildRefreshCookie(refreshToken, refreshTokenTtlSeconds))
                .build();
    }

    @POST
    @Path("/refresh")
    @PermitAll
    @Transactional
    public Response refresh(@CookieParam(REFRESH_COOKIE_NAME) String refreshToken) {
        RefreshTokenService.RotatedToken rotated = refreshTokenService.rotate(refreshToken, refreshTokenTtlSeconds);
        if (rotated == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .cookie(expiredRefreshCookie())
                    .build();
        }

        User user = rotated.user();
        LoginResponse payload = new LoginResponse(buildAccessToken(user), UserResponse.from(user));
        return Response.ok(payload)
                .cookie(buildRefreshCookie(rotated.plainToken(), refreshTokenTtlSeconds))
                .build();
    }

    @POST
    @Path("/logout")
    @PermitAll
    @Transactional
    public Response logout(@CookieParam(REFRESH_COOKIE_NAME) String refreshToken) {
        User user = refreshTokenService.revokeByPlainToken(refreshToken);
        if (user != null) {
            refreshTokenService.revokeAllForUser(user);
        }
        return Response.noContent().cookie(expiredRefreshCookie()).build();
    }

    private String buildAccessToken(User user) {
        return Jwt.issuer("quarkus-crud")
                .subject(user.username)
                .groups(Set.of(user.role))
                .expiresAt(Instant.now().plus(Duration.ofSeconds(accessTokenTtlSeconds)))
                .sign();
    }

    private NewCookie buildRefreshCookie(String token, long ttlSeconds) {
        return new NewCookie.Builder(REFRESH_COOKIE_NAME)
                .value(token)
                .httpOnly(true)
                .path("/")
                .sameSite(NewCookie.SameSite.STRICT)
                .secure(refreshCookieSecure)
                .maxAge((int) ttlSeconds)
                .build();
    }

    private NewCookie expiredRefreshCookie() {
        return new NewCookie.Builder(REFRESH_COOKIE_NAME)
                .value("")
                .httpOnly(true)
                .path("/")
                .sameSite(NewCookie.SameSite.STRICT)
                .secure(refreshCookieSecure)
                .maxAge(0)
                .build();
    }
}
