package com.example.users;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserSeeder {

    @Transactional
    void seed(@Observes StartupEvent event) {
        if (User.count() > 0) {
            return;
        }
        User admin = new User();
        admin.name = "Administrator";
        admin.email = "admin@example.com";
        admin.username = "admin";
        admin.passwordHash = PasswordUtils.hash("admin");
        admin.role = "admin";
        admin.active = true;
        admin.persist();
    }
}
