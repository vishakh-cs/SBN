package com.smartdonation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class OrganDonationPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    private UserAccount donor;

    @Column(length = 1000)
    private String organs;

    @Column(length = 1000)
    private String consentStatement;

    private boolean verified;
    private LocalDateTime registeredAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UserAccount getDonor() { return donor; }
    public void setDonor(UserAccount donor) { this.donor = donor; }
    public String getOrgans() { return organs; }
    public void setOrgans(String organs) { this.organs = organs; }
    public String getConsentStatement() { return consentStatement; }
    public void setConsentStatement(String consentStatement) { this.consentStatement = consentStatement; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }
}
