package com.cluesday.scoreboard.model;

import java.time.LocalDateTime;
import java.util.List;

/** Immutable record of a completed quiz stored in history. */
public record QuizSnapshot(String sessionName, String sessionUuid, LocalDateTime completedAt,
		List<TeamResult> leaderboard) {
}
