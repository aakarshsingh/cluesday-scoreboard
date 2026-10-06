package com.cluesday.scoreboard.service;

import com.cluesday.scoreboard.entity.QuizResultEntity;
import com.cluesday.scoreboard.event.QuizEndedEvent;
import com.cluesday.scoreboard.event.ScoreChangedEvent;
import com.cluesday.scoreboard.model.QuizSession;
import com.cluesday.scoreboard.model.QuizSnapshot;
import com.cluesday.scoreboard.model.Team;
import com.cluesday.scoreboard.model.TeamResult;
import com.cluesday.scoreboard.repository.QuizResultRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class QuizService {

	private final ApplicationEventPublisher events;

	private final QuizResultRepository quizResultRepository;

	private final ObjectMapper objectMapper;

	private volatile QuizSession activeSession;

	private final Map<String, Team> teams = new ConcurrentHashMap<>();

	// "teamId:roundNum" → score (null key absence = not yet scored)
	private final Map<String, Double> roundScores = new ConcurrentHashMap<>();

	// rounds the quizmaster has marked as complete
	private final Map<Integer, Boolean> completedRounds = new ConcurrentHashMap<>();

	public QuizService(ApplicationEventPublisher events, QuizResultRepository quizResultRepository,
			ObjectMapper objectMapper) {
		this.events = events;
		this.quizResultRepository = quizResultRepository;
		this.objectMapper = objectMapper;
	}

	// ── Session lifecycle ─────────────────────────────────────────────────────

	public boolean hasActiveSession() {
		return activeSession != null && activeSession.active();
	}

	public Optional<QuizSession> getActiveSession() {
		return Optional.ofNullable(activeSession);
	}

	public QuizSession createSession(int sessionNumber, String quizmasterName, LocalDate quizDate) {
		var session = new QuizSession(UUID.randomUUID().toString(), sessionNumber, quizmasterName.trim(), quizDate,
				true);
		activeSession = session;
		clearMutableState();
		return session;
	}

	public void endQuiz() {
		if (activeSession == null) {
			return;
		}
		try {
			String json = objectMapper.writeValueAsString(computeLeaderboard());
			quizResultRepository
				.save(new QuizResultEntity(activeSession.uuid(), activeSession.name(), LocalDateTime.now(), json));
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException("Failed to persist quiz result", e);
		}
		clearState();
		events.publishEvent(new QuizEndedEvent(this));
	}

	public void resetSession() {
		clearState();
	}

	private void clearState() {
		activeSession = null;
		clearMutableState();
	}

	private void clearMutableState() {
		teams.clear();
		roundScores.clear();
		completedRounds.clear();
	}

	// ── Teams ─────────────────────────────────────────────────────────────────

	public Team addTeam(Integer tableNumber, String customName) {
		String cn = (customName != null && !customName.isBlank()) ? customName.trim() : null;
		if (tableNumber == null && cn == null) {
			throw new IllegalArgumentException("Pick a table or enter a team name.");
		}
		if (tableNumber != null && tableNumber < 1) {
			throw new IllegalArgumentException("Table number must be 1 or higher.");
		}
		if (tableNumber != null && teams.values().stream().anyMatch(t -> tableNumber.equals(t.tableNumber()))) {
			throw new IllegalArgumentException("Table " + tableNumber + " is already playing.");
		}
		if (cn == null && Integer.valueOf(25).equals(tableNumber)) {
			cn = "∞";
		}
		var team = putTeam(tableNumber, cn);
		events.publishEvent(new ScoreChangedEvent(this));
		return team;
	}

	private Team putTeam(Integer tableNumber, String customName) {
		var team = new Team(UUID.randomUUID().toString(), tableNumber, customName);
		teams.put(team.id(), team);
		// Backfill all completed rounds with 0 so the team appears on the scoreboard
		completedRounds.keySet().forEach(r -> roundScores.putIfAbsent(team.id() + ":" + r, 0.0));
		return team;
	}

	public void renameTeam(String teamId, String newName) {
		Team current = teams.get(teamId);
		if (current == null) {
			return;
		}
		String cn = (newName != null && !newName.isBlank()) ? newName.trim() : null;
		teams.put(teamId, new Team(teamId, current.tableNumber(), cn));
		events.publishEvent(new ScoreChangedEvent(this));
	}

	public void deleteTeam(String teamId) {
		removeTeam(teamId);
		events.publishEvent(new ScoreChangedEvent(this));
	}

	private void removeTeam(String teamId) {
		teams.remove(teamId);
		roundScores.keySet().removeIf(k -> k.startsWith(teamId + ":"));
	}

	/**
	 * Syncs standard tables (1–25) to the selection. Teams that stay selected keep their
	 * id, name and scores, so this is safe to call mid-quiz.
	 */
	public void setStandardTables(List<Integer> tableNumbers) {
		Set<Integer> selected = tableNumbers == null ? Set.of()
				: tableNumbers.stream().filter(QuizService::isStandardTable).collect(Collectors.toSet());
		teams.values()
			.stream()
			.filter(t -> isStandardTable(t.tableNumber()) && !selected.contains(t.tableNumber()))
			.map(Team::id)
			.toList()
			.forEach(this::removeTeam);
		Set<Integer> present = getActiveStandardTables();
		selected.stream().filter(n -> !present.contains(n)).sorted().forEach(n -> putTeam(n, (n == 25) ? "∞" : null));
		events.publishEvent(new ScoreChangedEvent(this));
	}

	private static boolean isStandardTable(Integer tableNumber) {
		return tableNumber != null && tableNumber >= 1 && tableNumber <= 25;
	}

	public List<Team> getTeams() {
		return teams.values()
			.stream()
			.sorted(Comparator.comparingInt(t -> t.tableNumber() != null ? t.tableNumber() : Integer.MAX_VALUE))
			.toList();
	}

	public List<Integer> getFreeStandardTables() {
		Set<Integer> taken = getActiveStandardTables();
		return IntStream.rangeClosed(1, 25).filter(n -> !taken.contains(n)).boxed().toList();
	}

	public Set<Integer> getActiveStandardTables() {
		return teams.values()
			.stream()
			.map(Team::tableNumber)
			.filter(QuizService::isStandardTable)
			.collect(Collectors.toSet());
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

	public void setRoundScore(String teamId, int roundNum, Double score) {
		if (score == null) {
			roundScores.remove(teamId + ":" + roundNum);
		}
		else {
			roundScores.put(teamId + ":" + roundNum, score);
		}
		events.publishEvent(new ScoreChangedEvent(this));
	}

	public double getRoundScore(String teamId, int roundNum) {
		return roundScores.getOrDefault(teamId + ":" + roundNum, 0.0);
	}

	public boolean hasRoundScore(String teamId, int roundNum) {
		return roundScores.containsKey(teamId + ":" + roundNum);
	}

	public String formatRoundScore(String teamId, int roundNum) {
		if (!hasRoundScore(teamId, roundNum)) {
			return "";
		}
		double score = getRoundScore(teamId, roundNum);
		if (score == Math.floor(score) && !Double.isInfinite(score)) {
			return String.valueOf((long) score);
		}
		return String.valueOf(score);
	}

	public double getTeamTotal(String teamId) {
		double sum = 0.0;
		for (int r = 1; r <= QuizSession.MAX_ROUNDS; r++) {
			sum += roundScores.getOrDefault(teamId + ":" + r, 0.0);
		}
		return sum;
	}

	public String formatTeamTotal(String teamId) {
		double total = getTeamTotal(teamId);
		if (total == Math.floor(total) && !Double.isInfinite(total)) {
			return String.valueOf((long) total);
		}
		return String.valueOf(total);
	}

	// ── Leaderboard ───────────────────────────────────────────────────────────

	public List<TeamResult> computeLeaderboard() {
		return leaderboard(r -> true);
	}

	/**
	 * Leaderboard for the public scoreboard: only rounds marked complete count, so scores
	 * entered for an unpublished round don't leak into totals or ranking.
	 */
	public List<TeamResult> computePublicLeaderboard() {
		return leaderboard(this::isRoundComplete);
	}

	private List<TeamResult> leaderboard(IntPredicate includeRound) {
		if (activeSession == null) {
			return List.of();
		}
		return teams.values()
			.stream()
			.map(t -> computeTeamResult(t, includeRound))
			.sorted(Comparator.comparingDouble(TeamResult::grandTotal)
				.reversed()
				.thenComparingInt(r -> r.tableNumber() != null ? r.tableNumber() : Integer.MAX_VALUE))
			.toList();
	}

	private TeamResult computeTeamResult(Team team, IntPredicate includeRound) {
		Map<Integer, Double> roundTotals = new LinkedHashMap<>();
		for (int r = 1; r <= QuizSession.MAX_ROUNDS; r++) {
			// null = not yet scored, or excluded (shows as — on scoreboard)
			roundTotals.put(r, includeRound.test(r) ? roundScores.get(team.id() + ":" + r) : null);
		}
		double grand = roundTotals.values().stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
		return new TeamResult(team.id(), team.tableNumber(), team.customName(),
				Collections.unmodifiableMap(roundTotals), grand);
	}

	// ── History ───────────────────────────────────────────────────────────────

	public List<QuizSnapshot> getHistory() {
		return quizResultRepository.findAllByOrderByCompletedAtDesc().stream().map(this::toSnapshot).toList();
	}

	public Optional<QuizSnapshot> findSnapshot(String uuid) {
		return quizResultRepository.findBySessionUuid(uuid).map(this::toSnapshot);
	}

	private QuizSnapshot toSnapshot(QuizResultEntity entity) {
		try {
			List<TeamResult> leaderboard = objectMapper.readValue(entity.getLeaderboardJson(), new TypeReference<>() {
			});
			return new QuizSnapshot(entity.getSessionName(), entity.getSessionUuid(), entity.getCompletedAt(),
					leaderboard);
		}
		catch (JsonProcessingException e) {
			return new QuizSnapshot(entity.getSessionName(), entity.getSessionUuid(), entity.getCompletedAt(),
					List.of());
		}
	}

}
