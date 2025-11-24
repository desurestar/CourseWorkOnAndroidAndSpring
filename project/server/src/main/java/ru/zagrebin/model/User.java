package ru.zagrebin.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "display_name", length = 150)
    private String displayName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(length = 20)
    private String role;

    @Column(name = "date_joined")
    private OffsetDateTime dateJoined;

    @ManyToMany
    @JoinTable(
            name = "user_subscriptions",
            joinColumns = @JoinColumn(name = "subscriber_id"),
            inverseJoinColumns = @JoinColumn(name = "subscribed_to_id")
    )
    @Builder.Default
    private Set<User> subscriptions = new HashSet<>();


    @ManyToMany(mappedBy = "subscriptions")
    @Builder.Default
    private Set<User> subscribers = new HashSet<>();

    @PrePersist
    public void prePersist() {
        if (dateJoined == null) dateJoined = OffsetDateTime.now();
        if (role == null) role = "user";
    }
}
