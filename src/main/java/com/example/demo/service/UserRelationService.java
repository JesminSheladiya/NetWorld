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
    @Transactional
    public List<UserRelationSuggestionDTO> getInferredSuggestions(User currentUser) {
        // First regenerate all suggestions from current ACCEPTED relations
        regenerateAllSuggestions(currentUser);

        return userRelationRepo.findByFromUserAndStatus(currentUser, "SUGGESTED")
                .stream().map(ur -> {
                    User o = ur.getToUser();
                    String name = o.getFullName() != null ? o.getFullName() : o.getDisplayName();

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

    // Regenerate ALL suggestions for a user from scratch (cleans stale/wrong entries)
    private void regenerateAllSuggestions(User me) {
        // Delete all existing SUGGESTED records for this user (both directions)
        List<UserRelation> oldSuggestions = userRelationRepo.findByFromUserAndStatus(me, "SUGGESTED");
        oldSuggestions.addAll(userRelationRepo.findByToUserAndStatus(me, "SUGGESTED"));
        for (UserRelation old : oldSuggestions) {
            userRelationRepo.delete(old);
        }

        // Regenerate from every ACCEPTED connection
        List<UserRelation> accepted = userRelationRepo.findByFromUserAndStatus(me, "ACCEPTED");
        for (UserRelation rel : accepted) {
            generateAndStoreSuggestions(me, rel.getToUser());
        }
    }

    // Auto-generate SUGGESTED entries after a new connection is accepted
    private void generateAndStoreSuggestions(User me, User commonPerson) {
        Map<String, String> rules = buildRulesMap();

        Optional<UserRelation> myRelOpt = userRelationRepo.findByFromUserAndToUser(me, commonPerson);
        if (myRelOpt.isEmpty()) return;

        UserRelation myRel   = myRelOpt.get();
        Relation revOfMyRel  = findReverseRelation(myRel.getRelation());
        if (revOfMyRel == null) return;
        String myCatRev      = revOfMyRel.getRelationCategory();
        String myGenderRev   = revOfMyRel.getGender() != null ? revOfMyRel.getGender() : "N";

        // ── Part 1: find others connected to commonPerson ──
        // Rule format: what I AM TO commonPerson + what other IS TO commonPerson → what I AM TO other
        // Two cases from the query:
        //   A) other→commonPerson: category describes commonPerson → need REVERSE for "what other IS TO commonPerson"
        //   B) commonPerson→other: category describes other directly → use as-is for "what other IS TO commonPerson"
        List<UserRelation> othersViaCommon = userRelationRepo.findOthersRelatedToSameUser(commonPerson, me);
        for (UserRelation otherRel : othersViaCommon) {
            boolean isOtherToCommon = otherRel.getToUser().equals(commonPerson);
            User other = isOtherToCommon ? otherRel.getFromUser() : otherRel.getToUser();

            String otherCatRev;
            String otherGenRev;
            if (isOtherToCommon) {
                // Case A: other→commonPerson e.g. "commonPerson IS Son TO other"
                // Reverse → "other IS Father TO commonPerson"
                Relation revOther = findReverseRelation(otherRel.getRelation());
                if (revOther == null) continue;
                otherCatRev = revOther.getRelationCategory();
                otherGenRev = revOther.getGender() != null ? revOther.getGender() : "N";
            } else {
                // Case B: commonPerson→other e.g. "other IS Father TO commonPerson" → already correct
                otherCatRev = otherRel.getRelation().getRelationCategory();
                otherGenRev = otherRel.getRelation().getGender() != null ? otherRel.getRelation().getGender() : "N";
            }

            String inferred = resolveRule(rules, myCatRev, myGenderRev, otherCatRev, otherGenRev);
            if (inferred == null) continue;

            Optional<Relation> rel = relationRepository.findByRelationNameIgnoreCase(inferred);
            if (rel.isEmpty()) continue;

            // If existing ACCEPTED/PENDING relation exists → skip (already connected/requested)
            // If existing SUGGESTED → delete it so we can replace with the correct one
            Optional<UserRelation> existingBetween = userRelationRepo.findByFromUserAndToUser(me, other);
            if (existingBetween.isPresent()) {
                if (!"SUGGESTED".equals(existingBetween.get().getStatus())) continue;
                userRelationRepo.delete(existingBetween.get());
            }
            Optional<UserRelation> revExistingBetween = userRelationRepo.findByFromUserAndToUser(other, me);
            if (revExistingBetween.isPresent() && "SUGGESTED".equals(revExistingBetween.get().getStatus())) {
                userRelationRepo.delete(revExistingBetween.get());
            }

            // The inferred name is in human-readable form "A is X of B".
            // But UserRelation stores: A→B = X means "B is X to A" (opposite direction).
            // So we must store the REVERSE: if "A is Son of B" → store A→B = Father (B is Father to A)
            Relation storeRel = findReverseRelation(rel.get());
            if (storeRel == null) continue;

            userRelationRepo.save(new UserRelation(me, other, storeRel, "SUGGESTED"));
            Relation rev = findReverseRelation(storeRel);
            if (rev != null && userRelationRepo.findByFromUserAndToUser(other, me).isEmpty()) {
                userRelationRepo.save(new UserRelation(other, me, rev, "SUGGESTED"));
            }
        }

        // ── Part 2: also find others connected to me (bidirectional) ──
        // Rule format: what commonPerson IS TO me + what connection IS TO me → what commonPerson IS TO connection
        // Two cases from the query:
        //   A) connection→me: category describes me → need REVERSE for "what connection IS TO me"
        //   B) me→connection: category describes connection directly → use as-is for "what connection IS TO me"
        String myCatOrig    = myRel.getRelation().getRelationCategory();
        String myGenderOrig = myRel.getRelation().getGender() != null ? myRel.getRelation().getGender() : "N";
        List<UserRelation> othersViaMe = userRelationRepo.findOthersRelatedToSameUser(me, commonPerson);
        for (UserRelation myConnectionRel : othersViaMe) {
            boolean isConnToMe = myConnectionRel.getToUser().equals(me);
            User myConnection = isConnToMe ? myConnectionRel.getFromUser() : myConnectionRel.getToUser();

            String connCatRev;
            String connGenRev;
            if (isConnToMe) {
                // Case A: connection→me e.g. "I AM Son TO connection"
                // Reverse → "connection IS Father TO me"
                Relation revConn = findReverseRelation(myConnectionRel.getRelation());
                if (revConn == null) continue;
                connCatRev = revConn.getRelationCategory();
                connGenRev = revConn.getGender() != null ? revConn.getGender() : "N";
            } else {
                // Case B: me→connection e.g. "connection IS Son TO me" → already correct
                connCatRev = myConnectionRel.getRelation().getRelationCategory();
                connGenRev = myConnectionRel.getRelation().getGender() != null ? myConnectionRel.getRelation().getGender() : "N";
            }

            String inferred = resolveRule(rules, myCatOrig, myGenderOrig, connCatRev, connGenRev);
            if (inferred == null) continue;

            Optional<Relation> rel = relationRepository.findByRelationNameIgnoreCase(inferred);
            if (rel.isEmpty()) continue;

            // Same: delete old wrong SUGGESTED, skip ACCEPTED/PENDING
            Optional<UserRelation> existingBetween = userRelationRepo.findByFromUserAndToUser(commonPerson, myConnection);
            if (existingBetween.isPresent()) {
                if (!"SUGGESTED".equals(existingBetween.get().getStatus())) continue;
                userRelationRepo.delete(existingBetween.get());
            }
            Optional<UserRelation> revExistingBetween = userRelationRepo.findByFromUserAndToUser(myConnection, commonPerson);
            if (revExistingBetween.isPresent() && "SUGGESTED".equals(revExistingBetween.get().getStatus())) {
                userRelationRepo.delete(revExistingBetween.get());
            }

            // Same direction fix: reverse the human-readable relation name
            Relation storeRel = findReverseRelation(rel.get());
            if (storeRel == null) continue;

            userRelationRepo.save(new UserRelation(commonPerson, myConnection, storeRel, "SUGGESTED"));
            Relation rev = findReverseRelation(storeRel);
            if (rev != null && userRelationRepo.findByFromUserAndToUser(myConnection, commonPerson).isEmpty()) {
                userRelationRepo.save(new UserRelation(myConnection, commonPerson, rev, "SUGGESTED"));
            }
        }
    }

    private String resolveRule(Map<String, String> rules, String catA, String genderA, String catB, String genderB) {
        String inferred = rules.get(catA + "|" + genderA + "|" + catB + "|" + genderB);
        if (inferred == null) inferred = rules.get(catA + "|" + genderA + "|" + catB + "|N");
        if (inferred == null) inferred = rules.get(catA + "|N|" + catB + "|" + genderB);
        if (inferred == null) inferred = rules.get(catA + "|N|" + catB + "|N");
        return inferred;
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
        mirror.put("cousin brother", "Cousin");
        mirror.put("cousin sister",  "Cousin");
        String rev = mirror.get(rel.getRelationName().toLowerCase());
        return rev == null ? null : relationRepository.findByRelationNameIgnoreCase(rev).orElse(null);
    }
}