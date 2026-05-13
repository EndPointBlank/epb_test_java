package com.ejbtestjava.model;

import jakarta.persistence.*;

@Entity
@Table(name = "computers")
public class Computer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String description;
    private Long studentId;
    private Long teacherId;
    private String brand;

    public Computer() {}

    public Computer(String description, Long studentId, Long teacherId, String brand) {
        this.description = description;
        this.studentId = studentId;
        this.teacherId = teacherId;
        this.brand = brand;
    }

    public Long getId()              { return id; }
    public String getDescription()   { return description; }
    public Long getStudentId()       { return studentId; }
    public Long getTeacherId()       { return teacherId; }
    public String getBrand()         { return brand; }

    public void setId(Long id)                   { this.id = id; }
    public void setDescription(String description) { this.description = description; }
    public void setStudentId(Long studentId)       { this.studentId = studentId; }
    public void setTeacherId(Long teacherId)       { this.teacherId = teacherId; }
    public void setBrand(String brand)             { this.brand = brand; }
}
