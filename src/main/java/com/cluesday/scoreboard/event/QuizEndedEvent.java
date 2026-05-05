package com.cluesday.scoreboard.event;

import org.springframework.context.ApplicationEvent;

public class QuizEndedEvent extends ApplicationEvent {

	public QuizEndedEvent(Object source) {
		super(source);
	}

}
