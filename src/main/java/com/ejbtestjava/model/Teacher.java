package com.ejbtestjava.model;

import jakarta.persistence.*;

@Entity
@Table(name = "teachers")
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String num;

    public Teacher() {}

    public Teacher(String name, String num) {
        this.name = name;
        this.num = num;
    }

    public Long getId()     { return id; }
    public String getName() { return name; }
    public String getNum()  { return num; }

    public void setId(Long id)         { this.id = id; }
    public void setName(String name)   { this.name = name; }
    public void setNum(String num)     { this.num = num; }
}
