package com.cluesday.scoreboard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_result")
public class QuizResultEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String sessionUuid;

	@Column(nullable = false)
	private String sessionName;

	@Column(nullable = false)
	private LocalDateTime completedAt;

	@Column(columnDefinition = "TEXT", nullable = false)
	private String leaderboardJson;

	protected QuizResultEntity() {
	}

	public QuizResultEntity(String sessionUuid, String sessionName, LocalDateTime completedAt, String leaderboardJson) {
		this.sessionUuid = sessionUuid;
		this.sessionName = sessionName;
		this.completedAt = completedAt;
		this.leaderboardJson = leaderboardJson;
	}

	public String getSessionUuid() {
		return sessionUuid;
	}

	public String getSessionName() {
		return sessionName;
	}

	public LocalDateTime getCompletedAt() {
		return completedAt;
	}

	public String getLeaderboardJson() {
		return leaderboardJson;
	}

}
