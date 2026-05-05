package com.cluesday.scoreboard.service;

import com.cluesday.scoreboard.repository.AppUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class JpaUserDetailsService implements UserDetailsService {

	private final AppUserRepository repository;

	public JpaUserDetailsService(AppUserRepository repository) {
		this.repository = repository;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		return repository.findByUsername(username)
			.map(u -> User.withUsername(u.getUsername())
				.password(u.getPassword())
				.roles(u.getRole())
				.disabled(!u.isEnabled())
				.build())
			.orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
	}

}
