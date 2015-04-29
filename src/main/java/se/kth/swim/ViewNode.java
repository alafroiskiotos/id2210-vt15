package se.kth.swim;

import se.sics.p2ptoolbox.util.network.NatedAddress;

public class ViewNode {
	
	private final NatedAddress peer;
	private Integer infectedTimes;
	
	public ViewNode(NatedAddress peer) {
		this.peer = peer;
		this.infectedTimes = 0;
	}
	
	public NatedAddress getPeer() {
		return peer;
	}
	
	public Integer getInfectedTimes() {
		return infectedTimes;
	}
	
	public void increaseInfectedTimes() {
		infectedTimes++;
	}
	
	@Override
	public boolean equals(Object other) {
		boolean result = false;
		
		if (other instanceof ViewNode) {
			result = (this.peer.equals(((ViewNode) other).getPeer()));
		}
		
		return result;
	}
	
	@Override
	public int hashCode() {
		return peer.hashCode();
	}
}