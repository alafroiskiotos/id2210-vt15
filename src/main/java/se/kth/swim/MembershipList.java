package se.kth.swim;

public class MembershipList<C> {
	private FifoQueue<C> queue;
	
	public MembershipList(int size) {
		this.queue = new FifoQueue<C>(size);
	}

	public FifoQueue<C> getQueue() {
		return queue;
	}
}
