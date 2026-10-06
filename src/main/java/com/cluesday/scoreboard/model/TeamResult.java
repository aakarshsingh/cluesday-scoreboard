package com.cluesday.scoreboard.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public record TeamResult(String teamId, Integer tableNumber, String customName, Map<Integer, Double> roundTotals,
		double grandTotal) {
	public String displayName() {
		if (customName != null && !customName.isBlank())
			return customName;
		return tableNumber != null ? "T" + tableNumber : "—";
	}

	/** Compact score text for the scoreboard: "7", "7.5", "7.25"; "" if not scored. */
	public String roundText(int round) {
		Double pts = roundTotals.get(round);
		return pts == null ? "" : format(pts);
	}

	public String totalText() {
		return format(grandTotal);
	}

	private static String format(double value) {
		return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
	}
}
