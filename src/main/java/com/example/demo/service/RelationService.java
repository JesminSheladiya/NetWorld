package com.example.demo.service;

import com.example.demo.model.Relation;
import com.example.demo.repository.RelationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RelationService {

    private final RelationRepository relationRepository;

    public RelationService(RelationRepository relationRepository) {
        this.relationRepository = relationRepository;
    }

    public List<Relation> getAll() {
        return relationRepository.findAll();
    }
}
