package com.cluesday.scoreboard.model;

import java.time.LocalDate;

public record QuizSession(String uuid, int sessionNumber, String quizmasterName, LocalDate quizDate, int maxRounds,
		double defaultPointsPerQuestion, boolean active) {
	/** Display name for use in headers and history. */
	public String name() {
		return "Cluesday " + sessionNumber;
	}

	public QuizSession withActive(boolean newActive) {
		return new QuizSession(uuid, sessionNumber, quizmasterName, quizDate, maxRounds, defaultPointsPerQuestion,
				newActive);
	}
}
