package com.ejbtestjava.config;

import com.ejbtestjava.model.*;
import com.ejbtestjava.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final ComputerRepository computers;
    private final TabletRepository tablets;

    public DatabaseSeeder(StudentRepository students, TeacherRepository teachers,
                          ComputerRepository computers, TabletRepository tablets) {
        this.students = students;
        this.teachers = teachers;
        this.computers = computers;
        this.tablets = tablets;
    }

    @Override
    public void run(String... args) {
        if (students.count() == 0) {
            students.save(new Student("Pete Daniels", "1241234"));
            students.save(new Student("Jack Vigneau", "1241235"));
        }
        if (teachers.count() == 0) {
            teachers.save(new Teacher("Sam Smith", "12"));
            teachers.save(new Teacher("Joy Jones", "13"));
        }
        if (computers.count() == 0) {
            computers.save(new Computer("Dell Chromebook 3100", 1L, 1L, "Dell"));
            computers.save(new Computer("HP Desktop 400 G9", 2L, 1L, "HP"));
        }
        if (tablets.count() == 0) {
            tablets.save(new Tablet("Apple iPad (10th gen)", 1L, 1L, "Apple"));
            tablets.save(new Tablet("Microsoft Surface Pro 9", 2L, 2L, "Microsoft"));
        }
    }
}
