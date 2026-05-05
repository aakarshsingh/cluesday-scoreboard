package com.cluesday.scoreboard.controller;

import com.cluesday.scoreboard.service.QuizService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/quizmaster")
public class AdminController {

	private final QuizService quizService;

	public AdminController(QuizService quizService) {
		this.quizService = quizService;
	}

	// ── Setup ─────────────────────────────────────────────────────────────────

	@GetMapping
	public String setup(Model model, Authentication auth) {
		quizService.getActiveSession().ifPresent(s -> model.addAttribute("activeSession", s));
		model.addAttribute("today", LocalDate.now());
		model.addAttribute("isAdmin", auth != null && auth.getAuthorities()
			.stream()
			.anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
		return "admin/setup";
	}

	@PostMapping("/setup")
	public String createSession(@RequestParam int sessionNumber, @RequestParam String quizmasterName,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate quizDate, RedirectAttributes ra) {
		if (quizService.hasActiveSession()) {
			ra.addFlashAttribute("error", "A quiz is already active.");
			return "redirect:/quizmaster";
		}
		quizService.createSession(sessionNumber, quizmasterName, quizDate);
		return "redirect:/quizmaster/teams";
	}

	@PostMapping("/reset")
	public String resetSession() {
		quizService.resetSession();
		return "redirect:/quizmaster";
	}

	// ── Team registration ─────────────────────────────────────────────────────

	@GetMapping("/teams")
	public String teamsPage(Model model) {
		if (!quizService.hasActiveSession()) {
			return "redirect:/quizmaster";
		}
		quizService.getActiveSession().ifPresent(s -> model.addAttribute("quizSession", s));
		model.addAttribute("teams", quizService.getTeams());
		model.addAttribute("activeTables", quizService.getActiveStandardTables());
		return "admin/teams";
	}

	@PostMapping("/teams/set-tables")
	public String setTables(@RequestParam(name = "tables", required = false) List<Integer> tables) {
		quizService.setStandardTables(tables);
		return "redirect:/quizmaster/teams";
	}

	@PostMapping("/teams/add")
	public String addTeam(@RequestParam(required = false) Integer tableNumber,
			@RequestParam(required = false) String customName, Model model) {
		quizService.addTeam(tableNumber, customName);
		quizService.getActiveSession().ifPresent(s -> model.addAttribute("quizSession", s));
		model.addAttribute("teams", quizService.getTeams());
		model.addAttribute("activeTables", quizService.getActiveStandardTables());
		return "admin/teams :: #team-list";
	}

	@PostMapping("/teams/add-during-quiz")
	public String addTeamDuringQuiz(@RequestParam(required = false) Integer tableNumber,
			@RequestParam(required = false) String customName) {
		if (!quizService.hasActiveSession()) {
			return "redirect:/quizmaster";
		}
		quizService.addTeam(tableNumber, customName);
		return "redirect:/quizmaster/dashboard";
	}

	@PostMapping("/teams/rename")
	@ResponseBody
	public String renameTeam(@RequestParam String teamId, @RequestParam String newName) {
		quizService.renameTeam(teamId, newName);
		return "ok";
	}

	@PostMapping("/teams/delete")
	@ResponseBody
	public String deleteTeam(@RequestParam String teamId) {
		quizService.deleteTeam(teamId);
		return "ok";
	}

	// ── Dashboard ─────────────────────────────────────────────────────────────

	@GetMapping("/dashboard")
	public String dashboard(Model model) {
		if (!quizService.hasActiveSession()) {
			return "redirect:/quizmaster";
		}
		quizService.getActiveSession().ifPresent(s -> model.addAttribute("quizSession", s));
		model.addAttribute("teams", quizService.getTeams());
		model.addAttribute("quizService", quizService);
		return "admin/dashboard";
	}

	@PostMapping("/end")
	public String endQuiz() {
		quizService.endQuiz();
		return "redirect:/quizmaster";
	}

	@PostMapping("/discard")
	public String discardQuiz() {
		quizService.resetSession();
		return "redirect:/quizmaster";
	}

	// ── Round completion ──────────────────────────────────────────────────────

	@PostMapping("/round/{roundNum}/complete")
	@ResponseBody
	public String markRoundComplete(@PathVariable int roundNum, @RequestParam boolean complete) {
		quizService.markRoundComplete(roundNum, complete);
		return "ok";
	}

	// ── Scoring ───────────────────────────────────────────────────────────────

	@PostMapping("/round-score")
	@ResponseBody
	public ResponseEntity<String> setRoundScore(@RequestParam String teamId, @RequestParam int roundNum,
			@RequestParam(required = false) String score) {
		if (score == null || score.isBlank()) {
			quizService.setRoundScore(teamId, roundNum, null);
			return ResponseEntity.ok("ok");
		}
		try {
			double val = Double.parseDouble(score.trim());
			if (Double.isNaN(val) || Double.isInfinite(val)) {
				return ResponseEntity.badRequest().body("invalid");
			}
			quizService.setRoundScore(teamId, roundNum, val);
			return ResponseEntity.ok("ok");
		}
		catch (NumberFormatException e) {
			return ResponseEntity.badRequest().body("invalid");
		}
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
		}).orElse("redirect:/quizmaster/history");
	}

}
