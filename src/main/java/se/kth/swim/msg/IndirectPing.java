package se.kth.swim.msg;

import java.util.UUID;

import se.kth.swim.Peer;

public class IndirectPing {
	private final Peer peer;
	private final UUID deadPingTimeout;
	
	public IndirectPing(Peer peer, UUID deadPingTimeout) {
		this.peer = peer;
		this.deadPingTimeout = deadPingTimeout;
	}

	public Peer getPeer() {
		return peer;
	}

	public UUID getDeadPingTimeout() {
		return deadPingTimeout;
	}
}
