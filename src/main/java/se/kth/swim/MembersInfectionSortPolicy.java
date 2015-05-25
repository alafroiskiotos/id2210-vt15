package se.kth.swim;

import java.util.Comparator;

public class MembersInfectionSortPolicy implements Comparator<Member>{

	public MembersInfectionSortPolicy() {}

	@Override
	public int compare(Member o1, Member o2) {
		return o1.getInfectionTime() < o2.getInfectionTime() ? -1 :
			o1.getInfectionTime() == o2.getInfectionTime() ? 0 : 1;
	}
}