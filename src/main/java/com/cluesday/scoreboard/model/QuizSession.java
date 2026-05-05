package com.cluesday.scoreboard.model;

import java.time.LocalDate;

public record QuizSession(String uuid, int sessionNumber, String quizmasterName, LocalDate quizDate, int maxRounds,
		double defaultPointsPerQuestion, boolean active) {

	public String name() {
		return "Cluesday " + sessionNumber;
	}
}
