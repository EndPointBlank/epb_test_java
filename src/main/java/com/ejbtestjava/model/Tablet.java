package com.ejbtestjava.model;

/**
 * Tablet record managed by {@code /tablets} endpoints.
 */
public class Tablet {

    private final Long id;
    private final String description;
    private final Long studentId;
    private final Long teacherId;
    private final String brand;

    public Tablet(Long id, String description, Long studentId, Long teacherId, String brand) {
        this.id = id;
        this.description = description;
        this.studentId = studentId;
        this.teacherId = teacherId;
        this.brand = brand;
    }

    public Long getId()            { return id; }
    public String getDescription() { return description; }
    public Long getStudentId()     { return studentId; }
    public Long getTeacherId()     { return teacherId; }
    public String getBrand()       { return brand; }
}
