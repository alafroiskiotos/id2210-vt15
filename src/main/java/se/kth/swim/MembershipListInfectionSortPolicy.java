package se.kth.swim;

import java.util.Comparator;

public class MembershipListInfectionSortPolicy implements Comparator<MembershipListItem>{

	public MembershipListInfectionSortPolicy() {}

	@Override
	public int compare(MembershipListItem o1, MembershipListItem o2) {
		return o1.getInfectionTime() < o2.getInfectionTime() ? -1 :
			o1.getInfectionTime() == o2.getInfectionTime() ? 0 : 1;
	}
}