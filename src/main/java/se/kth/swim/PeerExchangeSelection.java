package se.kth.swim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class PeerExchangeSelection {
	public static List<Peer> getPeers(List<Member> members, Integer size,
			Integer maxInfection) {
		List<Peer> ret = new ArrayList<>();

		List<Member> selectables = members.stream()
				.filter(x -> x.getInfectionTime() <= maxInfection)
				.collect(Collectors.toList());

		while (ret.size() < Math.min(selectables.size(), size)) {
			ret.add(selectables.get(ret.size()).getPeer());
		}

		return ret;
	}

  /**
   * Merges the received piggybacked information with the membership list.
   * @param self Peer that invokes the merge operation.
   * @param localView Membership list of the peer.
   * @param receivedView View received in the piggybacked information.
   * @return The new merged membership list.
   */
	public static List<Member> merge(Peer self, List<Member> localView,
			List<Peer> receivedView) {
		
		Integer minPinged = getMinimumPinged(localView);
		
		List<Member> ret = new ArrayList<>();
		List<Peer> listViewPeers = localView.stream().map(y -> y.getPeer())
				.collect(Collectors.toList());

		receivedView.forEach(x -> {
			if (x.equals(self)) {
				// If the state is different and the incarnation less or
				// equal...
				if (!x.getState().equals(NodeState.ALIVE)) {
					// ... we reset the infection (so we spread fresher info)
					// and increase the incarnation
					self.setState(NodeState.ALIVE);
					self.setIncarnation(x.getIncarnation() + 1);
				}

				Member tmpPeer = new Member(self);
				tmpPeer.setPingedTimes(Integer.MAX_VALUE);
				ret.add(tmpPeer);
			} else if (listViewPeers.contains(x)) {
				Peer peer = listViewPeers.get(listViewPeers.indexOf(x));
				Member tmpPeer;
				if (x.getState().equals(NodeState.ALIVE)
						&& x.getIncarnation() > peer.getIncarnation()) {
					// ...
					tmpPeer = new Member(x);
					tmpPeer.setPingedTimes(minPinged);
					ret.add(tmpPeer);
			} else if (x.getState().equals(NodeState.SUSPECTED)
					&& peer.getState().equals(NodeState.SUSPECTED)
					&& x.getIncarnation() > peer.getIncarnation()) {
				// ...
				tmpPeer = new Member(x);
				tmpPeer.setPingedTimes(minPinged);
				ret.add(tmpPeer);
			} else if (x.getState().equals(NodeState.SUSPECTED)
					&& peer.getState().equals(NodeState.ALIVE)
					&& x.getIncarnation() >= peer.getIncarnation()) {
				// ...
				tmpPeer = new Member(x);
				tmpPeer.setPingedTimes(minPinged);
				ret.add(tmpPeer);
			} else if (x.getState().equals(NodeState.DEAD)
					&& (peer.getState().equals(NodeState.ALIVE) || peer
							.getState().equals(NodeState.SUSPECTED))) {
				// ...
				tmpPeer = new Member(x);
				tmpPeer.setPingedTimes(minPinged);
				ret.add(tmpPeer);
			}
		} else {
			// If we don't have it in our members list we just add it.
			
			Member tmpPeer = new Member(x);
			tmpPeer.setPingedTimes(minPinged);
			ret.add(tmpPeer);
		}
	})	;

		// Appends the rest of the members if they are not updated from the
		// piggyback.
		localView.forEach(x -> {
			if (!ret.contains(x)) {
				ret.add(x);
			}
		});

		// Eventually it returns, hopefully.
		return ret;
	}

	/**
	 * Returns the list of peers selectable for being pinged (Not self, alive
	 * and suspected)
	 * 
	 * @param self
	 *            Peer that wants to invoke the ping.
	 * @param members
	 *            Members list of the peer.
	 * @return List of peers eligible for ping.
	 */
	public static List<Member> getPingableTargets(Peer self,
			List<Member> members) {

		List<Member> membersWself = members.stream().filter(x -> !x.getPeer().equals(self)).collect(Collectors.toList());
		
		
		Integer minPingedValue = getMinimumPinged(membersWself);

		return members
				.stream()
				.filter(x -> !x.getPeer().equals(self)
						&& (x.getPeer().getState().equals(NodeState.SUSPECTED) || x
								.getPeer().getState().equals(NodeState.ALIVE))
						&& x.getPingedTimes().equals(minPingedValue))
				.collect(Collectors.toList());
	}

  /**
   * Returns the sorting policy to get members with lowest ping time
   * @param members The membership list
   * @return Integer number.
   */
	private static Integer getMinimumPinged(List<Member> members) {

		Collections.sort(members, new Comparator<Member>() {
			public int compare(Member o1, Member o2) {
				if (o1.getPingedTimes() < o2.getPingedTimes()) {
					return -1;
				} else if (o1.getPingedTimes() > o2.getPingedTimes()) {
					return 1;
				} else {
					return 0;
				}
			};
		});

		return members.get(0).getPingedTimes();
	}

	/**
	 * Increments the infection time of the piggybacked nodes.
	 * 
	 * @param list
	 *            Membership list to update.
	 * @param piggyback
	 *            Piggybacked information received.
	 */
	public static void updateInfectionTime(List<Member> list,
			List<Peer> piggyback) {
		list.forEach(x -> {
			if (piggyback.contains(x.getPeer())) {
				x.incInfectionTime();
			}
		});
	}

	/**
	 * Returns a Membership list of random peer for the indirect ping.
	 * 
	 * @param list
	 *            The membership list.
	 * @param size
	 *            The maximum size of the returned membership list of peers.
	 * @return Membership list of peers.
	 */
	public static List<Peer> getIndirectPingPeers(List<Member> list, int size,
			Random rand, Peer self) {
		// Returns a list of alive peers.
		List<Peer> alivePeers = list
				.stream()
				.filter(x -> x.getPeer().getState().equals(NodeState.ALIVE)
						&& !x.getPeer().equals(self)).map(x -> x.getPeer())
				.collect(Collectors.toList());

		List<Peer> ret = new ArrayList<>();

		// Peers selection loop.
		while (ret.size() < Math.min(size, alivePeers.size())
				&& ret.size() < list.size()) {

			int rn = rand.nextInt(Math.min(size, alivePeers.size()));

			if (ret.contains(alivePeers.get(rn))) {
				continue;
			}

			ret.add(alivePeers.get(rn));
		}

		return ret;
	}
}