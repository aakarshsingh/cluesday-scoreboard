package com.cluesday.scoreboard.model;

public record Team(String id, Integer tableNumber) {
	public String displayName() {
		return tableNumber != null ? "T" + tableNumber : "—";
	}
}
