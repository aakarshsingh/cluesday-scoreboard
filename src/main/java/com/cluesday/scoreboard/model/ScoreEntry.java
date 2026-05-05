package com.cluesday.scoreboard.model;

/**
 * One question's score for a team in a specific round. Points can be negative (round 5,
 * round 6 free-form).
 */
public record ScoreEntry(String teamId, int roundNum, int questionNum, double points) {
}
