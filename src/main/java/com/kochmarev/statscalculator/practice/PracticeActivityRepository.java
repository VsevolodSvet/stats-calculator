package com.kochmarev.statscalculator.practice;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

interface PracticeActivityRepository extends Repository<PracticeActivity, String> {

    List<PracticeActivity> findAllByOrderByActivityIdAscVersionDesc();

    Optional<PracticeActivity> findByActivityIdAndVersion(String activityId, int version);

    Optional<PracticeActivity> findById(String id);
}
