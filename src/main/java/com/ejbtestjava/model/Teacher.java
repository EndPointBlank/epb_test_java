package com.ejbtestjava.model;

/**
 * Simple teacher record returned by {@code GET /teachers} and {@code GET /teachers/{id}}.
 */
public class Teacher {

    private final Long id;
    private final String name;
    private final String num;

    public Teacher(Long id, String name, String num) {
        this.id = id;
        this.name = name;
        this.num = num;
    }

    public Long getId()    { return id; }
    public String getName() { return name; }
    public String getNum()  { return num; }
}
