package com.example.demo.service;

import com.example.demo.dto.InferredRelationDTO;
import com.example.demo.model.Contact;
import com.example.demo.model.Relation;
import com.example.demo.model.RelationRule;
import com.example.demo.repository.ContactRepository;
import com.example.demo.repository.RelationRepository;
import com.example.demo.repository.RelationRuleRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class RelationService {

    private final RelationRepository relationRepository;
    private final RelationRuleRepository relationRuleRepository;
    private final ContactRepository contactRepository; // already hoga tumhare paas

    public RelationService(RelationRepository relationRepository,
                           RelationRuleRepository relationRuleRepository,
                           ContactRepository contactRepository) {
        this.relationRepository = relationRepository;
        this.relationRuleRepository = relationRuleRepository;
        this.contactRepository = contactRepository;
    }

    public List<Relation> getAll() {
        return relationRepository.findAll();
    }

    // ✅ YE NAYA METHOD HAI — Inference Logic
    public List<InferredRelationDTO> inferRelations() {
        List<Contact> allContacts = contactRepository.findAll();
        List<InferredRelationDTO> suggestions = new ArrayList<>();

        // Har Contact A ke liye
        for (Contact contactA : allContacts) {
            if (contactA.getRelation() == null) continue;
            String relationA = contactA.getRelation().getRelationName();

            // Har doosre Contact B ke saath compare karo
            for (Contact contactB : allContacts) {
                if (contactB.getRelation() == null) continue;
                if (contactA.getId().equals(contactB.getId())) continue; // same contact skip

                String relationB = contactB.getRelation().getRelationName();

                // Rule dhundo: A ki relation + B ki relation = A ki B se relation
                Optional<RelationRule> rule = relationRuleRepository
                        .findByPersonARelationAndPersonBRelation(relationA, relationB);

                if (rule.isPresent()) {
                    suggestions.add(new InferredRelationDTO(
                            contactA.getName(),
                            contactB.getName(),
                            rule.get().getInferredRelation()
                    ));
                }
            }
        }
        return suggestions;
    }
}