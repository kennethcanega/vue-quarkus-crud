package com.example.users;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends PanacheEntity {
    @Column(nullable = false)
    public String name;

    @Column(nullable = false, unique = true)
    public String email;

    @Column(unique = true, nullable = false)
    public String username;

    @Column(name = "keycloak_user_id", unique = true)
    public String keycloakUserId;

    @Column
    public String role;

    @Column
    public Boolean active = true;

    public static User findByUsername(String username) {
        return find("username", username).firstResult();
    }
}
