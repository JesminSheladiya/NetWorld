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
    private final UserRepository userRepository;
    private final RelationRepository relationRepository;
    private final RelationInferenceRuleRepository inferenceRuleRepo;

    public UserRelationService(UserRelationRepository userRelationRepo,
                               UserRepository userRepository,
                               RelationRepository relationRepository,
                               RelationInferenceRuleRepository inferenceRuleRepo) {
        this.userRelationRepo = userRelationRepo;
        this.userRepository = userRepository;
        this.relationRepository = relationRepository;
        this.inferenceRuleRepo = inferenceRuleRepo;
    }

    @Transactional
    public void sendRelationRequest(User fromUser, String toEmail, Long relationId) {
        User toUser = userRepository.findByEmail(toEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + toEmail));

        if (fromUser.getId().equals(toUser.getId())) {
            throw new RuntimeException("Cannot add yourself!");
        }

        if (userRelationRepo.findByFromUserAndToUser(fromUser, toUser).isPresent()) {
            throw new RuntimeException("Request already sent!");
        }

        Relation relation = relationRepository.findById(relationId)
                .orElseThrow(() -> new RuntimeException("Invalid relation!"));

        userRelationRepo.save(new UserRelation(fromUser, toUser, relation, "PENDING"));
    }

    @Transactional
    public void acceptRelation(Long id, User currentUser) {
        UserRelation ur = userRelationRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Not found!"));

        if (!ur.getToUser().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Not authorized!");
        }

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

        if (!ur.getToUser().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Not authorized!");
        }

        ur.setStatus("DECLINED");
        userRelationRepo.save(ur);
    }

    public List<UserRelationSuggestionDTO> getPendingRequests(User currentUser) {
        return userRelationRepo.findByToUserAndStatus(currentUser, "PENDING")
                .stream().map(ur -> {
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

    public List<UserRelationSuggestionDTO> getInferredSuggestions(User currentUser) {
        Map<String, String> rules = buildRulesMap();
        List<UserRelation> myAccepted = userRelationRepo.findByFromUserAndStatus(currentUser, "ACCEPTED");
        List<UserRelationSuggestionDTO> suggestions = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        for (UserRelation myRel : myAccepted) {
            User commonPerson = myRel.getToUser();
            Relation myRelation = myRel.getRelation();
            String myCat = myRelation.getRelationCategory();
            String myGender = myRelation.getGender() != null ? myRelation.getGender() : "N";

            List<UserRelation> others = userRelationRepo.findOthersRelatedToSameUser(commonPerson, currentUser);

            for (UserRelation otherRel : others) {
                User otherUser = otherRel.getFromUser();
                if (seen.contains(otherUser.getId())) continue;

                String otherCat = otherRel.getRelation().getRelationCategory();
                String otherGender = otherRel.getRelation().getGender() != null ? otherRel.getRelation().getGender() : "N";

                String inferred = rules.get(myCat + "|" + myGender + "|" + otherCat + "|" + otherGender);
                if (inferred == null) inferred = rules.get(myCat + "|" + myGender + "|" + otherCat + "|N");
                if (inferred == null) inferred = rules.get(myCat + "|N|" + otherCat + "|" + otherGender);
                if (inferred == null) inferred = rules.get(myCat + "|N|" + otherCat + "|N");
                if (inferred == null) continue;

                if (userRelationRepo.findByFromUserAndToUser(currentUser, otherUser).isPresent()) continue;

                seen.add(otherUser.getId());

                String displayName = otherUser.getFullName() != null ? otherUser.getFullName() : otherUser.getDisplayName();
                String commonName = commonPerson.getFullName() != null ? commonPerson.getFullName() : commonPerson.getDisplayName();

                suggestions.add(new UserRelationSuggestionDTO(
                        null, displayName, otherUser.getEmail(), otherUser.getProfilePicture(),
                        inferred,
                        "Both connected to " + commonName,
                        "SUGGESTED"));
            }
        }
        return suggestions;
    }

    @Transactional
    public void acceptInferredSuggestion(User currentUser, String otherEmail, String relationName) {
        User otherUser = userRepository.findByEmail(otherEmail)
                .orElseThrow(() -> new RuntimeException("User not found!"));

        Relation relation = relationRepository.findByRelationNameIgnoreCase(relationName)
                .orElseThrow(() -> new RuntimeException("Relation not found!"));

        if (userRelationRepo.findByFromUserAndToUser(currentUser, otherUser).isPresent()) return;

        userRelationRepo.save(new UserRelation(currentUser, otherUser, relation, "ACCEPTED"));

        Relation reverse = findReverseRelation(relation);
        if (reverse != null && userRelationRepo.findByFromUserAndToUser(otherUser, currentUser).isEmpty()) {
            userRelationRepo.save(new UserRelation(otherUser, currentUser, reverse, "PENDING"));
        }
    }

    private void generateAndStoreSuggestions(User me, User commonPerson) {
        List<UserRelation> others = userRelationRepo.findOthersRelatedToSameUser(commonPerson, me);
        if (others.isEmpty()) return;

        Optional<UserRelation> myRelOpt = userRelationRepo.findByFromUserAndToUser(me, commonPerson);
        if (myRelOpt.isEmpty()) return;

        Map<String, String> rules = buildRulesMap();
        UserRelation myRel = myRelOpt.get();
        String myCat = myRel.getRelation().getRelationCategory();
        String myGender = myRel.getRelation().getGender() != null ? myRel.getRelation().getGender() : "N";

        for (UserRelation otherRel : others) {
            User other = otherRel.getFromUser();
            String otherCat = otherRel.getRelation().getRelationCategory();
            String otherGen = otherRel.getRelation().getGender() != null ? otherRel.getRelation().getGender() : "N";

            String inferred = rules.get(myCat + "|" + myGender + "|" + otherCat + "|" + otherGen);
            if (inferred == null) inferred = rules.get(myCat + "|" + myGender + "|" + otherCat + "|N");
            if (inferred == null) inferred = rules.get(myCat + "|N|" + otherCat + "|" + otherGen);
            if (inferred == null) continue;

            Optional<Relation> rel = relationRepository.findByRelationNameIgnoreCase(inferred);
            if (rel.isEmpty()) continue;

            if (userRelationRepo.findByFromUserAndToUser(me, other).isPresent()) continue;
            userRelationRepo.save(new UserRelation(me, other, rel.get(), "PENDING"));

            Relation rev = findReverseRelation(rel.get());
            if (rev != null && userRelationRepo.findByFromUserAndToUser(other, me).isEmpty()) {
                userRelationRepo.save(new UserRelation(other, me, rev, "PENDING"));
            }
        }
    }

    private Map<String, String> buildRulesMap() {
        Map<String, String> map = new HashMap<>();
        inferenceRuleRepo.findAll().forEach(r ->
                map.put(r.getCategoryA() + "|" + r.getGenderA() + "|" + r.getCategoryB() + "|" + r.getGenderB(),
                        r.getInferredRelationName()));
        return map;
    }

    private Relation findReverseRelation(Relation rel) {
        if (rel == null) return null;
        Map<String, String> mirror = new HashMap<>();
        mirror.put("son", "Father");
        mirror.put("daughter", "Father");
        mirror.put("father", "Son");
        mirror.put("mother", "Son");
        mirror.put("brother", "Brother");
        mirror.put("sister", "Sister");
        mirror.put("grandfather", "Grandson");
        mirror.put("grandmother", "Grandson");
        mirror.put("grandson", "Grandfather");
        mirror.put("granddaughter", "Grandfather");
        mirror.put("husband", "Wife");
        mirror.put("wife", "Husband");
        mirror.put("uncle", "Nephew");
        mirror.put("aunt", "Nephew");
        mirror.put("nephew", "Uncle");
        mirror.put("niece", "Uncle");
        mirror.put("father-in-law", "Son-in-law");
        mirror.put("mother-in-law", "Son-in-law");
        mirror.put("son-in-law", "Father-in-law");
        mirror.put("daughter-in-law", "Father-in-law");
        mirror.put("brother-in-law", "Brother-in-law");
        mirror.put("sister-in-law", "Sister-in-law");
        mirror.put("cousin", "Cousin");

        String rev = mirror.get(rel.getRelationName().toLowerCase());
        return rev == null ? null : relationRepository.findByRelationNameIgnoreCase(rev).orElse(null);
    }
}