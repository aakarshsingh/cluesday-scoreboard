package com.cluesday.scoreboard.controller;

import com.cluesday.scoreboard.service.QuizService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/admin")
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
			@RequestParam(defaultValue = "1.0") double defaultPointsPerQuestion, RedirectAttributes ra) {

		if (quizService.hasActiveSession()) {
			ra.addFlashAttribute("error", "A quiz is already active.");
			return "redirect:/admin";
		}
		quizService.createSession(sessionNumber, quizmasterName, quizDate, maxRounds, defaultPointsPerQuestion);
		return "redirect:/admin/teams";
	}

	@PostMapping("/reset")
	public String resetSession() {
		quizService.resetSession();
		return "redirect:/admin";
	}

	// ── Team registration ─────────────────────────────────────────────────────

	@GetMapping("/teams")
	public String teamsPage(Model model) {
		if (!quizService.hasActiveSession())
			return "redirect:/admin";
		quizService.getActiveSession().ifPresent(s -> model.addAttribute("session", s));
		model.addAttribute("teams", quizService.getTeams());
		model.addAttribute("activeTables", quizService.getActiveStandardTables());
		return "admin/teams";
	}

	@PostMapping("/teams/set-tables")
	public String setTables(@RequestParam(name = "tables", required = false) List<Integer> tables) {
		quizService.setStandardTables(tables);
		return "redirect:/admin/teams";
	}

	@PostMapping("/teams/add")
	public String addTeam(@RequestParam(required = false) String name,
			@RequestParam(required = false) Integer tableNumber, Model model) {
		quizService.addTeam(name, tableNumber);
		model.addAttribute("teams", quizService.getTeams());
		model.addAttribute("activeTables", quizService.getActiveStandardTables());
		quizService.getActiveSession().ifPresent(s -> model.addAttribute("session", s));
		return "admin/teams :: #team-list";
	}

	// ── Dashboard ─────────────────────────────────────────────────────────────

	@GetMapping("/dashboard")
	public String dashboard(Model model) {
		if (!quizService.hasActiveSession())
			return "redirect:/admin";
		quizService.getActiveSession().ifPresent(s -> model.addAttribute("session", s));
		model.addAttribute("teams", quizService.getTeams());
		model.addAttribute("quizService", quizService);
		return "admin/dashboard";
	}

	@PostMapping("/end")
	public String endQuiz() {
		quizService.endQuiz();
		return "redirect:/admin";
	}

	// ── Round question management ─────────────────────────────────────────────

	@PostMapping("/round/{roundNum}/add-question")
	public String addQuestion(@PathVariable int roundNum) {
		quizService.addQuestionToRound(roundNum);
		return "redirect:/admin/dashboard#round-" + roundNum;
	}

	@PostMapping("/round/{roundNum}/set-questions")
	public String setRoundQuestions(@PathVariable int roundNum, @RequestParam int count) {
		quizService.setQuestionsForRound(roundNum, Math.max(1, count));
		return "redirect:/admin/dashboard#round-" + roundNum;
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

	@PostMapping("/joker")
	@ResponseBody
	public String setJoker(@RequestParam String teamId, @RequestParam int roundNum, @RequestParam boolean played) {
		quizService.setJoker(teamId, roundNum, played);
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
		var snapshot = quizService.findSnapshot(uuid);
		if (snapshot.isEmpty())
			return "redirect:/admin/history";
		model.addAttribute("snapshot", snapshot.get());
		return "admin/history-detail";
	}

}
