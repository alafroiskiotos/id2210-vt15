package se.kth.swim;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class FifoQueue<V> {
	private final Integer size;
	private List<V> queue;
	
	public FifoQueue(Integer size) {
		this.size = size;
		queue = new LinkedList<V>();
	}
	
	public void push(V element) {
		if (queue.size() < size) {
			queue.add(0, element);
		} else {
			queue.remove(queue.size() - 1);
			queue.add(0, element);
		}
	}
	
	public Iterator<V> getIterator() {
		return queue.iterator();
	}
	
	public Integer getSize() {
		return queue.size();
	}
	
	public V getElement(Integer index) {
		return queue.get(index);
	} 
	
	public V pop() {
		return queue.get(0);
	}
	
	public List<V> getList() {
		return queue;
	}
}