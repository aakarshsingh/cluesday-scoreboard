package com.cluesday.scoreboard.model;

import java.util.Map;
import java.util.Set;

public record TeamResult(String teamId, Integer tableNumber, Map<Integer, Double> roundTotals, Set<Integer> jokerRounds,
		double grandTotal) {
	public String displayName() {
		return tableNumber != null ? "T" + tableNumber : "—";
	}
}
