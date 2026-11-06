package com.tikit.performance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "genres")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Genre {

    @Id
    @Column(length = 30)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "kopis_code", length = 20)
    private String kopisCode;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    public static Genre create(String code, String name, String kopisCode, int sortOrder, boolean active) {
        Genre g = new Genre();
        g.code = code;
        g.name = name;
        g.kopisCode = kopisCode;
        g.sortOrder = sortOrder;
        g.active = active;
        return g;
    }


}
