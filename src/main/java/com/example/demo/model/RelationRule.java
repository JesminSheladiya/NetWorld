package com.example.demo.model;

import jakarta.persistence.*;

@Entity
@Table(name = "relation_rules")
public class RelationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "person_a_relation", nullable = false)
    private String personARelation;

    @Column(name = "person_b_relation", nullable = false)
    private String personBRelation;

    @Column(name = "inferred_relation", nullable = false)
    private String inferredRelation;

    public RelationRule() {}

    // Getters
    public Long getId() { return id; }
    public String getPersonARelation() { return personARelation; }
    public String getPersonBRelation() { return personBRelation; }
    public String getInferredRelation() { return inferredRelation; }
}