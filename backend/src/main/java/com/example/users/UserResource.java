package com.example.users;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class UserResource {

    private final SecurityIdentity identity;
    private final JsonWebToken jsonWebToken;
    private final KeycloakAdminService keycloakAdminService;

    public UserResource(SecurityIdentity identity, JsonWebToken jsonWebToken, KeycloakAdminService keycloakAdminService) {
        this.identity = identity;
        this.jsonWebToken = jsonWebToken;
        this.keycloakAdminService = keycloakAdminService;
    }

    @GET
    public List<UserResponse> list() {
        assertAdmin();
        List<User> users = User.listAll();
        return users.stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/search")
    public List<UserSummary> search(@QueryParam("q") String query) {
        requireCurrentUser();
        String term = Optional.ofNullable(query).orElse("").trim();
        if (term.isBlank()) {
            return List.of();
        }
        List<User> users = User.find("lower(name) like ?1 or lower(email) like ?1", "%" + term.toLowerCase() + "%")
                .list();
        return users.stream()
                .map(user -> new UserSummary(user.id, user.name, user.email))
                .collect(Collectors.toList());
    }

    @GET
    @Path("/me")
    public UserResponse profile() {
        return UserResponse.from(requireCurrentUser());
    }

    @POST
    @Transactional
    public Response create(CreateUserRequest request) {
        assertAdmin();
        if (request == null
                || isBlank(request.username())
                || isBlank(request.password())
                || isBlank(request.name())
                || isBlank(request.email())) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        if (User.findByUsername(request.username()) != null) {
            return Response.status(Response.Status.CONFLICT).build();
        }

        String role = Optional.ofNullable(request.role()).orElse("user");
        Boolean active = Optional.ofNullable(request.active()).orElse(true);

        String keycloakUserId = keycloakAdminService.createUser(new KeycloakAdminService.CreateUserCommand(
                request.username(),
                request.email(),
                request.name(),
                request.password(),
                role,
                active));

        if (keycloakUserId == null) {
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity("Failed to create user in Keycloak")
                    .build();
        }

        User user = new User();
        user.name = request.name();
        user.email = request.email();
        user.username = request.username();
        user.keycloakUserId = keycloakUserId;
        user.role = role;
        user.active = active;
        user.persist();
        return Response.status(Response.Status.CREATED).entity(UserResponse.from(user)).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") Long id, UpdateUserRequest updated) {
        assertAdmin();
        User user = User.findById(id);
        if (user == null) {
            throw new NotFoundException();
        }

        String targetName = Optional.ofNullable(updated.name()).orElse(user.name);
        String targetEmail = Optional.ofNullable(updated.email()).orElse(user.email);
        String targetUsername = Optional.ofNullable(updated.username()).orElse(user.username);
        String targetRole = Optional.ofNullable(updated.role()).orElse(user.role);
        Boolean targetActive = Optional.ofNullable(updated.active()).orElse(user.active);

        boolean synced = keycloakAdminService.updateUser(
                user.keycloakUserId,
                new KeycloakAdminService.UpdateUserCommand(
                        targetUsername,
                        targetEmail,
                        targetName,
                        updated.password(),
                        targetRole,
                        targetActive));

        if (!synced) {
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity("Failed to update user in Keycloak")
                    .build();
        }

        user.name = targetName;
        user.email = targetEmail;
        user.username = targetUsername;
        user.role = targetRole;
        user.active = targetActive;

        return Response.ok(UserResponse.from(user)).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        assertAdmin();
        User user = User.findById(id);
        if (user == null) {
            throw new NotFoundException();
        }

        boolean keycloakDeleted = keycloakAdminService.deleteUser(user.keycloakUserId);
        if (!keycloakDeleted) {
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity("Failed to delete user in Keycloak")
                    .build();
        }

        user.delete();
        return Response.noContent().build();
    }

    private User requireCurrentUser() {
        String preferredUsername = Optional.ofNullable(jsonWebToken.getClaim("preferred_username"))
                .map(Object::toString)
                .orElse("")
                .trim();
        String subject = Optional.ofNullable(jsonWebToken.getClaim("sub"))
                .map(Object::toString)
                .orElse("")
                .trim();

        User user = null;
        if (!preferredUsername.isBlank()) {
            user = User.findByUsername(preferredUsername);
        }
        if (user == null && !subject.isBlank()) {
            user = User.findByKeycloakUserId(subject);
        }
        if (user == null) {
            String principalName = Optional.ofNullable(identity.getPrincipal())
                    .map(principal -> principal.getName())
                    .orElse("")
                    .trim();
            if (!principalName.isBlank()) {
                user = User.findByUsername(principalName);
            }
        }

        if (user == null || !Boolean.TRUE.equals(user.active)) {
            throw new ForbiddenException();
        }
        return user;
    }

    private void assertAdmin() {
        User currentUser = requireCurrentUser();
        if (!"admin".equalsIgnoreCase(Optional.ofNullable(currentUser.role).orElse(""))) {
            throw new ForbiddenException();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
