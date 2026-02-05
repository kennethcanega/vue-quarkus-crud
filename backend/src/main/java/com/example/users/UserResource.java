package com.example.users;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    private final SecurityIdentity identity;

    public UserResource(SecurityIdentity identity) {
        this.identity = identity;
    }

    @GET
    @RolesAllowed("admin")
    public List<UserResponse> list() {
        return User.findAll()
                .list()
                .stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/search")
    @RolesAllowed({"admin", "user"})
    public List<UserSummary> search(@QueryParam("q") String query) {
        String term = Optional.ofNullable(query).orElse("").trim();
        if (term.isBlank()) {
            return List.of();
        }
        return User.find("lower(name) like ?1 or lower(email) like ?1", "%" + term.toLowerCase() + "%")
                .list()
                .stream()
                .map(user -> new UserSummary(user.id, user.name, user.email))
                .collect(Collectors.toList());
    }

    @GET
    @Path("/me")
    @RolesAllowed({"admin", "user"})
    public UserResponse profile() {
        User user = User.findByUsername(identity.getPrincipal().getName());
        if (user == null) {
            throw new NotFoundException();
        }
        return UserResponse.from(user);
    }

    @POST
    @RolesAllowed("admin")
    @Transactional
    public Response create(CreateUserRequest request) {
        if (request == null
                || request.username() == null
                || request.password() == null
                || request.name() == null
                || request.email() == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        User existing = User.findByUsername(request.username());
        if (existing != null) {
            return Response.status(Response.Status.CONFLICT).build();
        }
        User user = new User();
        user.name = request.name();
        user.email = request.email();
        user.username = request.username();
        user.passwordHash = PasswordUtils.hash(request.password());
        user.role = Optional.ofNullable(request.role()).orElse("user");
        user.active = Optional.ofNullable(request.active()).orElse(true);
        user.persist();
        return Response.status(Response.Status.CREATED).entity(UserResponse.from(user)).build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed("admin")
    @Transactional
    public UserResponse update(@PathParam("id") Long id, UpdateUserRequest updated) {
        User user = User.findById(id);
        if (user == null) {
            throw new NotFoundException();
        }
        if (updated.name() != null) {
            user.name = updated.name();
        }
        if (updated.email() != null) {
            user.email = updated.email();
        }
        if (updated.username() != null) {
            user.username = updated.username();
        }
        if (updated.password() != null && !updated.password().isBlank()) {
            user.passwordHash = PasswordUtils.hash(updated.password());
        }
        if (updated.role() != null) {
            user.role = updated.role();
        }
        if (updated.active() != null) {
            user.active = updated.active();
        }
        return UserResponse.from(user);
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("admin")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        boolean deleted = User.deleteById(id);
        if (!deleted) {
            throw new NotFoundException();
        }
        return Response.noContent().build();
    }
}
