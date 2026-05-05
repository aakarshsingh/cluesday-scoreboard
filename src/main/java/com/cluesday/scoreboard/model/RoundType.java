package com.cluesday.scoreboard.model;

public enum RoundType {

	NORMAL, NEGATIVE, DYNAMIC;

	public static RoundType fromString(String s) {
		if (s == null) {
			return NORMAL;
		}
		return switch (s.toUpperCase()) {
			case "NEGATIVE", "SPEED" -> NEGATIVE;
			case "DYNAMIC", "CONNECTS" -> DYNAMIC;
			default -> NORMAL;
		};
	}

}
