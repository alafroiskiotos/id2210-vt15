package se.kth.swim;

import se.sics.p2ptoolbox.util.network.NatedAddress;

public class Peer {
	private final NatedAddress node;
	private NodeState state;
  private int incarnation;

	public Peer(NatedAddress node, NodeState state) {
		this.node = node;
		this.state = state;
	}
  
  public Peer(NatedAddress node, NodeState state, int incarnation) {
    this(node, state);
    this.incarnation = incarnation;
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

  public int getIncarnation() {
    return incarnation;
  }

  public void setIncarnation(int incarnation) {
    this.incarnation = incarnation;
  }

	@Override
	public String toString() {
		return "{" + node.getId() + "," + state + "," + incarnation + "}";
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof Peer) {
			if ((this.getNode().getId().equals(((Peer) other).getNode().getId()))) {
				return true;
			}
		}

		return false;
	}

	@Override
	public int hashCode() {
		return this.getNode().getId();
	}
}