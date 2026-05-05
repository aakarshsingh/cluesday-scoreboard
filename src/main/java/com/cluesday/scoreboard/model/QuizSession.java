package com.cluesday.scoreboard.model;

import java.time.LocalDate;

public record QuizSession(String uuid, int sessionNumber, String quizmasterName, LocalDate quizDate, boolean active) {

	public static final int MAX_ROUNDS = 6;

	public int maxRounds() {
		return MAX_ROUNDS;
	}

	public String name() {
		return "Cluesday " + sessionNumber;
	}
}
