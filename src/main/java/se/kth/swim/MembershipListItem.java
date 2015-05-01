package se.kth.swim;

public class MembershipListItem {
	private Peer peer;
	private int infectionTime;
	
	public MembershipListItem(Peer peer) {
		this.peer = peer;
		this.infectionTime = 0;
	}

	public int getInfectionTime() {
		return infectionTime;
	}
	
	public Peer getPeer() {
		return peer;
	}

	public void incInfectionTime() {
		infectionTime++;
	}
}