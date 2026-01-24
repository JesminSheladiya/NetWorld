package com.example.demo.service;

import com.example.demo.dto.ContactDTO;
import com.example.demo.mapper.ContactMapper;
import com.example.demo.model.Contact;
import com.example.demo.model.Relation;
import com.example.demo.repository.ContactRepository;
import com.example.demo.repository.ContactSpecification;
import com.example.demo.repository.RelationRepository;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;


import java.util.List;
import java.util.Optional;

@Service
public class ContactService {

    private final ContactRepository contactRepository;
    private final RelationRepository relationRepository;

    public ContactService(ContactRepository contactRepository,
                          RelationRepository relationRepository) {
        this.contactRepository = contactRepository;
        this.relationRepository = relationRepository;
    }

    // Normal list
    public List<Contact> getAllContacts() {
        return contactRepository.findAll();
    }

    // Pagination
    public Page<Contact> getAllContacts(Pageable pageable) {
        return contactRepository.findAll(pageable);
    }

    public Optional<Contact> getContactById(Long id) {
        return contactRepository.findById(id);
    }

    public Contact saveContact(ContactDTO dto) {
        Contact contact = ContactMapper.toEntity(dto);

        if (dto.getRelationId() != null) {
            Relation relation = relationRepository.findById(dto.getRelationId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid relation ID: " + dto.getRelationId()));
            contact.setRelation(relation);
        }

        return contactRepository.save(contact);
    }


    public Optional<Contact> updateContact(Long id, ContactDTO dto) {
        return contactRepository.findById(id).map(contact -> {

            if (contactRepository.existsByPhoneAndIdNot(dto.getPhone(), id)) {
                throw new RuntimeException("Phone number already exists!");
            }
            if (contactRepository.existsByEmailAndIdNot(dto.getEmail(), id)) {
                throw new RuntimeException("Email already exists!");
            }

            // Basic fields update
            contact.setName(dto.getName());
            contact.setPhone(dto.getPhone());
            contact.setEmail(dto.getEmail());

            if (dto.getRelationId() != null) {
                Relation relation = relationRepository.findById(dto.getRelationId())
                        .orElseThrow(() -> new IllegalArgumentException("Invalid relation ID"));
                contact.setRelation(relation);
            } else {
                contact.setRelation(null);
            }

            return contactRepository.save(contact);
        });
    }



    public void deleteContact(Long id) {
        contactRepository.deleteById(id);
    }

    public Page<Contact> searchContacts(String name, String phone, String email, Long relationId, Pageable pageable) {
        Specification<Contact> spec = ContactSpecification.buildSpec(name, phone, email, relationId);
        return contactRepository.findAll(spec, pageable);
    }

}
