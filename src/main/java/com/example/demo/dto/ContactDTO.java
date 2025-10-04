package com.example.demo.dto;
import com.example.demo.model.Relation;
import jakarta.validation.constraints.*;


public class ContactDTO {
    private Long id;

    @NotBlank(message = "Name is required")
    private String name;

    @Pattern(regexp = "^[0-9]{10}$", message = "Phone number must be 10 digits")
    @NotBlank(message = "Phone is required")
    private String phone;

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;


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
