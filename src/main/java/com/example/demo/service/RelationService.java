package com.example.demo.service;

import com.example.demo.dto.InferredRelationDTO;
import com.example.demo.model.Contact;
import com.example.demo.model.Relation;
import com.example.demo.repository.ContactRepository;
import com.example.demo.repository.RelationRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RelationService {

    private final RelationRepository relationRepository;
    private final ContactRepository contactRepository;

    // Key: "categoryA_categoryB_genderA"  →  inferred relation name
    private static final Map<String, String> INFERENCE_MAP = new HashMap<>();

    static {
        // ✅ SIBLING + SIBLING → siblings of each other
        INFERENCE_MAP.put("SIBLING_SIBLING_M", "Brother");
        INFERENCE_MAP.put("SIBLING_SIBLING_F", "Sister");
        INFERENCE_MAP.put("SIBLING_SIBLING_N", "Brother");

        // ✅ PARENT + PARENT → spouses
        INFERENCE_MAP.put("PARENT_PARENT_M", "Husband");
        INFERENCE_MAP.put("PARENT_PARENT_F", "Wife");

        // ✅ PARENT + SIBLING → parent hai sibling ka bhi
        INFERENCE_MAP.put("PARENT_SIBLING_M", "Father");
        INFERENCE_MAP.put("PARENT_SIBLING_F", "Mother");

        // ✅ SIBLING + PARENT → sibling is child of parent
        INFERENCE_MAP.put("SIBLING_PARENT_M", "Son");
        INFERENCE_MAP.put("SIBLING_PARENT_F", "Daughter");

        // ✅ CHILD + CHILD → siblings
        INFERENCE_MAP.put("CHILD_CHILD_M", "Brother");
        INFERENCE_MAP.put("CHILD_CHILD_F", "Sister");
        INFERENCE_MAP.put("CHILD_CHILD_N", "Brother");

        // ✅ GRANDPARENT + SIBLING → Grandfather/Grandmother of sibling ✅
        INFERENCE_MAP.put("GRANDPARENT_SIBLING_M", "Grandfather");
        INFERENCE_MAP.put("GRANDPARENT_SIBLING_F", "Grandmother");

        // ✅ GRANDPARENT + PARENT(M=Father) → dada IS father ka baap = "Father"
        INFERENCE_MAP.put("GRANDPARENT_PARENT_M_PARENT_M", "Father");
        // ✅ GRANDPARENT + PARENT(F=Mother) → dada IS mother ka sasur = "Father-in-law"
        INFERENCE_MAP.put("GRANDPARENT_PARENT_M_PARENT_F", "Father-in-law");
        INFERENCE_MAP.put("GRANDPARENT_PARENT_F_PARENT_M", "Mother");
        INFERENCE_MAP.put("GRANDPARENT_PARENT_F_PARENT_F", "Mother-in-law");

        // ✅ PARENT + CHILD → grandparent
        INFERENCE_MAP.put("PARENT_CHILD_M", "Grandfather");
        INFERENCE_MAP.put("PARENT_CHILD_F", "Grandmother");

        // ✅ CHILD + PARENT → grandchild
        INFERENCE_MAP.put("CHILD_PARENT_M", "Grandson");
        INFERENCE_MAP.put("CHILD_PARENT_F", "Granddaughter");

        // ✅ SPOUSE + SIBLING → parent of sibling
        INFERENCE_MAP.put("SPOUSE_SIBLING_M", "Father");
        INFERENCE_MAP.put("SPOUSE_SIBLING_F", "Mother");

        // ✅ SIBLING + CHILD → uncle/aunt
        INFERENCE_MAP.put("SIBLING_CHILD_M", "Uncle");
        INFERENCE_MAP.put("SIBLING_CHILD_F", "Aunt");

        // ✅ SPOUSE + CHILD → co-parent
        INFERENCE_MAP.put("SPOUSE_CHILD_M", "Father");
        INFERENCE_MAP.put("SPOUSE_CHILD_F", "Mother");
    }

    public RelationService(RelationRepository relationRepository,
                           ContactRepository contactRepository) {
        this.relationRepository = relationRepository;
        this.contactRepository = contactRepository;
    }

    public List<Relation> getAll() {
        return relationRepository.findAll();
    }

    public List<InferredRelationDTO> inferRelations() {
        List<Contact> allContacts = contactRepository.findAll();
        List<InferredRelationDTO> suggestions = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Contact contactA : allContacts) {
            Relation relA = contactA.getRelation();
            if (relA == null || relA.getRelationCategory() == null) continue;

            for (Contact contactB : allContacts) {
                if (contactA.getId().equals(contactB.getId())) continue;

                Relation relB = contactB.getRelation();
                if (relB == null || relB.getRelationCategory() == null) continue;

                String categoryA = relA.getRelationCategory();
                String categoryB = relB.getRelationCategory();
                String genderA   = relA.getGender() != null ? relA.getGender() : "N";
                String genderB   = relB.getGender() != null ? relB.getGender() : "N";

                if (categoryA.equals("OTHER") || categoryB.equals("OTHER")) continue;

                String mapKey;
                if (categoryA.equals("GRANDPARENT") && categoryB.equals("PARENT")) {

                    mapKey = "GRANDPARENT_PARENT_" + genderA + "_PARENT_" + genderB;
                } else {
                    // Normal key
                    mapKey = categoryA + "_" + categoryB + "_" + genderA;
                }

                String inferredName = INFERENCE_MAP.get(mapKey);
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
        return suggestions;
    }
}