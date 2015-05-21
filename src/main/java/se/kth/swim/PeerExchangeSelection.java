package se.kth.swim;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
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
  
  /**
   * Returns a Membership list of random peer for the indirect ping.
   * @param list The membership list.
   * @param size The maximum size of the returned membership list of peers.
   * @return Membership list of peers.
   */
  public static MembershipList<Peer> getIndirectPingPeers(MembershipList<MembershipListItem> list, int size) {
    // Returns a list of alive peers.
    List<Peer> alivePeers = list.getQueue().getList().stream()
            .filter(x -> x.getPeer().getState().equals(NodeState.ALIVE))
            .map(x -> x.getPeer())
            .collect(Collectors.toList());
    
    MembershipList<Peer> ret = new MembershipList<>(size);
    
    Random r = new Random();
    
    // Peers selection loop.
    while(ret.getQueue().getQueueSize() < size && 
            ret.getQueue().getQueueSize() < list.getQueue().getQueueSize()) {
      
      
      int rn = r.nextInt(Math.max(size, list.getQueue().getQueueSize()));
      
      if(ret.getQueue().contains(list.getQueue().getElement(rn).getPeer())) {
        continue;
      }
      
      ret.getQueue().push(list.getQueue().getElement(rn).getPeer());
    }
    
    return ret;
  }
}