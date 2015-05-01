package se.kth.swim;

import se.sics.p2ptoolbox.util.network.NatedAddress;

public class Node {
	private NatedAddress node;
	
	public Node(NatedAddress node) {
		this.node = node;
	}
	
	public NatedAddress getNode() {
		return node;
	}
}
