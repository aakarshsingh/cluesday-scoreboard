package com.cluesday.scoreboard.service;

import com.cluesday.scoreboard.event.QuizEndedEvent;
import com.cluesday.scoreboard.event.ScoreChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
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

	private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

	private final QuizService quizService;

	private final TemplateEngine templateEngine;

	public SseService(QuizService quizService, TemplateEngine templateEngine) {
		this.quizService = quizService;
		this.templateEngine = templateEngine;
	}

	public SseEmitter register() {
		var emitter = new SseEmitter(Long.MAX_VALUE);
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
				"<div id=\"scoreboard\" class=\"text-center py-16 text-2xl font-bold\">Quiz has ended — thanks for playing!</div>");
		emitters.forEach(SseEmitter::complete);
		emitters.clear();
	}

	private void broadcastScoreboard() {
		var ctx = new Context();
		ctx.setVariable("leaderboard", quizService.computeLeaderboard());
		ctx.setVariable("completedRounds", quizService.getCompletedRounds());
		quizService.getActiveSession().ifPresent(s -> ctx.setVariable("session", s));
		String html = templateEngine.process("fragments/scoreboard-table :: table", ctx);
		broadcast("score-update", html);
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
