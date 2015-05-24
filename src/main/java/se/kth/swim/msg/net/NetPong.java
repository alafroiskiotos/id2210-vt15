package se.kth.swim.msg.net;

import java.util.UUID;

import se.kth.swim.MembershipList;
import se.kth.swim.Peer;
import se.kth.swim.msg.Pong;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class NetPong extends NetMsg<Pong> {

	public NetPong(NatedAddress src, NatedAddress dst, MembershipList<Peer> view, UUID pingTimeoutUUID, Integer counter) {
        super(src, dst, new Pong(view, pingTimeoutUUID, counter));
    }
	
	public NetPong(Header<NatedAddress> header, Pong content) {
		super(header, content);
	}

	@Override
	public NetMsg copyMessage(Header<NatedAddress> newHeader) {
		return new NetPong(newHeader, getContent());
	}

}
