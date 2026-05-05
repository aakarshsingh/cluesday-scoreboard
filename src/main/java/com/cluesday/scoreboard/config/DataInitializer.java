package com.cluesday.scoreboard.config;

import com.cluesday.scoreboard.entity.AppUserEntity;
import com.cluesday.scoreboard.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

	private final AppUserRepository userRepository;

	private final PasswordEncoder encoder;

	@Value("${ADMIN_USER:admin}")
	private String adminUser;

	@Value("${ADMIN_PASS:changeme}")
	private String adminPass;

	public DataInitializer(AppUserRepository userRepository, PasswordEncoder encoder) {
		this.userRepository = userRepository;
		this.encoder = encoder;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (userRepository.count() == 0) {
			userRepository.save(new AppUserEntity(adminUser, encoder.encode(adminPass), "ADMIN"));
		}
	}

}
