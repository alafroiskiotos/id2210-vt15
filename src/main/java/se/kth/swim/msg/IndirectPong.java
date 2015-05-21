package se.kth.swim.msg;

import java.util.UUID;

import se.kth.swim.Peer;

public class IndirectPong {
	private final Peer peer;
	private final UUID deadTImeout;
	
	public IndirectPong(Peer peer, UUID deadTimeout) {
		this.peer = peer;
		this.deadTImeout = deadTimeout;
	}

	public Peer getPeer() {
		return peer;
	}

	public UUID getDeadTImeout() {
		return deadTImeout;
	}
}
