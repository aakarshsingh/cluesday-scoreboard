package com.cluesday.scoreboard.controller;

import com.cluesday.scoreboard.service.QuizService;
import com.cluesday.scoreboard.service.SseService;
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

	@GetMapping("/live/{uuid}")
	public String scoreboard(@PathVariable String uuid, Model model) {
		var session = quizService.getActiveSession().filter(s -> s.uuid().equals(uuid));

		if (session.isEmpty()) {
			model.addAttribute("ended", true);
			return "public/scoreboard";
		}

		model.addAttribute("session", session.get());
		model.addAttribute("leaderboard", quizService.computeLeaderboard());
		model.addAttribute("completedRounds", quizService.getCompletedRounds());
		model.addAttribute("uuid", uuid);
		return "public/scoreboard";
	}

	@GetMapping(value = "/live/{uuid}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter stream(@PathVariable String uuid) {
		var session = quizService.getActiveSession().filter(s -> s.uuid().equals(uuid));

		if (session.isEmpty()) {
			var dead = new SseEmitter();
			dead.complete();
			return dead;
		}
		return sseService.register();
	}

}
