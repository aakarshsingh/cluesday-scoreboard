package com.cluesday.scoreboard.model;

import java.util.Map;

public record TeamResult(String teamId, Integer tableNumber, String customName, Map<Integer, Double> roundTotals,
		double grandTotal) {
	public String displayName() {
		if (customName != null && !customName.isBlank()) return customName;
		return tableNumber != null ? "T" + tableNumber : "—";
	}
}
