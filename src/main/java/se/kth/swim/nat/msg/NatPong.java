package se.kth.swim.nat.msg;

import java.util.UUID;

public class NatPong {
	private final UUID HBTimeoutId;
	
	public NatPong(UUID HBTimeoutId) {
		this.HBTimeoutId = HBTimeoutId;
	}
	
	public UUID getHBTimeoutId() {
		return HBTimeoutId;
	}
}
