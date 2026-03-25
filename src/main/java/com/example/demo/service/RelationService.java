package com.example.demo.service;

import com.example.demo.dto.InferredRelationDTO;
import com.example.demo.model.Contact;
import com.example.demo.model.Relation;
import com.example.demo.model.RelationInferenceRule;
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

    public List<InferredRelationDTO> inferRelations() {
        List<Contact> allContacts = contactRepository.findAll();
        List<InferredRelationDTO> suggestions = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        List<RelationInferenceRule> allRules = inferenceRuleRepository.findAll();
        System.out.println("=== RULES LOADED FROM DB: " + allRules.size());

        Map<String, String> rulesMap = new HashMap<>();
        for (RelationInferenceRule rule : allRules) {
            String key = rule.getCategoryA() + "|" + rule.getGenderA()
                    + "|" + rule.getCategoryB() + "|" + rule.getGenderB();
            rulesMap.put(key, rule.getInferredRelationName());
        }

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

                String inferredName = rulesMap.get(
                        catA + "|" + genderA + "|" + catB + "|" + genderB);

                if (inferredName == null)
                    inferredName = rulesMap.get(
                            catA + "|" + genderA + "|" + catB + "|N");

                if (inferredName == null)
                    inferredName = rulesMap.get(
                            catA + "|N|" + catB + "|" + genderB);

                if (inferredName == null) continue;

                Optional<Relation> inferredRelation =
                        relationRepository.findByRelationNameIgnoreCase(inferredName);
                if (inferredRelation.isEmpty()) continue;

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

        System.out.println("=== TOTAL SUGGESTIONS: " + suggestions.size());
        return suggestions;
    }
}