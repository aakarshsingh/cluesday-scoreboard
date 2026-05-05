package com.cluesday.scoreboard.service;

import com.cluesday.scoreboard.entity.AppUserEntity;
import com.cluesday.scoreboard.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

	private final AppUserRepository repository;

	private final PasswordEncoder encoder;

	public UserService(AppUserRepository repository, PasswordEncoder encoder) {
		this.repository = repository;
		this.encoder = encoder;
	}

	public List<AppUserEntity> listUsers() {
		return repository.findAll();
	}

	@Transactional
	public void createUser(String username, String rawPassword, String role) {
		if (!role.equals("ADMIN") && !role.equals("QM")) {
			throw new IllegalArgumentException("Role must be ADMIN or QM.");
		}
		var user = new AppUserEntity(username.trim(), encoder.encode(rawPassword), role);
		repository.save(user);
	}

	@Transactional
	public void deleteUser(Long id) {
		if (repository.count() <= 1) {
			throw new IllegalStateException("Cannot delete the last user — you would be locked out.");
		}
		repository.deleteById(id);
	}

}
