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
    private final RelationInferenceRuleRepository inferenceRuleRepo;

    public UserRelationService(UserRelationRepository userRelationRepo,
                               UserRepository userRepository,
                               RelationRepository relationRepository,
                               RelationInferenceRuleRepository inferenceRuleRepo) {
        this.userRelationRepo   = userRelationRepo;
        this.userRepository     = userRepository;
        this.relationRepository = relationRepository;
        this.inferenceRuleRepo  = inferenceRuleRepo;
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

    // Accept a manually-sent PENDING request
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

        generateAndStoreSuggestions(currentUser, ur.getFromUser());
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

    // Only manually-sent requests (PENDING) — not system-generated suggestions
    public List<UserRelationSuggestionDTO> getPendingRequests(User currentUser) {
        return userRelationRepo.findByToUserAndStatus(currentUser, "PENDING")
                .stream()
                .filter(ur -> {
                    // Only show if the fromUser actually sent it (not system-generated)
                    // System suggestions are stored with status SUGGESTED
                    return true;
                })
                .map(ur -> {
                    User s = ur.getFromUser();
                    String name = s.getFullName() != null ? s.getFullName() : s.getDisplayName();
                    return new UserRelationSuggestionDTO(
                            ur.getId(), name, s.getEmail(), s.getProfilePicture(),
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
                            ur.getId(), name, o.getEmail(), o.getProfilePicture(),
                            ur.getRelation().getRelationName(), null, "ACCEPTED");
                }).collect(Collectors.toList());
    }

    // Returns system-inferred suggestions stored with status SUGGESTED
    public List<UserRelationSuggestionDTO> getInferredSuggestions(User currentUser) {
        return userRelationRepo.findByFromUserAndStatus(currentUser, "SUGGESTED")
                .stream().map(ur -> {
                    User o = ur.getToUser();
                    String name = o.getFullName() != null ? o.getFullName() : o.getDisplayName();

                    // Find who the common connection is
                    String reason = buildReason(currentUser, o);

                    return new UserRelationSuggestionDTO(
                            ur.getId(), name, o.getEmail(), o.getProfilePicture(),
                            ur.getRelation().getRelationName(),
                            reason,
                            "SUGGESTED");
                }).collect(Collectors.toList());
    }

    // Accept a system suggestion → save as ACCEPTED
    @Transactional
    public void acceptInferredSuggestion(User currentUser, String otherEmail, String relationName) {
        User otherUser = userRepository.findByEmail(otherEmail)
                .orElseThrow(() -> new RuntimeException("User not found!"));

        // Update existing SUGGESTED record to ACCEPTED
        Optional<UserRelation> existing = userRelationRepo.findByFromUserAndToUser(currentUser, otherUser);
        if (existing.isPresent()) {
            existing.get().setStatus("ACCEPTED");
            userRelationRepo.save(existing.get());
        } else {
            Relation relation = relationRepository.findByRelationNameIgnoreCase(relationName)
                    .orElseThrow(() -> new RuntimeException("Relation not found!"));
            userRelationRepo.save(new UserRelation(currentUser, otherUser, relation, "ACCEPTED"));
        }

        // Update reverse too
        Optional<UserRelation> reverseExisting = userRelationRepo.findByFromUserAndToUser(otherUser, currentUser);
        if (reverseExisting.isPresent() && "SUGGESTED".equals(reverseExisting.get().getStatus())) {
            reverseExisting.get().setStatus("PENDING");
            userRelationRepo.save(reverseExisting.get());
        }
    }

    // Dismiss a suggestion → mark DISMISSED so it won't show again
    @Transactional
    public void dismissSuggestion(Long id, User currentUser) {
        userRelationRepo.findById(id).ifPresent(ur -> {
            if (ur.getFromUser().getId().equals(currentUser.getId())) {
                ur.setStatus("DISMISSED");
                userRelationRepo.save(ur);
            }
        });
    }

    // Auto-generate SUGGESTED entries after a new connection is accepted
    private void generateAndStoreSuggestions(User me, User commonPerson) {
        List<UserRelation> others = userRelationRepo.findOthersRelatedToSameUser(commonPerson, me);
        if (others.isEmpty()) return;

        Optional<UserRelation> myRelOpt = userRelationRepo.findByFromUserAndToUser(me, commonPerson);
        if (myRelOpt.isEmpty()) return;

        Map<String, String> rules = buildRulesMap();
        UserRelation myRel  = myRelOpt.get();
        String myCat        = myRel.getRelation().getRelationCategory();
        String myGender     = myRel.getRelation().getGender() != null ? myRel.getRelation().getGender() : "N";

        for (UserRelation otherRel : others) {
            User other      = otherRel.getFromUser();
            String otherCat = otherRel.getRelation().getRelationCategory();
            String otherGen = otherRel.getRelation().getGender() != null ? otherRel.getRelation().getGender() : "N";

            String inferred = rules.get(myCat + "|" + myGender + "|" + otherCat + "|" + otherGen);
            if (inferred == null) inferred = rules.get(myCat + "|" + myGender + "|" + otherCat + "|N");
            if (inferred == null) inferred = rules.get(myCat + "|N|" + otherCat + "|" + otherGen);
            if (inferred == null) inferred = rules.get(myCat + "|N|" + otherCat + "|N");
            if (inferred == null) continue;

            Optional<Relation> rel = relationRepository.findByRelationNameIgnoreCase(inferred);
            if (rel.isEmpty()) continue;

            // Skip if any relation already exists between me and other (including DISMISSED)
            Optional<UserRelation> existing = userRelationRepo.findByFromUserAndToUser(me, other);
            if (existing.isPresent()) continue;

            userRelationRepo.save(new UserRelation(me, other, rel.get(), "SUGGESTED"));

            // Store reverse as SUGGESTED for other user too
            Relation rev = findReverseRelation(rel.get());
            if (rev != null && userRelationRepo.findByFromUserAndToUser(other, me).isEmpty()) {
                userRelationRepo.save(new UserRelation(other, me, rev, "SUGGESTED"));
            }
        }
    }

    private String buildReason(User me, User other) {
        List<UserRelation> myConnections = userRelationRepo.findByFromUserAndStatus(me, "ACCEPTED");
        for (UserRelation myRel : myConnections) {
            User common = myRel.getToUser();
            Optional<UserRelation> otherRel = userRelationRepo.findByFromUserAndToUser(other, common);
            if (otherRel.isPresent() && "ACCEPTED".equals(otherRel.get().getStatus())) {
                String commonName = common.getFullName() != null ? common.getFullName() : common.getDisplayName();
                return "Both connected to " + commonName;
            }
        }
        return "People you may know";
    }

    private Map<String, String> buildRulesMap() {
        Map<String, String> map = new HashMap<>();
        inferenceRuleRepo.findAll().forEach(r ->
                map.put(r.getCategoryA() + "|" + r.getGenderA()
                                + "|" + r.getCategoryB() + "|" + r.getGenderB(),
                        r.getInferredRelationName()));
        return map;
    }

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
        String rev = mirror.get(rel.getRelationName().toLowerCase());
        return rev == null ? null : relationRepository.findByRelationNameIgnoreCase(rev).orElse(null);
    }
}