package com.example.demo.service;

import com.example.demo.dto.UserRelationSuggestionDTO;
import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserRelationService {

    private final UserRelationRepository userRelationRepo;
    private final UserRepository         userRepository;
    private final RelationRepository     relationRepository;
    private final RelationshipResolver   resolver;

    public UserRelationService(UserRelationRepository userRelationRepo,
                               UserRepository userRepository,
                               RelationRepository relationRepository,
                               RelationshipResolver resolver) {
        this.userRelationRepo   = userRelationRepo;
        this.userRepository     = userRepository;
        this.relationRepository = relationRepository;
        this.resolver           = resolver;
    }

    // User manually sends a relation request
    @Transactional
    public void sendRelationRequest(User fromUser, String toEmail, Long relationId) {
        User toUser = userRepository.findByEmail(toEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + toEmail));

        if (fromUser.getId().equals(toUser.getId()))
            throw new RuntimeException("Cannot add yourself!");

        if (userRelationRepo.findByFromUserAndToUser(fromUser, toUser).isPresent())
            throw new RuntimeException("Request already sent!");

        Relation relation = relationRepository.findById(relationId)
                .orElseThrow(() -> new RuntimeException("Invalid relation!"));

        userRelationRepo.save(new UserRelation(fromUser, toUser, relation, "PENDING"));
    }

    // Accept a manually-sent or suggestion-based PENDING request
    @Transactional
    public void acceptRelation(Long id, User currentUser) {
        UserRelation ur = userRelationRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Not found!"));

        if (!ur.getToUser().getId().equals(currentUser.getId()))
            throw new RuntimeException("Not authorized!");

        ur.setStatus("ACCEPTED");
        userRelationRepo.save(ur);

        Relation reverse = findReverseRelation(ur.getRelation());
        if (reverse != null && userRelationRepo.findByFromUserAndToUser(currentUser, ur.getFromUser()).isEmpty()) {
            userRelationRepo.save(new UserRelation(currentUser, ur.getFromUser(), reverse, "ACCEPTED"));
        }

        regenerateAllSuggestions(currentUser);
        regenerateAllSuggestions(ur.getFromUser());
    }

    @Transactional
    public void declineRelation(Long id, User currentUser) {
        UserRelation ur = userRelationRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Not found!"));

        if (!ur.getToUser().getId().equals(currentUser.getId()))
            throw new RuntimeException("Not authorized!");

        ur.setStatus("DECLINED");
        userRelationRepo.save(ur);
    }

    public List<UserRelationSuggestionDTO> getPendingRequests(User currentUser) {
        return userRelationRepo.findByToUserAndStatus(currentUser, "PENDING")
                .stream()
                .map(ur -> {
                    User s = ur.getFromUser();
                    String name = s.getFullName() != null ? s.getFullName() : s.getDisplayName();
                    return new UserRelationSuggestionDTO(
                            ur.getId(), name, s.getEmail(), s.getPhone(), s.getProfilePicture(),
                            ur.getRelation().getRelationName(),
                            name + " wants to add you as their " + ur.getRelation().getRelationName(),
                            "PENDING");
                }).collect(Collectors.toList());
    }

    public List<UserRelationSuggestionDTO> getMyConnections(User currentUser) {
        return userRelationRepo.findByFromUserAndStatus(currentUser, "ACCEPTED")
                .stream().map(ur -> {
                    User o = ur.getToUser();
                    String name = o.getFullName() != null ? o.getFullName() : o.getDisplayName();
                    return new UserRelationSuggestionDTO(
                            ur.getId(), name, o.getEmail(), o.getPhone(), o.getProfilePicture(),
                            ur.getRelation().getRelationName(), null, "ACCEPTED");
                }).collect(Collectors.toList());
    }

    @Transactional
    public List<UserRelationSuggestionDTO> getInferredSuggestions(User currentUser) {
        regenerateAllSuggestions(currentUser);

        return userRelationRepo.findByFromUserAndStatus(currentUser, "SUGGESTED")
                .stream().map(ur -> {
                    User o = ur.getToUser();
                    String name = o.getFullName() != null ? o.getFullName() : o.getDisplayName();
                    return new UserRelationSuggestionDTO(
                            ur.getId(), name, o.getEmail(), o.getPhone(), o.getProfilePicture(),
                            ur.getRelation().getRelationName(),
                            "Discovered through your network connections",
                            "SUGGESTED");
                }).collect(Collectors.toList());
    }

    // Send a request based on a system suggestion → PENDING (not auto-accepted)
    @Transactional
    public void sendInferredSuggestionRequest(User currentUser, String otherEmail, String relationName) {
        User otherUser = userRepository.findByEmail(otherEmail)
                .orElseThrow(() -> new RuntimeException("User not found!"));

        Optional<UserRelation> existing = userRelationRepo.findByFromUserAndToUser(currentUser, otherUser);
        if (existing.isPresent()) {
            existing.get().setStatus("PENDING");
            userRelationRepo.save(existing.get());
        } else {
            Relation relation = relationRepository.findByRelationNameIgnoreCase(relationName)
                    .orElseThrow(() -> new RuntimeException("Relation not found!"));
            userRelationRepo.save(new UserRelation(currentUser, otherUser, relation, "PENDING"));
        }
    }

    @Transactional
    public void dismissSuggestion(Long id, User currentUser) {
        userRelationRepo.findById(id).ifPresent(ur -> {
            if (ur.getFromUser().getId().equals(currentUser.getId())) {
                ur.setStatus("DISMISSED");
                userRelationRepo.save(ur);
            }
        });
    }

    // Rebuild every SUGGESTED entry for 'me' from the full accepted-relations graph
    @Transactional
    public void regenerateAllSuggestions(User me) {
        List<UserRelation> old = userRelationRepo.findByFromUserAndStatus(me, "SUGGESTED");
        old.addAll(userRelationRepo.findByToUserAndStatus(me, "SUGGESTED"));
        old.forEach(userRelationRepo::delete);

        List<UserRelation> accepted = userRelationRepo.findByStatus("ACCEPTED");
        RelationshipResolver.Graph graph = resolver.buildGraph(accepted);

        if (!graph.users.containsKey(me.getId())) return;

        for (Long otherId : graph.users.keySet()) {
            if (otherId.equals(me.getId())) continue;
            User other = graph.users.get(otherId);

            Optional<UserRelation> existing = userRelationRepo.findByFromUserAndToUser(me, other);
            if (existing.isPresent() && !"SUGGESTED".equals(existing.get().getStatus())) continue;

            String otherToMe = resolver.resolve(graph, me.getId(), otherId);   // other is X to me
            String meToOther  = resolver.resolve(graph, otherId, me.getId());  // me is Y to other
            if (otherToMe == null || meToOther == null) continue;

            Optional<Relation> rel1 = relationRepository.findByRelationNameIgnoreCase(otherToMe);
            Optional<Relation> rel2 = relationRepository.findByRelationNameIgnoreCase(meToOther);
            if (rel1.isEmpty() || rel2.isEmpty()) continue;

            userRelationRepo.save(new UserRelation(me, other, rel1.get(), "SUGGESTED"));
            Optional<UserRelation> revExisting = userRelationRepo.findByFromUserAndToUser(other, me);
            if (revExisting.isEmpty()) {
                userRelationRepo.save(new UserRelation(other, me, rel2.get(), "SUGGESTED"));
            }
        }
    }

    // Direct 1-hop mirror — only used right after a manual request is accepted
    private Relation findReverseRelation(Relation rel) {
        if (rel == null) return null;
        Map<String, String> mirror = new HashMap<>();
        mirror.put("son",             "Father");
        mirror.put("daughter",        "Father");
        mirror.put("father",          "Son");
        mirror.put("mother",          "Son");
        mirror.put("brother",         "Brother");
        mirror.put("sister",          "Sister");
        mirror.put("grandfather",     "Grandson");
        mirror.put("grandmother",     "Grandson");
        mirror.put("grandson",        "Grandfather");
        mirror.put("granddaughter",   "Grandfather");
        mirror.put("husband",         "Wife");
        mirror.put("wife",            "Husband");
        mirror.put("uncle",           "Nephew");
        mirror.put("aunt",            "Nephew");
        mirror.put("nephew",          "Uncle");
        mirror.put("niece",           "Uncle");
        mirror.put("father-in-law",   "Son-in-law");
        mirror.put("mother-in-law",   "Son-in-law");
        mirror.put("son-in-law",      "Father-in-law");
        mirror.put("daughter-in-law", "Father-in-law");
        mirror.put("brother-in-law",  "Brother-in-law");
        mirror.put("sister-in-law",   "Sister-in-law");
        mirror.put("cousin",          "Cousin");
        mirror.put("cousin brother",  "Cousin");
        mirror.put("cousin sister",   "Cousin");
        String rev = mirror.get(rel.getRelationName().toLowerCase());
        return rev == null ? null : relationRepository.findByRelationNameIgnoreCase(rev).orElse(null);
    }
}