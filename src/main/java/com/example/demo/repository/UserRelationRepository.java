package com.example.demo.repository;

import com.example.demo.model.User;
import com.example.demo.model.UserRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRelationRepository extends JpaRepository<UserRelation, Long> {

    List<UserRelation> findByFromUser(User fromUser);
    List<UserRelation> findByToUser(User toUser);
    List<UserRelation> findByFromUserAndStatus(User fromUser, String status);
    List<UserRelation> findByToUserAndStatus(User toUser, String status);
    Optional<UserRelation> findByFromUserAndToUser(User fromUser, User toUser);

    
    @Query("""
        SELECT ur FROM UserRelation ur
        WHERE ((ur.toUser = :commonUser AND ur.fromUser <> :me)
           OR  (ur.fromUser = :commonUser AND ur.toUser <> :me))
          AND ur.status = 'ACCEPTED'
    """)
    List<UserRelation> findOthersRelatedToSameUser(
            @Param("commonUser") User commonUser,
            @Param("me") User me
    );
}