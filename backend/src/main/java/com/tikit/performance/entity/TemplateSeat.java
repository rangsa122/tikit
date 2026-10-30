package com.tikit.performance.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "template_seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TemplateSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private SeatTemplate template;

    @Column(name = "seat_code", nullable = false, length = 30)
    private String seatCode;

    @Column(nullable = false, length = 40)
    private String label;

    @Column(name = "row_label", nullable = false, length = 20)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    @Column(nullable = false, length = 20)
    private String grade;

    @Column(nullable = false)
    private int x;

    @Column(nullable = false)
    private int y;
}