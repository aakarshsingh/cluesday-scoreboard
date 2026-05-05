package com.cluesday.scoreboard.controller;

import com.cluesday.scoreboard.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class UserAdminController {

	private final UserService userService;

	public UserAdminController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public String list(Model model) {
		model.addAttribute("users", userService.listUsers());
		return "admin/users";
	}

	@PostMapping("/users")
	public String createUser(@RequestParam String username, @RequestParam String password,
			@RequestParam(defaultValue = "QM") String role, RedirectAttributes ra) {
		if (username.isBlank() || password.isBlank()) {
			ra.addFlashAttribute("error", "Username and password are required.");
			return "redirect:/admin";
		}
		try {
			userService.createUser(username, password, role);
			ra.addFlashAttribute("success", "User \"" + username.trim() + "\" created.");
		}
		catch (Exception e) {
			ra.addFlashAttribute("error", "Could not create user: " + e.getMessage());
		}
		return "redirect:/admin";
	}

	@PostMapping("/users/{id}/delete")
	public String deleteUser(@PathVariable Long id, RedirectAttributes ra) {
		try {
			userService.deleteUser(id);
		}
		catch (IllegalStateException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin";
	}

}
