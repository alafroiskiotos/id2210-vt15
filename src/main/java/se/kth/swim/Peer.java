package se.kth.swim;

import se.sics.p2ptoolbox.util.network.NatedAddress;

public class Peer {
	private NatedAddress node;
	private NodeState state;
	
	public Peer(NatedAddress node) {
		this.node = node;
	}
	
	public NatedAddress getNode() {
		return node;
	}

	public NodeState getState() {
		return state;
	}

	public void setState(NodeState state) {
		this.state = state;
	}
	
	@Override
	public String toString() {
		return node.toString();
	}
}