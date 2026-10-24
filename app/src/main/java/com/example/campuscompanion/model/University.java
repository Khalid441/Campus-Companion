package com.example.campuscompanion.model;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * A university, stored at universities/{code} (you edit these in the Firebase console).
 *  - domains empty  -> type 2: students join with a class code
 *  - domains filled -> type 1: students must use a verified university email
 */
public class University {
    public String code;
    public String name;
    public List<String> domains = new ArrayList<>();
    public List<String> sections = new ArrayList<>();
    /** true = students type their own section (only used for universities with email domains). */
    public boolean freeSections;

    public boolean usesEmailDomain() { return !domains.isEmpty(); }

    /** Students type their section themselves (type 1 only; type 2 always uses the fixed list). */
    public boolean typesOwnSection() { return freeSections && usesEmailDomain(); }

    public static University from(DocumentSnapshot d) {
        University u = new University();
        u.code = d.getId();
        u.name = d.getString("name") == null ? d.getId() : d.getString("name");
        u.freeSections = Boolean.TRUE.equals(d.getBoolean("freeSections"));
        Object dom = d.get("domains");
        if (dom instanceof List) {
            for (Object o : (List<?>) dom) if (o != null) u.domains.add(o.toString().toLowerCase());
        }
        Object sec = d.get("sections");
        if (sec instanceof List) {
            for (Object o : (List<?>) sec) if (o != null) u.sections.add(o.toString());
        }
        return u;
    }

    @Override
    public String toString() { return name; }
}
