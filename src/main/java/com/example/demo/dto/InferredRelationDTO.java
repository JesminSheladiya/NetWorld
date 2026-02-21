package com.example.demo.dto;

public class InferredRelationDTO {
    private String personAName;   // "Ramesh"
    private String personBName;   // "Suresh"
    private String inferredRelation; // "Father"
    private String message;       // "Ramesh is Father of Suresh"

    public InferredRelationDTO(String personAName, String personBName, String inferredRelation) {
        this.personAName = personAName;
        this.personBName = personBName;
        this.inferredRelation = inferredRelation;
        this.message = personAName + " is " + inferredRelation + " of " + personBName;
    }

    public String getPersonAName() { return personAName; }
    public String getPersonBName() { return personBName; }
    public String getInferredRelation() { return inferredRelation; }
    public String getMessage() { return message; }
}