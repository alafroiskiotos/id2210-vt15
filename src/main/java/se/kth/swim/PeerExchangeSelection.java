package se.kth.swim;

import java.util.Collections;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class PeerExchangeSelection {
	public static MembershipList<Peer> getPeers(NatedAddress target,
			MembershipList<MembershipListItem> list, int size) {
		MembershipList<Peer> ret = new MembershipList<Peer>(size);
		for (int i = 0; i < list.getQueue().getQueueSize() && ret.getQueue().getQueueSize() < size; i++) {
			Peer item = list.getQueue().getElement(i).getPeer();
			if (!item.getNode().getId().equals(target.getId())) {
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
  
  /**
   * Increments the infection time of the piggybacked nodes.
   * @param list Membership list to update.
   * @param piggyback Piggybacked information received.
   */
  public static void updateInfectionTime(MembershipList<MembershipListItem> list, MembershipList<Peer> piggyback) {
    list.getQueue().getList().forEach(x -> {
      if(piggyback.getQueue().getList().contains(x.getPeer())) {
        x.incInfectionTime();
      }
    });
  }
  
  /**
   * Sanitizes the list removing DEAD nodes.
   * @param list Membership list to sanitize.
   * @return A new membership list without DEAD nodes.
   */
  public static MembershipList<MembershipListItem> sanitize(MembershipList<MembershipListItem> list) {
    MembershipList<MembershipListItem> ret = new MembershipList<>(list.getQueue().getMaxSize());
    
    list.getQueue().getList().forEach(x -> {
      if(!x.getPeer().getState().equals(NodeState.DEAD)) {
        ret.getQueue().push(x);
      }
    });
    
    return ret;
  }
}