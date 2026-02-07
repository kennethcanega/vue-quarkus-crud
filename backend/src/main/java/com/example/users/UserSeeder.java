package com.example.users;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserSeeder {

    @Transactional
    void seed(@Observes StartupEvent event) {
        User admin = User.findByUsername("admin");
        if (admin == null) {
            admin = new User();
            admin.name = "Administrator";
            admin.email = "admin@example.com";
            admin.username = "admin";
            admin.role = "admin";
            admin.active = true;
            admin.persist();
            return;
        }

        if (admin.name == null || admin.name.isBlank()) admin.name = "Administrator";
        if (admin.email == null || admin.email.isBlank()) admin.email = "admin@example.com";
        if (admin.role == null || admin.role.isBlank()) admin.role = "admin";
        if (admin.active == null) admin.active = true;
    }
}
