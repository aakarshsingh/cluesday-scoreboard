package com.cluesday.scoreboard.model;

public record Team(String id, Integer tableNumber, String customName) {
	public String displayName() {
		if (customName != null && !customName.isBlank()) return customName;
		return tableNumber != null ? "T" + tableNumber : "—";
	}
}
