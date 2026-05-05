package com.cluesday.scoreboard.service;

import com.cluesday.scoreboard.event.QuizEndedEvent;
import com.cluesday.scoreboard.event.ScoreChangedEvent;
import com.cluesday.scoreboard.model.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class QuizService {

	private static final int ROUND_6 = 6;

	private static final int DEFAULT_QUESTIONS = 5;

	private final ApplicationEventPublisher events;

	private volatile QuizSession activeSession;

	private final ConcurrentHashMap<String, Team> teams = new ConcurrentHashMap<>();

	// "teamId:roundNum:questionNum" → points
	private final ConcurrentHashMap<String, Double> scores = new ConcurrentHashMap<>();

	// "teamId:roundNum" → joker played
	private final ConcurrentHashMap<String, Boolean> jokers = new ConcurrentHashMap<>();

	// roundNum → question count
	private final ConcurrentHashMap<Integer, Integer> questionsPerRound = new ConcurrentHashMap<>();

	// rounds the quizmaster has marked as complete
	private final ConcurrentHashMap<Integer, Boolean> completedRounds = new ConcurrentHashMap<>();

	private final CopyOnWriteArrayList<QuizSnapshot> history = new CopyOnWriteArrayList<>();

	public QuizService(ApplicationEventPublisher events) {
		this.events = events;
	}

	// ── Session lifecycle ─────────────────────────────────────────────────────

	public boolean hasActiveSession() {
		return activeSession != null && activeSession.active();
	}

	public Optional<QuizSession> getActiveSession() {
		return Optional.ofNullable(activeSession);
	}

	public QuizSession createSession(int sessionNumber, String quizmasterName, LocalDate quizDate, int maxRounds,
			double defaultPointsPerQuestion) {
		var session = new QuizSession(UUID.randomUUID().toString(), sessionNumber, quizmasterName.trim(), quizDate,
				maxRounds, defaultPointsPerQuestion, true);
		activeSession = session;
		teams.clear();
		scores.clear();
		jokers.clear();
		questionsPerRound.clear();
		completedRounds.clear();
		for (int r = 1; r <= maxRounds; r++) {
			questionsPerRound.put(r, r == ROUND_6 ? 0 : DEFAULT_QUESTIONS);
		}
		return session;
	}

	public void endQuiz() {
		if (activeSession == null)
			return;
		history.add(buildSnapshot());
		clearState();
		events.publishEvent(new QuizEndedEvent(this));
	}

	public void resetSession() {
		clearState();
	}

	private void clearState() {
		activeSession = null;
		teams.clear();
		scores.clear();
		jokers.clear();
		questionsPerRound.clear();
		completedRounds.clear();
	}

	// ── Teams ─────────────────────────────────────────────────────────────────

	public Team addTeam(String name, Integer tableNumber) {
		String resolvedName = (name != null && !name.isBlank()) ? name.trim()
				: (tableNumber != null ? "Table " + tableNumber : "Unknown");
		var team = new Team(UUID.randomUUID().toString(), resolvedName, tableNumber);
		teams.put(team.id(), team);
		return team;
	}

	/**
	 * Replaces all standard tables (numbers 1–25 with auto-generated name) with the given
	 * selection. Custom teams (table numbers outside 1-25 or with custom names) are
	 * preserved.
	 */
	public void setStandardTables(List<Integer> tableNumbers) {
		teams.entrySet().removeIf(e -> {
			Integer tn = e.getValue().tableNumber();
			return tn != null && tn >= 1 && tn <= 25 && e.getValue().name().equals("Table " + tn);
		});
		if (tableNumbers != null) {
			for (int n : tableNumbers) {
				var team = new Team(UUID.randomUUID().toString(), "Table " + n, n);
				teams.put(team.id(), team);
			}
		}
	}

	public List<Team> getTeams() {
		return teams.values()
			.stream()
			.sorted(Comparator.comparingInt(t -> t.tableNumber() != null ? t.tableNumber() : Integer.MAX_VALUE))
			.toList();
	}

	/** Returns the set of standard table numbers (1-25) currently registered. */
	public Set<Integer> getActiveStandardTables() {
		Set<Integer> active = new HashSet<>();
		for (Team t : teams.values()) {
			if (t.tableNumber() != null && t.tableNumber() >= 1 && t.tableNumber() <= 25
					&& t.name().equals("Table " + t.tableNumber())) {
				active.add(t.tableNumber());
			}
		}
		return active;
	}

	// ── Questions per round ───────────────────────────────────────────────────

	public int getQuestionsForRound(int roundNum) {
		return questionsPerRound.getOrDefault(roundNum, 0);
	}

	public int addQuestionToRound(int roundNum) {
		return questionsPerRound.merge(roundNum, 1, Integer::sum);
	}

	public void setQuestionsForRound(int roundNum, int count) {
		questionsPerRound.put(roundNum, count);
	}

	// ── Round completion tracking ─────────────────────────────────────────────

	public boolean isRoundComplete(int roundNum) {
		return completedRounds.getOrDefault(roundNum, false);
	}

	public void markRoundComplete(int roundNum, boolean complete) {
		if (complete) {
			completedRounds.put(roundNum, true);
		}
		else {
			completedRounds.remove(roundNum);
		}
		events.publishEvent(new ScoreChangedEvent(this));
	}

	public Set<Integer> getCompletedRounds() {
		return Collections.unmodifiableSet(completedRounds.keySet());
	}

	// ── Scoring ───────────────────────────────────────────────────────────────

	public void setScore(String teamId, int roundNum, int questionNum, double points) {
		scores.put(scoreKey(teamId, roundNum, questionNum), points);
		events.publishEvent(new ScoreChangedEvent(this));
	}

	public void setJoker(String teamId, int roundNum, boolean played) {
		if (played) {
			jokers.put(jokerKey(teamId, roundNum), true);
		}
		else {
			jokers.remove(jokerKey(teamId, roundNum));
		}
		events.publishEvent(new ScoreChangedEvent(this));
	}

	public boolean isJokerPlayed(String teamId, int roundNum) {
		return jokers.getOrDefault(jokerKey(teamId, roundNum), false);
	}

	public double getScore(String teamId, int roundNum, int questionNum) {
		return scores.getOrDefault(scoreKey(teamId, roundNum, questionNum), 0.0);
	}

	// ── Leaderboard ───────────────────────────────────────────────────────────

	public List<TeamResult> computeLeaderboard() {
		if (activeSession == null)
			return List.of();
		return teams.values()
			.stream()
			.map(this::computeTeamResult)
			.sorted(Comparator.comparingDouble(TeamResult::grandTotal)
				.reversed()
				.thenComparingInt(r -> r.tableNumber() != null ? r.tableNumber() : Integer.MAX_VALUE))
			.toList();
	}

	private TeamResult computeTeamResult(Team team) {
		int maxRounds = activeSession.maxRounds();
		Map<Integer, Double> roundTotals = new LinkedHashMap<>();
		Set<Integer> jokerRounds = new HashSet<>();

		for (int r = 1; r <= maxRounds; r++) {
			int qCount = questionsPerRound.getOrDefault(r, 0);
			double raw = 0;
			for (int q = 1; q <= qCount; q++) {
				raw += scores.getOrDefault(scoreKey(team.id(), r, q), 0.0);
			}
			boolean joker = jokers.getOrDefault(jokerKey(team.id(), r), false);
			double total = joker ? raw * 2 : raw;
			roundTotals.put(r, total);
			if (joker)
				jokerRounds.add(r);
		}

		double grand = roundTotals.values().stream().mapToDouble(Double::doubleValue).sum();
		return new TeamResult(team.id(), team.name(), team.tableNumber(), Collections.unmodifiableMap(roundTotals),
				Collections.unmodifiableSet(jokerRounds), grand);
	}

	// ── History ───────────────────────────────────────────────────────────────

	public List<QuizSnapshot> getHistory() {
		return Collections.unmodifiableList(history);
	}

	public Optional<QuizSnapshot> findSnapshot(String uuid) {
		return history.stream().filter(s -> s.sessionUuid().equals(uuid)).findFirst();
	}

	// ── Internals ─────────────────────────────────────────────────────────────

	private QuizSnapshot buildSnapshot() {
		return new QuizSnapshot(activeSession.name(), activeSession.uuid(), LocalDateTime.now(), computeLeaderboard());
	}

	private static String scoreKey(String teamId, int roundNum, int questionNum) {
		return teamId + ":" + roundNum + ":" + questionNum;
	}

	private static String jokerKey(String teamId, int roundNum) {
		return teamId + ":" + roundNum;
	}

}
