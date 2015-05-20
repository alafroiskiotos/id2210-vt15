package se.kth.swim;

import se.sics.p2ptoolbox.util.network.NatedAddress;

public class PeerExchangeSelection {
	public static MembershipList<Peer> getPeers(NatedAddress target, MembershipList<MembershipListItem> list, int size) {
		MembershipList<Peer> ret = new MembershipList<Peer>(size);
		for(int i = 0; i < list.getQueue().getSize() && i < size; i++) {
			NatedAddress item = list.getQueue().getElement(i).getPeer().getNode(); 
			if(item.getId() != target.getId()) {
				ret.getQueue().push(new Peer(item));
			}
		}
		
		return ret;
	}
}