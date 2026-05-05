package com.cluesday.scoreboard.service;

import com.cluesday.scoreboard.event.QuizEndedEvent;
import com.cluesday.scoreboard.event.ScoreChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseService {

	private static final Logger log = LoggerFactory.getLogger(SseService.class);

	private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

	private final QuizService quizService;

	private final TemplateEngine templateEngine;

	public SseService(QuizService quizService, @Qualifier("sseTemplateEngine") TemplateEngine templateEngine) {
		this.quizService = quizService;
		this.templateEngine = templateEngine;
	}

	public SseEmitter register() {
		var emitter = new SseEmitter(0L); // 0 = no timeout
		emitter.onCompletion(() -> emitters.remove(emitter));
		emitter.onTimeout(() -> emitters.remove(emitter));
		emitter.onError(e -> emitters.remove(emitter));
		emitters.add(emitter);
		return emitter;
	}

	@EventListener
	public void onScoreChanged(ScoreChangedEvent event) {
		broadcastScoreboard();
	}

	@EventListener
	public void onQuizEnded(QuizEndedEvent event) {
		broadcast("quiz-ended",
				"<div style=\"text-align:center;padding:64px 20px;font-size:24px;font-weight:700;\">Quiz has ended — thanks for playing!</div>");
		emitters.forEach(SseEmitter::complete);
		emitters.clear();
	}

	// Keep Railway/nginx connections alive — comment events are invisible to JS clients
	@Scheduled(fixedDelay = 20_000)
	public void heartbeat() {
		if (emitters.isEmpty()) {
			return;
		}
		List<SseEmitter> dead = new ArrayList<>();
		for (SseEmitter emitter : emitters) {
			try {
				emitter.send(SseEmitter.event().comment("keepalive"));
			}
			catch (IOException e) {
				dead.add(emitter);
			}
		}
		emitters.removeAll(dead);
	}

	private void broadcastScoreboard() {
		if (emitters.isEmpty()) {
			return;
		}
		try {
			var ctx = new Context();
			ctx.setVariable("leaderboard", quizService.computeLeaderboard());
			ctx.setVariable("completedRounds", quizService.getCompletedRounds());
			quizService.getActiveSession().ifPresent(s -> ctx.setVariable("quizSession", s));
			String html = templateEngine.process("fragments/scoreboard-table :: table", ctx);
			broadcast("score-update", html);
		}
		catch (Exception e) {
			log.error("broadcastScoreboard failed: {}", e.getMessage());
		}
	}

	private void broadcast(String eventName, String data) {
		List<SseEmitter> dead = new ArrayList<>();
		for (SseEmitter emitter : emitters) {
			try {
				emitter.send(SseEmitter.event().name(eventName).data(data, MediaType.TEXT_HTML));
			}
			catch (IOException e) {
				dead.add(emitter);
			}
		}
		emitters.removeAll(dead);
	}

}
