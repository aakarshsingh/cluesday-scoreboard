package com.cluesday.scoreboard.controller;

import com.cluesday.scoreboard.service.QuizService;
import com.cluesday.scoreboard.service.SseService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Controller
public class PublicController {

	private final QuizService quizService;

	private final SseService sseService;

	public PublicController(QuizService quizService, SseService sseService) {
		this.quizService = quizService;
		this.sseService = sseService;
	}

	@GetMapping("/")
	public String home(Model model) {
		quizService.getActiveSession().ifPresent(s -> model.addAttribute("activeSession", s));
		return "public/home";
	}

	@GetMapping("/live/{sessionNumber}")
	public String scoreboard(@PathVariable int sessionNumber, Model model) {
		var session = quizService.getActiveSession().filter(s -> s.sessionNumber() == sessionNumber);

		if (session.isEmpty()) {
			model.addAttribute("ended", true);
			return "public/scoreboard";
		}

		model.addAttribute("quizSession", session.get());
		model.addAttribute("leaderboard", quizService.computeLeaderboard());
		model.addAttribute("completedRounds", quizService.getCompletedRounds());
		model.addAttribute("sessionNumber", sessionNumber);
		return "public/scoreboard";
	}

	@GetMapping(value = "/live/{sessionNumber}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter stream(@PathVariable int sessionNumber, HttpServletResponse response) {
		response.setHeader("X-Accel-Buffering", "no");
		response.setHeader("Cache-Control", "no-cache, no-store");
		response.setHeader("Connection", "keep-alive");

		var session = quizService.getActiveSession().filter(s -> s.sessionNumber() == sessionNumber);

		if (session.isEmpty()) {
			var dead = new SseEmitter();
			dead.complete();
			return dead;
		}
		return sseService.register();
	}

}
