package com.example.demo.model;

import jakarta.persistence.*;

@Entity
@Table(
        name = "contact",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_contact_phone", columnNames = "phone"),
                @UniqueConstraint(name = "uk_contact_email", columnNames = "email")
        }
)
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(nullable = false, updatable = false)  // unique + not editable
    private String phone;

    @Column(nullable = false) // unique
    private String email;

    @Enumerated(EnumType.STRING)
    private Relation relation;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Relation getRelation() { return relation; }
    public void setRelation(Relation relation) { this.relation = relation; }
}
