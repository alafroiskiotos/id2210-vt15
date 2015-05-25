package se.kth.swim;

public class Member {
	private final Peer peer;
	private Integer infectionTime;
	private Integer sequenceNumber;
	
	public Member(Peer peer) {
		this.peer = peer;
		this.infectionTime = 0;
		this.sequenceNumber = 0;
	}

	public Integer getInfectionTime() {
		return infectionTime;
	}
	
	public Integer getSequenceNumber() {
		return sequenceNumber;
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
  
  public void setInfectionTime(int value) {
    infectionTime = value;
  }
  
  public boolean isCausal(Integer receivedSeqNum) {
    return receivedSeqNum > sequenceNumber;
  }

  public void setSequenceNumber(Integer sequenceNumber) {
    this.sequenceNumber = sequenceNumber;
  }
	
	@Override
	public String toString() {
  return "{" + peer.toString() + ",inf->" + infectionTime + "}";
	}
	
	@Override
	public boolean equals(Object other) {
		if (other instanceof Member) {
			return this.getPeer().equals(((Member) other).getPeer());
		}
		
		return false;
	}
	
	@Override
	public int hashCode() {
		return this.getPeer().hashCode() + infectionTime;
	}
}