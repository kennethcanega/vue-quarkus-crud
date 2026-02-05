package com.example.users;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class KeycloakAdminService {

    @ConfigProperty(name = "keycloak.server-url")
    String keycloakServerUrl;

    @ConfigProperty(name = "keycloak.realm")
    String keycloakRealm;

    @ConfigProperty(name = "keycloak.client-id")
    String keycloakClientId;

    @ConfigProperty(name = "keycloak.client-secret")
    String keycloakClientSecret;

    @Inject
    ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public record CreateUserCommand(String username, String email, String firstName, String password, String role, Boolean active) {
    }

    public record UpdateUserCommand(String username, String email, String firstName, String password, String role, Boolean active) {
    }

    public String createUser(CreateUserCommand command) {
        String adminToken = getServiceAccountToken();
        if (adminToken == null) {
            return null;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", command.username());
        payload.put("email", command.email());
        payload.put("firstName", command.firstName());
        payload.put("enabled", Optional.ofNullable(command.active()).orElse(true));
        payload.put("credentials", List.of(Map.of(
                "type", "password",
                "value", command.password(),
                "temporary", false)));

        HttpResponse<String> response = sendJson("POST", adminUsersEndpoint(), adminToken, payload);
        if (response == null || (response.statusCode() != 201 && response.statusCode() != 204)) {
            return null;
        }

        String userId = extractCreatedUserId(response);
        if (userId == null) {
            userId = findUserIdByUsername(command.username(), adminToken);
        }
        if (userId == null) {
            return null;
        }

        if (!assignRealmRole(userId, command.role(), adminToken)) {
            return null;
        }
        return userId;
    }

    public boolean updateUser(String keycloakUserId, UpdateUserCommand command) {
        String adminToken = getServiceAccountToken();
        if (adminToken == null || isBlank(keycloakUserId)) {
            return false;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", command.username());
        payload.put("email", command.email());
        payload.put("firstName", command.firstName());
        payload.put("enabled", Optional.ofNullable(command.active()).orElse(true));

        HttpResponse<String> response = sendJson("PUT", adminUsersEndpoint() + "/" + keycloakUserId, adminToken, payload);
        if (response == null || response.statusCode() >= 300) {
            return false;
        }

        if (!isBlank(command.password()) && !setPassword(keycloakUserId, command.password(), adminToken)) {
            return false;
        }

        return assignRealmRole(keycloakUserId, command.role(), adminToken);
    }

    public boolean deleteUser(String keycloakUserId) {
        String adminToken = getServiceAccountToken();
        if (adminToken == null || isBlank(keycloakUserId)) {
            return false;
        }
        HttpResponse<String> response = sendJson("DELETE", adminUsersEndpoint() + "/" + keycloakUserId, adminToken, null);
        return response != null && response.statusCode() < 300;
    }

    private boolean setPassword(String userId, String password, String adminToken) {
        Map<String, Object> payload = Map.of(
                "type", "password",
                "value", password,
                "temporary", false);
        HttpResponse<String> response = sendJson("PUT", adminUsersEndpoint() + "/" + userId + "/reset-password", adminToken, payload);
        return response != null && response.statusCode() < 300;
    }

    private boolean assignRealmRole(String userId, String roleName, String adminToken) {
        String finalRole = isBlank(roleName) ? "user" : roleName;

        JsonNode adminRole = getRole(finalRole, adminToken);
        JsonNode userRole = getRole("user", adminToken);
        JsonNode existing = getUserRealmRoles(userId, adminToken);
        if (adminRole == null || userRole == null || existing == null) {
            return false;
        }

        // remove existing managed roles
        List<JsonNode> toRemove = existing.findValuesAsText("name").stream()
                .filter(name -> "admin".equals(name) || "user".equals(name))
                .map(name -> "admin".equals(name) ? adminRole : userRole)
                .toList();

        if (!toRemove.isEmpty()) {
            HttpResponse<String> removeResponse = sendJson(
                    "DELETE",
                    adminUsersEndpoint() + "/" + userId + "/role-mappings/realm",
                    adminToken,
                    toRemove);
            if (removeResponse == null || removeResponse.statusCode() >= 300) {
                return false;
            }
        }

        JsonNode targetRole = "admin".equals(finalRole) ? adminRole : userRole;
        HttpResponse<String> addResponse = sendJson(
                "POST",
                adminUsersEndpoint() + "/" + userId + "/role-mappings/realm",
                adminToken,
                List.of(targetRole));
        return addResponse != null && addResponse.statusCode() < 300;
    }

    private JsonNode getRole(String roleName, String adminToken) {
        HttpResponse<String> response = sendJson("GET", adminRolesEndpoint() + "/" + roleName, adminToken, null);
        if (response == null || response.statusCode() >= 300) {
            return null;
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (IOException exception) {
            return null;
        }
    }

    private JsonNode getUserRealmRoles(String userId, String adminToken) {
        HttpResponse<String> response = sendJson("GET", adminUsersEndpoint() + "/" + userId + "/role-mappings/realm", adminToken, null);
        if (response == null || response.statusCode() >= 300) {
            return null;
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (IOException exception) {
            return null;
        }
    }

    private String findUserIdByUsername(String username, String adminToken) {
        HttpResponse<String> response = sendJson("GET", adminUsersEndpoint() + "?username=" + encode(username) + "&exact=true", adminToken, null);
        if (response == null || response.statusCode() >= 300) {
            return null;
        }
        try {
            JsonNode users = objectMapper.readTree(response.body());
            if (!users.isArray() || users.isEmpty()) {
                return null;
            }
            return users.get(0).path("id").asText(null);
        } catch (IOException exception) {
            return null;
        }
    }

    private String extractCreatedUserId(HttpResponse<String> response) {
        return response.headers()
                .firstValue("Location")
                .map(location -> location.substring(location.lastIndexOf('/') + 1))
                .orElse(null);
    }

    private String getServiceAccountToken() {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "client_credentials");
        form.put("client_id", keycloakClientId);
        form.put("client_secret", keycloakClientSecret);

        HttpResponse<String> response = postToTokenEndpoint(form);
        if (response == null || response.statusCode() != 200) {
            return null;
        }

        try {
            return objectMapper.readTree(response.body()).path("access_token").asText(null);
        } catch (IOException exception) {
            return null;
        }
    }

    private HttpResponse<String> postToTokenEndpoint(Map<String, String> form) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(keycloakServerUrl + "/realms/" + keycloakRealm + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(toFormEncodedBody(form)))
                .build();
        return sendRequest(request);
    }

    private HttpResponse<String> sendJson(String method, String endpoint, String bearerToken, Object payload) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + bearerToken);

        if (payload != null) {
            try {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
            } catch (IOException exception) {
                return null;
            }
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        return sendRequest(builder.build());
    }

    private HttpResponse<String> sendRequest(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private String adminUsersEndpoint() {
        return keycloakServerUrl + "/admin/realms/" + keycloakRealm + "/users";
    }

    private String adminRolesEndpoint() {
        return keycloakServerUrl + "/admin/realms/" + keycloakRealm + "/roles";
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
}
