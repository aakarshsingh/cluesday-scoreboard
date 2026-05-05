package com.cluesday.scoreboard.event;

import org.springframework.context.ApplicationEvent;

public class ScoreChangedEvent extends ApplicationEvent {

	public ScoreChangedEvent(Object source) {
		super(source);
	}

}
