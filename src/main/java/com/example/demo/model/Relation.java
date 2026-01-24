package com.example.demo.model;

import jakarta.persistence.*;

@Entity
@Table(name = "relations")
public class Relation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "relation_name", nullable = false, unique = true)
    private String relationName;

    public Relation() {}

    public Relation(String relationName) {
        this.relationName = relationName;
    }

    public Long getId() {
        return id;
    }

    public String getRelationName() {
        return relationName;
    }

    public void setRelationName(String relationName) {
        this.relationName = relationName;
    }
}
