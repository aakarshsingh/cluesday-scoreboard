package com.cluesday.scoreboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

/**
 * Provides a separate TemplateEngine for programmatic (non-web) use in SseService.
 *
 * Spring Boot's default SpringResourceTemplateResolver fails to resolve classpath
 * templates when called outside a web request context inside a fat JAR (Railway).
 * ClassLoaderTemplateResolver uses the thread's ClassLoader directly, which is reliable
 * in all environments.
 */
@Configuration
public class ThymeleafConfig {

	@Bean("sseTemplateEngine")
	public TemplateEngine sseTemplateEngine() {
		var engine = new TemplateEngine();
		engine.addTemplateResolver(classLoaderTemplateResolver());
		return engine;
	}

	private ITemplateResolver classLoaderTemplateResolver() {
		var resolver = new ClassLoaderTemplateResolver();
		resolver.setPrefix("templates/");
		resolver.setSuffix(".html");
		resolver.setCharacterEncoding("UTF-8");
		resolver.setCacheable(true); // cache in production is fine
		resolver.setCheckExistence(false);
		return resolver;
	}

}
