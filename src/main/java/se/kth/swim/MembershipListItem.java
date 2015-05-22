package se.kth.swim;

public class MembershipListItem {
	private final Peer peer;
	private int infectionTime;
	private Integer sequenceNumber;
	
	public MembershipListItem(Peer peer) {
		this.peer = peer;
		this.infectionTime = 0;
		this.sequenceNumber = 0;
	}

	public int getInfectionTime() {
		return infectionTime;
	}
	
	public Integer getSequenceNumber() {
		return sequenceNumber;
	}
  
	public boolean causalAfter(Integer receivedSeqNum) {
		return receivedSeqNum > sequenceNumber ? true : false;
	}
	
  public void resetInfectionTime() {
    infectionTime = 0;
  }
	
	public Peer getPeer() {
		return peer;
	}

	public void incInfectionTime() {
		infectionTime++;
	}
	
	@Override
	public String toString() {
		return peer.getNode().toString() + ", InfectionTime -> " + infectionTime + ", STATE: " + peer.getState();
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