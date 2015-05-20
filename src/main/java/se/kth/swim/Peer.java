package se.kth.swim;

import com.google.common.base.Equivalence;

import se.sics.p2ptoolbox.util.network.NatedAddress;

public class Peer {
	private NatedAddress node;
	private NodeState state;

	public Peer(NatedAddress node, NodeState state) {
		this.node = node;
		this.state = state;
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

	@Override
	public boolean equals(Object other) {
		if (other instanceof Peer) {
			if ((this.getNode().getId()
					.equals(((Peer) other).getNode().getId()))
					&& (this.getState().equals(((Peer) other).getState()))) {
				return true;
			}
		}

		return false;
	}

	@Override
	public int hashCode() {
		return this.getNode().getId() + this.getState().ordinal();
	}
}