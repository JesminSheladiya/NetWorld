package com.example.demo.controller;

import com.example.demo.dto.ContactDTO;
import com.example.demo.mapper.ContactMapper;
import com.example.demo.model.Contact;
import com.example.demo.model.Relation;
import com.example.demo.service.ContactService;
import java.util.Optional;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping
    public List<ContactDTO> getAllContacts() {
        return contactService.getAllContacts()
                .stream()
                .map(ContactMapper::toDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContactDTO> getContactById(@PathVariable Long id) {
        return contactService.getContactById(id)
                .map(contact -> ResponseEntity.ok(ContactMapper.toDTO(contact)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createContact(@Valid @RequestBody ContactDTO contactDTO) {
        try {
            Contact saved = contactService.saveContact(ContactMapper.toEntity(contactDTO));
            return ResponseEntity.ok(ContactMapper.toDTO(saved));
        } catch (DataIntegrityViolationException e) {
            String rootMessage = e.getRootCause() != null ? e.getRootCause().getMessage() : e.getMessage();
            String message = "Duplicate entry!";

            if (rootMessage.contains("uk_contact_phone")) {
                message = "Phone number already exists!";
            } else if (rootMessage.contains("uk_contact_email")) {
                message = "Email already exists!";
            }

            return ResponseEntity.badRequest().body(message);
        }
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
    public ResponseEntity<?> updateContact(@PathVariable Long id, @RequestBody ContactDTO contactDTO) {
        try {
            Optional<Contact> updated = contactService.updateContact(id, ContactMapper.toEntity(contactDTO));
            return updated.map(value -> ResponseEntity.ok(ContactMapper.toDTO(value)))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage()); // ✅ direct message return
        }
    }



    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContact(@PathVariable Long id) {
        contactService.deleteContact(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/relations")
    public Relation[] getRelations() {
        return Relation.values();
    }

    @GetMapping("/search")
    public Page<ContactDTO> searchContacts(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Relation relation,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {
        Page<Contact> contacts = contactService.searchContacts(name, phone, email, relation, PageRequest.of(page, size));
        return contacts.map(ContactMapper::toDTO);
    }

}
