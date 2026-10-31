package com.kochmarev.statscalculator.result;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ResultRepository extends JpaRepository<ResultEntity, UUID> {
}
