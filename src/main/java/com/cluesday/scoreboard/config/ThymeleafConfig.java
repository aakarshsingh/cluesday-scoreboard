package com.cluesday.scoreboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

/**
 * Provides a separate TemplateEngine for programmatic (non-web) use in SseService.
 *
 * Must be a SpringTemplateEngine: a plain TemplateEngine evaluates expressions with OGNL,
 * which is not on the classpath (Spring Boot uses SpEL), so rendering fails with
 * NoClassDefFoundError: ognl/PropertyAccessor.
 *
 * ClassLoaderTemplateResolver reads templates straight from the classpath, so it works
 * outside a web request and inside the fat JAR.
 */
@Configuration
public class ThymeleafConfig {

	@Bean("sseTemplateEngine")
	public TemplateEngine sseTemplateEngine() {
		var engine = new SpringTemplateEngine();
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
