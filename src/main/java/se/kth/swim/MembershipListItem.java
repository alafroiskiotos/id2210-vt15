package se.kth.swim;

public class MembershipListItem {
	private final Peer peer;
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
	
	@Override
	public String toString() {
		return peer.getNode().toString();
	}
	
	@Override
	public boolean equals(Object other) {
		if (other instanceof MembershipListItem) {
			return this.getPeer().equals(((MembershipListItem) other).getPeer());
		}
		
		return false;
	}
	
	@Override
	public int hashCode() {
		return this.getPeer().hashCode() + infectionTime;
	}
}