package se.kth.swim.msg;

import java.util.UUID;

import se.kth.swim.MembershipList;
import se.kth.swim.Peer;

public class Pong extends MessageCounter {
	private final MembershipList<Peer> view;
	private final UUID pingTimeoutUUID;
	
	public Pong(MembershipList<Peer> view, UUID pingTimeoutUUID, Integer counter) {
		super(counter);
		this.view = view;
		this.pingTimeoutUUID = pingTimeoutUUID;
	}
	
	public MembershipList<Peer> getView() {
		return view;
	}
	
	public UUID getPingTimeoutUUID() {
		return pingTimeoutUUID;
	}
}