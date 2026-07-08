package com.example.demo.controller;

import com.example.demo.dto.UserRelationSuggestionDTO;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.UserRelationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/user-relations")
public class UserRelationController {

    private final UserRelationService userRelationService;
    private final UserRepository      userRepository;

    public UserRelationController(UserRelationService userRelationService,
                                  UserRepository userRepository) {
        this.userRelationService = userRelationService;
        this.userRepository      = userRepository;
    }

    private User getCurrentUser(UserDetails ud) {
        return userRepository.findByEmail(ud.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @GetMapping("/search-users")
    public ResponseEntity<List<Map<String, Object>>> searchUsers(
            @RequestParam String query,
            @AuthenticationPrincipal UserDetails ud) {
        User me = getCurrentUser(ud);
        List<User> users = userRepository.searchUsers(query, me.getId());
        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> item = new HashMap<>();
            item.put("id",         u.getId());
            item.put("name",       u.getFullName() != null ? u.getFullName() : u.getDisplayName());
            item.put("email",      u.getEmail());
            item.put("profilePic", u.getProfilePicture() != null ? u.getProfilePicture() : "");
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendRequest(
            @AuthenticationPrincipal UserDetails ud,
            @RequestBody Map<String, Object> body) {
        User me = getCurrentUser(ud);
        String toEmail    = (String) body.get("toEmail");
        Long   relationId = Long.valueOf(body.get("relationId").toString());
        userRelationService.sendRelationRequest(me, toEmail, relationId);
        Map<String, String> resp = new HashMap<>();
        resp.put("message", "Request sent!");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<?> accept(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails ud) {
        userRelationService.acceptRelation(id, getCurrentUser(ud));
        Map<String, String> resp = new HashMap<>();
        resp.put("message", "Accepted!");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{id}/decline")
    public ResponseEntity<?> decline(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails ud) {
        userRelationService.declineRelation(id, getCurrentUser(ud));
        Map<String, String> resp = new HashMap<>();
        resp.put("message", "Declined!");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/pending")
    public ResponseEntity<List<UserRelationSuggestionDTO>> getPending(
            @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(userRelationService.getPendingRequests(getCurrentUser(ud)));
    }

    // Returns system-inferred SUGGESTED entries
    @GetMapping("/suggestions")
    public ResponseEntity<List<UserRelationSuggestionDTO>> getSuggestions(
            @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(userRelationService.getInferredSuggestions(getCurrentUser(ud)));
    }

    @GetMapping("/connections")
    public ResponseEntity<List<UserRelationSuggestionDTO>> getConnections(
            @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(userRelationService.getMyConnections(getCurrentUser(ud)));
    }

    @PostMapping("/suggestions/accept")
    public ResponseEntity<?> acceptSuggestion(
            @AuthenticationPrincipal UserDetails ud,
            @RequestBody Map<String, String> body) {
        userRelationService.acceptInferredSuggestion(
                getCurrentUser(ud),
                body.get("otherEmail"),
                body.get("relationName"));
        Map<String, String> resp = new HashMap<>();
        resp.put("message", "Accepted!");
        return ResponseEntity.ok(resp);
    }

    @DeleteMapping("/suggestions/{id}/dismiss")
    public ResponseEntity<?> dismissSuggestion(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails ud) {
        userRelationService.dismissSuggestion(id, getCurrentUser(ud));
        Map<String, String> resp = new HashMap<>();
        resp.put("message", "Dismissed!");
        return ResponseEntity.ok(resp);
    }
}