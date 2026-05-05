package com.cluesday.scoreboard.repository;

import com.cluesday.scoreboard.entity.QuizResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuizResultRepository extends JpaRepository<QuizResultEntity, Long> {

	List<QuizResultEntity> findAllByOrderByCompletedAtDesc();

	Optional<QuizResultEntity> findBySessionUuid(String sessionUuid);

}
