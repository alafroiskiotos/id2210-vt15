package se.kth.swim.msg.net;

import se.kth.swim.MembershipList;
import se.kth.swim.Peer;
import se.kth.swim.msg.Pong;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class NetPong extends NetMsg<Pong> {

	public NetPong(NatedAddress src, NatedAddress dst, MembershipList<Peer> view) {
        super(src, dst, new Pong(view));
    }
	
	public NetPong(Header<NatedAddress> header, Pong content) {
		super(header, content);
	}

	@Override
	public NetMsg copyMessage(Header<NatedAddress> newHeader) {
		return new NetPong(newHeader, getContent());
	}

}
