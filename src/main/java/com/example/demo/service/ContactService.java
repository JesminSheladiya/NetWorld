package com.example.demo.service;

import com.example.demo.model.Contact;
import com.example.demo.repository.ContactRepository;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Service
public class ContactService {

    private final ContactRepository contactRepository;

    public ContactService(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
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

    public Contact saveContact(Contact contact) {
        return contactRepository.save(contact);
    }

    public Optional<Contact> updateContact(Long id, Contact newContact) {
        return contactRepository.findById(id).map(contact -> {

            // ✅ Phone duplicate check
            if (contactRepository.existsByPhoneAndIdNot(newContact.getPhone(), id)) {
                throw new RuntimeException("Phone number already exists!");
            }

            // ✅ Email duplicate check
            if (contactRepository.existsByEmailAndIdNot(newContact.getEmail(), id)) {
                throw new RuntimeException("Email already exists!");
            }

            contact.setName(newContact.getName());
            contact.setPhone(newContact.getPhone());
            contact.setEmail(newContact.getEmail());
            contact.setRelation(newContact.getRelation());
            return contactRepository.save(contact);
        });
    }


    public void deleteContact(Long id) {
        contactRepository.deleteById(id);
    }
}
