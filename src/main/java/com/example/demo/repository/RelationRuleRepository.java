package com.example.demo.repository;

import com.example.demo.model.RelationRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RelationRuleRepository extends JpaRepository<RelationRule, Long> {

    Optional<RelationRule> findByPersonARelationAndPersonBRelation(
            String personARelation,
            String personBRelation
    );
}