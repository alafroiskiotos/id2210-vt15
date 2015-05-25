package se.kth.swim.nat.msg;

import java.util.UUID;

public class NatPing {
	private final UUID HBTimeoutId;
	
	public NatPing(UUID HBTimeoutId) {
		this.HBTimeoutId = HBTimeoutId;
	}
	
	public UUID getHBTimeoutId() {
		return HBTimeoutId;
	}
}
