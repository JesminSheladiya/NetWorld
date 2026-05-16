package com.example.demo.dto;

public class AuthResponse {

    private String token;
    private String username;
    private String email;
    private String phone;
    private String fullName;
    private Long id;

    public AuthResponse(String token, String username, String email,
                        String phone, String fullName, Long id) {
        this.token    = token;
        this.username = username;
        this.email    = email;
        this.phone    = phone;
        this.fullName = fullName;
        this.id       = id;
    }

    public String getToken()    { return token; }
    public String getUsername() { return username; }
    public String getEmail()    { return email; }
    public String getPhone()    { return phone; }
    public String getFullName() { return fullName; }
    public Long   getId()       { return id; }
}