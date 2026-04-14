package com.ejbtestjava.model;

/**
 * Simple student record returned by {@code GET /students}.
 */
public class Student {

    private final String name;
    private final String num;

    public Student(String name, String num) {
        this.name = name;
        this.num = num;
    }

    public String getName() { return name; }
    public String getNum()  { return num; }
}
