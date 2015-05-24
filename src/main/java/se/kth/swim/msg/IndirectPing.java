package se.kth.swim.msg;

import java.util.UUID;
import se.kth.swim.Peer;

public class IndirectPing extends MessageCounter {
  private final Peer indirectPingRequester;
	private final UUID deadPingTimeout;
	
	public IndirectPing(Peer requester, UUID deadPingTimeout, Integer counter) {
		super(counter);
		this.indirectPingRequester = requester;
    this.deadPingTimeout = deadPingTimeout;
	}
  
  public Peer getIndirectPingRequester() {
    return indirectPingRequester;
  }

	public UUID getDeadPingTimeout() {
		return deadPingTimeout;
	}
}