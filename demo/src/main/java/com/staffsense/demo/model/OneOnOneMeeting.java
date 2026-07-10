package com.staffsense.demo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "one_on_one_meetings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OneOnOneMeeting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private User employee;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id", nullable = false)
    private User manager;

    @NotNull
    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MeetingStatus status = MeetingStatus.PLANNED;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "action_items", columnDefinition = "TEXT")
    private String actionItems;

    @NotNull
    @Column(nullable = false)
    @Builder.Default
    private Boolean missed = false;

    @PrePersist
    protected void onCreate() {
        if (this.status == null) {
            this.status = MeetingStatus.PLANNED;
        }
        if (this.missed == null) {
            this.missed = false;
        }
    }
}
