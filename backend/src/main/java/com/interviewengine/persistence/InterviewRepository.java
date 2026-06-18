package com.interviewengine.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InterviewRepository extends JpaRepository<InterviewEntity, UUID> {
}
