package com.example.demo.service;

import com.example.demo.dto.InferredRelationDTO;
import com.example.demo.model.Contact;
import com.example.demo.model.Relation;
import com.example.demo.model.RelationInferenceRule;
import com.example.demo.model.User;
import com.example.demo.repository.ContactRepository;
import com.example.demo.repository.RelationInferenceRuleRepository;
import com.example.demo.repository.RelationRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RelationService {

    private final RelationRepository relationRepository;
    private final ContactRepository contactRepository;
    private final RelationInferenceRuleRepository inferenceRuleRepository;

    public RelationService(RelationRepository relationRepository,
                           ContactRepository contactRepository,
                           RelationInferenceRuleRepository inferenceRuleRepository) {
        this.relationRepository = relationRepository;
        this.contactRepository = contactRepository;
        this.inferenceRuleRepository = inferenceRuleRepository;
    }

    public List<Relation> getAll() {
        return relationRepository.findAll();
    }


    public List<InferredRelationDTO> inferRelations(User user) {

        // ── 1. Load only THIS user's contacts ──────────────────────────
        List<Contact> allContacts = contactRepository.findByUser(user);

        List<InferredRelationDTO> suggestions = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // ── 2. Load inference rules into a map ─────────────────────────
        List<RelationInferenceRule> allRules = inferenceRuleRepository.findAll();
        System.out.println("=== RULES LOADED FROM DB: " + allRules.size());
        System.out.println("=== USER CONTACTS COUNT : " + allContacts.size());

        Map<String, String> rulesMap = new HashMap<>();
        for (RelationInferenceRule rule : allRules) {
            String key = rule.getCategoryA() + "|" + rule.getGenderA()
                    + "|" + rule.getCategoryB() + "|" + rule.getGenderB();
            rulesMap.put(key, rule.getInferredRelationName());
        }

        // ── 3. Compare every pair of this user's contacts ──────────────
        for (Contact contactA : allContacts) {
            Relation relA = contactA.getRelation();
            if (relA == null || relA.getRelationCategory() == null) continue;
            if (relA.getRelationCategory().equals("OTHER")) continue;

            String catA    = relA.getRelationCategory();
            String genderA = relA.getGender() != null ? relA.getGender() : "N";

            for (Contact contactB : allContacts) {
                if (contactA.getId().equals(contactB.getId())) continue;

                Relation relB = contactB.getRelation();
                if (relB == null || relB.getRelationCategory() == null) continue;
                if (relB.getRelationCategory().equals("OTHER")) continue;

                String catB    = relB.getRelationCategory();
                String genderB = relB.getGender() != null ? relB.getGender() : "N";

                // Try most-specific match first, then fallbacks
                String inferredName = rulesMap.get(catA + "|" + genderA + "|" + catB + "|" + genderB);
                if (inferredName == null)
                    inferredName = rulesMap.get(catA + "|" + genderA + "|" + catB + "|N");
                if (inferredName == null)
                    inferredName = rulesMap.get(catA + "|N|" + catB + "|" + genderB);
                if (inferredName == null) continue;

                // Verify the inferred relation name exists in relations master
                Optional<Relation> inferredRelation =
                        relationRepository.findByRelationNameIgnoreCase(inferredName);
                if (inferredRelation.isEmpty()) continue;

                // Deduplicate A→B (we keep both A→B and B→A as separate suggestions)
                String dedupKey = contactA.getId() + "-" + contactB.getId();
                if (!seen.contains(dedupKey)) {
                    seen.add(dedupKey);
                    suggestions.add(new InferredRelationDTO(
                            contactA.getName(),
                            contactB.getName(),
                            inferredName
                    ));
                }
            }
        }

        System.out.println("=== TOTAL SUGGESTIONS FOR USER [" + user.getUsername() + "]: " + suggestions.size());
        return suggestions;
    }
}