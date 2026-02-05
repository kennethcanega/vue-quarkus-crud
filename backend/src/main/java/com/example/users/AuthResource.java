package com.example.users;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";

    @ConfigProperty(name = "keycloak.server-url")
    String keycloakServerUrl;

    @ConfigProperty(name = "keycloak.realm")
    String keycloakRealm;

    @ConfigProperty(name = "keycloak.client-id")
    String keycloakClientId;

    @ConfigProperty(name = "keycloak.client-secret")
    String keycloakClientSecret;

    @ConfigProperty(name = "auth.refresh-cookie.secure", defaultValue = "false")
    boolean refreshCookieSecure;

    @Inject
    ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @POST
    @Path("/login")
    @PermitAll
    public Response login(LoginRequest request) {
        if (request == null || isBlank(request.username()) || isBlank(request.password())) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "password");
        form.put("client_id", keycloakClientId);
        form.put("client_secret", keycloakClientSecret);
        form.put("username", request.username());
        form.put("password", request.password());

        TokenResponse tokenResponse = exchangeToken(form);
        if (tokenResponse == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        User user = resolveUserFromAccessToken(tokenResponse.accessToken());
        if (user == null || !Boolean.TRUE.equals(user.active)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        LoginResponse payload = new LoginResponse(tokenResponse.accessToken(), UserResponse.from(user));

        return Response.ok(payload)
                .cookie(buildRefreshCookie(tokenResponse.refreshToken(), tokenResponse.refreshExpiresIn()))
                .build();
    }

    @POST
    @Path("/refresh")
    @PermitAll
    public Response refresh(@CookieParam(REFRESH_COOKIE_NAME) String refreshToken) {
        if (isBlank(refreshToken)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .cookie(expiredRefreshCookie())
                    .build();
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("client_id", keycloakClientId);
        form.put("client_secret", keycloakClientSecret);
        form.put("refresh_token", refreshToken);

        TokenResponse tokenResponse = exchangeToken(form);
        if (tokenResponse == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .cookie(expiredRefreshCookie())
                    .build();
        }

        User user = resolveUserFromAccessToken(tokenResponse.accessToken());
        if (user == null || !Boolean.TRUE.equals(user.active)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .cookie(expiredRefreshCookie())
                    .build();
        }

        LoginResponse payload = new LoginResponse(tokenResponse.accessToken(), UserResponse.from(user));
        return Response.ok(payload)
                .cookie(buildRefreshCookie(tokenResponse.refreshToken(), tokenResponse.refreshExpiresIn()))
                .build();
    }

    @POST
    @Path("/logout")
    @PermitAll
    public Response logout(@CookieParam(REFRESH_COOKIE_NAME) String refreshToken) {
        if (!isBlank(refreshToken)) {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("client_id", keycloakClientId);
            form.put("client_secret", keycloakClientSecret);
            form.put("refresh_token", refreshToken);
            postToKeycloak(logoutEndpoint(), form);
        }
        return Response.noContent().cookie(expiredRefreshCookie()).build();
    }

    private TokenResponse exchangeToken(Map<String, String> form) {
        HttpResponse<String> response = postToKeycloak(tokenEndpoint(), form);
        if (response == null || response.statusCode() != 200) {
            return null;
        }
        try {
            JsonNode json = objectMapper.readTree(response.body());
            return new TokenResponse(
                    json.path("access_token").asText(null),
                    json.path("refresh_token").asText(null),
                    json.path("refresh_expires_in").asInt(0));
        } catch (IOException exception) {
            return null;
        }
    }

    private HttpResponse<String> postToKeycloak(String endpoint, Map<String, String> form) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(toFormEncodedBody(form)))
                .build();

        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private String extractUsername(String accessToken) {
        if (isBlank(accessToken)) {
            return null;
        }
        String[] tokenParts = accessToken.split("\\.");
        if (tokenParts.length < 2) {
            return null;
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(tokenParts[1]), StandardCharsets.UTF_8);
            JsonNode json = objectMapper.readTree(payload);
            String preferredUsername = json.path("preferred_username").asText("").trim();
            if (!preferredUsername.isEmpty()) {
                return preferredUsername;
            }
            return json.path("sub").asText("").trim();
        } catch (IllegalArgumentException | IOException exception) {
            return null;
        }
    }

    private NewCookie buildRefreshCookie(String token, int ttlSeconds) {
        return new NewCookie.Builder(REFRESH_COOKIE_NAME)
                .value(token == null ? "" : token)
                .httpOnly(true)
                .path("/")
                .sameSite(NewCookie.SameSite.STRICT)
                .secure(refreshCookieSecure)
                .maxAge(Math.max(ttlSeconds, 0))
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

    private String tokenEndpoint() {
        return keycloakServerUrl + "/realms/" + keycloakRealm + "/protocol/openid-connect/token";
    }

    private String logoutEndpoint() {
        return keycloakServerUrl + "/realms/" + keycloakRealm + "/protocol/openid-connect/logout";
    }

    private String toFormEncodedBody(Map<String, String> form) {
        return form.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record TokenResponse(String accessToken, String refreshToken, int refreshExpiresIn) {
    }

    private record TokenClaims(String preferredUsername, String subject) {
    }
}
