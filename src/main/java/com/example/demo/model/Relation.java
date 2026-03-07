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

    @Column(name = "generation_level", nullable = false)
    private Integer generationLevel = 0;

    @Column(name = "gender", length = 1)
    private String gender = "N"; // M, F, N

    @Column(name = "relation_category", length = 20)
    private String relationCategory = "OTHER";

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

    public Integer getGenerationLevel() { return generationLevel; }
    public void setGenerationLevel(Integer generationLevel) {
        this.generationLevel = generationLevel;
    }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getRelationCategory() { return relationCategory; }
    public void setRelationCategory(String relationCategory) {
        this.relationCategory = relationCategory;
    }
}
