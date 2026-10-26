package com.smartdonation.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
public class DonationHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private UserAccount donor;

    @ManyToOne
    private BloodRequest bloodRequest;

    private LocalDate donatedOn;
    private String donationType;
    private String location;
    private String notes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UserAccount getDonor() { return donor; }
    public void setDonor(UserAccount donor) { this.donor = donor; }
    public BloodRequest getBloodRequest() { return bloodRequest; }
    public void setBloodRequest(BloodRequest bloodRequest) { this.bloodRequest = bloodRequest; }
    public LocalDate getDonatedOn() { return donatedOn; }
    public void setDonatedOn(LocalDate donatedOn) { this.donatedOn = donatedOn; }
    public String getDonationType() { return donationType; }
    public void setDonationType(String donationType) { this.donationType = donationType; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
