package com.example.demo.repository;

import com.example.demo.model.Relation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RelationRepository extends JpaRepository<Relation, Long> {

    Optional<Relation> findFirstByGenerationLevelAndGender(
            Integer generationLevel, String gender);

    Optional<Relation> findByRelationNameIgnoreCase(String relationName);
}