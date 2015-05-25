package se.kth.swim;

import java.util.Comparator;

/**
 * 
 * @author Lorenzo Corneo and Antonios Kouzoupis
 * 
 * This class makes a MembershipList<MembershipListItem> sortable by a given NodeState
 *
 */
public class MembersStateSortPolicy implements Comparator<Member> {
	private final NodeState state;
	public MembersStateSortPolicy(NodeState state) {
		this.state = state;
	}

	public int compare(Member o1, Member o2) {
		if(o1.getPeer().getState() == state && o2.getPeer().getState() != state) {
			return 1;
		} else if(o2.getPeer().getState() == state && o1.getPeer().getState() != state) {
			return -1;
		}
		// this implies both that => o1.getPeer().getState() == o2.getPeer().getState() && o1.getPeer().getState() == state
		// and the default policy that sorts by the infection time
		return new MembersInfectionSortPolicy().compare(o1, o2);
	}
}