package com.cluesday.scoreboard.service;

import com.cluesday.scoreboard.event.QuizEndedEvent;
import com.cluesday.scoreboard.event.ScoreChangedEvent;
import com.cluesday.scoreboard.model.QuizSession;
import com.cluesday.scoreboard.model.QuizSnapshot;
import com.cluesday.scoreboard.model.RoundType;
import com.cluesday.scoreboard.model.Team;
import com.cluesday.scoreboard.model.TeamResult;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
public class QuizService {

	private static final int DEFAULT_QUESTIONS = 5;

	private final ApplicationEventPublisher events;

	private volatile QuizSession activeSession;

	private final Map<String, Team> teams = new ConcurrentHashMap<>();

	// "teamId:roundNum:questionNum" → points
	private final Map<String, Double> scores = new ConcurrentHashMap<>();

	// "teamId:roundNum" → direct total override (bypasses per-Q scoring)
	private final Map<String, Double> roundTotalOverrides = new ConcurrentHashMap<>();

	// "teamId:roundNum" → questionNum with joker (0 = no joker)
	private final Map<String, Integer> jokers = new ConcurrentHashMap<>();

	// "roundNum:questionNum" → configured max points
	private final Map<String, Double> questionMaxPoints = new ConcurrentHashMap<>();

	// "roundNum:questionNum" → configured min points (for DYNAMIC rounds)
	private final Map<String, Double> questionMinPoints = new ConcurrentHashMap<>();

	// roundNum → question count
	private final Map<Integer, Integer> questionsPerRound = new ConcurrentHashMap<>();

	// roundNum → round type
	private final Map<Integer, RoundType> roundTypes = new ConcurrentHashMap<>();

	// rounds the quizmaster has marked as complete
	private final Map<Integer, Boolean> completedRounds = new ConcurrentHashMap<>();

	private final List<QuizSnapshot> history = new CopyOnWriteArrayList<>();

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
			double defaultPointsPerQuestion, List<Integer> roundQuestions, List<String> roundTypeList,
			Map<String, Double> questionPointOverrides, Map<String, Double> questionMinOverrides) {
		var session = new QuizSession(UUID.randomUUID().toString(), sessionNumber, quizmasterName.trim(), quizDate,
				maxRounds, defaultPointsPerQuestion, true);
		activeSession = session;
		clearMutableState();
		initRoundConfig(maxRounds, roundQuestions, roundTypeList);
		if (questionPointOverrides != null) {
			questionMaxPoints.putAll(questionPointOverrides);
		}
		if (questionMinOverrides != null) {
			questionMinPoints.putAll(questionMinOverrides);
		}
		return session;
	}

	private void initRoundConfig(int maxRounds, List<Integer> roundQuestions, List<String> roundTypeList) {
		for (int r = 1; r <= maxRounds; r++) {
			questionsPerRound.put(r, resolveQuestionCount(roundQuestions, r));
			roundTypes.put(r, resolveRoundType(roundTypeList, r));
		}
	}

	private static int resolveQuestionCount(List<Integer> roundQuestions, int roundIndex) {
		if (roundQuestions == null || roundIndex > roundQuestions.size()) {
			return DEFAULT_QUESTIONS;
		}
		Integer value = roundQuestions.get(roundIndex - 1);
		return value != null ? Math.max(0, value) : DEFAULT_QUESTIONS;
	}

	private static RoundType resolveRoundType(List<String> roundTypeList, int roundIndex) {
		if (roundTypeList == null || roundIndex > roundTypeList.size()) {
			return RoundType.NORMAL;
		}
		return RoundType.fromString(roundTypeList.get(roundIndex - 1));
	}

	public void endQuiz() {
		if (activeSession == null) {
			return;
		}
		history.add(buildSnapshot());
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
		scores.clear();
		roundTotalOverrides.clear();
		jokers.clear();
		questionMaxPoints.clear();
		questionMinPoints.clear();
		questionsPerRound.clear();
		roundTypes.clear();
		completedRounds.clear();
	}

	// ── Teams ─────────────────────────────────────────────────────────────────

	public Team addTeam(Integer tableNumber) {
		var team = new Team(UUID.randomUUID().toString(), tableNumber);
		teams.put(team.id(), team);
		return team;
	}

	public void setStandardTables(List<Integer> tableNumbers) {
		teams.entrySet().removeIf(e -> {
			Integer tn = e.getValue().tableNumber();
			return tn != null && tn >= 1 && tn <= 25;
		});
		if (tableNumbers != null) {
			tableNumbers.forEach(n -> {
				var team = new Team(UUID.randomUUID().toString(), n);
				teams.put(team.id(), team);
			});
		}
	}

	public List<Team> getTeams() {
		return teams.values()
			.stream()
			.sorted(Comparator.comparingInt(t -> t.tableNumber() != null ? t.tableNumber() : Integer.MAX_VALUE))
			.toList();
	}

	public Set<Integer> getActiveStandardTables() {
		return teams.values()
			.stream()
			.map(Team::tableNumber)
			.filter(tn -> tn != null && tn >= 1 && tn <= 25)
			.collect(Collectors.toSet());
	}

	// ── Questions per round ───────────────────────────────────────────────────

	public int getQuestionsForRound(int roundNum) {
		return questionsPerRound.getOrDefault(roundNum, 0);
	}

	public int addQuestionToRound(int roundNum) {
		return questionsPerRound.merge(roundNum, 1, Integer::sum);
	}

	public void removeQuestionFromRound(int roundNum) {
		questionsPerRound.compute(roundNum, (k, v) -> (v != null && v > 1) ? v - 1 : 0);
	}

	// ── Round types ───────────────────────────────────────────────────────────

	public RoundType getRoundType(int roundNum) {
		return roundTypes.getOrDefault(roundNum, RoundType.NORMAL);
	}

	// ── Question points config ────────────────────────────────────────────────

	public double getQuestionMaxPoints(int roundNum, int questionNum) {
		Double override = questionMaxPoints.get(roundNum + ":" + questionNum);
		if (override != null) {
			return override;
		}
		return activeSession != null ? activeSession.defaultPointsPerQuestion() : 1.0;
	}

	public double getQuestionMinPoints(int roundNum, int questionNum) {
		Double min = questionMinPoints.get(roundNum + ":" + questionNum);
		if (min != null) {
			return min;
		}
		RoundType type = getRoundType(roundNum);
		return type == RoundType.NEGATIVE ? -1.0 : 0.0;
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
		// Clear any direct total override for this round since per-Q scoring is active
		roundTotalOverrides.remove(teamId + ":" + roundNum);
		events.publishEvent(new ScoreChangedEvent(this));
	}

	public void setRoundTotal(String teamId, int roundNum, double total) {
		roundTotalOverrides.put(teamId + ":" + roundNum, total);
		// Clear per-Q scores for this round
		int qCount = getQuestionsForRound(roundNum);
		for (int q = 1; q <= qCount; q++) {
			scores.remove(scoreKey(teamId, roundNum, q));
		}
		events.publishEvent(new ScoreChangedEvent(this));
	}

	public void clearRoundTotal(String teamId, int roundNum) {
		roundTotalOverrides.remove(teamId + ":" + roundNum);
		events.publishEvent(new ScoreChangedEvent(this));
	}

	public Double getRoundTotalOverride(String teamId, int roundNum) {
		return roundTotalOverrides.get(teamId + ":" + roundNum);
	}

	public void setJoker(String teamId, int roundNum, int questionNum) {
		if (questionNum <= 0) {
			jokers.remove(jokerKey(teamId, roundNum));
		}
		else {
			jokers.put(jokerKey(teamId, roundNum), questionNum);
		}
		events.publishEvent(new ScoreChangedEvent(this));
	}

	public int getJokerQuestion(String teamId, int roundNum) {
		return jokers.getOrDefault(jokerKey(teamId, roundNum), 0);
	}

	public boolean hasScore(String teamId, int roundNum, int questionNum) {
		return scores.containsKey(scoreKey(teamId, roundNum, questionNum));
	}

	public double getScore(String teamId, int roundNum, int questionNum) {
		return scores.getOrDefault(scoreKey(teamId, roundNum, questionNum), 0.0);
	}

	// ── Leaderboard ───────────────────────────────────────────────────────────

	public List<TeamResult> computeLeaderboard() {
		if (activeSession == null) {
			return List.of();
		}
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
			Double override = roundTotalOverrides.get(team.id() + ":" + r);
			if (override != null) {
				roundTotals.put(r, override);
				continue;
			}
			int qCount = questionsPerRound.getOrDefault(r, 0);
			int jokerQ = jokers.getOrDefault(jokerKey(team.id(), r), 0);
			double raw = 0.0;
			for (int q = 1; q <= qCount; q++) {
				double score = scores.getOrDefault(scoreKey(team.id(), r, q), 0.0);
				if (q == jokerQ) {
					score *= 2;
				}
				raw += score;
			}
			roundTotals.put(r, raw);
			if (jokerQ > 0) {
				jokerRounds.add(r);
			}
		}

		double grand = roundTotals.values().stream().mapToDouble(Double::doubleValue).sum();
		return new TeamResult(team.id(), team.tableNumber(), Collections.unmodifiableMap(roundTotals),
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
