package com.example.demo.controller;

import com.example.demo.dto.ContactDTO;
import com.example.demo.mapper.ContactMapper;
import com.example.demo.model.Contact;
import com.example.demo.model.Relation;
import com.example.demo.service.ContactService;

import com.example.demo.service.RelationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactService contactService;
    private final RelationService relationService;

    public ContactController(ContactService contactService,
                             RelationService relationService) {
        this.contactService = contactService;
        this.relationService = relationService;
    }


    @GetMapping
    public ResponseEntity<List<ContactDTO>> getAllContacts() {
        List<Contact> contacts = contactService.getAllContacts();
        List<ContactDTO> dtos = contacts.stream()
                .map(contact -> {
                    ContactDTO dto = ContactMapper.toDTO(contact);
                    if (contact.getRelation() != null) {
                        dto.setRelationName(contact.getRelation().getRelationName());
                    }
                    return dto;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }


    @GetMapping("/{id}")
    public ResponseEntity<ContactDTO> getContactById(@PathVariable Long id) {
        return contactService.getContactById(id)
                .map(contact -> ResponseEntity.ok(ContactMapper.toDTO(contact)))
                .orElse(ResponseEntity.notFound().build());
    }


    @PostMapping
    public ResponseEntity<?> createContact(@Valid @RequestBody ContactDTO dto) {
        Contact saved = contactService.saveContact(dto);
        return ResponseEntity.ok(ContactMapper.toDTO(saved));
    }


    @GetMapping("/paginated")
    public Page<ContactDTO> getContactsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Contact> contactsPage = contactService.getAllContacts(pageable);

        return contactsPage.map(ContactMapper::toDTO);
    }


    @PutMapping("/{id}")
    public ResponseEntity<ContactDTO> updateContact(@PathVariable Long id, @Valid @RequestBody ContactDTO dto) {
        Contact updated = contactService.updateContact(id, dto)
                .orElseThrow(() -> new RuntimeException("Contact not found"));

        ContactDTO responseDto = ContactMapper.toDTO(updated);
        if (updated.getRelation() != null) {
            responseDto.setRelationName(updated.getRelation().getRelationName());
        }

        return ResponseEntity.ok(responseDto);
    }



    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContact(@PathVariable Long id) {
        contactService.deleteContact(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/relations")
    public List<Relation> getRelations() {
        return relationService.getAll();
    }


    @GetMapping("/search")
    public Page<ContactDTO> searchContacts(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Long relationId,   // id as param
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {
        Page<Contact> contacts =
                contactService.searchContacts(name, phone, email, relationId, PageRequest.of(page, size));
        return contacts.map(ContactMapper::toDTO);
    }


}
