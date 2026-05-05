package com.cluesday.scoreboard.model;

import java.util.Map;
import java.util.Set;

/**
 * Computed result for one team — used in both the live scoreboard and history snapshots.
 * roundTotals values already include the joker multiplier where applied.
 */
public record TeamResult(String teamId, String teamName, Integer tableNumber, Map<Integer, Double> roundTotals,
		Set<Integer> jokerRounds, double grandTotal) {
}
