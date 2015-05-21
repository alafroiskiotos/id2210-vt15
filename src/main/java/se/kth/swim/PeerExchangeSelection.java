package se.kth.swim;

import java.util.Collections;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class PeerExchangeSelection {
	public static MembershipList<Peer> getPeers(NatedAddress target,
			MembershipList<MembershipListItem> list, int size) {
		MembershipList<Peer> ret = new MembershipList<Peer>(size);
		for (int i = 0; i < list.getQueue().getQueueSize() && i < size; i++) {
			Peer item = list.getQueue().getElement(i).getPeer();
			if (item.getNode().getId().equals(target.getId())) {
				ret.getQueue().push(item);
			}
		}

		return ret;
	}

	public static MembershipList<MembershipListItem> merge(
			MembershipList<MembershipListItem> localView,
			MembershipList<Peer> receivedView) {
		
		MembershipList<MembershipListItem> ret = new MembershipList<MembershipListItem>(localView.getQueue().getMaxSize());
		
		for (Peer peer : receivedView.getQueue().getList()) {
			ret.getQueue().push(new MembershipListItem(peer));
		}
		
		Collections.sort(ret.getQueue().getList(), new MembershipListStateSortPolicy(NodeState.DEAD));
		
		for (MembershipListItem listItem : localView.getQueue().getList()) {
			if (!ret.getQueue().contains(listItem)) {
				ret.getQueue().push(listItem);
			}
		}
		
		return ret;
	}
}