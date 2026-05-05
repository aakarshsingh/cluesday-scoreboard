package com.cluesday.scoreboard.controller;

import com.cluesday.scoreboard.service.QuizService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/quizSetup")
public class AdminController {

	private final QuizService quizService;

	public AdminController(QuizService quizService) {
		this.quizService = quizService;
	}

	// ── Setup ─────────────────────────────────────────────────────────────────

	@GetMapping
	public String setup(Model model) {
		quizService.getActiveSession().ifPresent(s -> model.addAttribute("activeSession", s));
		model.addAttribute("today", LocalDate.now());
		return "admin/setup";
	}

	@PostMapping("/setup")
	public String createSession(@RequestParam int sessionNumber, @RequestParam String quizmasterName,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate quizDate,
			@RequestParam(defaultValue = "6") int maxRounds,
			@RequestParam(defaultValue = "1.0") double defaultPointsPerQuestion,
			@RequestParam(value = "q", required = false) List<Integer> roundQuestions,
			@RequestParam(value = "rt", required = false) List<String> roundTypeList, HttpServletRequest request,
			RedirectAttributes ra) {

		if (quizService.hasActiveSession()) {
			ra.addFlashAttribute("error", "A quiz is already active.");
			return "redirect:/quizSetup";
		}
		Map<String, Double> maxOverrides  = parseParams(request, "qpt_");
		Map<String, Double> minOverrides  = parseParams(request, "qptmin_");
		Map<String, Double> bonusOverrides = parseParams(request, "bonus_");
		quizService.createSession(sessionNumber, quizmasterName, quizDate, maxRounds, defaultPointsPerQuestion,
				roundQuestions, roundTypeList, maxOverrides, minOverrides, bonusOverrides);
		return "redirect:/quizSetup/teams";
	}

	private static Map<String, Double> parseParams(HttpServletRequest request, String prefix) {
		Map<String, Double> result = new HashMap<>();
		var names = request.getParameterNames();
		while (names.hasMoreElements()) {
			String name = names.nextElement();
			if (name.startsWith(prefix)) {
				String key = name.substring(prefix.length()).replace('_', ':');
				try {
					result.put(key, Double.parseDouble(request.getParameter(name)));
				}
				catch (NumberFormatException ignored) {
				}
			}
		}
		return result;
	}

	@PostMapping("/reset")
	public String resetSession() {
		quizService.resetSession();
		return "redirect:/quizSetup";
	}

	// ── Team registration ─────────────────────────────────────────────────────

	@GetMapping("/teams")
	public String teamsPage(Model model) {
		if (!quizService.hasActiveSession()) {
			return "redirect:/quizSetup";
		}
		quizService.getActiveSession().ifPresent(s -> model.addAttribute("quizSession", s));
		model.addAttribute("teams", quizService.getTeams());
		model.addAttribute("activeTables", quizService.getActiveStandardTables());
		return "admin/teams";
	}

	@PostMapping("/teams/set-tables")
	public String setTables(@RequestParam(name = "tables", required = false) List<Integer> tables) {
		quizService.setStandardTables(tables);
		return "redirect:/quizSetup/teams";
	}

	@PostMapping("/teams/add")
	public String addTeam(@RequestParam(required = false) Integer tableNumber, Model model) {
		quizService.addTeam(tableNumber);
		quizService.getActiveSession().ifPresent(s -> model.addAttribute("quizSession", s));
		model.addAttribute("teams", quizService.getTeams());
		model.addAttribute("activeTables", quizService.getActiveStandardTables());
		return "admin/teams :: #team-list";
	}

	// ── Dashboard ─────────────────────────────────────────────────────────────

	@GetMapping("/dashboard")
	public String dashboard(Model model) {
		if (!quizService.hasActiveSession()) {
			return "redirect:/quizSetup";
		}
		quizService.getActiveSession().ifPresent(s -> model.addAttribute("quizSession", s));
		model.addAttribute("teams", quizService.getTeams());
		model.addAttribute("quizService", quizService);
		return "admin/dashboard";
	}

	@PostMapping("/end")
	public String endQuiz() {
		quizService.endQuiz();
		return "redirect:/quizSetup";
	}

	@PostMapping("/discard")
	public String discardQuiz() {
		quizService.resetSession();
		return "redirect:/quizSetup";
	}

	// ── Round question management ─────────────────────────────────────────────

	@PostMapping("/round/{roundNum}/add-question")
	@ResponseBody
	public String addQuestion(@PathVariable int roundNum) {
		int count = quizService.addQuestionToRound(roundNum);
		return String.valueOf(count);
	}

	@PostMapping("/round/{roundNum}/remove-question")
	@ResponseBody
	public String removeQuestion(@PathVariable int roundNum) {
		quizService.removeQuestionFromRound(roundNum);
		return String.valueOf(quizService.getQuestionsForRound(roundNum));
	}

	// ── Round completion ──────────────────────────────────────────────────────

	@PostMapping("/round/{roundNum}/complete")
	@ResponseBody
	public String markRoundComplete(@PathVariable int roundNum, @RequestParam boolean complete) {
		quizService.markRoundComplete(roundNum, complete);
		return "ok";
	}

	// ── Scoring ───────────────────────────────────────────────────────────────

	@PostMapping("/score")
	@ResponseBody
	public String setScore(@RequestParam String teamId, @RequestParam int roundNum, @RequestParam int questionNum,
			@RequestParam double points) {
		quizService.setScore(teamId, roundNum, questionNum, points);
		return "ok";
	}

	@PostMapping("/round-total")
	@ResponseBody
	public String setRoundTotal(@RequestParam String teamId, @RequestParam int roundNum, @RequestParam double total) {
		quizService.setRoundTotal(teamId, roundNum, total);
		return "ok";
	}

	@PostMapping("/round-total/clear")
	@ResponseBody
	public String clearRoundTotal(@RequestParam String teamId, @RequestParam int roundNum) {
		quizService.clearRoundTotal(teamId, roundNum);
		return "ok";
	}

	@PostMapping("/joker")
	@ResponseBody
	public String setJoker(@RequestParam String teamId, @RequestParam int roundNum, @RequestParam int questionNum) {
		quizService.setJoker(teamId, roundNum, questionNum);
		return "ok";
	}

	@PostMapping("/bonus")
	@ResponseBody
	public String setBonus(@RequestParam String teamId, @RequestParam int roundNum,
			@RequestParam boolean awarded) {
		quizService.setBonusAward(teamId, roundNum, awarded);
		return "ok";
	}

	// ── History ───────────────────────────────────────────────────────────────

	@GetMapping("/history")
	public String history(Model model) {
		model.addAttribute("history", quizService.getHistory());
		return "admin/history";
	}

	@GetMapping("/history/{uuid}")
	public String historyDetail(@PathVariable String uuid, Model model) {
		return quizService.findSnapshot(uuid).map(snapshot -> {
			model.addAttribute("snapshot", snapshot);
			return "admin/history-detail";
		}).orElse("redirect:/quizSetup/history");
	}

}
