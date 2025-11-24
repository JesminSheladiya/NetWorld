package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtUtil;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtUtil jwt;
    private final AuthenticationManager authManager;
    private final CustomUserDetailsService uds;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtUtil jwt,
                       AuthenticationManager authManager, CustomUserDetailsService uds) {
        this.users = users; this.encoder = encoder; this.jwt = jwt;
        this.authManager = authManager; this.uds = uds;
    }

    public AuthResponse register(RegisterRequest req) {
        if (users.existsByUsername(req.getUsername())) throw new RuntimeException("Username already exists");
        if (users.existsByEmail(req.getEmail())) throw new RuntimeException("Email already exists");

        User u = new User();
        u.setUsername(req.getUsername());
        u.setPassword(encoder.encode(req.getPassword()));
        u.setEmail(req.getEmail());
        users.save(u);

        UserDetails d = uds.loadUserByUsername(u.getUsername());
        String token = jwt.generateToken(d);
        return new AuthResponse(token, u.getUsername(), u.getEmail());
    }

    public AuthResponse login(LoginRequest req) {
        authManager.authenticate(new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
        UserDetails d = uds.loadUserByUsername(req.getUsername());
        User u = users.findByUsername(req.getUsername()).orElseThrow();
        String token = jwt.generateToken(d);
        return new AuthResponse(token, u.getUsername(), u.getEmail());
    }
}
