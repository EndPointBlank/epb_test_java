package com.ejbtestjava.model;

/**
 * Computer record managed by {@code /computers} endpoints.
 */
public class Computer {

    private final Long id;
    private final String description;
    private final Long studentId;
    private final Long teacherId;
    private final String brand;

    public Computer(Long id, String description, Long studentId, Long teacherId, String brand) {
        this.id = id;
        this.description = description;
        this.studentId = studentId;
        this.teacherId = teacherId;
        this.brand = brand;
    }

    public Long getId()          { return id; }
    public String getDescription() { return description; }
    public Long getStudentId()   { return studentId; }
    public Long getTeacherId()   { return teacherId; }
    public String getBrand()     { return brand; }
}
