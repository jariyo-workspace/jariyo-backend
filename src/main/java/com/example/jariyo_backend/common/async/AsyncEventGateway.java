package com.example.jariyo_backend.common.async;

public interface AsyncEventGateway {
	void dispatch(AsyncEventOutbox event);
}
