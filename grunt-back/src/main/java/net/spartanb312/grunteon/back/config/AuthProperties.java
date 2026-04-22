package net.spartanb312.grunteon.back.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grunteon.back.auth")
public class AuthProperties {

    private final List<User> users = new ArrayList<>(List.of(
        new User("user", "grunteon-user", List.of("USER")),
        new User("platform-admin", "grunteon-platform-admin", List.of("PLATFORM_ADMIN")),
        new User("super-admin", "grunteon-super-admin", List.of("SUPER_ADMIN"))
    ));

    public List<User> getUsers() {
        return users;
    }

    public static class User {
        private String username = "user";
        private String password = "grunteon-user";
        private List<String> roles = new ArrayList<>(List.of("USER"));

        public User() {
        }

        public User(String username, String password, List<String> roles) {
            this.username = username;
            this.password = password;
            this.roles = new ArrayList<>(roles == null ? List.of() : roles);
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles == null ? new ArrayList<>() : new ArrayList<>(roles);
        }
    }
}
