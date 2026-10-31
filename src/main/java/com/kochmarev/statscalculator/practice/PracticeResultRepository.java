package com.kochmarev.statscalculator.practice;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PracticeResultRepository extends JpaRepository<PracticeResult, UUID> {
}
