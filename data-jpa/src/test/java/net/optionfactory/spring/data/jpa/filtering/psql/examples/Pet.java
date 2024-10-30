package net.optionfactory.spring.data.jpa.filtering.psql.examples;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import java.time.LocalDate;

@Entity
public class Pet {

    @Id
    @GeneratedValue
    public long id;

    public enum PetType {
        CAT, DOG;
    };

    @Enumerated(EnumType.STRING)
    public PetType type;
    public String breed;
    public String name;
    public LocalDate birthDate;

    public static Pet of(PetType type, String breed, String name, LocalDate birthDate) {
        final var p = new Pet();
        p.type = type;
        p.breed = breed;
        p.name = name;
        p.birthDate = birthDate;
        return p;
    }

}
