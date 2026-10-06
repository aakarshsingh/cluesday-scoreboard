package com.cluesday.scoreboard.model;

import java.util.List;
import java.util.Map;

/**
 * Snapshot polled by open dashboards so a second device stays current. {@code scores} is
 * keyed "teamId:round" with the same text the score inputs show.
 */
public record DashboardState(List<Integer> done, List<TeamInfo> teams, Map<String, String> scores) {

	public record TeamInfo(String id, String name) {
	}

}
