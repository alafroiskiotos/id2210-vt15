package se.kth.swim.nat.msg;

import java.util.UUID;

import se.kth.swim.msg.net.NetMsg;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class NetNatPing extends NetMsg<NatPing>{

	public NetNatPing(NatedAddress source, NatedAddress destination, UUID HBTimeoutId){
		super(source, destination, new NatPing(HBTimeoutId));
	}
	
	public NetNatPing(Header<NatedAddress> header, NatPing content) {
		super(header, content);
	}

	@Override
	public NetMsg copyMessage(Header<NatedAddress> newHeader) {
		return new NetNatPing(newHeader, getContent());
	}
}
