package se.kth.swim.msg;

import java.util.List;
import java.util.UUID;
import se.kth.swim.Peer;

public class Pong extends MessageCounter {
	private final List<Peer> view;
	private final UUID pingTimeoutUUID;
	
	public Pong(List<Peer> view, UUID pingTimeoutUUID, Integer counter) {
		super(counter);
		this.view = view;
		this.pingTimeoutUUID = pingTimeoutUUID;
	}
	
	public List<Peer> getView() {
		return view;
	}
	
	public UUID getPingTimeoutUUID() {
		return pingTimeoutUUID;
	}
}