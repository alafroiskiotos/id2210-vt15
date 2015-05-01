package se.kth.swim.msg;

import se.kth.swim.MembershipList;
import se.kth.swim.Peer;

public class Pong {
	private final MembershipList<Peer> view;
	
	public Pong(MembershipList<Peer> view) {
		this.view = view;
	}
	
	public MembershipList<Peer> getView() {
		return view;
	}
}