package com.example.demo.dto;

import jakarta.validation.constraints.*;

public class RegisterRequest {
    @NotBlank @Size(min=3, max=20) private String username;
    @NotBlank @Size(min=6) private String password;
    @NotBlank @Email private String email;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
