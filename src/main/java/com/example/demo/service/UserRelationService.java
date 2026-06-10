package com.example.demo.service;

import com.example.demo.dto.UserRelationSuggestionDTO;
import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

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
        this.userRelationRepo  = userRelationRepo;
        this.userRepository    = userRepository;
        this.relationRepository = relationRepository;
        this.inferenceRuleRepo  = inferenceRuleRepo;
    }

    @Transactional
    public UserRelation sendRelationRequest(User fromUser, String toEmail, Long relationId) {
        User toUser = userRepository.findByEmail(toEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + toEmail));

        if (fromUser.getId().equals(toUser.getId()))
            throw new RuntimeException("Cannot add yourself!");
        
        Optional<UserRelation> existing = userRelationRepo.findByFromUserAndToUser(fromUser, toUser);
        if (existing.isPresent()) {
            throw new RuntimeException("Relation request already sent!");
        }

        Relation relation = relationRepository.findById(relationId)
                .orElseThrow(() -> new RuntimeException("Invalid relation!"));

        UserRelation ur = new UserRelation(fromUser, toUser, relation, "PENDING");
        return userRelationRepo.save(ur);
    }


    @Transactional
    public void acceptRelation(Long userRelationId, User currentUser) {
        UserRelation ur = userRelationRepo.findById(userRelationId)
                .orElseThrow(() -> new RuntimeException("Relation not found!"));

        if (!ur.getToUser().getId().equals(currentUser.getId()))
            throw new RuntimeException("Not authorized!");

        ur.setStatus("ACCEPTED");
        userRelationRepo.save(ur);

        Relation reverseRelation = findReverseRelation(ur.getRelation());
        if (reverseRelation != null) {
            Optional<UserRelation> reverseExists =
                    userRelationRepo.findByFromUserAndToUser(currentUser, ur.getFromUser());
            if (reverseExists.isEmpty()) {
                UserRelation reverse = new UserRelation(
                        currentUser, ur.getFromUser(), reverseRelation, "ACCEPTED");
                userRelationRepo.save(reverse);
            }
        }

        generateAndStoreSuggestions(currentUser, ur.getFromUser());
    }


    @Transactional
    public void declineRelation(Long userRelationId, User currentUser) {
        UserRelation ur = userRelationRepo.findById(userRelationId)
                .orElseThrow(() -> new RuntimeException("Relation not found!"));

        if (!ur.getToUser().getId().equals(currentUser.getId()))
            throw new RuntimeException("Not authorized!");

        ur.setStatus("DECLINED");
        userRelationRepo.save(ur);
    }

    public List<UserRelationSuggestionDTO> getPendingRequests(User currentUser) {
        List<UserRelation> pending =
                userRelationRepo.findByToUserAndStatus(currentUser, "PENDING");

        List<UserRelationSuggestionDTO> result = new ArrayList<>();
        for (UserRelation ur : pending) {
            User sender = ur.getFromUser();
            result.add(new UserRelationSuggestionDTO(
                    ur.getId(),
                    sender.getFullName() != null ? sender.getFullName() : sender.getDisplayName(),
                    sender.getEmail(),
                    sender.getProfilePicture(),
                    ur.getRelation().getRelationName(),
                    sender.getDisplayName() + " wants to add you as their " +
                            ur.getRelation().getRelationName(),
                    "PENDING"
            ));
        }
        return result;
    }


    public List<UserRelationSuggestionDTO> getInferredSuggestions(User currentUser) {
        List<UserRelation> myAccepted =
                userRelationRepo.findByFromUserAndStatus(currentUser, "ACCEPTED");

        Map<String, String> rulesMap = buildRulesMap();
        List<UserRelationSuggestionDTO> suggestions = new ArrayList<>();
        Set<Long> seenUserIds = new HashSet<>();

        for (UserRelation myRel : myAccepted) {
            User commonPerson = myRel.getToUser();
            String myCatTowardsThem = myRel.getRelation().getRelationCategory();
            String myGender         = myRel.getRelation().getGender() != null
                    ? myRel.getRelation().getGender() : "N";
            
            List<UserRelation> othersRelatedToSame =
                    userRelationRepo.findOthersRelatedToSameUser(commonPerson, currentUser);

            for (UserRelation otherRel : othersRelatedToSame) {
                User otherUser = otherRel.getFromUser();
                if (seenUserIds.contains(otherUser.getId())) continue;
                
                String otherCat    = otherRel.getRelation().getRelationCategory();
                String otherGender = otherRel.getRelation().getGender() != null
                        ? otherRel.getRelation().getGender() : "N";
                
                String inferredName = rulesMap.get(
                        myCatTowardsThem + "|" + myGender + "|" + otherCat + "|" + otherGender);
                if (inferredName == null)
                    inferredName = rulesMap.get(
                            myCatTowardsThem + "|" + myGender + "|" + otherCat + "|N");
                if (inferredName == null)
                    inferredName = rulesMap.get(
                            myCatTowardsThem + "|N|" + otherCat + "|" + otherGender);
                if (inferredName == null) continue;
                
                Optional<UserRelation> alreadyExists =
                        userRelationRepo.findByFromUserAndToUser(currentUser, otherUser);
                if (alreadyExists.isPresent()) continue;

                seenUserIds.add(otherUser.getId());
                String displayName = otherUser.getFullName() != null
                        ? otherUser.getFullName() : otherUser.getDisplayName();
                String commonName  = commonPerson.getFullName() != null
                        ? commonPerson.getFullName() : commonPerson.getDisplayName();

                suggestions.add(new UserRelationSuggestionDTO(
                        null,
                        displayName,
                        otherUser.getEmail(),
                        otherUser.getProfilePicture(),
                        inferredName,
                        "Both of you are connected to " + commonName,
                        "SUGGESTED"
                ));
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

        Optional<UserRelation> exists =
                userRelationRepo.findByFromUserAndToUser(currentUser, otherUser);
        if (exists.isPresent()) return;
        
        userRelationRepo.save(new UserRelation(currentUser, otherUser, relation, "ACCEPTED"));
        
        Relation reverseRelation = findReverseRelation(relation);
        if (reverseRelation != null) {
            Optional<UserRelation> reverseExists =
                    userRelationRepo.findByFromUserAndToUser(otherUser, currentUser);
            if (reverseExists.isEmpty()) {
                userRelationRepo.save(
                        new UserRelation(otherUser, currentUser, reverseRelation, "PENDING"));
            }
        }
    }


    public List<UserRelationSuggestionDTO> getMyConnections(User currentUser) {
        List<UserRelation> accepted =
                userRelationRepo.findByFromUserAndStatus(currentUser, "ACCEPTED");

        List<UserRelationSuggestionDTO> result = new ArrayList<>();
        for (UserRelation ur : accepted) {
            User other = ur.getToUser();
            String displayName = other.getFullName() != null
                    ? other.getFullName() : other.getDisplayName();
            result.add(new UserRelationSuggestionDTO(
                    ur.getId(),
                    displayName,
                    other.getEmail(),
                    other.getProfilePicture(),
                    ur.getRelation().getRelationName(),
                    null,
                    "ACCEPTED"
            ));
        }
        return result;
    }

    private Map<String, String> buildRulesMap() {
        Map<String, String> map = new HashMap<>();
        inferenceRuleRepo.findAll().forEach(rule ->
                map.put(rule.getCategoryA() + "|" + rule.getGenderA()
                                + "|" + rule.getCategoryB() + "|" + rule.getGenderB(),
                        rule.getInferredRelationName())
        );
        return map;
    }


    private void generateAndStoreSuggestions(User newlyConnectedUser, User commonPerson) {
        List<UserRelation> othersRelatedToCommon =
                userRelationRepo.findOthersRelatedToSameUser(commonPerson, newlyConnectedUser);

        if (othersRelatedToCommon.isEmpty()) return;

        Map<String, String> rulesMap = buildRulesMap();
        
        Optional<UserRelation> myRelOpt =
                userRelationRepo.findByFromUserAndToUser(newlyConnectedUser, commonPerson);
        if (myRelOpt.isEmpty()) return;

        UserRelation myRel    = myRelOpt.get();
        String myCat          = myRel.getRelation().getRelationCategory();
        String myGender       = myRel.getRelation().getGender() != null
                ? myRel.getRelation().getGender() : "N";

        for (UserRelation otherRel : othersRelatedToCommon) {
            User otherUser   = otherRel.getFromUser();
            String otherCat  = otherRel.getRelation().getRelationCategory();
            String otherGender = otherRel.getRelation().getGender() != null
                    ? otherRel.getRelation().getGender() : "N";
            
            String inferredName = rulesMap.get(
                    myCat + "|" + myGender + "|" + otherCat + "|" + otherGender);
            if (inferredName == null)
                inferredName = rulesMap.get(myCat + "|" + myGender + "|" + otherCat + "|N");
            if (inferredName == null)
                inferredName = rulesMap.get(myCat + "|N|" + otherCat + "|" + otherGender);
            if (inferredName == null) continue;

            Optional<Relation> rel = relationRepository.findByRelationNameIgnoreCase(inferredName);
            if (rel.isEmpty()) continue;
            
            if (userRelationRepo.findByFromUserAndToUser(newlyConnectedUser, otherUser).isPresent())
                continue;
            
            userRelationRepo.save(
                    new UserRelation(newlyConnectedUser, otherUser, rel.get(), "PENDING"));
            
            Relation reverseRel = findReverseRelation(rel.get());
            if (reverseRel != null &&
                    userRelationRepo.findByFromUserAndToUser(otherUser, newlyConnectedUser).isEmpty()) {
                userRelationRepo.save(
                        new UserRelation(otherUser, newlyConnectedUser, reverseRel, "PENDING"));
            }
        }
    }


    private Relation findReverseRelation(Relation rel) {
        if (rel == null) return null;
        String cat    = rel.getRelationCategory();
        String gender = rel.getGender() != null ? rel.getGender() : "N";
        String name   = rel.getRelationName().toLowerCase();
        
        Map<String, String> mirror = new HashMap<>();
        mirror.put("son",              "Father");
        mirror.put("daughter",         "Father");
        mirror.put("father",           "Son");
        mirror.put("mother",           "Son");
        mirror.put("brother",          "Brother");
        mirror.put("sister",           "Sister");
        mirror.put("grandfather",      "Grandson");
        mirror.put("grandmother",      "Grandson");
        mirror.put("grandson",         "Grandfather");
        mirror.put("granddaughter",    "Grandfather");
        mirror.put("husband",          "Wife");
        mirror.put("wife",             "Husband");
        mirror.put("uncle",            "Nephew");
        mirror.put("aunt",             "Nephew");
        mirror.put("nephew",           "Uncle");
        mirror.put("niece",            "Uncle");
        mirror.put("father-in-law",    "Son-in-law");
        mirror.put("mother-in-law",    "Son-in-law");
        mirror.put("son-in-law",       "Father-in-law");
        mirror.put("daughter-in-law",  "Father-in-law");
        mirror.put("brother-in-law",   "Brother-in-law");
        mirror.put("sister-in-law",    "Sister-in-law");
        mirror.put("cousin",           "Cousin");

        String reverseName = mirror.get(name);
        if (reverseName == null) return null;

        return relationRepository.findByRelationNameIgnoreCase(reverseName).orElse(null);
    }
}