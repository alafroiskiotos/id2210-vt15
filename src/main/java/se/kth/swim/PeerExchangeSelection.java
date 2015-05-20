package se.kth.swim;

import se.sics.p2ptoolbox.util.network.NatedAddress;

public class PeerExchangeSelection {
	public static MembershipList<MembershipListItem> getPeers(NatedAddress target, MembershipList<MembershipListItem> list, int size) {
		MembershipList<MembershipListItem> ret = new MembershipList<MembershipListItem>(size);
		for(int i = 0; i < list.getQueue().getSize() && i < size; i++) {
			NatedAddress item = list.getQueue().getElement(i).getPeer().getNode(); 
			if(item.getId() != target.getId()) {
				ret.getQueue().push(new MembershipListItem(new Peer(item)));
			}
		}
		
		return ret;
	}
}