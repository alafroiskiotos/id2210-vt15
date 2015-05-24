package se.kth.swim.msg;

import java.util.UUID;
import se.kth.swim.Peer;

public class IndirectPong extends MessageCounter{
	private final Peer initiator;
  private final Peer suspected;
	private final UUID deadTImeout;
	
	public IndirectPong(Peer initiator, Peer suspected, UUID deadTimeout, Integer counter) {
		super(counter);
		this.initiator = initiator;
    this.suspected = suspected;
		this.deadTImeout = deadTimeout;
	}

	public Peer getInitiatorPeer() {
		return initiator;
	}

  public Peer getSuspectedPeer() {
    return suspected;
  }
  
	public UUID getDeadTImeout() {
		return deadTImeout;
	}
}