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
            admin.passwordHash = PasswordUtils.hash("admin");
            admin.role = "admin";
            admin.active = true;
            admin.persist();
        } else {
            if (admin.name == null || admin.name.isBlank()) admin.name = "Administrator";
            if (admin.email == null || admin.email.isBlank()) admin.email = "admin@example.com";
            admin.role = "admin";
            admin.active = true;
            admin.passwordHash = PasswordUtils.hash("admin");
        }

        java.util.List<User> users = User.listAll();
        users.forEach(this::backfillUser);
    }

    private void backfillUser(User user) {
        boolean updated = false;

        if (user.username == null || user.username.isBlank()) {
            String base = deriveUsername(user);
            user.username = makeUsernameUnique(base, user.id);
            updated = true;
        }

        if (user.passwordHash == null || user.passwordHash.isBlank()) {
            user.passwordHash = PasswordUtils.hash("changeme");
            updated = true;
        }

        if (user.role == null || user.role.isBlank()) {
            user.role = "user";
            updated = true;
        }

        if (user.active == null) {
            user.active = true;
            updated = true;
        }

        // No need to call persist/merge; listAll() returns managed entities in the same transaction.
        if (!updated) return;
    }

    private String deriveUsername(User user) {
        if (user.email != null && user.email.contains("@")) {
            return user.email.substring(0, user.email.indexOf('@'));
        }
        if (user.name != null && !user.name.isBlank()) {
            return user.name.toLowerCase().replaceAll("\\s+", ".") + user.id;
        }
        return "user" + user.id;
    }

    private String makeUsernameUnique(String base, Long currentUserId) {
        String cleaned = sanitize(base);
        if (cleaned.isBlank()) cleaned = "user";

        // if it's free, use it
        if (!usernameTakenByAnotherUser(cleaned, currentUserId)) return cleaned;

        // otherwise, add a suffix until it's free: test, test2, test3, ...
        int i = 2;
        while (true) {
            String candidate = cleaned + i;
            if (!usernameTakenByAnotherUser(candidate, currentUserId)) return candidate;
            i++;
        }
    }

    private boolean usernameTakenByAnotherUser(String username, Long currentUserId) {
        // count rows where username matches AND id is different
        Long count = User.count("username = ?1 and id <> ?2", username, currentUserId);
        return count != null && count > 0;
    }

    private String sanitize(String username) {
        // keep it simple and URL-ish; adjust rules if you want
        return username
                .trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9._-]", "");
    }
}
