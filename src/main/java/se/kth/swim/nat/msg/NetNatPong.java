package se.kth.swim.nat.msg;

import java.util.UUID;

import se.kth.swim.msg.net.NetMsg;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class NetNatPong extends NetMsg<NatPong> {
	public NetNatPong(NatedAddress source, NatedAddress destination, UUID HBTimeoutId) {
		super(source, destination, new NatPong(HBTimeoutId));
	}

	public NetNatPong(Header<NatedAddress> header, NatPong content) {
		super(header, content);
	}

	@Override
	public NetMsg copyMessage(Header<NatedAddress> newHeader) {
		return new NetNatPong(newHeader, getContent());
	}
}
