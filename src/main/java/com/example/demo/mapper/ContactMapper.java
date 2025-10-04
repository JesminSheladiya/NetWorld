package com.example.demo.mapper;

import com.example.demo.dto.ContactDTO;
import com.example.demo.model.Contact;
import com.example.demo.model.Relation;

public class ContactMapper {

    // Entity → DTO
    public static ContactDTO toDTO(Contact contact) {
        ContactDTO dto = new ContactDTO();
        dto.setId(contact.getId());
        dto.setName(contact.getName());
        dto.setPhone(contact.getPhone());
        dto.setEmail(contact.getEmail());
        dto.setRelation(contact.getRelation());
        return dto;
    }

    // DTO → Entity
    public static Contact toEntity(ContactDTO dto) {
        Contact contact = new Contact();
        contact.setId(dto.getId());
        contact.setName(dto.getName());
        contact.setPhone(dto.getPhone());
        contact.setEmail(dto.getEmail());
        contact.setRelation(dto.getRelation());
        return contact;
    }
}
